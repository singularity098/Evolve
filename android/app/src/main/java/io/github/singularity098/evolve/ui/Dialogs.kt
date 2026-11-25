package io.github.singularity098.evolve.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.singularity098.evolve.Store
import io.github.singularity098.evolve.core.Session
import io.github.singularity098.evolve.core.TimerMode
import io.github.singularity098.evolve.core.WorkoutKind
import io.github.singularity098.evolve.core.distanceToM
import io.github.singularity098.evolve.core.formatDuration
import io.github.singularity098.evolve.core.kindLabel
import io.github.singularity098.evolve.core.programTotalSecs
import io.github.singularity098.evolve.core.weightToKg
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** A text field for numbers; [onValue] gets null while it isn't one. */
@Composable
fun NumberField(text: String, onText: (String) -> Unit, label: String, modifier: Modifier = Modifier, decimal: Boolean = true) {
    OutlinedTextField(
        value = text,
        onValueChange = { v -> onText(v.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }.replace(',', '.')) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onClose()
            }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

/** What to start for a kind that has choices: a routine, a program, minutes. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StartSheet(store: Store, kind: WorkoutKind, onManual: () -> Unit, onClose: () -> Unit) {
    val lib = store.library.value
    val s = lib.settings
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var starting by remember { mutableStateOf(false) }
    // The sheet slides away first; only once it's gone does the workout start (and the app
    // turn to the In progress tab), so the sheet's window isn't torn down mid-change.
    fun go(f: io.github.singularity098.evolve.core.Engine.() -> Session) {
        if (starting) return
        starting = true
        scope.launch { sheet.hide() }.invokeOnCompletion {
            onClose()
            store.start(f)
        }
    }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(kindIcon(kind))
                Text(kindLabel(kind), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.headlineSmall)
            }
            val now = System.currentTimeMillis()
            when (kind) {
                WorkoutKind.LIFTING -> {
                    SectionTitle("Routines")
                    if (lib.routines.isEmpty()) Hint("No routines yet: design them in Settings → Routines.")
                    lib.routines.forEach { r ->
                        ListItem(
                            modifier = Modifier.clickable { go { startLifting(r.id, now) } },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = { PaintedIcon(EvolveIcons.Dumbbell, Modifier.size(24.dp)) },
                            headlineContent = { Text(r.name) },
                            supportingContent = {
                                val names = r.exercises.mapNotNull { re -> lib.exercises.find { it.id == re.exerciseId }?.name }
                                Text(names.joinToString(" · ").ifEmpty { r.notes })
                            },
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    ListItem(
                        modifier = Modifier.clickable { go { startLifting(null, now) } },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { PaintedIcon(EvolveIcons.Plus, Modifier.size(24.dp)) },
                        headlineContent = { Text("Empty workout") },
                        supportingContent = { Text("Add exercises as you go") },
                    )
                }
                WorkoutKind.STATIONARY_BIKE -> {
                    var minutes by remember { mutableIntStateOf(30) }
                    SectionTitle("Simple")
                    ListItem(
                        modifier = Modifier.clickable { go { startTimer(kind, TimerMode.STOPWATCH, null, 0u, now) } },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { PaintedIcon(EvolveIcons.Timer, Modifier.size(24.dp)) },
                        headlineContent = { Text("Free ride") },
                        supportingContent = { Text("A stopwatch, until you stop") },
                    )
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { PaintedIcon(EvolveIcons.Timer, Modifier.size(24.dp)) },
                        headlineContent = { Text("Countdown") },
                        supportingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Stepper("$minutes min", { d -> minutes = (minutes + d * 5).coerceIn(5, 240) }, "Minutes", 72.dp)
                            }
                        },
                        trailingContent = {
                            Button(onClick = { go { startTimer(kind, TimerMode.COUNTDOWN, null, (minutes * 60).toUInt(), now) } }) { Text("Start") }
                        },
                    )
                    SectionTitle("Interval programs")
                    if (lib.programs.isEmpty()) Hint("Design interval programs in Settings → Interval programs.")
                    lib.programs.forEach { p ->
                        ListItem(
                            modifier = Modifier.clickable { go { startTimer(kind, TimerMode.PROGRAM, p.id, 0u, now) } },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = { PaintedIcon(EvolveIcons.Pulse, Modifier.size(24.dp)) },
                            headlineContent = { Text(p.name) },
                            supportingContent = {
                                val n = p.blocks.sumOf { b -> b.repeats.toInt().coerceAtLeast(1) * b.intervals.size }
                                Text("${formatDuration(programTotalSecs(p).toDouble())} · $n intervals")
                            },
                        )
                    }
                }
                WorkoutKind.MEDITATION -> {
                    var minutes by remember { mutableIntStateOf(s.meditationMinutes.toInt().coerceAtLeast(1)) }
                    Hint("A quiet timer. A singing bowl sounds at the end.", Modifier.padding(top = 8.dp))
                    FlowRow(
                        Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(5, 10, 15, 20, 30, 45, 60).forEach { m ->
                            FilterChip(selected = minutes == m, onClick = { minutes = m }, label = { Text("$m min") })
                        }
                    }
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Stepper("$minutes min", { d -> minutes = (minutes + d).coerceIn(1, 180) }, "Minutes", 72.dp)
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                            Button(onClick = { go { startMeditation((minutes * 60).toUInt(), now) } }) { Text("Begin") }
                        }
                    }
                    TextButton(onClick = onManual, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                        Text("Already meditated? Enter the time")
                    }
                }
                WorkoutKind.BREATHING -> {
                    Hint(
                        "Each round: ${s.breathCount} deep breaths, then breathe out fully and hold as long as you can. " +
                            "Then a full breath in, held for ${s.breathRecoverySecs} seconds, and the next round begins.",
                        Modifier.padding(top = 8.dp),
                    )
                    Hint("Never practise in water, or while driving. Change the counts in Settings.")
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
                        Button(onClick = { go { startBreathing(now) } }) { Text("Begin") }
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
fun BodyWeightDialog(store: Store, onClose: () -> Unit) {
    val s = store.settings
    val last = remember { store.engine.bodyWeights().firstOrNull() }
    var text by remember { mutableStateOf(last?.let { weightNumber(it.weightKg, s) } ?: "") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        icon = { PaintedIcon(EvolveIcons.Scale) },
        title = { Text("Body weight") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(text, { text = it }, "Weight (${unitLabel(s)})")
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, singleLine = true)
                last?.let { Text("Last: ${weight(it.weightKg, s)} on ${shortDate(it.atMs)}", style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.toDoubleOrNull()?.let { it > 0 } == true,
                onClick = {
                    val kg = weightToKg(text.toDouble(), s.weightUnit)
                    store.act { addBodyWeight(System.currentTimeMillis(), kg, note.trim()) }
                    store.say("Weight saved")
                    onClose()
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

/** A workout entered after the fact. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualWorkoutDialog(store: Store, initial: WorkoutKind, onClose: () -> Unit) {
    val s = store.settings
    var kind by remember { mutableStateOf(initial) }
    var name by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf(if (initial == WorkoutKind.MEDITATION) s.meditationMinutes.toString() else "30") }
    var distance by remember { mutableStateOf("") }
    var daysAgo by remember { mutableIntStateOf(0) }
    var notes by remember { mutableStateOf("") }
    val hasDistance = kind != WorkoutKind.LIFTING && kind != WorkoutKind.MEDITATION && kind != WorkoutKind.BREATHING
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Enter a workout") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WorkoutKind.entries.forEach { k ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(kindLabel(k)) })
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Name (optional)") }, singleLine = true)
                NumberField(minutes, { minutes = it }, "Minutes")
                if (hasDistance) NumberField(distance, { distance = it }, "Distance (${if (s.distanceUnit.name == "KM") "km" else "mi"}, optional)")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("When", Modifier.weight(1f))
                    Stepper(
                        when (daysAgo) {
                            0 -> "Today"
                            1 -> "Yesterday"
                            else -> "$daysAgo days ago"
                        },
                        { d -> daysAgo = (daysAgo - d).coerceAtLeast(0) },
                        "Day",
                        100.dp,
                    )
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") })
            }
        },
        confirmButton = {
            TextButton(
                enabled = minutes.toDoubleOrNull()?.let { it > 0 } == true,
                onClick = {
                    val mins = minutes.toDouble()
                    val day = LocalDate.now().minusDays(daysAgo.toLong())
                    // Today: ended just now. Another day: noon.
                    val start = if (daysAgo == 0) System.currentTimeMillis() - (mins * 60_000).toLong()
                    else day.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val dist = if (hasDistance) distance.toDoubleOrNull()?.let { distanceToM(it, s.distanceUnit) } else null
                    store.act { logManual(kind, name.trim(), start, mins * 60, dist, notes.trim()) }
                    store.say("Saved")
                    onClose()
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

/** Save (with a name) or discard the workout under way. */
@Composable
fun FinishDialog(store: Store, session: Session, onClose: () -> Unit) {
    val s = store.settings
    val place = session.gps?.placeName
    var name by remember { mutableStateOf(store.engine.defaultSessionName()) }
    var edited by remember { mutableStateOf(false) }
    // The place name may arrive while the dialog is open.
    LaunchedEffect(place) {
        if (!edited) name = store.engine.defaultSessionName()
    }
    var notes by remember { mutableStateOf("") }
    var distance by remember { mutableStateOf("") }
    var updateRoutine by remember { mutableStateOf(true) }
    var discard by remember { mutableStateOf(false) }
    val lifting = session.lifting
    AlertDialog(
        onDismissRequest = onClose,
        icon = { PaintedIcon(EvolveIcons.Flag) },
        title = { Text("Finish ${kindLabel(session.kind).lowercase()}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it; edited = true }, label = { Text("Name") }, singleLine = true)
                if (session.kind == WorkoutKind.STATIONARY_BIKE) {
                    NumberField(distance, { distance = it }, "Distance (${if (s.distanceUnit.name == "KM") "km" else "mi"}, optional)")
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") })
                if (lifting?.routineId != null) {
                    Row(
                        Modifier.clickable { updateRoutine = !updateRoutine },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(updateRoutine, { updateRoutine = it })
                        Text("Update “${lifting.routineName}” with today's weights")
                    }
                }
                TextButton(onClick = { discard = true }) {
                    Text("Discard this workout", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val dist = distance.toDoubleOrNull()?.let { distanceToM(it, s.distanceUnit) }
                store.finish(name, notes.trim(), dist, updateRoutine && lifting?.routineId != null)
                onClose()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Keep going") } },
    )
    if (discard) {
        ConfirmDialog(
            "Discard the workout?",
            "It won't be saved to your history.",
            "Discard",
            onConfirm = {
                store.discard()
                onClose()
            },
            onClose = { discard = false },
        )
    }
}

/** The pieces of a weight in kg as typed in the chosen unit. */
fun parseWeight(text: String, store: Store): Double? = text.toDoubleOrNull()?.let { weightToKg(it, store.settings.weightUnit) }
