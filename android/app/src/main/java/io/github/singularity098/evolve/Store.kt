package io.github.singularity098.evolve

import android.content.Context
import android.location.Location
import android.net.Uri
import android.util.Log
import io.github.singularity098.evolve.core.Engine
import io.github.singularity098.evolve.core.GpsLive
import io.github.singularity098.evolve.core.AppLibrary
import io.github.singularity098.evolve.core.Session
import io.github.singularity098.evolve.core.Settings
import io.github.singularity098.evolve.core.TrackPoint
import io.github.singularity098.evolve.core.exportFileName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.TimeZone
import java.util.concurrent.Executors

/**
 * The app's state, as the Rust engine last told it, for Compose to show;
 * and the way to the engine. One per process (see [EvolveApplication]).
 *
 * The engine keeps everything in memory and writes each change to disk
 * at once. Changes to the workout under way are quick (a small file) and
 * run where they're asked for, so a tap shows at once; the rest (the
 * library, imports and exports) run in order on a thread of their own.
 */
class Store(private val context: Context) {
    val engine: Engine = Engine(context.filesDir.absolutePath)

    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "engine-io") }
    private val ioDispatcher = io.asCoroutineDispatcher()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _library = MutableStateFlow(engine.library())
    val library: StateFlow<AppLibrary> = _library
    private val _session = MutableStateFlow(engine.session())
    val session: StateFlow<Session?> = _session
    /** Goes up with every change to the history (or body weights), for lists to read it again. */
    private val _historyVersion = MutableStateFlow(0)
    val historyVersion: StateFlow<Int> = _historyVersion

    /** The GPS workout's numbers, and its trail. */
    private val _gps = MutableStateFlow(engine.session()?.gps)
    val gps: StateFlow<GpsLive?> = _gps
    private val _trail = MutableStateFlow(engine.sessionTrack())
    val trail: StateFlow<List<TrackPoint>> = _trail
    /** The last fix, kept or not (the "you are here" dot). */
    val location = MutableStateFlow<Location?>(null)

    /** Short messages for the snackbar. */
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    /** A workout to show in the History tab (after saving one, or from the calendar). */
    val showWorkout = MutableSharedFlow<String>(extraBufferCapacity = 4)

    val settings: Settings get() = _library.value.settings

    fun say(text: String) {
        messages.tryEmit(text)
    }

    // ------------------------------------------------------------ library

    /** Runs [f] on the engine thread, then shows what changed. */
    fun act(f: Engine.() -> Unit) {
        io.execute {
            try {
                engine.f()
            } catch (t: Throwable) {
                Log.e("evolve", "engine call failed", t)
                say(t.message ?: "Something went wrong")
            }
            refresh()
        }
    }

    suspend fun <T> ask(f: Engine.() -> T): T? = withContext(ioDispatcher) {
        try {
            engine.f()
        } catch (t: Throwable) {
            Log.e("evolve", "engine call failed", t)
            say(t.message ?: "Something went wrong")
            null
        }
    }

    fun updateSettings(f: (Settings) -> Settings) {
        val next = f(settings)
        _library.value = _library.value.copy(settings = next)
        act { setSettings(next) }
    }

    fun refresh() {
        _library.value = engine.library()
        _session.value = engine.session()
        _historyVersion.value += 1
    }

    // ------------------------------------------------------------ session

    /** Changes the workout under way, at once. */
    fun live(f: Engine.() -> Session?) {
        try {
            val s = engine.f()
            if (s != null) _session.value = s
        } catch (t: Throwable) {
            Log.e("evolve", "session call failed", t)
            say(t.message ?: "Something went wrong")
        }
    }

    /** Starts a workout (and its service, which keeps it going with the screen off). */
    fun start(f: Engine.() -> Session) {
        try {
            val s = engine.f()
            _session.value = s
            _gps.value = s.gps
            _trail.value = emptyList()
            location.value = null
            WorkoutService.start(context)
        } catch (t: Throwable) {
            Log.e("evolve", "start failed", t)
            say(t.message ?: "The activity could not start")
        }
    }

    /** Takes a GPS fix; true if it paused the workout (too fast for too long: a car). */
    fun addPoint(p: TrackPoint): Boolean {
        try {
            val u = engine.addPoint(p)
            _gps.value = u.live
            u.kept?.let { _trail.value = _trail.value + it }
            if (u.paused) {
                // Paused back where the speed went up: the trail may have lost its last few points.
                _session.value = engine.session()
                _trail.value = engine.sessionTrack()
                say("Paused: moving too fast for this activity (a car?). Resume to go on.")
            }
            return u.paused
        } catch (t: Throwable) {
            Log.e("evolve", "addPoint failed", t)
            return false
        }
    }

    /** Steps the pedometer counted since it last reported (not counted while paused). */
    fun addSteps(n: Int) {
        if (n <= 0) return
        try {
            val s = engine.addSteps(n.toUInt())
            _session.value = s
            _gps.value = s.gps
        } catch (t: Throwable) {
            Log.e("evolve", "addSteps failed", t)
        }
    }

    /** Saves the workout under way to the history, and exports if asked to. */
    fun finish(name: String, notes: String, distanceM: Double?, updateRoutine: Boolean) {
        try {
            val id = engine.finishSession(System.currentTimeMillis(), name, notes, distanceM, updateRoutine)
            sessionEnded()
            showWorkout.tryEmit(id)
            if (settings.autoExport) autoExport()
        } catch (t: Throwable) {
            Log.e("evolve", "finish failed", t)
            say(t.message ?: "The activity could not be saved")
        }
    }

    fun discard() {
        try {
            engine.discardSession()
        } catch (t: Throwable) {
            Log.e("evolve", "discard failed", t)
        }
        sessionEnded()
    }

    private fun sessionEnded() {
        _session.value = null
        _gps.value = null
        _trail.value = emptyList()
        WorkoutService.stop(context)
        refresh()
    }

    // ------------------------------------------------------ import/export

    private fun offsetMin(now: Long) = TimeZone.getDefault().getOffset(now) / 60_000

    /** Everything to the export folder; with [auto], pruned to the last few automatic ones. */
    fun exportNow(auto: Boolean = false) {
        val uri = settings.exportUri
        if (uri.isEmpty()) {
            say("Choose an export folder in Settings first")
            return
        }
        scope.launch {
            val now = System.currentTimeMillis()
            val ok = withContext(ioDispatcher) {
                try {
                    val json = engine.exportJson(now)
                    val name = exportFileName(if (auto) "auto" else "backup", now, offsetMin(now)) + ".gz"
                    // octet-stream, so the folder keeps the name as given (a gzip type can earn a second .gz).
                    Exports.write(context, Uri.parse(uri), name, "application/octet-stream", Exports.gzip(json.toByteArray())).also {
                        if (auto && it) Exports.prune(context, Uri.parse(uri), "evolve-auto-", settings.autoExportKeep.toInt().coerceAtLeast(1))
                    }
                } catch (t: Throwable) {
                    Log.e("evolve", "export failed", t)
                    false
                }
            }
            say(if (ok) (if (auto) "Exported to your backup folder" else "Exported") else "The export failed: choose the folder again in Settings")
        }
    }

    private fun autoExport() = exportNow(auto = true)

    fun import(uri: Uri, replace: Boolean) {
        scope.launch {
            val r = withContext(ioDispatcher) {
                try {
                    val text = Exports.read(context, uri)
                    engine.importJson(text, replace)
                } catch (t: Throwable) {
                    Log.e("evolve", "import failed", t)
                    say("The import failed: ${t.message}")
                    null
                }
            }
            refresh()
            if (r != null) {
                say("Imported ${r.workouts} activities, ${r.bodyWeights} weigh-ins, ${r.routines} routines, ${r.programs} programs")
            }
        }
    }

    fun writeGpx(uri: Uri, workoutId: String) {
        scope.launch {
            val ok = withContext(ioDispatcher) {
                try {
                    val gpx = engine.gpx(workoutId) ?: return@withContext false
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(gpx.toByteArray()) } != null
                } catch (t: Throwable) {
                    Log.e("evolve", "gpx failed", t)
                    false
                }
            }
            say(if (ok) "GPX saved" else "The GPX could not be saved")
        }
    }
}
