package io.github.singularity098.evolve

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.singularity098.evolve.ui.MapStylesToCache
import io.github.singularity098.evolve.core.BreathPhase
import io.github.singularity098.evolve.core.Intensity
import io.github.singularity098.evolve.core.Session
import io.github.singularity098.evolve.core.TimerMode
import io.github.singularity098.evolve.core.TrackPoint
import io.github.singularity098.evolve.core.formatDistance
import io.github.singularity098.evolve.core.formatDuration
import io.github.singularity098.evolve.core.kindLabel
import io.github.singularity098.evolve.core.kindUsesGps

/**
 * Runs while a workout is under way, so it goes on with the screen off or
 * the app in the background: the notification, the GPS for walks, runs,
 * hikes and rides, and the sounds that mark the end of a rest, each
 * interval, a meditation's end and a breath round's recovery.
 */
class WorkoutService : Service() {
    companion object {
        private const val CHANNEL = "workout"
        private const val ID = 1

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, WorkoutService::class.java))
            } catch (e: Exception) {
                // Not allowed from the background (Android 12+): MainActivity starts it when opened.
                Log.w("evolve", "the workout service could not start", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WorkoutService::class.java))
        }
    }

    private val store get() = (application as EvolveApplication).store
    private lateinit var tones: Tones
    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var locationManager: LocationManager? = null
    private var gpsOn = false
    private var placeAsked = false

    // What has been sounded already, so each cue plays once.
    private var restNotified: Long? = null
    private var lastInterval = -1
    private var lastTickSecond = -1L
    private var timerDone = false
    private var meditationDone = false
    private var lastNotified = 0L

    // Every method spelled out: before Android 11 the others had no default.
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = onLocation(location)

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        tones = Tones(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Workout in progress", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val session = store.session.value
        if (session == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val gps = kindUsesGps(session.kind) && hasLocation()
        val type = when {
            Build.VERSION.SDK_INT >= 34 ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH or (if (gps) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
            Build.VERSION.SDK_INT >= 29 && gps -> ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            else -> 0
        }
        var withGps = gps
        try {
            ServiceCompat.startForeground(this, ID, notification("Starting…"), type)
        } catch (e: Exception) {
            // Location can't start from the background: carry on without it
            // until the app is opened again (MainActivity restarts this).
            Log.w("evolve", "startForeground failed", e)
            withGps = false
            try {
                ServiceCompat.startForeground(this, ID, notification("Starting…"), if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0)
            } catch (e2: Exception) {
                Log.e("evolve", "startForeground failed again", e2)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        if (wakeLock == null) {
            // The timers and cues must keep time with the screen off.
            wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "evolve:workout")
                .apply { acquire(6 * 3600 * 1000L) }
        }
        if (withGps) startGps()
        // A rest that ended while nothing was running isn't announced late.
        session.lifting?.restUntilMs?.let { if (it <= System.currentTimeMillis()) restNotified = it }
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        stopGps()
        MapCache.forget()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    // ---------------------------------------------------------------- GPS

    private fun hasLocation() =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun startGps() {
        if (gpsOn || !hasLocation()) return
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        locationManager = lm
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
            gpsOn = true
            // Somewhere to show the map until the first fix.
            if (store.location.value == null) {
                (lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER))
                    ?.let { store.location.value = it }
            }
        } catch (e: Exception) {
            Log.e("evolve", "GPS failed", e)
            store.say("The GPS could not be started")
        }
    }

    private fun stopGps() {
        if (gpsOn) locationManager?.removeUpdates(listener)
        gpsOn = false
    }

    private fun onLocation(loc: Location) {
        store.location.value = loc
        val s = store.session.value ?: return
        if (s.pausedAtMs != null) return
        val autoPaused = store.addPoint(
            TrackPoint(
                lat = loc.latitude,
                lon = loc.longitude,
                alt = if (loc.hasAltitude()) loc.altitude else null,
                tMs = loc.time,
                accuracy = if (loc.hasAccuracy()) loc.accuracy.toDouble() else 10.0,
                seg = 0u,
            )
        )
        if (autoPaused) tones.go(hard = false)
        if (loc.hasAccuracy() && loc.accuracy < 50) {
            MapCache.ensure(this, loc.latitude, loc.longitude, store.settings.mapCacheMiles, MapStylesToCache)
            if (!placeAsked && s.gps?.placeName.isNullOrEmpty()) {
                placeAsked = true
                val lat = loc.latitude
                val lon = loc.longitude
                Thread {
                    val name = Places.nameFor(this, lat, lon)
                    if (name != null) handler.post { store.live { setPlaceName(name) } }
                }.start()
            }
        }
    }

    // ---------------------------------------------------------------- cues

    private val tick = object : Runnable {
        override fun run() {
            val s = store.session.value
            if (s == null) {
                stopSelf()
                return
            }
            // A cue or the notification failing must not take the app down with it.
            try {
                step(s)
            } catch (t: Throwable) {
                Log.e("evolve", "workout tick failed", t)
            }
            handler.postDelayed(this, 200)
        }
    }

    private fun step(s: Session) {
        val now = System.currentTimeMillis()
        val paused = s.pausedAtMs != null
        var text = formatDuration(s.startedAtMs.let { (now - it - s.pausedMs - (s.pausedAtMs?.let { p -> now - p } ?: 0)) / 1000.0 })

        s.lifting?.let { l ->
            val until = l.restUntilMs
            if (until != null && now >= until && restNotified != until) {
                restNotified = until
                tones.restOver()
            }
            if (until != null && now < until) text += " · rest ${formatDuration((until - now) / 1000.0)}"
            val done = l.exercises.sumOf { e -> e.sets.count { it.done } }
            val all = l.exercises.sumOf { it.sets.size }
            text += " · $done/$all sets"
        }
        s.timer?.let { t ->
            val st = store.engine.timerStatus(now)
            if (st != null && !paused) {
                val i = st.index.toInt()
                if (lastInterval != -1 && i != lastInterval && !st.finished) {
                    tones.go(st.intensity == Intensity.HARD || st.intensity == Intensity.MAX)
                }
                lastInterval = i
                if (t.mode != TimerMode.STOPWATCH && !st.finished) {
                    val secLeft = (st.stepRemainingMs + 999) / 1000
                    if (secLeft in 1..3 && secLeft != lastTickSecond) tones.tick()
                    lastTickSecond = secLeft
                    text += " · ${st.label} ${formatDuration(st.stepRemainingMs / 1000.0)}"
                }
                if (st.finished && !timerDone && t.mode != TimerMode.STOPWATCH) {
                    timerDone = true
                    tones.done()
                }
            }
        }
        s.meditation?.let { m ->
            val el = s.startedAtMs.let { now - it - s.pausedMs - (s.pausedAtMs?.let { p -> now - p } ?: 0) }
            if (!paused && !meditationDone && el >= m.targetSecs.toLong() * 1000) {
                meditationDone = true
                tones.bell()
            }
        }
        s.breath?.let { b ->
            val st = store.engine.breathStatus(now)
            if (st != null && b.phase == BreathPhase.RECOVERY && st.recoveryRemainingMs <= 0) {
                store.live { breathAdvance(now) }
                tones.breathe()
            }
            if (st != null) text += " · round ${st.round}"
        }
        store.gps.value?.let { g -> text += " · " + formatDistance(g.distanceM, store.settings.distanceUnit) }
        if (paused) text = (if (s.gps?.autoPaused == true) "Paused (too fast) · " else "Paused · ") + text

        if (now - lastNotified >= 1000) {
            lastNotified = now
            getSystemService(NotificationManager::class.java).notify(ID, notification(text))
        }
    }

    private fun notification(text: String): Notification {
        val s = store.session.value
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_evolve)
            .setContentTitle(s?.let { kindLabel(it.kind) } ?: "Evolve")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
