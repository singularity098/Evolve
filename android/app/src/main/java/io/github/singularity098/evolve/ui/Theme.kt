package io.github.singularity098.evolve.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.singularity098.evolve.R
import io.github.singularity098.evolve.core.Contrast

/**
 * Evolve's looks, Chippy's own (Chippy's ui/Theme.kt) and Lotus, the icon's:
 * a flat dark background, and a theme that paints the highlights with two
 * or three colours. The names are stored in settings.json: never rename one.
 */
@Immutable
data class Look(
    val name: String,
    val paintA: Color,
    val paintB: Color,
    val paintC: Color,
    val accent: Color,
    val text: Color,
    val textDim: Color,
    val scanlines: Boolean = false,
) {
    /** The highlight paint, spread across each highlighted thing. */
    fun paint(start: Offset = Offset.Zero, end: Offset = Offset.Infinite): Brush =
        Brush.linearGradient(0f to paintA, 0.55f to paintB, 1f to paintC, start = start, end = end)

    val paintHorizontal: Brush
        get() = Brush.horizontalGradient(0f to paintA, 0.55f to paintB, 1f to paintC)
}

val Looks = listOf(
    Look("Lotus", Color(0xFFB0D8FF), Color(0xFF5D9CF2), Color(0xFF3A66D4), Color(0xFFB0D8FF), Color(0xFFDCE8F7), Color(0xFF8091A8)),
    Look("Iris", Color(0xFF7278FE), Color(0xFFC688D6), Color(0xFFA85EEA), Color(0xFFFFFFFF), Color(0xFFE6E6EA), Color(0xFF8A8A94)),
    Look("Ember", Color(0xFFFFB36B), Color(0xFFFF6A3D), Color(0xFFE0336F), Color(0xFFFFB36B), Color(0xFFF0E2D8), Color(0xFF9C8A80)),
    Look("Abyss", Color(0xFF7FF5E0), Color(0xFF2EC8E8), Color(0xFF3A7BFF), Color(0xFF7FF5E0), Color(0xFFD8EAF0), Color(0xFF7F99A5)),
    Look("Phosphor", Color(0xFFB8FFC8), Color(0xFF4DFF88), Color(0xFF22D36A), Color(0xFFB8FFC8), Color(0xFF9CF0B0), Color(0xFF5F9C6C), scanlines = true),
    Look("Synthwave", Color(0xFFFF4FD8), Color(0xFFB65CFF), Color(0xFF29E0FF), Color(0xFFFF4FD8), Color(0xFFECE2FA), Color(0xFF9486AB)),
    Look("Gold Leaf", Color(0xFFF6E2B3), Color(0xFFD4AF6A), Color(0xFFA77C3C), Color(0xFFF6E2B3), Color(0xFFECE4D4), Color(0xFF948A76)),
    Look("Aurora", Color(0xFF8DFFC3), Color(0xFF2FD6C3), Color(0xFF9A6BFF), Color(0xFF8DFFC3), Color(0xFFDCEFE8), Color(0xFF82988F)),
    Look("Blood Moon", Color(0xFFFF9AA4), Color(0xFFE0263F), Color(0xFFAF3254), Color(0xFFFF9AA4), Color(0xFFEEDCDE), Color(0xFF9B8285)),
    Look("Glacier", Color(0xFFFFFFFF), Color(0xFFCFE6FF), Color(0xFF8FBAF0), Color(0xFFCFE6FF), Color(0xFFE6EEF7), Color(0xFF8796A8)),
    Look("Graphite", Color(0xFFFFFFFF), Color(0xFFD0D0D0), Color(0xFF8E8E8E), Color(0xFFFFFFFF), Color(0xFFE8E8E8), Color(0xFF8E8E8E)),
    Look("Oxide", Color(0xFFF0A46C), Color(0xFFC8693C), Color(0xFF4FC2A6), Color(0xFFF0A46C), Color(0xFFEEE2D8), Color(0xFF978A80)),
)

fun lookNamed(name: String?): Look = Looks.firstOrNull { it.name == name } ?: Looks[0]

/** The window background, as in Chippy. */
val EvolveBackground = Color(0xFF15151A)

val LocalLook = staticCompositionLocalOf { Looks[0] }

/** The contrast chosen in Settings, for the few things drawn outside the colour scheme. */
val LocalContrast = staticCompositionLocalOf { Contrast.STANDARD }

/** The contrast levels' names, in the order Settings shows them. */
val ContrastNames = listOf(
    Contrast.STANDARD to "Standard",
    Contrast.HIGH to "High",
    Contrast.MAXIMUM to "Maximum",
)

/**
 * The look with its text made to stand out more, whatever the theme:
 * High makes the text all but white (a hint of the theme left) and the dim
 * text a light grey; Maximum makes the text white and the dim text nearly so.
 */
fun Look.withContrast(c: Contrast): Look = when (c) {
    Contrast.STANDARD -> this
    Contrast.HIGH -> copy(text = lerp(text, Color.White, 0.9f), textDim = lerp(textDim, Color.White, 0.72f))
    Contrast.MAXIMUM -> copy(text = Color.White, textDim = lerp(textDim, Color.White, 0.85f))
}

private fun backgroundFor(c: Contrast) = if (c == Contrast.MAXIMUM) Color(0xFF000000) else EvolveBackground

private val Jost = FontFamily(
    Font(R.font.jost_light, FontWeight.Light),
    Font(R.font.jost_regular, FontWeight.Normal),
    // Jost has no heavier faces bundled: Medium falls back to Regular.
    Font(R.font.jost_regular, FontWeight.Medium),
)

/**
 * Material 3's type scale in Jost, a little larger (Jost runs small). The
 * same at every contrast: contrast changes colours only.
 */
private val EvolveTypography: Typography = run {
    val base = Typography()
    fun TextStyle.jost(weight: FontWeight = FontWeight.Normal, bump: Float = 1f): TextStyle =
        copy(fontFamily = Jost, fontWeight = weight, fontSize = (fontSize.value + bump).sp)
    Typography(
        displayLarge = base.displayLarge.jost(FontWeight.Light),
        displayMedium = base.displayMedium.jost(FontWeight.Light),
        displaySmall = base.displaySmall.jost(FontWeight.Light),
        headlineLarge = base.headlineLarge.jost(FontWeight.Light),
        headlineMedium = base.headlineMedium.jost(FontWeight.Light),
        headlineSmall = base.headlineSmall.jost(FontWeight.Light),
        titleLarge = base.titleLarge.jost(FontWeight.Normal),
        titleMedium = base.titleMedium.jost(FontWeight.Normal),
        titleSmall = base.titleSmall.jost(FontWeight.Normal),
        bodyLarge = base.bodyLarge.jost(FontWeight.Light),
        bodyMedium = base.bodyMedium.jost(FontWeight.Light),
        bodySmall = base.bodySmall.jost(FontWeight.Normal),
        labelLarge = base.labelLarge.jost(FontWeight.Normal),
        labelMedium = base.labelMedium.jost(FontWeight.Normal),
        labelSmall = base.labelSmall.jost(FontWeight.Normal),
    )
}

private fun schemeFor(look: Look, contrast: Contrast): ColorScheme {
    val bg = backgroundFor(contrast)
    // Lines and panel edges: fainter at Standard, plainer with more contrast.
    val (outline, outlineVariant) = when (contrast) {
        Contrast.STANDARD -> Color(0x40FFFFFF) to Color(0x1FFFFFFF)
        Contrast.HIGH -> Color(0x8CFFFFFF) to Color(0x4DFFFFFF)
        Contrast.MAXIMUM -> Color(0xB3FFFFFF) to Color(0x66FFFFFF)
    }
    return darkColorScheme(
    primary = look.paintB,
    onPrimary = Color.Black,
    primaryContainer = look.paintC.copy(alpha = 0.28f).compositeOver(bg),
    onPrimaryContainer = look.text,
    secondary = look.paintA,
    onSecondary = Color.Black,
    secondaryContainer = look.paintA.copy(alpha = 0.22f).compositeOver(bg),
    onSecondaryContainer = look.text,
    tertiary = look.paintC,
    onTertiary = Color.Black,
    background = bg,
    onBackground = look.text,
    surface = bg,
    onSurface = look.text,
    surfaceVariant = Color(0xFF23232A),
    onSurfaceVariant = look.textDim,
    surfaceContainerLowest = Color(0xFF0E0E12),
    surfaceContainerLow = Color(0xFF18181E),
    surfaceContainer = Color(0xFF1C1C23),
    surfaceContainerHigh = Color(0xFF212129),
    surfaceContainerHighest = Color(0xFF282831),
    surfaceBright = Color(0xFF2E2E38),
    inverseSurface = look.text,
    inverseOnSurface = bg,
    inversePrimary = look.paintC,
    outline = outline,
    outlineVariant = outlineVariant,
    error = if (contrast == Contrast.STANDARD) Color(0xFFE5484D) else Color(0xFFFF6B6F),
    onError = Color.Black,
    scrim = Color(0xB3000000),
)
}

private fun Color.compositeOver(bg: Color): Color {
    val a = alpha
    return Color(
        red = red * a + bg.red * (1 - a),
        green = green * a + bg.green * (1 - a),
        blue = blue * a + bg.blue * (1 - a),
        alpha = 1f,
    )
}

@Composable
fun EvolveTheme(look: Look, contrast: Contrast = Contrast.STANDARD, content: @Composable () -> Unit) {
    val shown = look.withContrast(contrast)
    CompositionLocalProvider(LocalLook provides shown, LocalContrast provides contrast) {
        MaterialTheme(
            colorScheme = schemeFor(shown, contrast),
            typography = EvolveTypography,
            content = content,
        )
    }
}
