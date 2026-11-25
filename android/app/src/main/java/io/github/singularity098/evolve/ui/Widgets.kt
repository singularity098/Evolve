package io.github.singularity098.evolve.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The pieces Chippy is drawn with (its ui/Widgets.kt and ui/Settings.kt),
// and a few of Evolve's own in the same style.

/** Paints what is drawn (an icon, say) with a brush instead of a colour. */
fun Modifier.paintWith(brush: Brush): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        drawRect(brush, blendMode = BlendMode.SrcIn)
    }

/** An icon in the theme's paint. */
@Composable
fun PaintedIcon(icon: ImageVector, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Icon(
        icon,
        contentDescription,
        modifier.paintWith(LocalLook.current.paint()),
        tint = Color.White,
    )
}

/** A section's title in a list. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        letterSpacing = 1.6.sp,
        color = LocalLook.current.accent,
    )
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** − value + */
@Composable
fun Stepper(value: String, onStep: (Int) -> Unit, label: String, minWidth: Dp = 48.dp) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        FilledTonalIconButton(onClick = { onStep(-1) }, modifier = Modifier.size(36.dp)) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            value,
            Modifier
                .widthIn(min = minWidth)
                .semantics { contentDescription = "$label: $value" },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        FilledTonalIconButton(onClick = { onStep(1) }, modifier = Modifier.size(36.dp)) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
fun SwitchRow(title: String, detail: String, on: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.clickable(role = Role.Switch) { onChange(!on) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(title) },
        supportingContent = if (detail.isNotEmpty()) {
            { Text(detail) }
        } else null,
        trailingContent = { Switch(checked = on, onCheckedChange = onChange) },
    )
}

@Composable
fun StepRow(title: String, value: String, detail: String = "", onStep: (Int) -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(title) },
        supportingContent = if (detail.isNotEmpty()) {
            { Text(detail) }
        } else null,
        trailingContent = { Stepper(value, onStep, title) },
    )
}

/** The big round button, in the theme's paint. */
@Composable
fun PaintedCircle(
    size: Dp,
    onClick: () -> Unit,
    onClickLabel: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val look = LocalLook.current
    Box(
        modifier
            .size(size)
            .shadow(10.dp, CircleShape, ambientColor = look.paintB, spotColor = look.paintB)
            .clip(CircleShape)
            .background(look.paint())
            .clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Fills the width with a message, for empty lists. */
@Composable
fun EmptyMessage(icon: ImageVector, title: String, detail: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PaintedIcon(icon, Modifier.size(56.dp).graphicsLayer(alpha = 0.7f))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (detail.isNotEmpty()) {
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        action?.invoke()
    }
}

/** A raised panel, the colour of Chippy's sheets. */
@Composable
fun Panel(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) { content() }
}

/** A number and what it is, big over small. */
@Composable
fun Stat(value: String, label: String, modifier: Modifier = Modifier, big: Boolean = false) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = if (big) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall,
            maxLines = 1,
        )
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A small round icon tile in the theme's paint, faintly. */
@Composable
fun IconBadge(icon: ImageVector, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(look.paintC.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        PaintedIcon(icon, Modifier.size(size * 0.55f))
    }
}

/** The looks, as swatches of their paint (as in Chippy's settings). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemePicker(current: String, onPick: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Theme", style = MaterialTheme.typography.bodyLarge)
        FlowRow(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Looks.forEach { look ->
                val picked = look.name == current
                Column(
                    Modifier
                        .widthIn(min = 64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.RadioButton) { onPick(look.name) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(look.paint())
                            .border(
                                if (picked) 3.dp else 1.dp,
                                if (picked) Color.White else Color.White.copy(alpha = 0.15f),
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (picked) Icon(EvolveIcons.Check, null, Modifier.size(20.dp), tint = Color.Black)
                    }
                    Text(
                        look.name,
                        Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (picked) look.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
