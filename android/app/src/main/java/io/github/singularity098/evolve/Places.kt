package io.github.singularity098.evolve

import android.content.Context
import android.location.Geocoder
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * A name for where a workout started ("Central Park"): OpenStreetMap's
 * Nominatim first (it knows parks and trails), then Android's geocoder.
 * Blocking: call off the main thread.
 */
object Places {
    fun nameFor(context: Context, lat: Double, lon: Double): String? =
        try {
            nominatim(lat, lon)
        } catch (e: Exception) {
            Log.w("evolve", "nominatim failed", e)
            null
        } ?: try {
            geocoder(context, lat, lon)
        } catch (e: Exception) {
            Log.w("evolve", "geocoder failed", e)
            null
        }

    private fun nominatim(lat: Double, lon: Double): String? {
        val url = URL(
            String.format(
                Locale.ROOT,
                "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=%.6f&lon=%.6f&zoom=16&accept-language=%s",
                lat, lon, Locale.getDefault().language,
            )
        )
        val c = url.openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        // Nominatim's usage policy asks apps to say who they are.
        c.setRequestProperty("User-Agent", "Evolve-Android/0.1 (fitness tracker)")
        try {
            if (c.responseCode != 200) return null
            val j = JSONObject(c.inputStream.bufferedReader().readText())
            val a = j.optJSONObject("address")
            val keys = listOf("park", "nature_reserve", "leisure", "tourism", "natural", "neighbourhood", "suburb", "village", "town", "city")
            val name = j.optString("name").takeIf { it.isNotBlank() && !it.first().isDigit() }
            return name ?: keys.firstNotNullOfOrNull { k -> a?.optString(k)?.takeIf { it.isNotBlank() } }
        } finally {
            c.disconnect()
        }
    }

    @Suppress("DEPRECATION")
    private fun geocoder(context: Context, lat: Double, lon: Double): String? {
        if (!Geocoder.isPresent()) return null
        val a = Geocoder(context).getFromLocation(lat, lon, 1)?.firstOrNull() ?: return null
        val feature = a.featureName?.takeIf { it.isNotBlank() && !it.first().isDigit() }
        return feature ?: a.subLocality ?: a.locality
    }
}
