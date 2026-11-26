package io.github.singularity098.evolve.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import io.github.singularity098.evolve.core.MapMode
import io.github.singularity098.evolve.core.Settings
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer

private fun c(argb: Long) = argb.toInt()

/** OpenFreeMap's styles: free vector tiles, no key, no limits, offline use allowed. All share one set of tiles. */
private const val OFM = "https://tiles.openfreemap.org/styles/"

/**
 * The colours a [MapStyle] repaints its base with (ARGB). The base is
 * OpenFreeMap's Positron, whose plain layers take new colours well; [widths]
 * scales the roads and paths, so they read at a glance.
 */
data class MapPalette(
    val background: Int,
    val land: Int,
    val park: Int,
    val wood: Int,
    val water: Int,
    val ice: Int,
    val building: Int,
    val buildingLine: Int,
    val path: Int,
    val minor: Int,
    val major: Int,
    val motorway: Int,
    val casing: Int,
    val rail: Int,
    val boundary: Int,
    val label: Int,
    val roadLabel: Int,
    val waterLabel: Int,
    val halo: Int,
    val widths: Float,
)

/** A map style: [base] is the style's URL, repainted with [palette] if it has one. */
data class MapStyle(
    val name: String,
    val dark: Boolean,
    val base: String,
    val palette: MapPalette? = null,
)

/** The light map: OpenFreeMap's Bright, as it comes. */
val LightMap = MapStyle("Bright", false, OFM + "bright")

/** The dark map: deep navy, pale blue roads, amber highways. */
val DarkMap = MapStyle(
    "Midnight", true, OFM + "positron",
    MapPalette(
        background = c(0xFF0B1426), land = c(0xFF0F1A30), park = c(0xFF0F2E2C), wood = c(0xFF12362F),
        water = c(0xFF173F6B), ice = c(0xFF1C2840), building = c(0xFF1B2843), buildingLine = c(0xFF2D3D60),
        path = c(0xFFEBD48A), minor = c(0xFF4F6898), major = c(0xFF95B5EC), motorway = c(0xFFFFC46B),
        casing = c(0xFF050A14), rail = c(0xFF5F749C), boundary = c(0xFF8F7EBE),
        label = c(0xFFEDF4FF), roadLabel = c(0xFFBCCFEE), waterLabel = c(0xFFA2D2FF), halo = c(0xFF050A14),
        widths = 1.25f,
    ),
)

/** The base styles worth keeping offline. */
val MapStylesToCache: List<String> = listOf(LightMap.base, DarkMap.base).distinct()

/**
 * The map style to show: [pinned] (the sun/moon button) if set, else as
 * Settings say, which by default is dark indoors and at night (the light
 * sensor) and light outside in daylight.
 */
@Composable
fun rememberMapStyle(s: Settings, pinned: Boolean?): MapStyle {
    val daylight by rememberDaylight()
    val dark = pinned ?: when (s.mapMode) {
        MapMode.LIGHT -> false
        MapMode.DARK -> true
        MapMode.AUTO -> !daylight
    }
    return if (dark) DarkMap else LightMap
}

private fun widths(base: Float, vararg stops: Pair<Float, Float>): Expression =
    Expression.interpolate(
        Expression.exponential(base), Expression.zoom(),
        *stops.map { (z, w) -> Expression.stop(z, w) }.toTypedArray(),
    )

/** Repaints a Positron-based style's layers with [p]. Layers it doesn't know are left alone. */
fun applyPalette(style: Style, p: MapPalette) {
    val s = p.widths
    for (layer in style.layers) {
        val id = layer.id
        try {
            when (layer) {
                is BackgroundLayer -> layer.setProperties(PropertyFactory.backgroundColor(p.background))
                is FillLayer -> when {
                    id == "water" -> layer.setProperties(PropertyFactory.fillColor(p.water))
                    id == "park" -> layer.setProperties(PropertyFactory.fillColor(p.park), PropertyFactory.fillOpacity(1f))
                    id == "landcover_wood" -> layer.setProperties(PropertyFactory.fillColor(p.wood))
                    id == "landuse_residential" -> layer.setProperties(PropertyFactory.fillColor(p.land))
                    id.startsWith("landcover_") -> layer.setProperties(PropertyFactory.fillColor(p.ice))
                    id == "building" -> layer.setProperties(PropertyFactory.fillColor(p.building), PropertyFactory.fillOutlineColor(p.buildingLine))
                    id.startsWith("aeroway") -> layer.setProperties(PropertyFactory.fillColor(p.minor))
                    id.startsWith("road_area") -> layer.setProperties(PropertyFactory.fillColor(p.land))
                }
                is LineLayer -> when {
                    id == "waterway" -> layer.setProperties(PropertyFactory.lineColor(p.water))
                    id.startsWith("aeroway") -> layer.setProperties(PropertyFactory.lineColor(p.minor))
                    id == "road_pier" -> layer.setProperties(PropertyFactory.lineColor(p.land))
                    id == "highway_path" -> layer.setProperties(
                        PropertyFactory.lineColor(p.path),
                        PropertyFactory.lineOpacity(1f),
                        PropertyFactory.lineDasharray(arrayOf(2f, 1f)),
                        PropertyFactory.lineWidth(widths(1.2f, 12f to 0.8f * s, 14f to 1.2f * s, 16f to 2f * s, 20f to 6f * s)),
                    )
                    id == "highway_minor" -> layer.setProperties(
                        PropertyFactory.lineColor(p.minor),
                        PropertyFactory.lineOpacity(1f),
                        PropertyFactory.lineWidth(widths(1.55f, 11f to 0.8f * s, 13f to 1.8f * s, 20f to 20f * s)),
                    )
                    id == "highway_major_casing" -> layer.setProperties(
                        PropertyFactory.lineColor(p.casing),
                        PropertyFactory.lineWidth(widths(1.3f, 10f to 3f * s, 20f to 23f * s)),
                    )
                    id == "highway_major_inner" -> layer.setProperties(
                        PropertyFactory.lineColor(p.major),
                        PropertyFactory.lineWidth(widths(1.3f, 10f to 2f * s, 20f to 20f * s)),
                    )
                    id == "highway_major_subtle" -> layer.setProperties(PropertyFactory.lineColor(p.major), PropertyFactory.lineWidth(2f * s))
                    id.contains("motorway") && id.endsWith("casing") -> layer.setProperties(
                        PropertyFactory.lineColor(p.casing),
                        PropertyFactory.lineOpacity(1f),
                        PropertyFactory.lineWidth(widths(1.4f, 5.8f to 0f, 6f to 3f * s, 20f to 40f * s)),
                    )
                    id.contains("motorway") -> layer.setProperties(PropertyFactory.lineColor(p.motorway))
                    id.startsWith("railway") && id.endsWith("dashline") -> layer.setProperties(PropertyFactory.lineColor(p.land))
                    id.startsWith("railway") -> layer.setProperties(PropertyFactory.lineColor(p.rail))
                    id.startsWith("boundary") -> layer.setProperties(PropertyFactory.lineColor(p.boundary), PropertyFactory.lineOpacity(1f))
                }
                is SymbolLayer -> {
                    val text = when {
                        id.startsWith("label_") -> p.label
                        id.startsWith("water") -> p.waterLabel
                        id.startsWith("highway-name") || id == "airport" -> p.roadLabel
                        else -> null // shields: their own colours, on their own signs
                    }
                    if (text != null) layer.setProperties(
                        PropertyFactory.textColor(text),
                        PropertyFactory.textHaloColor(p.halo),
                        PropertyFactory.textHaloWidth(1.6f),
                        PropertyFactory.textHaloBlur(0.2f),
                    )
                }
            }
        } catch (_: Exception) {
            // A property the layer type refuses: leave that layer as it was.
        }
    }
    // Positron leaves city parks and lawns (landcover "grass": Central Park, say) unpainted.
    try {
        val grass = FillLayer("evolve-grass", "openmaptiles")
            .withSourceLayer("landcover")
            .withFilter(Expression.eq(Expression.get("class"), Expression.literal("grass")))
            .withProperties(PropertyFactory.fillColor(p.park), PropertyFactory.fillOpacity(0.85f))
        if (style.getLayer("landcover_wood") != null) style.addLayerBelow(grass, "landcover_wood") else style.addLayer(grass)
    } catch (_: Exception) {
    }
}
