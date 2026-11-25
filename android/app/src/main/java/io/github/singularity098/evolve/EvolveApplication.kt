package io.github.singularity098.evolve

import android.app.Application
import org.maplibre.android.MapLibre
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

/**
 * The process: the engine lives here, not in the activity, so a workout
 * (and its notification) carries on while no activity is showing, and an
 * activity that comes back finds everything as it was.
 */
class EvolveApplication : Application() {
    lateinit var store: Store
        private set

    override fun onCreate() {
        super.onCreate()
        recordCrashes()
        MapLibre.getInstance(this)
        store = Store(this)
        MapCache.prune(this)
        // A workout that was under way when the app was last closed goes on.
        if (store.session.value != null) WorkoutService.start(this)
    }

    /**
     * A crash ends the process, which on a phone looks like the app just closing. Keep what went
     * wrong, so the next launch can show it and it can be copied into a bug report.
     */
    private fun recordCrashes() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                val trace = StringWriter().also { e.printStackTrace(PrintWriter(it)) }
                crashFile().writeText(
                    "Evolve ${packageManager.getPackageInfo(packageName, 0).versionName} · " +
                        "Android ${android.os.Build.VERSION.RELEASE} · ${android.os.Build.MODEL}\n" +
                        "${Instant.now()} · thread ${thread.name}\n\n$trace"
                )
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, e)
        }
    }

    fun crashFile() = File(filesDir, "last-crash.txt")
}
