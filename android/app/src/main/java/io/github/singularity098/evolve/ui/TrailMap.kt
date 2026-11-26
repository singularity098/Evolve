package io.github.singularity098.evolve.ui

import android.location.Location
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.singularity098.evolve.MapCache
import io.github.singularity098.evolve.core.TrackPoint
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * The map (MapLibre, OpenFreeMap's tiles) with the trail drawn on it, a
 * dot where the workout started and one where you are. With [follow] the
 * map keeps you in the middle; a drag stops that ([onUserMoved]) until
 * [recenter] changes. Without [here] (a saved workout) it fits the trail,
 * and fits it again whenever [recenter] changes.
 * [dark] picks the dark map over the light (the default), any time.
 */
@Composable
fun TrailMap(
    trail: List<TrackPoint>,
    here: Location?,
    modifier: Modifier = Modifier,
    follow: Boolean = false,
    interactive: Boolean = true,
    recenter: Int = 0,
    onUserMoved: () -> Unit = {},
    dark: Boolean = false,
) {
    val context = LocalContext.current
    val look = LocalLook.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var placed by remember { mutableStateOf(false) }
    // The [recenter] a saved trail was last fitted for.
    var fitted by remember { mutableStateOf<Int?>(null) }
    val moved by rememberUpdatedState(onUserMoved)

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            if (!interactive) m.uiSettings.setAllGesturesEnabled(false)
            m.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) moved()
            }
        }
    }

    // The map's look; a new one drops the old style's layers, so they're added again.
    LaunchedEffect(map, dark) {
        val m = map ?: return@LaunchedEffect
        style = null
        m.setStyle(Style.Builder().fromUri(if (dark) MapCache.STYLE_DARK else MapCache.STYLE)) { st ->
            st.addSource(GeoJsonSource("trail"))
            st.addSource(GeoJsonSource("start"))
            st.addSource(GeoJsonSource("here"))
            st.addLayer(
                LineLayer("trail-casing", "trail").withProperties(
                    PropertyFactory.lineColor(android.graphics.Color.argb(160, 0, 0, 0)),
                    PropertyFactory.lineWidth(8f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                )
            )
            st.addLayer(
                LineLayer("trail-line", "trail").withProperties(
                    PropertyFactory.lineColor(look.paintB.toArgb()),
                    PropertyFactory.lineWidth(5f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                )
            )
            st.addLayer(
                CircleLayer("start-dot", "start").withProperties(
                    PropertyFactory.circleRadius(7f),
                    PropertyFactory.circleColor(android.graphics.Color.rgb(95, 211, 154)),
                    PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
                    PropertyFactory.circleStrokeWidth(2.5f),
                )
            )
            st.addLayer(
                CircleLayer("here-dot", "here").withProperties(
                    PropertyFactory.circleRadius(8f),
                    PropertyFactory.circleColor(look.paintA.toArgb()),
                    PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
                    PropertyFactory.circleStrokeWidth(3f),
                )
            )
            style = st
        }
    }

    // The trail, a line per segment (pauses break it).
    LaunchedEffect(style, trail, recenter) {
        val st = style ?: return@LaunchedEffect
        val lines = trail.groupBy { it.seg }.values.filter { it.size >= 2 }.map { seg ->
            Feature.fromGeometry(LineString.fromLngLats(seg.map { Point.fromLngLat(it.lon, it.lat) }))
        }
        st.getSourceAs<GeoJsonSource>("trail")?.setGeoJson(FeatureCollection.fromFeatures(lines))
        trail.firstOrNull()?.let { p ->
            st.getSourceAs<GeoJsonSource>("start")?.setGeoJson(Point.fromLngLat(p.lon, p.lat))
        }
        // A saved workout: the whole trail in view (at once the first time, gliding after).
        if (here == null && trail.isNotEmpty() && fitted != recenter) {
            val first = fitted == null
            fitted = recenter
            placed = true
            val m = map ?: return@LaunchedEffect
            val update = if (trail.size == 1) {
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder().target(LatLng(trail[0].lat, trail[0].lon)).zoom(15.0).build()
                )
            } else {
                val b = LatLngBounds.Builder()
                trail.forEach { b.include(LatLng(it.lat, it.lon)) }
                CameraUpdateFactory.newLatLngBounds(b.build(), 80)
            }
            if (first) m.moveCamera(update) else m.animateCamera(update, 500)
        }
    }

    LaunchedEffect(style, here, follow, recenter) {
        val st = style ?: return@LaunchedEffect
        val h = here ?: return@LaunchedEffect
        st.getSourceAs<GeoJsonSource>("here")?.setGeoJson(Point.fromLngLat(h.longitude, h.latitude))
        val m = map ?: return@LaunchedEffect
        if (!placed) {
            placed = true
            m.cameraPosition = CameraPosition.Builder().target(LatLng(h.latitude, h.longitude)).zoom(15.5).build()
        } else if (follow) {
            m.animateCamera(CameraUpdateFactory.newLatLng(LatLng(h.latitude, h.longitude)), 600)
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}
