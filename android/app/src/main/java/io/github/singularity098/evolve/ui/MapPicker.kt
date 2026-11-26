package io.github.singularity098.evolve.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** A row of map styles to pick from, each drawn as a little map in its colours. */
@Composable
fun MapStylePicker(title: String, styles: List<MapStyle>, current: String, onPick: (String) -> Unit) {
    val look = LocalLook.current
    val picked = styles.firstOrNull { it.name == current } ?: styles[0]
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(title, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyLarge)
        Text(
            picked.about,
            Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(
            Modifier.padding(top = 10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(styles) { st ->
                val on = st.name == picked.name
                Column(
                    Modifier
                        .width(76.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.RadioButton) { onPick(st.name) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MapSwatch(
                        st.swatch,
                        Modifier
                            .size(68.dp, 52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                if (on) 3.dp else 1.dp,
                                if (on) look.accent else Color.White.copy(alpha = 0.2f),
                                RoundedCornerShape(10.dp),
                            ),
                    )
                    Text(
                        st.name,
                        Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (on) look.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** A tiny made-up map: a park, a lake, a road with its casing, a side street, a trail and a label. */
@Composable
private fun MapSwatch(p: MapPalette, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(Color(p.background))
        drawRect(Color(p.park), Offset(0f, 0f), Size(w * 0.42f, h * 0.45f))
        drawOval(Color(p.water), Offset(w * 0.58f, h * 0.52f), Size(w * 0.55f, h * 0.6f))
        drawLine(Color(p.minor), Offset(w * 0.15f, h), Offset(w * 0.7f, 0f), 2.5.dp.toPx())
        drawLine(
            Color(p.path), Offset(0f, h * 0.7f), Offset(w * 0.55f, h * 0.95f), 1.6.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 2.dp.toPx())),
        )
        drawLine(Color(p.casing), Offset(0f, h * 0.55f), Offset(w, h * 0.28f), 6.dp.toPx(), StrokeCap.Butt)
        drawLine(Color(p.major), Offset(0f, h * 0.55f), Offset(w, h * 0.28f), 4.dp.toPx(), StrokeCap.Butt)
        // A label: a bar of its colour on a halo.
        drawRoundRect(Color(p.halo), Offset(w * 0.5f, h * 0.06f), Size(w * 0.42f, h * 0.17f), CornerRadius(3.dp.toPx()))
        drawRoundRect(Color(p.label), Offset(w * 0.54f, h * 0.11f), Size(w * 0.34f, h * 0.07f), CornerRadius(2.dp.toPx()))
    }
}
