package io.github.singularity098.evolve.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/** Above this many lux we're probably outside in daylight (overcast is ~1,000+, indoors ~50–500). */
private const val OUTSIDE_LUX = 1500f

/** Below this we're probably inside or it's night. The gap keeps the map from flickering. */
private const val INSIDE_LUX = 400f

/** How long a new reading has to hold before the map changes (a passing shadow, a hand over the sensor). */
private const val SETTLE_MS = 4000L

/**
 * Whether the light sensor says we're outside in daylight: true for a
 * light map, false for a dark one (indoors, or at night). Starts true and
 * stays true on a phone without the sensor; the first reading counts at
 * once, later changes only once they've held for a few seconds.
 */
@Composable
fun rememberDaylight(): State<Boolean> {
    val context = LocalContext.current
    val daylight = remember { mutableStateOf(true) }
    // What the latest reading says, before settling; null until one comes.
    val raw = remember { mutableStateOf<Boolean?>(null) }
    DisposableEffect(context) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (sm == null || sensor == null) return@DisposableEffect onDispose {}
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val lux = e.values.firstOrNull() ?: return
                val first = raw.value == null
                raw.value = when {
                    lux >= OUTSIDE_LUX -> true
                    lux <= INSIDE_LUX -> false
                    else -> raw.value ?: daylight.value
                }
                if (first) daylight.value = raw.value!!
            }

            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose { sm.unregisterListener(listener) }
    }
    LaunchedEffect(raw.value) {
        val want = raw.value ?: return@LaunchedEffect
        if (want == daylight.value) return@LaunchedEffect
        delay(SETTLE_MS)
        daylight.value = want
    }
    return daylight
}
