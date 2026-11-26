package io.github.singularity098.evolve.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.singularity098.evolve.Store
import io.github.singularity098.evolve.core.Contrast
import io.github.singularity098.evolve.core.DistanceUnit
import io.github.singularity098.evolve.core.MapMode
import io.github.singularity098.evolve.core.WeightUnit
import io.github.singularity098.evolve.core.formatDuration
import io.github.singularity098.evolve.core.formatNumber
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import java.net.URLDecoder
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Settings' own pages, opened from its list. */
private enum class SettingsPage { Main, Exercises, Routines, Programs, BodyWeights }

@Composable
fun SettingsScreen(store: Store, host: Host) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.Main) }
    var editRoutine by rememberSaveable { mutableStateOf<String?>(null) }
    var editProgram by rememberSaveable { mutableStateOf<String?>(null) }
    when {
        editRoutine != null -> RoutineEditor(store, editRoutine!!, onClose = { editRoutine = null })
        editProgram != null -> ProgramEditor(store, editProgram!!, onClose = { editProgram = null })
        page == SettingsPage.Exercises -> ExercisesPage(store, onClose = { page = SettingsPage.Main })
        page == SettingsPage.Routines -> RoutinesPage(store, onEdit = { editRoutine = it }, onClose = { page = SettingsPage.Main })
        page == SettingsPage.Programs -> ProgramsPage(store, onEdit = { editProgram = it }, onClose = { page = SettingsPage.Main })
        page == SettingsPage.BodyWeights -> BodyWeightsPage(store, onClose = { page = SettingsPage.Main })
        else -> MainSettings(store, host) { page = it }
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { PaintedIcon(icon) },
        headlineContent = { Text(title) },
        supportingContent = { Text(detail) },
        trailingContent = { Icon(EvolveIcons.ChevronRight, null) },
    )
}

@Composable
private fun MainSettings(store: Store, host: Host, open: (SettingsPage) -> Unit) {
    val library by store.library.collectAsStateWithLifecycle()
    val s = library.settings
    var importing by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { Text("Settings", Modifier.padding(start = 16.dp, top = 16.dp), style = MaterialTheme.typography.headlineMedium) }

        item { SectionTitle("Design your workouts") }
        item { NavRow(EvolveIcons.Dumbbell, "Routines", "${library.routines.size} weight-lifting routines: exercises, sets, reps, weights, rest") { open(SettingsPage.Routines) } }
        item { NavRow(EvolveIcons.Timer, "Interval programs", "${library.programs.size} timer programs for the stationary bike") { open(SettingsPage.Programs) } }
        item { NavRow(EvolveIcons.Edit, "Exercises", "${library.exercises.size} in the library") { open(SettingsPage.Exercises) } }
        item { NavRow(EvolveIcons.Scale, "Body weight", "Every weigh-in") { open(SettingsPage.BodyWeights) } }

        item { SectionTitle("During a workout") }
        item {
            SwitchRow("Keep the screen on", "While a workout is under way. Each workout can change it with the sun button.", s.keepScreenOn) { on ->
                store.updateSettings { it.copy(keepScreenOn = on) }
            }
        }
        item { SwitchRow("Sounds", "Rest over, each interval, the meditation and breathing bowl.", s.sounds) { on -> store.updateSettings { it.copy(sounds = on) } } }
        item { SwitchRow("Vibrate", "", s.vibrate) { on -> store.updateSettings { it.copy(vibrate = on) } } }
        item {
            StepRow("Rest between sets", formatDuration(s.defaultRestSecs.toDouble()), "For exercises added to a routine or a workout") { d ->
                store.updateSettings { it.copy(defaultRestSecs = (it.defaultRestSecs.toInt() + d * 15).coerceIn(15, 600).toUInt()) }
            }
        }

        item { SectionTitle("Breathing") }
        item {
            StepRow("Breaths per round", "${s.breathCount}") { d ->
                store.updateSettings { it.copy(breathCount = (it.breathCount.toInt() + d * 5).coerceIn(5, 100).toUInt()) }
            }
        }
        item {
            StepRow("Recovery hold", "${s.breathRecoverySecs} s", "The breath in after each hold") { d ->
                store.updateSettings { it.copy(breathRecoverySecs = (it.breathRecoverySecs.toInt() + d * 5).coerceIn(5, 60).toUInt()) }
            }
        }
        item {
            StepRow("Pacing guide", if (s.breathPaceSecs <= 0) "Off" else "${formatNumber(s.breathPaceSecs, 1u)} s", "Seconds per breath for the growing circle") { d ->
                store.updateSettings { it.copy(breathPaceSecs = (it.breathPaceSecs + d * 0.5).coerceIn(0.0, 10.0)) }
            }
        }

        item { SectionTitle("Units") }
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(s.weightUnit == WeightUnit.LB, { store.updateSettings { it.copy(weightUnit = WeightUnit.LB) } }, { Text("Pounds") })
                FilterChip(s.weightUnit == WeightUnit.KG, { store.updateSettings { it.copy(weightUnit = WeightUnit.KG) } }, { Text("Kilograms") })
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(s.distanceUnit == DistanceUnit.MI, { store.updateSettings { it.copy(distanceUnit = DistanceUnit.MI) } }, { Text("Miles") })
                FilterChip(s.distanceUnit == DistanceUnit.KM, { store.updateSettings { it.copy(distanceUnit = DistanceUnit.KM) } }, { Text("Kilometres") })
            }
        }

        item { SectionTitle("Weeks start on") }
        item {
            val days = listOf(7, 1, 2, 3, 4, 5, 6)
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(days) { d ->
                    FilterChip(
                        s.weekStartDay.toInt() == d,
                        { store.updateSettings { it.copy(weekStartDay = d.toUInt()) } },
                        { Text(DayOfWeek.of(d).getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                    )
                }
            }
        }

        item { SectionTitle("Maps") }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text("Light or dark", style = MaterialTheme.typography.bodyLarge)
                Text(
                    when (s.mapMode) {
                        MapMode.AUTO -> "By the light sensor: dark indoors and at night, light outside in daylight"
                        MapMode.LIGHT -> "Always the light map"
                        MapMode.DARK -> "Always the dark map"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(MapMode.AUTO to "Automatic", MapMode.LIGHT to "Light", MapMode.DARK to "Dark").forEach { (m, label) ->
                        FilterChip(s.mapMode == m, { store.updateSettings { it.copy(mapMode = m) } }, { Text(label) })
                    }
                }
            }
        }
        item { MapStylePicker("Light map", LightMapStyles, s.mapLightStyle) { n -> store.updateSettings { it.copy(mapLightStyle = n) } } }
        item { MapStylePicker("Dark map", DarkMapStyles, s.mapDarkStyle) { n -> store.updateSettings { it.copy(mapDarkStyle = n) } } }
        item { Hint("The sun and moon button on a map switches it for that workout.") }
        item {
            StepRow("Offline map radius", "${formatNumber(s.mapCacheMiles, 0u)} mi", "Kept around GPS workouts, for when the signal goes") { d ->
                store.updateSettings { it.copy(mapCacheMiles = (it.mapCacheMiles + d * 5).coerceIn(5.0, 50.0)) }
            }
        }
        item { Hint("Maps: OpenFreeMap, © OpenMapTiles, data © OpenStreetMap contributors. Place names: Nominatim.") }

        item { SectionTitle("Your data") }
        item {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("Export folder") },
                supportingContent = {
                    Text(
                        if (s.exportUri.isEmpty()) "None chosen"
                        else URLDecoder.decode(s.exportUri.substringAfterLast("/tree/"), "UTF-8").replace("primary:", "Internal storage/")
                    )
                },
            )
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = host::pickExportFolder) { Text("Choose…") }
                OutlinedButton(onClick = { store.exportNow() }, enabled = s.exportUri.isNotEmpty()) {
                    Icon(EvolveIcons.Export, null)
                    Text("Export now", Modifier.padding(start = 6.dp))
                }
            }
        }
        item {
            SwitchRow("Export after every workout", "Everything is saved to the export folder as soon as a workout is.", s.autoExport) { on ->
                if (on && s.exportUri.isEmpty()) host.pickExportFolder()
                store.updateSettings { it.copy(autoExport = on) }
            }
        }
        item {
            StepRow("Automatic exports kept", "${s.autoExportKeep}", "Older ones are deleted. Exports are gzipped JSON (.json.gz).") { d ->
                store.updateSettings { it.copy(autoExportKeep = (it.autoExportKeep.toInt() + d).coerceIn(1, 100).toUInt()) }
            }
        }
        item {
            ListItem(
                modifier = Modifier.clickable { importing = true },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { PaintedIcon(EvolveIcons.Import) },
                headlineContent = { Text("Import…") },
                supportingContent = { Text("From an Evolve export (.json.gz, or an older .json)") },
            )
        }

        item { SectionTitle("Look") }
        item { ThemePicker(s.theme) { name -> store.updateSettings { it.copy(theme = name) } } }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Contrast", style = MaterialTheme.typography.bodyLarge)
                Text(
                    when (s.contrast) {
                        Contrast.STANDARD -> "As the theme is drawn"
                        Contrast.HIGH -> "Brighter text in a sturdier face, plainer edges"
                        Contrast.MAXIMUM -> "White text, larger, on black"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ContrastNames.forEach { (c, label) ->
                        FilterChip(s.contrast == c, { store.updateSettings { it.copy(contrast = c) } }, { Text(label) })
                    }
                }
            }
        }

        item { SectionTitle("About") }
        item { Hint("Evolve: weights, walks, runs, hikes, rides, intervals, meditation and breathing, kept on this phone. Styled after Chippy.") }
    }

    if (importing) {
        AlertDialog(
            onDismissRequest = { importing = false },
            icon = { PaintedIcon(EvolveIcons.Import) },
            title = { Text("Import") },
            text = {
                Text(
                    "Merge adds what isn't here yet and keeps everything you have. " +
                        "Replace makes this phone a copy of the export: what's here now is deleted."
                )
            },
            confirmButton = {
                TextButton(onClick = { importing = false; host.pickImport(false) }) { Text("Merge") }
            },
            dismissButton = {
                TextButton(onClick = { importing = false; host.pickImport(true) }) {
                    Text("Replace", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
}

@Composable
fun BackBar(title: String, onClose: () -> Unit, actions: @Composable () -> Unit = {}) {
    BackHandler(onBack = onClose)
    Row(Modifier.padding(4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        androidx.compose.material3.IconButton(onClick = onClose) { Icon(EvolveIcons.Back, "Back") }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        actions()
    }
}
