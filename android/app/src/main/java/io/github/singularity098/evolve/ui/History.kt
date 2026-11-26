package io.github.singularity098.evolve.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.singularity098.evolve.Store
import io.github.singularity098.evolve.core.HistoryFilter
import io.github.singularity098.evolve.core.TimerMode
import io.github.singularity098.evolve.core.Workout
import io.github.singularity098.evolve.core.WorkoutKind
import io.github.singularity098.evolve.core.formatDistance
import io.github.singularity098.evolve.core.formatDuration
import io.github.singularity098.evolve.core.formatElevation
import io.github.singularity098.evolve.core.formatNumber
import io.github.singularity098.evolve.core.formatPace
import io.github.singularity098.evolve.core.formatSpeed
import io.github.singularity098.evolve.core.kindLabel
import io.github.singularity098.evolve.core.weightIn
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(store: Store, host: Host, open: String?, onOpen: (String?) -> Unit) {
    if (open != null) {
        val version by store.historyVersion.collectAsStateWithLifecycle()
        val w = remember(open, version) { store.engine.workout(open) }
        if (w == null) {
            onOpen(null)
            return
        }
        BackHandler { onOpen(null) }
        WorkoutDetail(store, host, w, onClose = { onOpen(null) })
        return
    }
    val version by store.historyVersion.collectAsStateWithLifecycle()
    var kinds by rememberSaveable { mutableStateOf(setOf<WorkoutKind>()) }
    var query by rememberSaveable { mutableStateOf("") }
    val list = remember(version, kinds, query) { store.engine.history(HistoryFilter(kinds.toList(), query)) }
    val months = DateTimeFormatter.ofPattern("MMMM yyyy")

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("History", Modifier.padding(start = 16.dp, top = 16.dp), style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            query, { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search names and notes") },
            leadingIcon = { Icon(EvolveIcons.Search, null) },
            trailingIcon = if (query.isNotEmpty()) {
                { IconButton(onClick = { query = "" }) { Icon(EvolveIcons.Close, "Clear") } }
            } else null,
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(selected = kinds.isEmpty(), onClick = { kinds = emptySet() }, label = { Text("All") })
            }
            items(WorkoutKind.entries) { k ->
                FilterChip(
                    selected = k in kinds,
                    onClick = { kinds = if (k in kinds) kinds - k else kinds + k },
                    label = { Text(kindLabel(k)) },
                    leadingIcon = { Icon(kindIcon(k), null, Modifier.size(18.dp)) },
                )
            }
        }
        if (list.isEmpty()) {
            EmptyMessage(
                EvolveIcons.History,
                if (kinds.isEmpty() && query.isEmpty()) "No workouts yet" else "Nothing matches",
                if (kinds.isEmpty() && query.isEmpty()) "Your finished workouts gather here, newest first." else "Try other filters.",
            )
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            var lastMonth = ""
            list.forEach { w ->
                val m = months.format(localDate(w.startedAtMs))
                if (m != lastMonth) {
                    lastMonth = m
                    item(key = "m:$m") { SectionTitle(m, Modifier.padding(start = 0.dp)) }
                }
                item(key = w.id) {
                    Panel(Modifier.fillMaxWidth(), onClick = { onOpen(w.id) }) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(kindIcon(w.kind))
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(w.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                Text(
                                    "${dateLabel(w.startedAtMs)} · ${timeLabel(w.startedAtMs)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(w.headline, style = MaterialTheme.typography.bodyMedium)
                            }
                            Icon(EvolveIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutDetail(store: Store, host: Host, w: Workout, onClose: () -> Unit) {
    val s = store.settings
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val track = remember(w.id) { if (w.gps != null) store.engine.track(w.id) else emptyList() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(EvolveIcons.Back, "Back") }
            Spacer(Modifier.weight(1f))
            if (track.isNotEmpty()) IconButton(onClick = { host.saveGpx(w.id, w.name) }) { Icon(EvolveIcons.Export, "Save as GPX") }
            IconButton(onClick = { editing = true }) { Icon(EvolveIcons.Edit, "Rename") }
            IconButton(onClick = { deleting = true }) { Icon(EvolveIcons.Trash, "Delete") }
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(kindIcon(w.kind), 52.dp)
                Column(Modifier.padding(start = 14.dp)) {
                    Text(w.name, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "${kindLabel(w.kind)} · ${dateYearLabel(w.startedAtMs)} · ${timeLabel(w.startedAtMs)}" + if (w.manual) " · entered by hand" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (w.notes.isNotBlank()) Text(w.notes, style = MaterialTheme.typography.bodyLarge)

            w.gps?.let { g ->
                if (track.isNotEmpty()) {
                    TrailMap(
                        track, null,
                        Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(20.dp)),
                        interactive = false,
                        dark = !rememberDaylight().value,
                    )
                }
                Panel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row {
                            Stat(formatDistance(g.distanceM, s.distanceUnit), "distance", Modifier.weight(1f))
                            Stat(formatDuration(w.durationSecs), "time", Modifier.weight(1f))
                        }
                        Row {
                            if (w.kind == WorkoutKind.BIKE) Stat(formatSpeed(g.distanceM, g.movingSecs, s.distanceUnit), "avg speed", Modifier.weight(1f))
                            else Stat(formatPace(g.distanceM, g.movingSecs, s.distanceUnit), "pace", Modifier.weight(1f))
                            Stat(formatDuration(g.movingSecs), "moving", Modifier.weight(1f))
                            Stat(formatElevation(g.elevationGainM, s.distanceUnit), "climb", Modifier.weight(1f))
                        }
                    }
                }
            }
            if (w.gps == null) {
                Panel(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(vertical = 14.dp)) {
                        Stat(formatDuration(w.durationSecs), "time", Modifier.weight(1f))
                        w.distanceM?.let { d ->
                            Stat(formatDistance(d, s.distanceUnit), "distance", Modifier.weight(1f))
                            Stat(
                                if (w.kind == WorkoutKind.STATIONARY_BIKE || w.kind == WorkoutKind.BIKE) formatSpeed(d, w.durationSecs, s.distanceUnit)
                                else formatPace(d, w.durationSecs, s.distanceUnit),
                                if (w.kind == WorkoutKind.STATIONARY_BIKE || w.kind == WorkoutKind.BIKE) "speed" else "pace",
                                Modifier.weight(1f),
                            )
                        }
                        w.lifting?.let { l ->
                            val sets = l.exercises.sumOf { e -> e.sets.count { it.done } }
                            Stat("$sets", "sets", Modifier.weight(1f))
                            val vol = l.exercises.sumOf { e -> e.sets.filter { it.done }.sumOf { it.weightKg * it.reps.toDouble() } }
                            Stat(formatNumber(weightIn(vol, s.weightUnit), 0u), "volume (${unitLabel(s)})", Modifier.weight(1f))
                        }
                        w.breath?.let { b ->
                            Stat("${b.rounds.size}", "rounds", Modifier.weight(1f))
                            Stat(formatDuration(b.rounds.maxOfOrNull { it.holdSecs } ?: 0.0), "best hold", Modifier.weight(1f))
                        }
                    }
                }
            }
            w.lifting?.let { l ->
                l.exercises.forEach { e ->
                    Panel(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(e.name, style = MaterialTheme.typography.titleMedium)
                            e.sets.forEachIndexed { i, set ->
                                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Set ${i + 1}", Modifier.width(64.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        if (set.done) "${set.reps} × ${weight(set.weightKg, s)}" else "skipped (${set.targetReps} × ${weight(set.weightKg, s)})",
                                        Modifier.weight(1f),
                                        color = if (set.done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (set.done && set.reps < set.targetReps) {
                                        Text("of ${set.targetReps}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (set.done) PaintedIcon(EvolveIcons.Check, Modifier.padding(start = 8.dp).size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
            w.timer?.let { t ->
                Panel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            when (t.mode) {
                                TimerMode.STOPWATCH -> "Free ride"
                                TimerMode.COUNTDOWN -> "Countdown"
                                TimerMode.PROGRAM -> "Program: ${t.programName}"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (t.mode == TimerMode.PROGRAM) {
                            Text(
                                "${t.intervalsDone} of ${t.intervalsTotal} intervals",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            w.breath?.let { b ->
                val best = b.rounds.maxOfOrNull { it.holdSecs }?.coerceAtLeast(1.0) ?: 1.0
                val look = LocalLook.current
                Panel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Breath holds", style = MaterialTheme.typography.titleMedium)
                        b.rounds.forEachIndexed { i, r ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${i + 1}", Modifier.width(28.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Box(Modifier.weight(1f).height(18.dp)) {
                                    Box(
                                        Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth((r.holdSecs / best).toFloat().coerceIn(0.02f, 1f))
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(look.paint()),
                                    )
                                }
                                Text(formatDuration(r.holdSecs), Modifier.padding(start = 10.dp).width(52.dp))
                            }
                        }
                        if (b.rounds.isEmpty()) Text("No rounds were finished.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (editing) {
        var name by remember { mutableStateOf(w.name) }
        var notes by remember { mutableStateOf(w.notes) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Edit workout") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(notes, { notes = it }, label = { Text("Notes") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    store.act { updateWorkout(w.id, name.trim().ifEmpty { w.name }, notes.trim()) }
                    editing = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } },
        )
    }
    if (deleting) {
        ConfirmDialog(
            "Delete “${w.name}”?",
            "It's removed from your history for good (an export you made keeps it).",
            "Delete",
            onConfirm = {
                store.act { deleteWorkout(w.id) }
                onClose()
            },
            onClose = { deleting = false },
        )
    }
}
