package io.github.singularity098.evolve

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.singularity098.evolve.ui.EvolveRoot
import io.github.singularity098.evolve.ui.Host

/**
 * Evolve's one activity: the Compose UI over the engine (which lives in
 * [EvolveApplication], so it outlasts the activity), plus what needs an
 * activity: permissions, the document pickers, and keeping the screen on.
 */
class MainActivity : ComponentActivity(), Host {
    private val store get() = (application as EvolveApplication).store

    /** What to run once location access is granted. */
    private var afterLocation: (() -> Unit)? = null
    private var gpxFor: String? = null
    private var importReplace = false

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        val then = afterLocation
        afterLocation = null
        if (r[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            then?.invoke()
        } else {
            Toast.makeText(this, "GPS workouts need location access", Toast.LENGTH_LONG).show()
        }
    }

    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: Exception) {
        }
        store.updateSettings { it.copy(exportUri = uri.toString()) }
    }

    private val pickImport = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) store.import(uri, importReplace)
    }

    private val createGpx = registerForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        val id = gpxFor
        gpxFor = null
        if (uri != null && id != null) store.writeGpx(uri, id)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        preferFastestRefreshRate()
        setContent {
            val session by store.session.collectAsStateWithLifecycle()
            val screenOn = session?.keepScreenOn == true
            LaunchedEffect(screenOn) {
                if (screenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            EvolveRoot(store, this)
        }
    }

    override fun onResume() {
        super.onResume()
        // A workout under way has its service (and GPS): it may not have
        // been allowed to start while the app was in the background.
        if (store.session.value != null) WorkoutService.start(this)
    }

    // ------------------------------------------------------------ Host

    override fun withLocation(then: () -> Unit) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            then()
        } else {
            afterLocation = then
            locationPermission.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    override fun pickExportFolder() {
        try {
            pickFolder.launch(store.settings.exportUri.takeIf { it.isNotEmpty() }?.let(Uri::parse))
        } catch (_: Exception) {
            Toast.makeText(this, "The folder picker could not be shown", Toast.LENGTH_LONG).show()
        }
    }

    override fun pickImport(replace: Boolean) {
        importReplace = replace
        try {
            pickImport.launch(arrayOf("application/gzip", "application/x-gzip", "application/json", "text/plain", "application/octet-stream", "*/*"))
        } catch (_: Exception) {
            Toast.makeText(this, "The file picker could not be shown", Toast.LENGTH_LONG).show()
        }
    }

    override fun saveGpx(workoutId: String, name: String) {
        gpxFor = workoutId
        val safe = name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "track" }
        try {
            createGpx.launch("$safe.gpx")
        } catch (_: Exception) {
            gpxFor = null
        }
    }

    /**
     * Phones with 90 or 120 Hz screens often give apps 60 Hz unless they
     * ask: ask for the fastest mode the display has at its current size.
     */
    @Suppress("DEPRECATION")
    private fun preferFastestRefreshRate() {
        val display: Display = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else windowManager.defaultDisplay)
            ?: return
        val current = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        val lp = window.attributes
        lp.preferredDisplayModeId = best.modeId
        window.attributes = lp
    }
}
