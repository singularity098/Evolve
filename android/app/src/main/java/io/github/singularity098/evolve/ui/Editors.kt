package io.github.singularity098.evolve.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.singularity098.evolve.Store
import io.github.singularity098.evolve.core.Equipment
import io.github.singularity098.evolve.core.Exercise
import io.github.singularity098.evolve.core.Intensity
import io.github.singularity098.evolve.core.Routine
import io.github.singularity098.evolve.core.RoutineExercise
import io.github.singularity098.evolve.core.SetPlan
import io.github.singularity098.evolve.core.TimerBlock
import io.github.singularity098.evolve.core.TimerInterval
import io.github.singularity098.evolve.core.TimerProgram
import io.github.singularity098.evolve.core.formatDuration
import io.github.singularity098.evolve.core.programTotalSecs
import io.github.singularity098.evolve.core.weightIn
import io.github.singularity098.evolve.core.weightToKg

private fun equipmentLabel(e: Equipment) = e.name.lowercase().replaceFirstChar { it.uppercase() }

/** Swaps items i and i + d. */
private fun <T> List<T>.moved(i: Int, d: Int): List<T> {
    val j = i + d
    if (j !in indices) return this
    return toMutableList().also { val t = it[i]; it[i] = it[j]; it[j] = t }
}

private fun <T> List<T>.replaced(i: Int, f: (T) -> T): List<T> = mapIndexed { k, v -> if (k == i) f(v) else v }

// --------------------------------------------------------------- exercises

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExercisesPage(store: Store, onClose: () -> Unit) {
    val library by store.library.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Exercise?>(null) }
    var deleting by remember { mutableStateOf<Exercise?>(null) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        BackBar("Exercises", onClose) {
            IconButton(onClick = { editing = Exercise("", "", Equipment.BARBELL, "") }) { Icon(EvolveIcons.Plus, "New exercise") }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            items(library.exercises, key = { it.id }) { e ->
                ListItem(
                    modifier = Modifier.clickable { editing = e },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(e.name) },
                    supportingContent = { Text(equipmentLabel(e.equipment) + if (e.notes.isNotBlank()) " · ${e.notes}" else "") },
                    trailingContent = { IconButton(onClick = { deleting = e }) { Icon(EvolveIcons.Trash, "Delete ${e.name}") } },
                )
            }
        }
    }
    editing?.let { e ->
        var name by remember(e) { mutableStateOf(e.name) }
        var equipment by remember(e) { mutableStateOf(e.equipment) }
        var notes by remember(e) { mutableStateOf(e.notes) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (e.id.isEmpty()) "New exercise" else "Edit exercise") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Equipment.entries.forEach { q -> FilterChip(equipment == q, { equipment = q }, { Text(equipmentLabel(q)) }) }
                    }
                    OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") })
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    val next = e.copy(name = name.trim(), equipment = equipment, notes = notes.trim())
                    store.act { saveExercise(next) }
                    editing = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
    deleting?.let { e ->
        ConfirmDialog(
            "Delete “${e.name}”?",
            "It's removed from the library and from routines. Past activities keep it.",
            "Delete",
            onConfirm = { store.act { deleteExercise(e.id) } },
            onClose = { deleting = null },
        )
    }
}

// ---------------------------------------------------------------- routines

@Composable
fun RoutinesPage(store: Store, onEdit: (String) -> Unit, onClose: () -> Unit) {
    val library by store.library.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<Routine?>(null) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        BackBar("Routines", onClose) {
            IconButton(onClick = { onEdit("") }) { Icon(EvolveIcons.Plus, "New routine") }
        }
        if (library.routines.isEmpty()) EmptyMessage(EvolveIcons.Dumbbell, "No routines", "The + above starts one.")
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            items(library.routines, key = { it.id }) { r ->
                ListItem(
                    modifier = Modifier.clickable { onEdit(r.id) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { PaintedIcon(EvolveIcons.Dumbbell) },
                    headlineContent = { Text(r.name) },
                    supportingContent = {
                        Text(r.exercises.joinToString(" · ") { re ->
                            val n = library.exercises.find { it.id == re.exerciseId }?.name ?: "?"
                            "$n ${re.sets.size}×${re.sets.firstOrNull()?.reps ?: 0}"
                        })
                    },
                    trailingContent = { IconButton(onClick = { deleting = r }) { Icon(EvolveIcons.Trash, "Delete ${r.name}") } },
                )
            }
        }
    }
    deleting?.let { r ->
        ConfirmDialog("Delete “${r.name}”?", "Past activities keep what was done.", "Delete", onConfirm = { store.act { deleteRoutine(r.id) } }, onClose = { deleting = null })
    }
}

@Composable
fun RoutineEditor(store: Store, id: String, onClose: () -> Unit) {
    val lib = store.library.value
    val s = lib.settings
    val start = remember(id) { lib.routines.find { it.id == id } ?: Routine("", "", "", emptyList()) }
    var name by remember { mutableStateOf(start.name) }
    var notes by remember { mutableStateOf(start.notes) }
    var exercises by remember { mutableStateOf(start.exercises) }
    var adding by remember { mutableStateOf(false) }
    var weightEdit by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    fun save() {
        val r = start.copy(name = name.trim().ifEmpty { "Routine" }, notes = notes.trim(), exercises = exercises)
        store.act { saveRoutine(r) }
        onClose()
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        BackBar(if (id.isEmpty()) "New routine" else "Edit routine", onClose) {
            TextButton(onClick = ::save) { Text("Save") }
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item {
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth())
            }
            itemsIndexed(exercises) { ei, re ->
                val ex = lib.exercises.find { it.id == re.exerciseId }
                Panel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(ex?.name ?: "(deleted exercise)", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { exercises = exercises.moved(ei, -1) }, enabled = ei > 0) { Icon(EvolveIcons.ChevronUp, "Move up") }
                            IconButton(onClick = { exercises = exercises.moved(ei, 1) }, enabled = ei < exercises.size - 1) { Icon(EvolveIcons.ChevronDown, "Move down") }
                            IconButton(onClick = { exercises = exercises.filterIndexed { k, _ -> k != ei } }) { Icon(EvolveIcons.Trash, "Remove") }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Rest", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Stepper(formatDuration(re.restSecs.toDouble()), { d ->
                                exercises = exercises.replaced(ei) { it.copy(restSecs = (it.restSecs.toInt() + d * 15).coerceIn(0, 900).toUInt()) }
                            }, "Rest", 64.dp)
                        }
                        re.sets.forEachIndexed { si, set ->
                            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Set ${si + 1}", Modifier.width(52.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Stepper("${set.reps}", { d ->
                                    exercises = exercises.replaced(ei) { e -> e.copy(sets = e.sets.replaced(si) { it.copy(reps = (it.reps.toInt() + d).coerceIn(1, 100).toUInt()) }) }
                                }, "Reps", 32.dp)
                                Text("reps", Modifier.padding(start = 4.dp), style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { weightEdit = ei to si }, modifier = Modifier.weight(1f)) {
                                    Text(weight(set.weightKg, s))
                                }
                                IconButton(onClick = {
                                    exercises = exercises.replaced(ei) { e -> e.copy(sets = e.sets.filterIndexed { k, _ -> k != si }) }
                                }, modifier = Modifier.size(36.dp)) { Icon(EvolveIcons.Close, "Remove set", Modifier.size(18.dp)) }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            OutlinedButton(onClick = {
                                exercises = exercises.replaced(ei) { e -> e.copy(sets = e.sets + (e.sets.lastOrNull() ?: SetPlan(10u, 0.0))) }
                            }) { Text("Add set") }
                            OutlinedButton(onClick = { weightEdit = ei to -1 }) { Text("Weight for all") }
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
        }
    }
    if (adding) {
        ExercisePicker(store, onPick = { exId ->
            exercises = exercises + RoutineExercise(exId, List(3) { SetPlan(10u, 0.0) }, s.defaultRestSecs)
        }, onClose = { adding = false })
    }
    weightEdit?.let { (ei, si) ->
        val current = exercises.getOrNull(ei)?.sets?.getOrNull(maxOf(si, 0))?.weightKg ?: 0.0
        var text by remember(weightEdit) { mutableStateOf(weightNumber(current, s)) }
        AlertDialog(
            onDismissRequest = { weightEdit = null },
            title = { Text(if (si < 0) "Weight for every set" else "Weight for set ${si + 1}") },
            text = { NumberField(text, { text = it }, "Weight (${unitLabel(s)})") },
            confirmButton = {
                TextButton(onClick = {
                    text.toDoubleOrNull()?.let { v ->
                        val kg = weightToKg(v, s.weightUnit)
                        exercises = exercises.replaced(ei) { e ->
                            e.copy(sets = e.sets.mapIndexed { k, p -> if (si < 0 || k == si) p.copy(weightKg = kg) else p })
                        }
                    }
                    weightEdit = null
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { weightEdit = null }) { Text("Cancel") } },
        )
    }
}

// ------------------------------------------------------- interval programs

@Composable
fun ProgramsPage(store: Store, onEdit: (String) -> Unit, onClose: () -> Unit) {
    val library by store.library.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<TimerProgram?>(null) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        BackBar("Interval programs", onClose) {
            IconButton(onClick = { onEdit("") }) { Icon(EvolveIcons.Plus, "New program") }
        }
        if (library.programs.isEmpty()) EmptyMessage(EvolveIcons.Timer, "No programs", "The + above starts one.")
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            items(library.programs, key = { it.id }) { p ->
                ListItem(
                    modifier = Modifier.clickable { onEdit(p.id) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { PaintedIcon(EvolveIcons.Timer) },
                    headlineContent = { Text(p.name) },
                    supportingContent = { Text("${formatDuration(programTotalSecs(p).toDouble())} · ${p.blocks.size} blocks") },
                    trailingContent = { IconButton(onClick = { deleting = p }) { Icon(EvolveIcons.Trash, "Delete ${p.name}") } },
                )
            }
        }
    }
    deleting?.let { p ->
        ConfirmDialog("Delete “${p.name}”?", "Past rides keep their record.", "Delete", onConfirm = { store.act { deleteProgram(p.id) } }, onClose = { deleting = null })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgramEditor(store: Store, id: String, onClose: () -> Unit) {
    val lib = store.library.value
    val start = remember(id) {
        lib.programs.find { it.id == id } ?: TimerProgram(
            "", "",
            listOf(
                TimerBlock(1u, listOf(TimerInterval("Get ready", 10u, Intensity.REST), TimerInterval("Warm-up", 300u, Intensity.EASY))),
                TimerBlock(5u, listOf(TimerInterval("Work", 30u, Intensity.HARD), TimerInterval("Recover", 90u, Intensity.EASY))),
                TimerBlock(1u, listOf(TimerInterval("Cool-down", 300u, Intensity.EASY))),
            ),
        )
    }
    var name by remember { mutableStateOf(start.name) }
    var blocks by remember { mutableStateOf(start.blocks) }
    var editing by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val total = programTotalSecs(TimerProgram("", "", blocks))

    fun save() {
        val p = start.copy(name = name.trim().ifEmpty { "Intervals" }, blocks = blocks.filter { it.intervals.isNotEmpty() })
        store.act { saveProgram(p) }
        onClose()
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        BackBar(if (id.isEmpty()) "New program" else "Edit program", onClose) {
            TextButton(onClick = ::save) { Text("Save") }
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item {
                Text(
                    "Total ${formatDuration(total.toDouble())}. Each block's intervals play in order, repeated as many times as it says.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            itemsIndexed(blocks) { bi, b ->
                Panel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Block ${bi + 1}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { blocks = blocks.moved(bi, -1) }, enabled = bi > 0) { Icon(EvolveIcons.ChevronUp, "Move up") }
                            IconButton(onClick = { blocks = blocks.moved(bi, 1) }, enabled = bi < blocks.size - 1) { Icon(EvolveIcons.ChevronDown, "Move down") }
                            IconButton(onClick = { blocks = blocks.filterIndexed { k, _ -> k != bi } }) { Icon(EvolveIcons.Trash, "Remove block") }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Repeat", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Stepper("× ${b.repeats}", { d -> blocks = blocks.replaced(bi) { it.copy(repeats = (it.repeats.toInt() + d).coerceIn(1, 99).toUInt()) } }, "Repeat")
                        }
                        b.intervals.forEachIndexed { ii, iv ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                                    .clickable { editing = bi to ii },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                androidx.compose.foundation.layout.Box(
                                    Modifier.size(12.dp).clip(CircleShape).background(intensityColor(iv.intensity)),
                                )
                                Text(iv.label, Modifier.padding(start = 10.dp).weight(1f))
                                Text(formatDuration(iv.secs.toDouble()), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                IconButton(onClick = { blocks = blocks.replaced(bi) { it.copy(intervals = it.intervals.moved(ii, -1)) } }, enabled = ii > 0, modifier = Modifier.size(36.dp)) {
                                    Icon(EvolveIcons.ChevronUp, "Move up", Modifier.size(18.dp))
                                }
                                IconButton(onClick = { blocks = blocks.replaced(bi) { it.copy(intervals = it.intervals.filterIndexed { k, _ -> k != ii }) } }, modifier = Modifier.size(36.dp)) {
                                    Icon(EvolveIcons.Close, "Remove", Modifier.size(18.dp))
                                }
                            }
                        }
                        OutlinedButton(onClick = {
                            blocks = blocks.replaced(bi) { it.copy(intervals = it.intervals + TimerInterval("Interval", 60u, Intensity.MODERATE)) }
                            editing = bi to b.intervals.size
                        }, modifier = Modifier.padding(top = 8.dp)) { Text("Add interval") }
                    }
                }
            }
            item {
                OutlinedButton(onClick = {
                    blocks = blocks + TimerBlock(1u, listOf(TimerInterval("Interval", 60u, Intensity.MODERATE)))
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(EvolveIcons.Plus, null, Modifier.size(18.dp))
                    Text("Add block", Modifier.padding(start = 8.dp))
                }
            }
        }
    }
    editing?.let { (bi, ii) ->
        val iv = blocks.getOrNull(bi)?.intervals?.getOrNull(ii) ?: run { editing = null; return }
        var label by remember(editing) { mutableStateOf(iv.label) }
        var secs by remember(editing) { mutableStateOf(iv.secs.toInt()) }
        var intensity by remember(editing) { mutableStateOf(iv.intensity) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Interval") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(label, { label = it }, label = { Text("Name") }, singleLine = true)
                    Text("Minutes", style = MaterialTheme.typography.labelLarge)
                    Stepper("${secs / 60}", { d -> secs = (secs + d * 60).coerceIn(0, 7200) }, "Minutes")
                    Text("Seconds", style = MaterialTheme.typography.labelLarge)
                    Stepper("%02d".format(secs % 60), { d ->
                        val m = secs / 60
                        val sec = ((secs % 60) + d * 5 + 60) % 60
                        secs = m * 60 + sec
                    }, "Seconds")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Intensity.entries.forEach { q -> FilterChip(intensity == q, { intensity = q }, { Text(intensityLabel(q)) }) }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = secs > 0, onClick = {
                    blocks = blocks.replaced(bi) { b ->
                        b.copy(intervals = b.intervals.replaced(ii) { TimerInterval(label.trim().ifEmpty { intensityLabel(intensity) }, secs.toUInt(), intensity) })
                    }
                    editing = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}

// ------------------------------------------------------------- body weight

@Composable
fun BodyWeightsPage(store: Store, onClose: () -> Unit) {
    val version by store.historyVersion.collectAsStateWithLifecycle()
    val s = store.settings
    val list = remember(version) { store.engine.bodyWeights() }
    var adding by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        BackBar("Body weight", onClose) {
            IconButton(onClick = { adding = true }) { Icon(EvolveIcons.Plus, "Weigh in") }
        }
        if (list.isEmpty()) EmptyMessage(EvolveIcons.Scale, "No weigh-ins yet", "Log your weight any day, active or not; the Graphs tab charts it.")
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            itemsIndexed(list, key = { _, b -> b.id }) { i, b ->
                val prev = list.getOrNull(i + 1)
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(weight(b.weightKg, s)) },
                    supportingContent = {
                        Text("${dateYearLabel(b.atMs)} · ${timeLabel(b.atMs)}" + if (b.note.isNotBlank()) " · ${b.note}" else "")
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            prev?.let {
                                val d = weightIn(b.weightKg, s.weightUnit) - weightIn(it.weightKg, s.weightUnit)
                                Text(
                                    (if (d >= 0) "+" else "−") + "%.1f".format(kotlin.math.abs(d)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { store.act { deleteBodyWeight(b.id) } }) { Icon(EvolveIcons.Trash, "Delete") }
                        }
                    },
                )
            }
        }
    }
    if (adding) BodyWeightDialog(store, onClose = { adding = false })
}
