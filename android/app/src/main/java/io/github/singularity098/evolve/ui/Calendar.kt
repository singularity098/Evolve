package io.github.singularity098.evolve.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.singularity098.evolve.Store
import io.github.singularity098.evolve.core.formatDuration
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarScreen(store: Store) {
    val version by store.historyVersion.collectAsStateWithLifecycle()
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthText)
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val look = LocalLook.current
    val today = LocalDate.now()

    val first = month.atDay(1)
    val workouts = remember(version, monthText) {
        store.engine.workoutsBetween(dayStartMs(first), dayStartMs(month.plusMonths(1).atDay(1)))
    }
    val byDay = workouts.groupBy { localDate(it.startedAtMs) }
    // Weeks start on Monday.
    val lead = (first.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    val cells = lead + month.lengthOfMonth()
    val rows = (cells + 6) / 7

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        Text("Calendar", Modifier.padding(start = 16.dp, top = 16.dp), style = MaterialTheme.typography.headlineMedium)
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { monthText = month.minusMonths(1).toString(); selected = null }) { Icon(EvolveIcons.ChevronLeft, "Previous month") }
            Text(
                DateTimeFormatter.ofPattern("MMMM yyyy").format(month),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { monthText = month.plusMonths(1).toString(); selected = null }) { Icon(EvolveIcons.ChevronRight, "Next month") }
        }
        if (month != YearMonth.now()) {
            TextButton(onClick = { monthText = YearMonth.now().toString() }, Modifier.align(Alignment.CenterHorizontally)) { Text("Back to today") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            DayOfWeek.entries.forEach { d ->
                Text(
                    d.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                for (c in 0 until 7) {
                    val n = r * 7 + c - lead + 1
                    Box(Modifier.weight(1f).aspectRatio(0.9f).padding(3.dp), contentAlignment = Alignment.Center) {
                        if (n in 1..month.lengthOfMonth()) {
                            val day = month.atDay(n)
                            val list = byDay[day].orEmpty()
                            val isSel = selected == day.toString()
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        when {
                                            list.isNotEmpty() -> look.paintC.copy(alpha = 0.25f)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .border(
                                        if (isSel || day == today) 2.dp else 0.dp,
                                        if (isSel) look.paintA else if (day == today) look.paintB.copy(alpha = 0.6f) else Color.Transparent,
                                        RoundedCornerShape(12.dp),
                                    )
                                    .clickable {
                                        // One workout: straight to it. More: list them below.
                                        if (list.size == 1) store.showWorkout.tryEmit(list[0].id)
                                        selected = day.toString()
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    "$n",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (day == today) FontWeight.Medium else FontWeight.Normal,
                                    color = if (day.isAfter(today)) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                )
                                if (list.isNotEmpty()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                                        list.map { it.kind }.distinct().take(3).forEach { k ->
                                            PaintedIcon(kindIcon(k), Modifier.size(12.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // The month in numbers.
        val active = byDay.size
        val minutes = workouts.sumOf { it.durationSecs } / 60
        Panel(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.padding(vertical = 14.dp)) {
                Stat("${workouts.size}", "workouts", Modifier.weight(1f))
                Stat("$active", "active days", Modifier.weight(1f))
                Stat(formatDuration(minutes * 60), "total time", Modifier.weight(1f))
            }
        }

        selected?.let { LocalDate.parse(it) }?.let { day ->
            val list = byDay[day].orEmpty()
            SectionTitle(dateYearLabel(dayStartMs(day)))
            if (list.isEmpty()) Hint(if (day.isAfter(today)) "Still to come." else "A rest day.")
            list.forEach { w ->
                Panel(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
                    onClick = { store.showWorkout.tryEmit(w.id) },
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(kindIcon(w.kind), 38.dp)
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(w.name, style = MaterialTheme.typography.titleSmall)
                            Text("${timeLabel(w.startedAtMs)} · ${w.headline}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(EvolveIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Box(Modifier.size(24.dp).clip(CircleShape))
    }
}
