package io.github.singularity098.evolve.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.singularity098.evolve.core.Intensity
import io.github.singularity098.evolve.core.Settings
import io.github.singularity098.evolve.core.WeightUnit
import io.github.singularity098.evolve.core.WorkoutKind
import io.github.singularity098.evolve.core.formatNumber
import io.github.singularity098.evolve.core.formatWeight
import io.github.singularity098.evolve.core.weightIn
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

fun kindIcon(kind: WorkoutKind): ImageVector = when (kind) {
    WorkoutKind.LIFTING -> EvolveIcons.Dumbbell
    WorkoutKind.WALK -> EvolveIcons.Walk
    WorkoutKind.RUN -> EvolveIcons.Run
    WorkoutKind.HIKE -> EvolveIcons.Mountain
    WorkoutKind.BIKE -> EvolveIcons.Bike
    WorkoutKind.STATIONARY_BIKE -> EvolveIcons.StationaryBike
    WorkoutKind.MEDITATION -> EvolveIcons.Lotus
    WorkoutKind.BREATHING -> EvolveIcons.Wind
}

fun intensityColor(i: Intensity): Color = when (i) {
    Intensity.REST -> Color(0xFF8A94A8)
    Intensity.EASY -> Color(0xFF5FD39A)
    Intensity.MODERATE -> Color(0xFFF2D06B)
    Intensity.HARD -> Color(0xFFFF9A4D)
    Intensity.MAX -> Color(0xFFFF5468)
}

fun intensityLabel(i: Intensity): String = when (i) {
    Intensity.REST -> "Rest"
    Intensity.EASY -> "Easy"
    Intensity.MODERATE -> "Moderate"
    Intensity.HARD -> "Hard"
    Intensity.MAX -> "Max"
}

private val zone: ZoneId get() = ZoneId.systemDefault()

fun localDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

/** Weeks start on Sunday. */
val WeekStart: java.time.DayOfWeek = java.time.DayOfWeek.SUNDAY

fun dayStartMs(d: LocalDate): Long = d.atStartOfDay(zone).toInstant().toEpochMilli()

fun dateLabel(ms: Long): String = DateTimeFormatter.ofPattern("EEE, MMM d").format(Instant.ofEpochMilli(ms).atZone(zone))

fun dateYearLabel(ms: Long): String = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy").format(Instant.ofEpochMilli(ms).atZone(zone))

fun timeLabel(ms: Long): String = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(Instant.ofEpochMilli(ms).atZone(zone))

fun shortDate(ms: Long): String = DateTimeFormatter.ofPattern("MMM d").format(Instant.ofEpochMilli(ms).atZone(zone))

fun weight(kg: Double, s: Settings) = formatWeight(kg, s.weightUnit)

/** A weight in the chosen unit, without the unit: "135". */
fun weightNumber(kg: Double, s: Settings) = formatNumber(weightIn(kg, s.weightUnit), 1u)

fun weightStep(s: Settings) = if (s.weightUnit == WeightUnit.LB) 5.0 else 2.5

fun unitLabel(s: Settings) = if (s.weightUnit == WeightUnit.LB) "lb" else "kg"

/** The time now, a few times a second. */
@Composable
fun rememberNow(periodMs: Long = 100): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        value = System.currentTimeMillis()
        delay(periodMs)
    }
}

/** "1:05" from milliseconds, rounded up (a countdown shows 0:01 until it ends). */
fun countdown(ms: Long): String {
    val s = (ms + 999) / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

fun stopwatch(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/** A stopwatch with tenths: "1:23.4". */
fun stopwatchTenths(ms: Long): String = "%s.%d".format(stopwatch(ms), (ms % 1000) / 100)
