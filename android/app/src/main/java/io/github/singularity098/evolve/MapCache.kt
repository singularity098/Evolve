package io.github.singularity098.evolve

import android.content.Context
import android.util.Log
import io.github.singularity098.evolve.core.haversineMeters
import org.json.JSONObject
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import kotlin.math.cos

/**
 * Keeps the map around a GPS workout on the phone, so a hike that loses
 * signal still has its map: on the first fix, and again whenever the
 * workout has gone half the way to the edge, everything within the
 * radius chosen in Settings (10 miles at first) is downloaded, at every
 * zoom the vector tiles have. Areas older than two weeks are deleted.
 */
object MapCache {
    /** OpenFreeMap: free vector tiles, no key, no limits, offline use allowed. */
    const val STYLE = "https://tiles.openfreemap.org/styles/liberty"
    private const val MAX_AGE_MS = 14L * 24 * 3600 * 1000

    private var lastLat = Double.NaN
    private var lastLon = Double.NaN

    /** Main thread only (MapLibre's rule). */
    fun ensure(context: Context, lat: Double, lon: Double, miles: Double) {
        val radius = (miles.coerceIn(1.0, 50.0)) * 1609.344
        if (!lastLat.isNaN() && haversineMeters(lastLat, lastLon, lat, lon) < radius / 2) return
        lastLat = lat
        lastLon = lon
        val dLat = radius / 111_320.0
        val dLon = radius / (111_320.0 * cos(Math.toRadians(lat)).coerceAtLeast(0.05))
        val bounds = LatLngBounds.from(
            (lat + dLat).coerceAtMost(85.0), lon + dLon, (lat - dLat).coerceAtLeast(-85.0), lon - dLon,
        )
        val ratio = context.resources.displayMetrics.density
        val def = OfflineTilePyramidRegionDefinition(STYLE, bounds, 0.0, 14.0, ratio)
        val meta = JSONObject().put("t", System.currentTimeMillis()).toString().toByteArray()
        OfflineManager.getInstance(context).createOfflineRegion(def, meta, object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) {
                offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                    override fun onStatusChanged(status: OfflineRegionStatus) {
                        if (status.isComplete) {
                            Log.i("evolve", "map cached: ${status.completedResourceCount} resources")
                            offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                        }
                    }

                    override fun onError(error: OfflineRegionError) {
                        Log.w("evolve", "map cache: ${error.reason} ${error.message}")
                    }

                    override fun mapboxTileCountLimitExceeded(limit: Long) {}
                })
                offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
            }

            override fun onError(error: String) {
                Log.w("evolve", "map cache: $error")
            }
        })
    }

    fun forget() {
        lastLat = Double.NaN
        lastLon = Double.NaN
    }

    /** Deletes cached areas from old workouts. */
    fun prune(context: Context) {
        try {
            OfflineManager.getInstance(context).listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(offlineRegions: Array<OfflineRegion>?) {
                    val now = System.currentTimeMillis()
                    offlineRegions?.forEach { r ->
                        val t = try {
                            JSONObject(String(r.metadata)).optLong("t", 0)
                        } catch (_: Exception) {
                            0L
                        }
                        if (now - t > MAX_AGE_MS) {
                            r.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                                override fun onDelete() {}
                                override fun onError(error: String) {}
                            })
                        }
                    }
                }

                override fun onError(error: String) {}
            })
        } catch (t: Throwable) {
            Log.w("evolve", "map cache prune failed", t)
        }
    }
}
