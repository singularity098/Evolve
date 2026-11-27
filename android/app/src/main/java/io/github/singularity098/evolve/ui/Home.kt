package io.github.singularity098.evolve.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.singularity098.evolve.Store
import io.github.singularity098.evolve.core.ActivitySort
import io.github.singularity098.evolve.core.HistoryFilter
import io.github.singularity098.evolve.core.WorkoutKind
import io.github.singularity098.evolve.core.kindCountsSteps
import io.github.singularity098.evolve.core.kindIsFitness
import io.github.singularity098.evolve.core.kindLabel
import io.github.singularity098.evolve.core.kindUsesGps
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

private fun tileLabel(k: WorkoutKind) = when (k) {
    WorkoutKind.MEDITATION -> "Meditate"
    WorkoutKind.BREATHING -> "Breathe"
    else -> kindLabel(k)
}

private fun tileDetail(k: WorkoutKind) = when (k) {
    WorkoutKind.LIFTING -> "Routines, sets and rest"
    WorkoutKind.WALK, WorkoutKind.RUN, WorkoutKind.HIKE, WorkoutKind.BIKE -> "GPS and map"
    WorkoutKind.STATIONARY_BIKE -> "Timers and intervals"
    WorkoutKind.MEDITATION -> "A quiet timer"
    WorkoutKind.BREATHING -> "Breath-hold rounds"
    WorkoutKind.BOAT, WorkoutKind.DRIVE -> "A trip, kept out of trends"
}

private val SortChoices = listOf(
    ActivitySort.STANDARD to "Standard order",
    ActivitySort.MOST_USED to "Most used first",
    ActivitySort.RECENT to "Most recent first",
    ActivitySort.ALPHABETICAL to "A to Z",
)

@Composable
fun HomeScreen(store: Store, host: Host) {
    val library by store.library.collectAsStateWithLifecycle()
    val session by store.session.collectAsStateWithLifecycle()
    val version by store.historyVersion.collectAsStateWithLifecycle()
    val s = library.settings
    var picking by rememberSaveable { mutableStateOf<WorkoutKind?>(null) }
    var busy by remember { mutableStateOf(false) }
    var weighIn by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf<WorkoutKind?>(null) }
    var sortMenu by remember { mutableStateOf(false) }
    var arranging by remember { mutableStateOf(false) }

    val today = LocalDate.now()
    val everything = remember(version) { store.engine.history(HistoryFilter(emptyList(), "")) }
    // Boat and car rides are trips, not exercise: they don't count towards the week or the streak.
    val history = remember(everything) { everything.filter { kindIsFitness(it.kind) } }
    val kinds = remember(version, s.activitySort, s.activityOrder) { store.engine.homeKinds(s.activitySort, s.activityOrder) }
    val weights = remember(version) { store.engine.bodyWeights() }
    val weekStart = today.with(TemporalAdjusters.previousOrSame(WeekStart))
    val thisWeek = history.count { !localDate(it.startedAtMs).isBefore(weekStart) }
    val days = history.map { localDate(it.startedAtMs) }.toSet()
    // Days in a row with a workout, ending today (or yesterday, if not yet today).
    var streak = 0
    var d = if (today in days) today else today.minusDays(1)
    while (d in days) {
        streak++
        d = d.minusDays(1)
    }
    val todays = everything.filter { localDate(it.startedAtMs) == today }

    fun pick(k: WorkoutKind) {
        if (session != null) {
            busy = true
            return
        }
        val now = System.currentTimeMillis()
        when {
            kindUsesGps(k) -> host.withLocation(kindCountsSteps(k)) { store.start { startGps(k, now) } }
            k == WorkoutKind.BREATHING || k == WorkoutKind.MEDITATION || k == WorkoutKind.LIFTING || k == WorkoutKind.STATIONARY_BIKE -> picking = k
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(2) }) {
            Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                val hour = LocalTime.now().hour
                Text(
                    when {
                        hour < 5 -> "Good night"
                        hour < 12 -> "Good morning"
                        hour < 18 -> "Good afternoon"
                        else -> "Good evening"
                    },
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    dateYearLabel(System.currentTimeMillis()),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(span = { GridItemSpan(2) }) {
            Panel(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Stat("$thisWeek", "this week", Modifier.weight(1f))
                    Stat("$streak", if (streak == 1) "day streak" else "days streak", Modifier.weight(1f))
                    Stat(weights.firstOrNull()?.let { weightNumber(it.weightKg, s) } ?: "–", "weight (${unitLabel(s)})", Modifier.weight(1f))
                }
            }
        }
        session?.let { live ->
            item(span = { GridItemSpan(2) }) {
                Panel(Modifier.fillMaxWidth(), onClick = { store.say("Swipe or tap In progress below") }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(kindIcon(live.kind))
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text("Activity in progress", style = MaterialTheme.typography.titleMedium)
                            Text(kindLabel(live.kind), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        PaintedIcon(EvolveIcons.Pulse, Modifier.size(28.dp))
                    }
                }
            }
        }
        item(span = { GridItemSpan(2) }) {
            Row(verticalAlignment = Alignment.Bottom) {
                SectionTitle(
                    if (todays.isEmpty()) "Choose today's activity" else "Something more today?",
                    Modifier.padding(start = 0.dp).weight(1f),
                )
                Box {
                    IconButton(onClick = { sortMenu = true }) {
                        Icon(EvolveIcons.Sort, "Order of activities", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        SortChoices.forEach { (sort, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                trailingIcon = { if (s.activitySort == sort) Icon(EvolveIcons.Check, null) },
                                onClick = {
                                    sortMenu = false
                                    store.updateSettings { it.copy(activitySort = sort) }
                                },
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Arrange by hand…") },
                            trailingIcon = { if (s.activitySort == ActivitySort.MANUAL) Icon(EvolveIcons.Check, null) },
                            onClick = { sortMenu = false; arranging = true },
                        )
                    }
                }
            }
        }
        items(kinds, key = { it.name }) { k ->
            Panel(Modifier.fillMaxWidth().height(118.dp), onClick = { pick(k) }) {
                Column(Modifier.padding(14.dp)) {
                    IconBadge(kindIcon(k), 42.dp)
                    Spacer(Modifier.weight(1f))
                    Text(tileLabel(k), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(
                        tileDetail(k),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        item(span = { GridItemSpan(2) }) { SectionTitle("Log", Modifier.padding(start = 0.dp)) }
        item {
            Panel(Modifier.fillMaxWidth(), onClick = { weighIn = true }) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    PaintedIcon(EvolveIcons.Scale, Modifier.size(26.dp))
                    Column(Modifier.padding(start = 10.dp)) {
                        Text("Body weight", style = MaterialTheme.typography.titleSmall)
                        Text(
                            weights.firstOrNull()?.let { shortDate(it.atMs) } ?: "Not yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            Panel(Modifier.fillMaxWidth(), onClick = { manual = WorkoutKind.MEDITATION }) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    PaintedIcon(EvolveIcons.Edit, Modifier.size(26.dp))
                    Column(Modifier.padding(start = 10.dp)) {
                        Text("Past activity", style = MaterialTheme.typography.titleSmall)
                        Text("Enter it by hand", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (todays.isNotEmpty()) {
            item(span = { GridItemSpan(2) }) { SectionTitle("Today", Modifier.padding(start = 0.dp)) }
            items(todays, span = { GridItemSpan(2) }) { w ->
                Panel(Modifier.fillMaxWidth(), onClick = { store.showWorkout.tryEmit(w.id) }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(kindIcon(w.kind), 38.dp)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(w.name, style = MaterialTheme.typography.titleSmall)
                            Text(w.headline, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item(span = { GridItemSpan(2) }) { Spacer(Modifier.height(16.dp)) }
    }

    picking?.let { k -> StartSheet(store, k, onManual = { picking = null; manual = k }, onClose = { picking = null }) }
    if (busy) {
        AlertDialog(
            onDismissRequest = { busy = false },
            icon = { PaintedIcon(EvolveIcons.Pulse) },
            title = { Text("An activity is under way") },
            text = { Text("Finish or discard it on the In progress tab before starting another.") },
            confirmButton = { TextButton(onClick = { busy = false }) { Text("OK") } },
        )
    }
    if (arranging) {
        ArrangeActivitiesDialog(kinds, onClose = { arranging = false }) { order ->
            arranging = false
            store.updateSettings { it.copy(activitySort = ActivitySort.MANUAL, activityOrder = order) }
        }
    }
    if (weighIn) BodyWeightDialog(store, onClose = { weighIn = false })
    manual?.let { k -> ManualWorkoutDialog(store, k, onClose = { manual = null }) }
}

/** The activities in a list, each moved up or down with its arrows; Save keeps that order. */
@Composable
private fun ArrangeActivitiesDialog(current: List<WorkoutKind>, onClose: () -> Unit, onSave: (List<WorkoutKind>) -> Unit) {
    val order = remember { mutableStateListOf(*current.toTypedArray()) }
    AlertDialog(
        onDismissRequest = onClose,
        icon = { PaintedIcon(EvolveIcons.Sort) },
        title = { Text("Arrange activities") },
        text = {
            LazyColumn(Modifier.height(420.dp)) {
                itemsIndexed(order, key = { _, k -> k.name }) { i, k ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(kindIcon(k), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(tileLabel(k), Modifier.padding(start = 12.dp).weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { order.add(i - 1, order.removeAt(i)) }, enabled = i > 0) {
                            Icon(EvolveIcons.ChevronUp, "Move up")
                        }
                        IconButton(onClick = { order.add(i + 1, order.removeAt(i)) }, enabled = i < order.size - 1) {
                            Icon(EvolveIcons.ChevronDown, "Move down")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(order.toList()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}
