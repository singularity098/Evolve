package io.github.singularity098.evolve.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import io.github.singularity098.evolve.EvolveApplication
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.singularity098.evolve.Store
import io.github.singularity098.evolve.core.Contrast
import io.github.singularity098.evolve.core.kindUsesGps
import kotlinx.coroutines.launch

enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", EvolveIcons.Home),
    Workout("In progress", EvolveIcons.Pulse),
    History("History", EvolveIcons.History),
    Graphs("Graphs", EvolveIcons.Chart),
    Calendar("Calendar", EvolveIcons.Calendar),
    Settings("Settings", EvolveIcons.Gear),
}

/** What only the activity can do. */
interface Host {
    /**
     * Runs [then] once location access is granted (asking for it if need be), and with
     * [steps], asks for the pedometer too (the activity goes ahead without it if refused).
     */
    fun withLocation(steps: Boolean, then: () -> Unit)
    fun pickExportFolder()
    fun pickImport(replace: Boolean)
    fun saveGpx(workoutId: String, name: String)
}

@Composable
fun EvolveRoot(store: Store, host: Host) {
    val library by store.library.collectAsStateWithLifecycle()
    EvolveTheme(lookNamed(library.settings.theme), library.settings.contrast) {
        val look = LocalLook.current
        val session by store.session.collectAsStateWithLifecycle()
        val hasSession = session != null
        // The In progress tab is there only while a workout is under way.
        val pages = if (hasSession) Tab.entries.toList() else Tab.entries.filter { it != Tab.Workout }
        val pagesNow by rememberUpdatedState(pages)
        // The pager reads pagesNow, not pages: its count and its keys can be asked for in
        // different frames, and a captured list goes stale when the In progress tab comes or goes.
        val pager = rememberPagerState { pagesNow.size }
        var tab by rememberSaveable { mutableStateOf(if (hasSession) Tab.Workout else Tab.Home) }
        var openWorkout by rememberSaveable { mutableStateOf<String?>(null) }
        // A saved GPS workout's map, opened full screen.
        var historyMap by rememberSaveable { mutableStateOf(false) }
        val snackbar = remember { SnackbarHostState() }

        LaunchedEffect(hasSession) {
            if (hasSession) {
                // A workout just started (or was under way): straight to In progress.
                tab = Tab.Workout
                pager.scrollToPage(pages.indexOf(Tab.Workout))
            } else if (tab == Tab.Workout) {
                tab = Tab.Home
            }
        }
        LaunchedEffect(pages, tab) {
            val i = pages.indexOf(tab)
            if (i >= 0 && pager.currentPage != i) pager.animateScrollToPage(i)
        }
        LaunchedEffect(pager) {
            snapshotFlow { pager.settledPage }.collect { p -> pagesNow.getOrNull(p)?.let { tab = it } }
        }
        LaunchedEffect(Unit) {
            store.messages.collect { m ->
                launch {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(m)
                }
            }
        }
        LaunchedEffect(Unit) {
            store.showWorkout.collect { id ->
                openWorkout = id
                historyMap = false
                tab = Tab.History
            }
        }
        BackHandler(enabled = tab != Tab.Home && tab != Tab.Workout) { tab = Tab.Home }

        // A GPS workout's map takes sideways drags for itself.
        val onMap = when (pages.getOrNull(pager.currentPage)) {
            Tab.Workout -> session?.let { kindUsesGps(it.kind) } == true
            Tab.History -> historyMap && openWorkout != null
            else -> false
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                    Tab.entries.forEach { t ->
                        val enabled = t != Tab.Workout || hasSession
                        NavigationBarItem(
                            selected = tab == t,
                            enabled = enabled,
                            onClick = { tab = t },
                            icon = {
                                if (tab == t) PaintedIcon(t.icon, Modifier.size(24.dp), t.label)
                                else Icon(t.icon, t.label, Modifier.size(24.dp))
                            },
                            label = {
                                Text(t.label, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = look.paintC.copy(alpha = 0.22f),
                                selectedTextColor = look.accent,
                                disabledIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .background(
                        // A glow of the theme behind everything, as in Chippy (fainter with more contrast).
                        Brush.radialGradient(
                            listOf(look.paintC.copy(alpha = if (LocalContrast.current == Contrast.STANDARD) 0.10f else 0.05f), Color.Transparent),
                            radius = 1400f,
                        )
                    )
                    .padding(padding),
            ) {
                HorizontalPager(
                    state = pager,
                    userScrollEnabled = !onMap,
                    beyondViewportPageCount = 0,
                    key = { pagesNow.getOrNull(it)?.name ?: "page-$it" },
                ) { i ->
                    when (pagesNow.getOrNull(i)) {
                        Tab.Home -> HomeScreen(store, host)
                        Tab.Workout -> session?.let { InProgressScreen(store, it) }
                        Tab.History -> HistoryScreen(
                            store, host, openWorkout,
                            onOpen = {
                                openWorkout = it
                                historyMap = false
                            },
                            mapOpen = historyMap,
                            onMapOpen = { historyMap = it },
                        )
                        Tab.Graphs -> GraphsScreen(store)
                        Tab.Calendar -> CalendarScreen(store)
                        Tab.Settings -> SettingsScreen(store, host)
                        null -> {}
                    }
                }
            }
        }
        LastCrash()
    }
}

/** What went wrong the last time the app crashed, if it did: shown once, to copy. */
@Composable
private fun LastCrash() {
    val app = LocalContext.current.applicationContext as EvolveApplication
    val file = remember { app.crashFile() }
    var report by remember { mutableStateOf(try { file.takeIf { it.exists() }?.readText() } catch (_: Exception) { null }) }
    val text = report ?: return
    val clipboard = LocalClipboardManager.current
    val close = {
        file.delete()
        report = null
    }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Evolve closed unexpectedly") },
        text = {
            Column {
                Text("The app crashed last time. Copy this and send it along so it can be fixed.")
                SelectionContainer(Modifier.padding(top = 12.dp).heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Text(text, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(text))
                close()
            }) { Text("Copy and close") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Close") } },
    )
}
