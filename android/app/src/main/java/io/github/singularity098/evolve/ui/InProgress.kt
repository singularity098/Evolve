package io.github.singularity098.evolve.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.singularity098.evolve.Store
import io.github.singularity098.evolve.core.BreathPhase
import io.github.singularity098.evolve.core.Session
import io.github.singularity098.evolve.core.TimerMode
import io.github.singularity098.evolve.core.WorkoutKind
import io.github.singularity098.evolve.core.flattenProgram
import io.github.singularity098.evolve.core.formatDistance
import io.github.singularity098.evolve.core.formatDuration
import io.github.singularity098.evolve.core.formatElevation
import io.github.singularity098.evolve.core.formatPace
import io.github.singularity098.evolve.core.formatSpeed
import io.github.singularity098.evolve.core.kindLabel
import io.github.singularity098.evolve.core.kindUsesGps
import io.github.singularity098.evolve.core.weightIn

@Composable
fun InProgressScreen(store: Store, session: Session) {
    var finishing by rememberSaveable { mutableStateOf(false) }
    when {
        session.lifting != null -> LiftingScreen(store, session) { finishing = true }
        kindUsesGps(session.kind) -> GpsScreen(store, session) { finishing = true }
        session.timer != null -> TimerScreen(store, session) { finishing = true }
        session.meditation != null -> MeditationScreen(store, session) { finishing = true }
        session.breath != null -> BreathScreen(store, session) { finishing = true }
    }
    if (finishing) FinishDialog(store, session, onClose = { finishing = false })
}

fun elapsedMs(s: Session, now: Long): Long =
    (now - s.startedAtMs - s.pausedMs - (s.pausedAtMs?.let { now - it } ?: 0)).coerceAtLeast(0)

/** The title row every workout has: what it is, the time, screen-on, pause, finish. */
@Composable
private fun WorkoutBar(store: Store, session: Session, now: Long, onFinish: () -> Unit, canPause: Boolean = true, modifier: Modifier = Modifier) {
    val paused = session.pausedAtMs != null
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(kindIcon(session.kind), 40.dp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(kindLabel(session.kind), style = MaterialTheme.typography.titleMedium)
            Text(
                (if (paused) "Paused · " else "") + stopwatch(elapsedMs(session, now)),
                style = MaterialTheme.typography.bodyMedium,
                color = if (paused) LocalLook.current.accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconToggleButton(
            checked = session.keepScreenOn,
            onCheckedChange = { on ->
                store.live { setSessionScreenOn(on) }
                store.say(if (on) "The screen stays on" else "The screen may turn off")
            },
        ) {
            if (session.keepScreenOn) PaintedIcon(EvolveIcons.Sun, Modifier.size(24.dp), "Keep the screen on")
            else Icon(EvolveIcons.Sun, "Keep the screen on", Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
        if (canPause) {
            IconButton(onClick = {
                val t = System.currentTimeMillis()
                store.live { if (paused) resume(t) else pause(t) }
            }) {
                Icon(if (paused) EvolveIcons.Play else EvolveIcons.Pause, if (paused) "Resume" else "Pause")
            }
        }
        FilledTonalButton(onClick = onFinish, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("Finish") }
    }
}

// ----------------------------------------------------------------- weights

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun LiftingScreen(store: Store, session: Session, onFinish: () -> Unit) {
    val now by rememberNow(200)
    val l = session.lifting ?: return
    val s = store.settings
    val look = LocalLook.current
    var editSet by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var editWeight by remember { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf(false) }
    // The list stays where it is when an exercise is finished: no scrolling to the next.
    val list = rememberLazyListState()
    var weighIn by remember { mutableStateOf(false) }
    val version by store.historyVersion.collectAsStateWithLifecycle()
    val lastWeight = remember(version) { store.engine.bodyWeights().firstOrNull() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        WorkoutBar(store, session, now, onFinish, canPause = false)
        val done = l.exercises.sumOf { e -> e.sets.count { it.done } }
        val all = l.exercises.sumOf { it.sets.size }
        LinearProgressIndicator(
            progress = { if (all == 0) 0f else done.toFloat() / all },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(4.dp).clip(CircleShape),
        )
        Box(Modifier.weight(1f)) {
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            // Room at the end for the rest panel, always, so nothing moves when it comes and goes.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = REST_ROOM),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // A reminder to weigh in, until it's done today.
            item {
                val today = lastWeight?.takeIf { localDate(it.atMs) == java.time.LocalDate.now() }
                WeighInRow(today?.let { weight(it.weightKg, s) }, onClick = { weighIn = true })
            }
            if (l.exercises.isEmpty()) {
                item { EmptyMessage(EvolveIcons.Dumbbell, "No exercises yet", "Add the first one below.") }
            }
            itemsIndexed(l.exercises) { ei, e ->
                val current = ei == l.current.toInt()
                // Every set done: the exercise is marked off, its card darker and everything on it
                // faded out like a disabled control, until a set is added (or one undone).
                val finished = e.sets.isNotEmpty() && e.sets.all { it.done }
                // A finished exercise folds down to a thin row; a tap opens it again (to undo a set).
                var opened by remember { mutableStateOf(false) }
                LaunchedEffect(finished) { if (!finished) opened = false }
                val folded = finished && !opened
                val shape = RoundedCornerShape(if (folded) 14.dp else 20.dp)
                var menu by remember { mutableStateOf(false) }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                        .clip(shape)
                        .background(if (finished) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.surfaceContainer)
                        .border(if (current && !finished) 2.dp else 1.dp, if (current && !finished) look.paint() else Brush.linearGradient(listOf(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.colorScheme.outlineVariant)), shape)
                        .clickable { if (finished) opened = !opened else store.live { selectExercise(ei.toUInt()) } },
                ) {
                    if (folded) {
                        Row(
                            Modifier.alpha(0.38f).padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PaintedIcon(EvolveIcons.Check, Modifier.padding(end = 10.dp).size(20.dp), "Done")
                            Text(e.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${e.sets.size} sets",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(EvolveIcons.More, "More") }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text("Add a set") }, onClick = { menu = false; store.live { addSet(ei.toUInt()) } })
                                    DropdownMenuItem(text = { Text("Remove exercise") }, onClick = { menu = false; store.live { removeSessionExercise(ei.toUInt()) } })
                                }
                            }
                        }
                    } else Column(Modifier.alpha(if (finished) 0.38f else 1f).padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (finished) PaintedIcon(EvolveIcons.Check, Modifier.padding(end = 10.dp).size(26.dp), "Done")
                            Column(Modifier.weight(1f)) {
                                Text(
                                    e.name,
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                val doneSets = e.sets.count { it.done }
                                Text(
                                    if (finished) "Done · ${e.sets.size} sets"
                                    else "${e.equipment.name.lowercase().replaceFirstChar { it.uppercase() }} · $doneSets of ${e.sets.size} sets · rest ${formatDuration(e.restSecs.toDouble())}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(EvolveIcons.More, "More") }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text("Add a set") }, onClick = { menu = false; store.live { addSet(ei.toUInt()) } })
                                    DropdownMenuItem(text = { Text("Remove exercise") }, onClick = { menu = false; store.live { removeSessionExercise(ei.toUInt()) } })
                                }
                            }
                        }
                        // The weight for the sets still to do.
                        val pending = e.sets.firstOrNull { !it.done }
                        if (pending != null) {
                            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                FilledTonalIconButton(onClick = {
                                    val v = (weightIn(pending.weightKg, s.weightUnit) - weightStep(s)).coerceAtLeast(0.0)
                                    store.live { setWeight(ei.toUInt(), null, io.github.singularity098.evolve.core.weightToKg(v, s.weightUnit)) }
                                }) { Text("−", style = MaterialTheme.typography.titleLarge) }
                                Text(
                                    weight(pending.weightKg, s),
                                    Modifier
                                        .weight(1f)
                                        .clickable { editWeight = ei },
                                    style = MaterialTheme.typography.headlineMedium,
                                    textAlign = TextAlign.Center,
                                )
                                FilledTonalIconButton(onClick = {
                                    val v = weightIn(pending.weightKg, s.weightUnit) + weightStep(s)
                                    store.live { setWeight(ei.toUInt(), null, io.github.singularity098.evolve.core.weightToKg(v, s.weightUnit)) }
                                }) { Text("+", style = MaterialTheme.typography.titleLarge) }
                            }
                        }
                        // A button per set: its reps. A tap marks it done; hold to change it
                        // (the set's weight shows there). Sized so six fit on a row.
                        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = if (finished) 12.dp else 0.dp)) {
                            val gap = 8.dp
                            val circle = ((maxWidth - gap * 5) / 6).coerceAtMost(52.dp)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap)) {
                                e.sets.forEachIndexed { si, set ->
                                    val next = !set.done && e.sets.take(si).all { it.done }
                                    val outline = MaterialTheme.colorScheme.outline
                                    Box(
                                        Modifier
                                            .size(circle)
                                            .clip(CircleShape)
                                            .drawBehind { setRing(set.done, next, look, outline) }
                                            .combinedClickable(
                                                onClick = {
                                                    val t = System.currentTimeMillis()
                                                    store.live {
                                                        if (set.done) undoSet(ei.toUInt(), si.toUInt())
                                                        else completeSet(ei.toUInt(), si.toUInt(), set.targetReps, t)
                                                    }
                                                },
                                                onLongClick = { editSet = ei to si },
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            "${if (set.done) set.reps else set.targetReps}",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Medium,
                                            textAlign = TextAlign.Center,
                                            color = if (set.done) look.paintA else MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                                Box(
                                    Modifier
                                        .size(circle)
                                        .clip(CircleShape)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                        .clickable { store.live { addSet(ei.toUInt()) } },
                                    contentAlignment = Alignment.Center,
                                ) { Icon(EvolveIcons.Plus, "Add a set", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(EvolveIcons.Plus, null, Modifier.size(18.dp))
                    Text("Add exercise", Modifier.padding(start = 8.dp))
                }
            }
            if (l.exercises.isNotEmpty()) {
                item {
                    Text(
                        "Tap a set when it's done; hold it to change its reps or weight.",
                        Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        // The rest between sets, floating over the bottom of the list.
        val until = l.restUntilMs
        if (until != null && until > now) {
            RestPanel(store, until - now, l.restTotalSecs.toLong() * 1000, Modifier.align(Alignment.BottomCenter))
        }
        }
    }

    editSet?.let { (ei, si) ->
        val set = l.exercises.getOrNull(ei)?.sets?.getOrNull(si) ?: run { editSet = null; return }
        SetDialog(store, ei, si, set.done, set.targetReps.toInt(), set.reps.toInt(), set.weightKg, onClose = { editSet = null })
    }
    editWeight?.let { ei ->
        val pending = l.exercises.getOrNull(ei)?.sets?.firstOrNull { !it.done } ?: l.exercises.getOrNull(ei)?.sets?.lastOrNull()
        var text by remember { mutableStateOf(pending?.let { weightNumber(it.weightKg, s) } ?: "") }
        AlertDialog(
            onDismissRequest = { editWeight = null },
            title = { Text("Weight") },
            text = { NumberField(text, { text = it }, "Weight (${unitLabel(s)}) for the sets to do") },
            confirmButton = {
                TextButton(onClick = {
                    parseWeight(text, store)?.let { kg -> store.live { setWeight(ei.toUInt(), null, kg) } }
                    editWeight = null
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { editWeight = null }) { Text("Cancel") } },
        )
    }
    if (weighIn) BodyWeightDialog(store, onClose = { weighIn = false })
    if (adding) {
        ExercisePicker(store, onPick = { id -> store.live { addSessionExercise(id) } }, onClose = { adding = false })
    }
}

/**
 * A set's button, "dashed to solid": a dashed ring while it's to do, closing into a solid
 * painted ring with a faint wash once it's done. The next set's dashes carry the full paint,
 * with a fainter wash behind its number. Sizes are in dp on a 48 dp button, scaled with it.
 */
private fun DrawScope.setRing(done: Boolean, next: Boolean, look: Look, outline: Color) {
    val r = size.minDimension / 2
    val u = r / 24f
    if (done) {
        val w = 3f * u
        val c = r - w / 2
        drawCircle(look.paintB.copy(alpha = 0.12f), radius = c - w / 2)
        drawCircle(look.paint(), radius = c, style = Stroke(w))
    } else {
        val w = (if (next) 3f else 2f) * u
        val c = r - w / 2
        // Dashes that close up evenly round the ring.
        val dash = (if (next) 6f else 4f) * u
        val gap = 4f * u
        val n = (2 * Math.PI.toFloat() * c / (dash + gap)).toInt().coerceAtLeast(1)
        val scale = (2 * Math.PI.toFloat() * c / n) / (dash + gap)
        val stroke = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash * scale, gap * scale)))
        if (next) {
            drawCircle(look.paintB.copy(alpha = 0.08f), radius = c - w / 2)
            drawCircle(look.paint(), radius = c, style = stroke)
        } else {
            // A little brighter than the outline at Standard contrast, so the dashes hold up.
            drawCircle(outline.copy(alpha = maxOf(outline.alpha, 0.4f)), radius = c, style = stroke)
        }
    }
}

/** Body weight, at the top of a lifting workout: a reminder until today's is logged, then a quiet line. */
@Composable
private fun WeighInRow(todays: String?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (todays == null) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = if (todays == null) 10.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PaintedIcon(EvolveIcons.Scale, Modifier.size(if (todays == null) 24.dp else 18.dp))
        if (todays == null) {
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text("Weigh in", style = MaterialTheme.typography.titleSmall)
                Text("Not logged today", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("Log") }
        } else {
            Text(
                "Body weight today: $todays",
                Modifier.padding(start = 10.dp).weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PaintedIcon(EvolveIcons.Check, Modifier.size(16.dp), "Logged")
        }
    }
}

/** What the lifting list keeps free at its end for the rest panel. */
private val REST_ROOM = 190.dp

@Composable
private fun RestPanel(store: Store, leftMs: Long, totalMs: Long, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    Panel(modifier.fillMaxWidth().padding(12.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("REST", style = MaterialTheme.typography.labelLarge, letterSpacing = 2.sp, color = look.accent)
                Text(
                    countdown(leftMs),
                    Modifier.padding(start = 12.dp).weight(1f),
                    style = MaterialTheme.typography.headlineLarge,
                )
                Button(onClick = { store.live { adjustRest(0, System.currentTimeMillis()) } }) { Text("Skip") }
            }
            LinearProgressIndicator(
                progress = { (leftMs.toFloat() / totalMs.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(6.dp).clip(CircleShape),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { store.live { adjustRest(-15, System.currentTimeMillis()) } }, Modifier.weight(1f)) { Text("−15 s") }
                OutlinedButton(onClick = { store.live { adjustRest(30, System.currentTimeMillis()) } }, Modifier.weight(1f)) { Text("+30 s") }
            }
        }
    }
}

/** One set: the reps done (or to do) and its weight. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetDialog(store: Store, ei: Int, si: Int, done: Boolean, target: Int, reps: Int, weightKg: Double, onClose: () -> Unit) {
    val s = store.settings
    var r by remember { mutableIntStateOf(if (done) reps else target) }
    var w by remember { mutableStateOf(weightNumber(weightKg, s)) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Set ${si + 1}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (done) "Reps done" else "Reps", style = MaterialTheme.typography.labelLarge)
                Stepper("$r", { d -> r = (r + d).coerceIn(0, 100) }, "Reps")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (maxOf(0, target - 4)..target + 2).forEach { n ->
                        OutlinedButton(onClick = { r = n }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("$n") }
                    }
                }
                NumberField(w, { w = it }, "Weight (${unitLabel(s)})")
                TextButton(onClick = {
                    store.live { removeSet(ei.toUInt(), si.toUInt()) }
                    onClose()
                }) { Text("Remove this set", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val t = System.currentTimeMillis()
                parseWeight(w, store)?.let { kg -> store.live { setWeight(ei.toUInt(), si.toUInt(), kg) } }
                store.live {
                    if (done) completeSet(ei.toUInt(), si.toUInt(), r.toUInt(), t) else setTargetReps(ei.toUInt(), si.toUInt(), r.toUInt())
                }
                onClose()
            }) { Text("OK") }
        },
        dismissButton = {
            if (!done) {
                TextButton(onClick = {
                    val t = System.currentTimeMillis()
                    parseWeight(w, store)?.let { kg -> store.live { setWeight(ei.toUInt(), si.toUInt(), kg) } }
                    store.live { completeSet(ei.toUInt(), si.toUInt(), r.toUInt(), t) }
                    onClose()
                }) { Text("Done with $r") }
            } else {
                TextButton(onClick = onClose) { Text("Cancel") }
            }
        },
    )
}

@Composable
fun ExercisePicker(store: Store, onPick: (String) -> Unit, onClose: () -> Unit) {
    val lib = store.library.value
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Add exercise") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (lib.exercises.isEmpty()) Text("The library is empty: add exercises in Settings → Exercises.")
                lib.exercises.forEach { e ->
                    ListItem(
                        modifier = Modifier.clickable {
                            onPick(e.id)
                            onClose()
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(e.name) },
                        supportingContent = { Text(e.equipment.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
    )
}

// --------------------------------------------------------------------- GPS

@Composable
private fun GpsScreen(store: Store, session: Session, onFinish: () -> Unit) {
    val now by rememberNow(500)
    val gps by store.gps.collectAsStateWithLifecycle()
    val trail by store.trail.collectAsStateWithLifecycle()
    val here by store.location.collectAsStateWithLifecycle()
    val s = store.settings
    var follow by rememberSaveable { mutableStateOf(true) }
    var recenter by remember { mutableIntStateOf(0) }
    // Light or dark as Settings say (by the light sensor, at first), unless
    // the sun/moon button pins one for this workout.
    var pinned by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val mapStyle = rememberMapStyle(s, pinned)
    val g = gps ?: session.gps
    val paused = session.pausedAtMs != null

    Box(Modifier.fillMaxSize()) {
        TrailMap(
            trail = trail,
            here = here,
            modifier = Modifier.fillMaxSize(),
            follow = follow,
            recenter = recenter,
            onUserMoved = { follow = false },
            style = mapStyle,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f)),
        ) {
            WorkoutBar(store, session, now, onFinish)
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                val d = g?.distanceM ?: 0.0
                val moving = g?.movingSecs ?: 0.0
                Stat(formatDistance(d, s.distanceUnit), "distance", Modifier.weight(1f))
                if (session.kind == WorkoutKind.BIKE) Stat(formatSpeed(d, moving, s.distanceUnit), "speed", Modifier.weight(1f))
                else Stat(formatPace(d, moving, s.distanceUnit), "pace", Modifier.weight(1f))
                Stat(formatElevation(g?.elevationGainM ?: 0.0, s.distanceUnit), "climb", Modifier.weight(1f))
            }
            val place = g?.placeName.orEmpty()
            val tooFast = paused && session.gps?.autoPaused == true
            if (here == null || place.isNotEmpty() || tooFast) {
                Text(
                    when {
                        tooFast -> "Paused: moving too fast (a car?). Resume to go on."
                        here == null -> "Waiting for the GPS…"
                        else -> place
                    },
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (tooFast) LocalLook.current.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        FilledTonalIconButton(
            onClick = { pinned = !mapStyle.dark },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 116.dp)
                .size(48.dp),
        ) {
            if (mapStyle.dark) Icon(EvolveIcons.Sun, "Light map")
            else Icon(EvolveIcons.Moon, "Dark map")
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            FilledTonalIconButton(
                onClick = {
                    follow = true
                    recenter++
                },
                modifier = Modifier.size(52.dp),
            ) { Icon(EvolveIcons.Locate, "Back to where you are") }
            PaintedCircle(76.dp, onClick = {
                val t = System.currentTimeMillis()
                store.live { if (paused) resume(t) else pause(t) }
            }, onClickLabel = if (paused) "Resume" else "Pause") {
                Icon(if (paused) EvolveIcons.Play else EvolveIcons.Pause, null, Modifier.size(34.dp), tint = Color.Black)
            }
            FilledTonalIconButton(onClick = onFinish, modifier = Modifier.size(52.dp)) { Icon(EvolveIcons.Stop, "Finish") }
        }
    }
}

// ------------------------------------------------------------------- timer

/** A ring that empties as [progress] goes from 1 to 0, in the theme's paint. */
@Composable
fun Ring(progress: Float, modifier: Modifier = Modifier, color: Color? = null, content: @Composable () -> Unit) {
    val look = LocalLook.current
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 14.dp.toPx()
            val sz = Size(size.minDimension - w, size.minDimension - w)
            val tl = Offset((size.width - sz.width) / 2, (size.height - sz.height) / 2)
            drawArc(track, 0f, 360f, false, tl, sz, style = Stroke(w))
            val brush = color?.let { Brush.linearGradient(listOf(it, it)) } ?: look.paint(Offset.Zero, Offset(size.width, size.height))
            drawArc(brush, -90f, 360f * p, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}

@Composable
private fun TimerScreen(store: Store, session: Session, onFinish: () -> Unit) {
    val now by rememberNow(100)
    val t = session.timer ?: return
    val st = remember(now, session) { store.engine.timerStatus(now) } ?: return
    val steps = remember(t.program) { t.program?.let { flattenProgram(it) } ?: emptyList() }

    Column(Modifier.fillMaxSize().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        WorkoutBar(store, session, now, onFinish)
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight) * 0.85f
            val color = if (t.mode == TimerMode.PROGRAM) intensityColor(st.intensity) else null
            Ring(
                when {
                    t.mode == TimerMode.STOPWATCH -> 1f
                    st.finished -> 0f
                    else -> st.stepRemainingMs.toFloat() / st.stepTotalMs.coerceAtLeast(1)
                },
                Modifier.size(side),
                color,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    when {
                        st.finished -> {
                            Text("Done", style = MaterialTheme.typography.displayMedium)
                            Text("Great work: save it with Finish", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        t.mode == TimerMode.STOPWATCH -> {
                            Text("FREE RIDE", style = MaterialTheme.typography.labelLarge, letterSpacing = 2.sp)
                            Text(stopwatch(st.elapsedMs), style = MaterialTheme.typography.displayLarge)
                        }
                        else -> {
                            Text(
                                st.label.uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                letterSpacing = 2.sp,
                                color = color ?: LocalLook.current.accent,
                            )
                            Text(countdown(st.stepRemainingMs), style = MaterialTheme.typography.displayLarge, fontSize = 72.sp)
                            if (t.mode == TimerMode.PROGRAM) {
                                Text(
                                    "Interval ${st.index + 1u} of ${st.count}" + if (st.reps > 1u) " · round ${st.rep}/${st.reps}" else "",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                st.nextLabel?.let { Text("Next: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }
            }
        }
        if (t.mode != TimerMode.STOPWATCH && !st.finished) {
            Text(
                "${countdown(st.totalRemainingMs)} left of ${formatDuration(st.totalMs / 1000.0)}",
                Modifier.padding(8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (steps.isNotEmpty()) {
            // What's coming.
            val upcoming = steps.drop(st.index.toInt()).take(5)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                upcoming.forEachIndexed { i, step ->
                    Column(
                        Modifier
                            .weight(step.secs.toFloat().coerceIn(20f, 300f))
                            .clip(RoundedCornerShape(8.dp))
                            .background(intensityColor(step.intensity).copy(alpha = if (i == 0) 0.9f else 0.35f))
                            .padding(6.dp),
                    ) {
                        Text(step.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, color = if (i == 0) Color.Black else MaterialTheme.colorScheme.onSurface)
                        Text(formatDuration(step.secs.toDouble()), style = MaterialTheme.typography.labelSmall, color = if (i == 0) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// -------------------------------------------------------------- meditation

@Composable
private fun MeditationScreen(store: Store, session: Session, onFinish: () -> Unit) {
    val now by rememberNow(200)
    val m = session.meditation ?: return
    val el = elapsedMs(session, now)
    val target = m.targetSecs.toLong() * 1000
    val done = el >= target
    Column(Modifier.fillMaxSize().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        WorkoutBar(store, session, now, onFinish)
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight) * 0.8f
            Ring(if (done) 1f else (target - el).toFloat() / target.coerceAtLeast(1), Modifier.size(side)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PaintedIcon(EvolveIcons.Lotus, Modifier.size(48.dp))
                    if (done) {
                        Text("Complete", style = MaterialTheme.typography.displaySmall)
                        Text("+" + stopwatch(el - target), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text(countdown(target - el), style = MaterialTheme.typography.displayLarge)
                        Text("of ${formatDuration(m.targetSecs.toDouble())}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Text(
            if (done) "Rest here as long as you like, then Finish." else "Breathe, and let thoughts pass.",
            Modifier.padding(24.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------- breathing

@Composable
private fun BreathScreen(store: Store, session: Session, onFinish: () -> Unit) {
    val now by rememberNow(50)
    val b = session.breath ?: return
    val st = remember(now, session) { store.engine.breathStatus(now) } ?: return
    val look = LocalLook.current

    Column(Modifier.fillMaxSize().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        WorkoutBar(store, session, now, onFinish, canPause = false)
        Text("ROUND ${st.round}", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge, letterSpacing = 2.sp, color = look.accent)
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight) * 0.8f
            when (st.phase) {
                BreathPhase.BREATHING -> {
                    // A circle that grows on the in-breath and shrinks on the out-breath.
                    val p = st.breathProgress.toFloat()
                    val scale = if (b.paceSecs > 0) (0.55f + 0.45f * if (p < 0.5f) p * 2 else (1 - p) * 2) else 0.8f
                    Box(
                        Modifier
                            .size(side * scale)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(look.paintA.copy(alpha = 0.55f), look.paintC.copy(alpha = 0.15f)))),
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (b.paceSecs > 0) {
                            Text(if (p < 0.5f) "In" else "Out", style = MaterialTheme.typography.headlineMedium)
                            Text("${minOf(st.breathsGuided + 1u, b.targetBreaths)} / ${b.targetBreaths}", style = MaterialTheme.typography.displayMedium)
                        } else {
                            Text("${b.targetBreaths} deep breaths", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
                BreathPhase.HOLD -> Ring(1f, Modifier.size(side)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("HOLD", style = MaterialTheme.typography.titleLarge, letterSpacing = 3.sp, color = look.accent)
                        Text(stopwatchTenths(st.phaseElapsedMs), style = MaterialTheme.typography.displayLarge)
                        if (st.bestHoldSecs > 0) Text("Best today ${formatDuration(st.bestHoldSecs)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                BreathPhase.RECOVERY -> Ring(st.recoveryRemainingMs.toFloat() / (b.recoverySecs.toFloat() * 1000).coerceAtLeast(1f), Modifier.size(side)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("RECOVERY HOLD", style = MaterialTheme.typography.titleMedium, letterSpacing = 2.sp, color = look.accent)
                        Text(countdown(st.recoveryRemainingMs), style = MaterialTheme.typography.displayLarge)
                        st.lastHoldSecs?.let { Text("You held ${formatDuration(it)}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
        val (label, hint) = when (st.phase) {
            BreathPhase.BREATHING -> "Done: breathe out and hold" to "Breathe deeply ${b.targetBreaths} times, then tap and breathe all the way out."
            BreathPhase.HOLD -> "Breathe in" to "Hold as long as you comfortably can. Tap when you need to breathe."
            BreathPhase.RECOVERY -> "Skip" to "A full breath in, held. The next round starts by itself."
        }
        Text(hint, Modifier.padding(horizontal = 32.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(
            onClick = { store.live { breathAdvance(System.currentTimeMillis()) } },
            modifier = Modifier.fillMaxWidth().padding(24.dp).height(64.dp),
        ) { Text(label, style = MaterialTheme.typography.titleMedium) }
        if (b.rounds.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                b.rounds.takeLast(6).forEachIndexed { i, r ->
                    Stat(formatDuration(r.holdSecs), "round ${b.rounds.size - minOf(6, b.rounds.size) + i + 1}")
                }
            }
        }
    }
}
