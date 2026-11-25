package io.github.singularity098.evolve

import android.app.Application
import org.maplibre.android.MapLibre

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
        MapLibre.getInstance(this)
        store = Store(this)
        MapCache.prune(this)
        // A workout that was under way when the app was last closed goes on.
        if (store.session.value != null) WorkoutService.start(this)
    }
}
