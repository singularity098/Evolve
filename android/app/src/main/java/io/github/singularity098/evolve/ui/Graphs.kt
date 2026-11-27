package io.github.singularity098.evolve.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.singularity098.evolve.Store
import io.github.singularity098.evolve.core.GraphQuery
import io.github.singularity098.evolve.core.GraphSeries
import io.github.singularity098.evolve.core.Metric
import io.github.singularity098.evolve.core.WorkoutKind
import io.github.singularity098.evolve.core.formatDuration
import io.github.singularity098.evolve.core.formatNumber
import io.github.singularity098.evolve.core.kindIsFitness
import io.github.singularity098.evolve.core.kindLabel
import java.util.TimeZone
import kotlin.math.abs

private class MetricOption(val label: String, val metric: Metric, val exercise: Boolean = false, val kinds: List<WorkoutKind> = emptyList())

private val Distanced = listOf(WorkoutKind.WALK, WorkoutKind.RUN, WorkoutKind.HIKE, WorkoutKind.BIKE, WorkoutKind.STATIONARY_BIKE)

private val Options = listOf(
    MetricOption("Body weight", Metric.BODY_WEIGHT),
    MetricOption("Heaviest set", Metric.EXERCISE_MAX_WEIGHT, exercise = true),
    MetricOption("Estimated 1RM", Metric.EXERCISE_ONE_REP_MAX, exercise = true),
    MetricOption("Exercise volume", Metric.EXERCISE_VOLUME, exercise = true),
    MetricOption("Exercise reps", Metric.EXERCISE_REPS, exercise = true),
    MetricOption("Lifting volume", Metric.LIFTING_VOLUME),
    MetricOption("Distance", Metric.DISTANCE, kinds = Distanced),
    MetricOption("Pace", Metric.PACE, kinds = listOf(WorkoutKind.WALK, WorkoutKind.RUN, WorkoutKind.HIKE)),
    MetricOption("Speed", Metric.SPEED, kinds = listOf(WorkoutKind.BIKE, WorkoutKind.STATIONARY_BIKE)),
    MetricOption("Climb", Metric.ELEVATION_GAIN, kinds = listOf(WorkoutKind.WALK, WorkoutKind.RUN, WorkoutKind.HIKE, WorkoutKind.BIKE)),
    MetricOption("Steps", Metric.STEPS, kinds = listOf(WorkoutKind.WALK, WorkoutKind.RUN, WorkoutKind.HIKE)),
    // Boat and car rides are trips, not exercise: never in the graphs.
    MetricOption("Duration", Metric.DURATION, kinds = WorkoutKind.entries.filter { kindIsFitness(it) }),
    MetricOption("Longest breath hold", Metric.BREATH_MAX_HOLD),
    MetricOption("Average breath hold", Metric.BREATH_AVERAGE_HOLD),
    MetricOption("Meditation", Metric.MEDITATION_MINUTES),
    MetricOption("Activities per week", Metric.WORKOUTS_PER_WEEK),
    MetricOption("Minutes per week", Metric.MINUTES_PER_WEEK),
)

private val Ranges = listOf("1M" to 30L, "3M" to 91L, "6M" to 182L, "1Y" to 365L, "All" to 0L)

@Composable
fun GraphsScreen(store: Store) {
    val version by store.historyVersion.collectAsStateWithLifecycle()
    val library by store.library.collectAsStateWithLifecycle()
    var optionIndex by rememberSaveable { mutableStateOf(0) }
    var exerciseId by rememberSaveable { mutableStateOf(library.exercises.firstOrNull()?.id) }
    var kind by rememberSaveable { mutableStateOf<WorkoutKind?>(null) }
    var range by rememberSaveable { mutableStateOf("3M") }
    val option = Options[optionIndex]
    val days = Ranges.first { it.first == range }.second
    val series = remember(version, library, optionIndex, exerciseId, kind, range) {
        val now = System.currentTimeMillis()
        store.engine.graph(
            GraphQuery(
                metric = option.metric,
                exerciseId = exerciseId,
                kinds = listOfNotNull(kind?.takeIf { it in option.kinds }),
                sinceMs = if (days == 0L) null else now - days * 86_400_000,
                utcOffsetMin = TimeZone.getDefault().getOffset(now) / 60_000,
            )
        )
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        Text("Graphs", Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp), style = MaterialTheme.typography.headlineMedium)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Options.size) { i ->
                FilterChip(selected = i == optionIndex, onClick = { optionIndex = i }, label = { Text(Options[i].label) })
            }
        }
        if (option.exercise) {
            var open by remember { mutableStateOf(false) }
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedButton(onClick = { open = true }) {
                    Icon(EvolveIcons.Dumbbell, null, Modifier.size(18.dp))
                    Text(
                        library.exercises.find { it.id == exerciseId }?.name ?: "Choose an exercise",
                        Modifier.padding(horizontal = 8.dp),
                    )
                    Icon(EvolveIcons.ChevronDown, null, Modifier.size(18.dp))
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    library.exercises.forEach { e ->
                        DropdownMenuItem(text = { Text(e.name) }, onClick = { exerciseId = e.id; open = false })
                    }
                }
            }
        }
        if (option.kinds.isNotEmpty()) {
            LazyRow(
                Modifier.padding(top = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { FilterChip(selected = kind == null || kind !in option.kinds, onClick = { kind = null }, label = { Text("All") }) }
                items(option.kinds) { k ->
                    FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(kindLabel(k)) })
                }
            }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Ranges.forEach { (label, _) ->
                FilterChip(selected = range == label, onClick = { range = label }, label = { Text(label) })
            }
        }
        Panel(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(series.title, style = MaterialTheme.typography.titleMedium)
                if (series.points.isEmpty()) {
                    EmptyMessage(EvolveIcons.Chart, "Nothing to show yet", "Activities and weigh-ins in this time show here as they're saved.")
                } else {
                    LineChart(series, Modifier.fillMaxWidth().height(260.dp).padding(top = 12.dp))
                }
            }
        }
        if (series.points.size >= 2) Summary(series)
    }
}

private fun valueText(s: GraphSeries, v: Double): String =
    if (s.isDuration) formatDuration(v) else formatNumber(v, if (abs(v) >= 100) 0u else 1u)

@Composable
private fun Summary(series: GraphSeries) {
    val pts = series.points
    // Lower is better for pace.
    val lowerBetter = series.unit.startsWith("/")
    val best = if (lowerBetter) pts.minOf { it.value } else pts.maxOf { it.value }
    val change = pts.last().value - pts.first().value
    val unit = if (series.isDuration) "" else " ${series.unit}"
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Stat(valueText(series, pts.last().value) + unit, "latest", Modifier.weight(1f))
        Stat(valueText(series, best) + unit, "best", Modifier.weight(1f))
        Stat((if (change >= 0) "+" else "−") + valueText(series, abs(change)), "change", Modifier.weight(1f))
    }
}

@Composable
private fun LineChart(series: GraphSeries, modifier: Modifier) {
    val look = LocalLook.current
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurface
    var selected by remember(series) { mutableStateOf<Int?>(null) }
    val pts = series.points
    val t0 = pts.first().tMs
    val t1 = pts.last().tMs.coerceAtLeast(t0 + 1)
    var lo = pts.minOf { it.value }
    var hi = pts.maxOf { it.value }
    if (hi - lo < 1e-9) {
        lo -= 1
        hi += 1
    }
    val pad = (hi - lo) * 0.1
    lo -= pad
    hi += pad
    val left = 52f
    fun xOf(t: Long, w: Float) = left + (if (pts.size == 1) 0.5f else (t - t0).toFloat() / (t1 - t0)) * (w - left - 8f)
    fun yOf(v: Double, h: Float) = (h - 24f) - ((v - lo) / (hi - lo)).toFloat() * (h - 36f)
    fun nearest(x: Float, w: Float): Int = pts.indices.minBy { abs(xOf(pts[it].tMs, w) - x) }

    Canvas(
        modifier
            .pointerInput(series) { detectTapGestures { o -> selected = nearest(o.x, size.width.toFloat()) } }
            .pointerInput(series) {
                detectDragGesturesAfterLongPress { change, _ -> selected = nearest(change.position.x, size.width.toFloat()) }
            },
    ) {
        val w = size.width
        val h = size.height
        val style = TextStyle(fontSize = 10.sp, color = labelColor)
        for (i in 0..3) {
            val v = lo + (hi - lo) * i / 3
            val y = yOf(v, h)
            drawLine(grid, Offset(left, y), Offset(w, y), 1f)
            drawText(measurer, valueText(series, v), Offset(0f, y - 14f), style)
        }
        drawText(measurer, shortDate(t0), Offset(left, h - 14f), style)
        if (pts.size > 1) {
            val end = measurer.measure(shortDate(t1), style)
            drawText(end, topLeft = Offset(w - end.size.width, h - 14f))
        }
        val line = Path()
        val area = Path()
        pts.forEachIndexed { i, p ->
            val x = xOf(p.tMs, w)
            val y = yOf(p.value, h)
            if (i == 0) {
                line.moveTo(x, y)
                area.moveTo(x, h - 24f)
                area.lineTo(x, y)
            } else {
                line.lineTo(x, y)
                area.lineTo(x, y)
            }
        }
        area.lineTo(xOf(pts.last().tMs, w), h - 24f)
        area.close()
        drawPath(area, Brush.verticalGradient(listOf(look.paintB.copy(alpha = 0.30f), Color.Transparent), startY = 0f, endY = h))
        drawPath(line, look.paintHorizontal, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (pts.size <= 60) {
            pts.forEach { p -> drawCircle(look.paintA, 3.5.dp.toPx(), Offset(xOf(p.tMs, w), yOf(p.value, h))) }
        }
        selected?.let { i ->
            val p = pts[i]
            val x = xOf(p.tMs, w)
            val y = yOf(p.value, h)
            drawLine(textColor.copy(alpha = 0.4f), Offset(x, 0f), Offset(x, h - 24f), 1.5f)
            drawCircle(Color.White, 6.dp.toPx(), Offset(x, y))
            drawCircle(look.paintB, 4.dp.toPx(), Offset(x, y))
            val label = measurer.measure(
                "${valueText(series, p.value)}${if (series.isDuration) "" else " " + series.unit} · ${shortDate(p.tMs)}",
                TextStyle(fontSize = 12.sp, color = textColor),
            )
            val lx = (x - label.size.width / 2).coerceIn(left, w - label.size.width)
            drawText(label, topLeft = Offset(lx, 0f))
        }
    }
}
