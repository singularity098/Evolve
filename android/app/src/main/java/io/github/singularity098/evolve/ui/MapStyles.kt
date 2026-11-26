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

/**
 * A map style to pick in Settings: [base] is the style's URL, repainted
 * with [palette] if it has one. The names are stored in settings.json:
 * never rename one.
 */
data class MapStyle(
    val name: String,
    val dark: Boolean,
    val about: String,
    val base: String,
    val palette: MapPalette? = null,
    /** For a style used as it comes: the colours of its swatch in Settings. */
    private val looksLike: MapPalette? = null,
) {
    /** The colours Settings draws this style's swatch with. */
    val swatch: MapPalette get() = palette ?: looksLike!!
}

/** A swatch's colours, for the styles used as they come. */
private fun swatchOf(bg: Long, park: Long, water: Long, major: Long, casing: Long, minor: Long, path: Long, label: Long) = MapPalette(
    background = c(bg), land = c(bg), park = c(park), wood = c(park), water = c(water), ice = c(bg),
    building = c(bg), buildingLine = c(bg), path = c(path), minor = c(minor), major = c(major), motorway = c(major),
    casing = c(casing), rail = c(minor), boundary = c(minor), label = c(label), roadLabel = c(label),
    waterLabel = c(label), halo = c(bg), widths = 1f,
)


val LightMapStyles = listOf(
    MapStyle(
        "Liberty", false, "OpenFreeMap's colourful map, with shops and places", OFM + "liberty",
        looksLike = swatchOf(0xFFF8F4F0, 0xFFD8E8C8, 0xFFA0C8F0, 0xFFFFEEAA, 0xFFE9AC77, 0xFFFFFFFF, 0xFFCBA884, 0xFF333333),
    ),
    MapStyle(
        "Bright", false, "Bold roads and lively colours", OFM + "bright",
        looksLike = swatchOf(0xFFF8F4F0, 0xFFC8E3B0, 0xFFA0CFDF, 0xFFFFA35C, 0xFFE5A04C, 0xFFFFFFFF, 0xFFCBA884, 0xFF333333),
    ),
    MapStyle(
        "Paper", false, "Quiet greys, crisp roads and dark labels", OFM + "positron",
        MapPalette(
            background = c(0xFFF4F3F0), land = c(0xFFECEAE5), park = c(0xFFD3E6CC), wood = c(0xFFC4DDBB),
            water = c(0xFFA6C8E8), ice = c(0xFFFFFFFF), building = c(0xFFDCD8D1), buildingLine = c(0xFFBDB7AE),
            path = c(0xFF8A5A2B), minor = c(0xFFB3ADA3), major = c(0xFFFFFFFF), motorway = c(0xFFF5C060),
            casing = c(0xFF8E887F), rail = c(0xFF8A8A8A), boundary = c(0xFF9673AE),
            label = c(0xFF151515), roadLabel = c(0xFF333333), waterLabel = c(0xFF24527F), halo = c(0xFFFFFFFF),
            widths = 1.15f,
        ),
    ),
    MapStyle(
        "Ink", false, "Highest contrast: dark roads on white", OFM + "positron",
        MapPalette(
            background = c(0xFFFFFFFF), land = c(0xFFF4F4F4), park = c(0xFFC8E8BF), wood = c(0xFFAED9A2),
            water = c(0xFF86BEF0), ice = c(0xFFFFFFFF), building = c(0xFFDDDDDD), buildingLine = c(0xFF999999),
            path = c(0xFFB3400A), minor = c(0xFF666666), major = c(0xFF2A2A2A), motorway = c(0xFFC62828),
            casing = c(0xFFFFFFFF), rail = c(0xFF505050), boundary = c(0xFF7B1FA2),
            label = c(0xFF000000), roadLabel = c(0xFF000000), waterLabel = c(0xFF0B3770), halo = c(0xFFFFFFFF),
            widths = 1.3f,
        ),
    ),
    MapStyle(
        "Trail", false, "Warm and outdoorsy: green woods, red trails", OFM + "positron",
        MapPalette(
            background = c(0xFFF3EEDF), land = c(0xFFEDE5D1), park = c(0xFFC6E0AC), wood = c(0xFFA9D094),
            water = c(0xFF89BDE1), ice = c(0xFFFFFFFF), building = c(0xFFD8CBB4), buildingLine = c(0xFFB5A386),
            path = c(0xFFC0392B), minor = c(0xFFA3947B), major = c(0xFFF2A65A), motorway = c(0xFFE06A35),
            casing = c(0xFF7F6443), rail = c(0xFF746450), boundary = c(0xFF8B67AD),
            label = c(0xFF261C12), roadLabel = c(0xFF46372A), waterLabel = c(0xFF1B5687), halo = c(0xFFFFFBF0),
            widths = 1.2f,
        ),
    ),
)

val DarkMapStyles = listOf(
    MapStyle(
        "Night", true, "Near black, bright roads and white labels", OFM + "positron",
        MapPalette(
            background = c(0xFF101114), land = c(0xFF16171B), park = c(0xFF15291F), wood = c(0xFF193323),
            water = c(0xFF0F2D4D), ice = c(0xFF22262B), building = c(0xFF25272D), buildingLine = c(0xFF3A3D45),
            path = c(0xFFE0C98F), minor = c(0xFF656B78), major = c(0xFFA9B0BD), motorway = c(0xFFE6B95A),
            casing = c(0xFF000000), rail = c(0xFF737782), boundary = c(0xFF8C79A0),
            label = c(0xFFF4F6FA), roadLabel = c(0xFFD0D5DE), waterLabel = c(0xFF92C6F7), halo = c(0xFF000000),
            widths = 1.25f,
        ),
    ),
    MapStyle(
        "Midnight", true, "Deep navy, pale blue roads, amber highways", OFM + "positron",
        MapPalette(
            background = c(0xFF0B1426), land = c(0xFF0F1A30), park = c(0xFF0F2E2C), wood = c(0xFF12362F),
            water = c(0xFF173F6B), ice = c(0xFF1C2840), building = c(0xFF1B2843), buildingLine = c(0xFF2D3D60),
            path = c(0xFFEBD48A), minor = c(0xFF4F6898), major = c(0xFF95B5EC), motorway = c(0xFFFFC46B),
            casing = c(0xFF050A14), rail = c(0xFF5F749C), boundary = c(0xFF8F7EBE),
            label = c(0xFFEDF4FF), roadLabel = c(0xFFBCCFEE), waterLabel = c(0xFFA2D2FF), halo = c(0xFF050A14),
            widths = 1.25f,
        ),
    ),
    MapStyle(
        "Fiord", true, "Slate blue and softer, still easy to read", OFM + "positron",
        MapPalette(
            background = c(0xFF2B3A48), land = c(0xFF314252), park = c(0xFF2F4A44), wood = c(0xFF325046),
            water = c(0xFF1A2734), ice = c(0xFF3C4E60), building = c(0xFF3B4E61), buildingLine = c(0xFF4C6277),
            path = c(0xFFE8D5A5), minor = c(0xFF7389A0), major = c(0xFFB9CAD9), motorway = c(0xFFF1D18C),
            casing = c(0xFF1B2733), rail = c(0xFF8193A4), boundary = c(0xFFA998C2),
            label = c(0xFFFFFFFF), roadLabel = c(0xFFE0E8EF), waterLabel = c(0xFFAAD0F0), halo = c(0xFF16202A),
            widths = 1.2f,
        ),
    ),
    MapStyle(
        "Red light", true, "Red on black, to keep your night vision", OFM + "positron",
        MapPalette(
            background = c(0xFF000000), land = c(0xFF070000), park = c(0xFF150404), wood = c(0xFF1A0505),
            water = c(0xFF220509), ice = c(0xFF120303), building = c(0xFF1B0606), buildingLine = c(0xFF320C0C),
            path = c(0xFFC23A2F), minor = c(0xFF6E1B16), major = c(0xFFB42B22), motorway = c(0xFFFF4A3A),
            casing = c(0xFF000000), rail = c(0xFF5E1712), boundary = c(0xFF7E1F1A),
            label = c(0xFFFF6E5E), roadLabel = c(0xFFDD4C40), waterLabel = c(0xFFBC424C), halo = c(0xFF000000),
            widths = 1.25f,
        ),
    ),
    MapStyle(
        "Contrast", true, "Highest contrast: black, white and yellow", OFM + "positron",
        MapPalette(
            background = c(0xFF000000), land = c(0xFF000000), park = c(0xFF0B2A14), wood = c(0xFF0E3418),
            water = c(0xFF0A3C80), ice = c(0xFF1A1A1A), building = c(0xFF262626), buildingLine = c(0xFF4A4A4A),
            path = c(0xFFFFE066), minor = c(0xFFBDBDBD), major = c(0xFFFFFFFF), motorway = c(0xFFFFC400),
            casing = c(0xFF000000), rail = c(0xFFA0A0A0), boundary = c(0xFFE040FB),
            label = c(0xFFFFFFFF), roadLabel = c(0xFFFFFFFF), waterLabel = c(0xFF80D8FF), halo = c(0xFF000000),
            widths = 1.45f,
        ),
    ),
)

fun lightMapStyle(name: String?) = LightMapStyles.firstOrNull { it.name == name } ?: LightMapStyles[0]
fun darkMapStyle(name: String?) = DarkMapStyles.firstOrNull { it.name == name } ?: DarkMapStyles[0]

/** The base styles worth keeping offline: those of the chosen light and dark styles. */
fun mapStylesToCache(s: Settings): List<String> =
    listOf(lightMapStyle(s.mapLightStyle).base, darkMapStyle(s.mapDarkStyle).base).distinct()

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
    return if (dark) darkMapStyle(s.mapDarkStyle) else lightMapStyle(s.mapLightStyle)
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
