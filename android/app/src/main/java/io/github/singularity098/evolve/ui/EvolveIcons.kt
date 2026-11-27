// Evolve's icons: line drawings on a 24 x 24 grid. The general ones are
// Chippy's (its ChippyIcons.kt), the fitness ones Evolve's own, drawn alike.
// Each is a stroked path (round ends) and/or a filled one (even-odd);
// Icon() tints both with the content colour.
package io.github.singularity098.evolve.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private fun icon(name: String, line: String = "", solid: String = "", weight: Float = 2f): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        if (solid.isNotEmpty()) {
            addPath(addPathNodes(solid), fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd)
        }
        if (line.isNotEmpty()) {
            addPath(
                addPathNodes(line),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = weight,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

object EvolveIcons {
    val Play: ImageVector by lazy {
        icon(
            "Play",
            solid = "M 8.2 4.6 A 1.2 1.2 0 0 1 10 3.6 L 19.4 10.9 A 1.4 1.4 0 0 1 19.4 13.1 L 10 20.4 A 1.2 1.2 0 0 1 8.2 19.4 Z",
        )
    }
    val Pause: ImageVector by lazy {
        icon(
            "Pause",
            solid = "M 6.5 5 L 10 5 L 10 19 L 6.5 19 Z M 14 5 L 17.5 5 L 17.5 19 L 14 19 Z",
        )
    }
    val Stop: ImageVector by lazy {
        icon(
            "Stop",
            solid = "M 7 6 L 17 6 A 1 1 0 0 1 18 7 L 18 17 A 1 1 0 0 1 17 18 L 7 18 A 1 1 0 0 1 6 17 L 6 7 A 1 1 0 0 1 7 6 Z",
        )
    }
    val Back: ImageVector by lazy {
        icon(
            "Back",
            line = "M 19 12 L 5 12 M 11 6 L 5 12 L 11 18",
            weight = 2f,
        )
    }
    val ChevronLeft: ImageVector by lazy {
        icon(
            "ChevronLeft",
            line = "M 14.5 5.5 L 8 12 L 14.5 18.5",
            weight = 2f,
        )
    }
    val ChevronRight: ImageVector by lazy {
        icon(
            "ChevronRight",
            line = "M 9.5 5.5 L 16 12 L 9.5 18.5",
            weight = 2f,
        )
    }
    val ChevronDown: ImageVector by lazy {
        icon(
            "ChevronDown",
            line = "M 5.5 9.5 L 12 16 L 18.5 9.5",
            weight = 2f,
        )
    }
    val ChevronUp: ImageVector by lazy {
        icon(
            "ChevronUp",
            line = "M 5.5 14.5 L 12 8 L 18.5 14.5",
            weight = 2f,
        )
    }
    val Close: ImageVector by lazy {
        icon(
            "Close",
            line = "M 6.5 6.5 L 17.5 17.5 M 17.5 6.5 L 6.5 17.5",
            weight = 2f,
        )
    }
    val Check: ImageVector by lazy {
        icon(
            "Check",
            line = "M 5 12.5 L 10 17.5 L 19 7",
            weight = 2.2f,
        )
    }
    val Trash: ImageVector by lazy {
        icon(
            "Trash",
            line = "M 4 6.5 L 20 6.5 M 9.5 6.5 L 9.5 4 L 14.5 4 L 14.5 6.5 M 6.2 6.5 L 7.2 20 L 16.8 20 L 17.8 6.5 M 10 10.5 L 10 16.5 M 14 10.5 L 14 16.5",
            weight = 1.7f,
        )
    }
    val Plus: ImageVector by lazy {
        icon(
            "Plus",
            line = "M 6 12 L 18 12 M 12 6 L 12 18",
            weight = 2f,
        )
    }
    val Minus: ImageVector by lazy {
        icon(
            "Minus",
            line = "M 6 12 L 18 12",
            weight = 2f,
        )
    }
    val Gear: ImageVector by lazy {
        icon(
            "Gear",
            line = "M 18.98 9.53 L 21.39 10.00 L 21.39 14.00 L 18.98 14.47 L 18.68 15.19 L 20.05 17.22 L 17.22 20.05 L 15.19 18.68 L 14.47 18.98 L 14.00 21.39 L 10.00 21.39 L 9.53 18.98 L 8.81 18.68 L 6.78 20.05 L 3.95 17.22 L 5.32 15.19 L 5.02 14.47 L 2.61 14.00 L 2.61 10.00 L 5.02 9.53 L 5.32 8.81 L 3.95 6.78 L 6.78 3.95 L 8.81 5.32 L 9.53 5.02 L 10.00 2.61 L 14.00 2.61 L 14.47 5.02 L 15.19 5.32 L 17.22 3.95 L 20.05 6.78 L 18.68 8.81 Z M 15 12 A 3 3 0 1 1 9 12 A 3 3 0 1 1 15 12 Z",
            weight = 1.6f,
        )
    }
    val Home: ImageVector by lazy {
        icon(
            "Home",
            line = "M 3.5 11 L 12 3.8 L 20.5 11 M 6 9 L 6 20 L 18 20 L 18 9 M 10 20 L 10 14.5 L 14 14.5 L 14 20",
            weight = 1.8f,
        )
    }
    val Filter: ImageVector by lazy {
        icon(
            "Filter",
            line = "M 4 5 L 20 5 L 14 12.5 L 14 19 L 10 17 L 10 12.5 Z",
            weight = 1.8f,
        )
    }
    val Search: ImageVector by lazy {
        icon(
            "Search",
            line = "M 16 10.5 A 5.5 5.5 0 1 1 5 10.5 A 5.5 5.5 0 1 1 16 10.5 Z M 14.6 14.6 L 20 20",
            weight = 2f,
        )
    }
    val Export: ImageVector by lazy {
        icon(
            "Export",
            line = "M 12 3.5 L 12 14.5 M 7.5 10 L 12 14.5 L 16.5 10 M 4.5 15 L 4.5 19 A 1.5 1.5 0 0 0 6 20.5 L 18 20.5 A 1.5 1.5 0 0 0 19.5 19 L 19.5 15",
            weight = 1.8f,
        )
    }
    val Info: ImageVector by lazy {
        icon(
            "Info",
            line = "M 12 3 A 9 9 0 1 1 11.99 3 Z M 12 11 L 12 16.5 M 12 7.6 L 12 7.7",
            weight = 1.8f,
        )
    }
    val More: ImageVector by lazy {
        icon(
            "More",
            solid = "M 13.6 5.5 A 1.6 1.6 0 1 1 10.4 5.5 A 1.6 1.6 0 1 1 13.6 5.5 Z M 13.6 12 A 1.6 1.6 0 1 1 10.4 12 A 1.6 1.6 0 1 1 13.6 12 Z M 13.6 18.5 A 1.6 1.6 0 1 1 10.4 18.5 A 1.6 1.6 0 1 1 13.6 18.5 Z",
        )
    }
    val Refresh: ImageVector by lazy {
        icon(
            "Refresh",
            line = "M 19.05 9.43 A 7.5 7.5 0 1 1 13.30 4.61 M 10.75 2.07 L 13.30 4.61 L 10.76 7.16",
            weight = 1.9f,
        )
    }
    val Open: ImageVector by lazy {
        icon(
            "Open",
            line = "M 6.5 3 L 14 3 L 18.5 7.5 L 18.5 19.5 A 1.5 1.5 0 0 1 17 21 L 6.5 21 A 1.5 1.5 0 0 1 5 19.5 L 5 4.5 A 1.5 1.5 0 0 1 6.5 3 Z M 14 3 L 14 7.5 L 18.5 7.5 M 11.75 10.5 L 11.75 17 M 8.5 13.75 L 15 13.75",
            weight = 1.7f,
        )
    }
    val Volume: ImageVector by lazy {
        icon(
            "Volume",
            line = "M 15.5 8.8 A 4.5 4.5 0 0 1 15.5 15.2 M 18.4 5.8 A 8.6 8.6 0 0 1 18.4 18.2",
            solid = "M 3 9 L 7 9 L 12 4.5 L 12 19.5 L 7 15 L 3 15 Z",
            weight = 1.8f,
        )
    }
    val Mute: ImageVector by lazy {
        icon(
            "Mute",
            line = "M 15.5 9 L 21 15 M 21 9 L 15.5 15",
            solid = "M 3 9 L 7 9 L 12 4.5 L 12 19.5 L 7 15 L 3 15 Z",
            weight = 1.8f,
        )
    }
    val Eye: ImageVector by lazy {
        icon(
            "Eye",
            line = "M 2 12 C 5 6.5 8.5 5 12 5 C 15.5 5 19 6.5 22 12 C 19 17.5 15.5 19 12 19 C 8.5 19 5 17.5 2 12 Z",
            solid = "M 15 12 A 3 3 0 1 1 9 12 A 3 3 0 1 1 15 12 Z",
            weight = 1.7f,
        )
    }

    val Dumbbell: ImageVector by lazy {
        icon("Dumbbell", line = "M 2.5 12 L 21.5 12 M 5 8.5 L 5 15.5 M 7.5 6.5 L 7.5 17.5 M 16.5 6.5 L 16.5 17.5 M 19 8.5 L 19 15.5")
    }
    val Walk: ImageVector by lazy {
        icon(
            "Walk",
            line = "M 12 7.5 L 10.5 13 L 13 15.5 L 14 21 M 10.5 13 L 8.5 21 M 11.8 8.5 L 8 10.5 L 7 14 M 12 8.5 L 15 11.5 L 17.5 12",
            solid = "M 13.6 4 A 1.8 1.8 0 1 1 13.59 4 Z",
        )
    }
    val Run: ImageVector by lazy {
        icon(
            "Run",
            line = "M 13 8 L 10.5 13.5 L 14 16 L 13 21 M 10.5 13.5 L 8.5 17 L 4.5 17 M 12.5 8.2 L 9 9.5 L 7 12.5 M 13 8.2 L 15.5 11.5 L 19 11.5",
            solid = "M 15.2 3.6 A 1.9 1.9 0 1 1 15.19 3.6 Z",
        )
    }
    val Mountain: ImageVector by lazy {
        icon("Mountain", line = "M 2.5 19.5 L 9 8 L 13 14.5 L 15.5 11 L 21.5 19.5 Z M 7 11.5 L 9 13 L 11 11.2")
    }
    val Bike: ImageVector by lazy {
        icon(
            "Bike",
            line = "M 6 17.5 A 3.5 3.5 0 1 1 6 10.5 A 3.5 3.5 0 1 1 6 17.5 Z M 18 17.5 A 3.5 3.5 0 1 1 18 10.5 A 3.5 3.5 0 1 1 18 17.5 Z M 6 14 L 9.5 8 L 15.5 8 L 18 14 M 9.5 8 L 12 14 L 15.5 8 M 6 14 L 12 14 M 8.5 6 L 11 6 M 15 5.5 L 17 5.5",
            weight = 1.7f,
        )
    }
    /** A sailing boat: hull, mast, mainsail and jib. */
    val Boat: ImageVector by lazy {
        icon(
            "Boat",
            line = "M 3 15.5 L 21 15.5 L 18.5 19.5 L 5.5 19.5 Z M 12 3.5 L 12 15.5 M 12 4.5 L 18.5 12.5 L 12 12.5 M 10.5 6.5 L 6 12.5 L 10.5 12.5",
            weight = 1.7f,
        )
    }
    /** A car from the side: body, windows' line, two wheels. */
    val Car: ImageVector by lazy {
        icon(
            "Car",
            line = "M 5.5 17 L 3.5 17 L 3.5 12.5 L 6 8 C 6.3 7.4 6.9 7 7.6 7 L 16.4 7 C 17.1 7 17.7 7.4 18 8 L 20.5 12.5 L 20.5 17 L 18.5 17 M 14.5 17 L 9.5 17 M 3.5 12.5 L 20.5 12.5 M 12 7 L 12 12.5 M 7.5 19 A 2 2 0 1 1 7.5 15 A 2 2 0 1 1 7.5 19 Z M 16.5 19 A 2 2 0 1 1 16.5 15 A 2 2 0 1 1 16.5 19 Z",
            weight = 1.7f,
        )
    }
    /** Lines shortening downwards: put in order. */
    val Sort: ImageVector by lazy {
        icon("Sort", line = "M 4 7 L 20 7 M 4 12 L 15 12 M 4 17 L 10 17")
    }
    val StationaryBike: ImageVector by lazy {
        icon(
            "StationaryBike",
            line = "M 8 15 A 3 3 0 1 1 8 9 A 3 3 0 1 1 8 15 Z M 8 12 L 15 7 L 16.5 4.5 M 14 4.5 L 18 4.5 M 15 7 L 17 20 M 8 12 L 10 20 M 6.5 20 L 19 20",
            weight = 1.7f,
        )
    }
    val Timer: ImageVector by lazy {
        icon("Timer", line = "M 12 21 A 7.5 7.5 0 1 1 12 6 A 7.5 7.5 0 1 1 12 21 Z M 12 13.5 L 12 9.5 M 10 2.8 L 14 2.8 M 12 2.8 L 12 6 M 18.5 6.5 L 19.8 5.2")
    }
    val Lotus: ImageVector by lazy {
        icon(
            "Lotus",
            line = "M 12 4.5 C 15 8 15 13 12 17.5 C 9 13 9 8 12 4.5 Z M 12 17.5 C 8.5 17.5 5 15.5 3.5 10.5 C 6.5 10.5 9 12 10.2 14 M 12 17.5 C 15.5 17.5 19 15.5 20.5 10.5 C 17.5 10.5 15 12 13.8 14 M 4 20 L 20 20",
            weight = 1.7f,
        )
    }
    val Wind: ImageVector by lazy {
        icon(
            "Wind",
            line = "M 3 9 L 14.5 9 A 2.8 2.8 0 1 0 11.8 5.5 M 3 13 L 18 13 A 2.8 2.8 0 1 1 15.3 16.5 M 3 17 L 9.5 17",
        )
    }
    val Scale: ImageVector by lazy {
        icon(
            "Scale",
            line = "M 6 3.5 L 18 3.5 A 2.5 2.5 0 0 1 20.5 6 L 20.5 18 A 2.5 2.5 0 0 1 18 20.5 L 6 20.5 A 2.5 2.5 0 0 1 3.5 18 L 3.5 6 A 2.5 2.5 0 0 1 6 3.5 Z M 7.5 10 A 6 6 0 0 1 16.5 10 M 12 10.5 L 13.5 7.5",
        )
    }
    val Calendar: ImageVector by lazy {
        icon(
            "Calendar",
            line = "M 5.5 5 L 18.5 5 A 2 2 0 0 1 20.5 7 L 20.5 19 A 2 2 0 0 1 18.5 21 L 5.5 21 A 2 2 0 0 1 3.5 19 L 3.5 7 A 2 2 0 0 1 5.5 5 Z M 3.5 10 L 20.5 10 M 8 3 L 8 7 M 16 3 L 16 7",
            solid = "M 7 13 L 9 13 L 9 15 L 7 15 Z M 11 13 L 13 13 L 13 15 L 11 15 Z M 15 13 L 17 13 L 17 15 L 15 15 Z M 7 16.5 L 9 16.5 L 9 18.5 L 7 18.5 Z M 11 16.5 L 13 16.5 L 13 18.5 L 11 18.5 Z",
        )
    }
    val Chart: ImageVector by lazy {
        icon("Chart", line = "M 3.5 3.5 L 3.5 20.5 L 20.5 20.5 M 6.5 16 L 10.5 11 L 13.5 13.5 L 19.5 6.5 M 15.5 6.5 L 19.5 6.5 L 19.5 10.5")
    }
    val History: ImageVector by lazy {
        icon(
            "History",
            line = "M 4.2 13 A 8 8 0 1 0 6.3 6.3 M 3.5 3.8 L 3.5 8 L 7.7 8 M 12 8 L 12 12.5 L 15 14.5",
        )
    }
    val Pulse: ImageVector by lazy {
        icon("Pulse", line = "M 2.5 12.5 L 7.5 12.5 L 9.5 8 L 12.5 18 L 15 10 L 16.5 12.5 L 21.5 12.5")
    }
    val Pin: ImageVector by lazy {
        icon(
            "Pin",
            line = "M 12 21.5 C 12 21.5 5 14.5 5 9.5 A 7 7 0 0 1 19 9.5 C 19 14.5 12 21.5 12 21.5 Z M 12 12 A 2.5 2.5 0 1 1 12 7 A 2.5 2.5 0 1 1 12 12 Z",
        )
    }
    val Locate: ImageVector by lazy {
        icon(
            "Locate",
            line = "M 12 19 A 7 7 0 1 1 12 5 A 7 7 0 1 1 12 19 Z M 12 2 L 12 5 M 12 19 L 12 22 M 2 12 L 5 12 M 19 12 L 22 12",
            solid = "M 12 9.5 A 2.5 2.5 0 1 1 11.99 9.5 Z",
        )
    }
    val Sun: ImageVector by lazy {
        icon(
            "Sun",
            line = "M 12 16 A 4 4 0 1 1 12 8 A 4 4 0 1 1 12 16 Z M 12 2.5 L 12 4.5 M 12 19.5 L 12 21.5 M 2.5 12 L 4.5 12 M 19.5 12 L 21.5 12 M 5.3 5.3 L 6.7 6.7 M 17.3 17.3 L 18.7 18.7 M 5.3 18.7 L 6.7 17.3 M 17.3 6.7 L 18.7 5.3",
        )
    }
    val Expand: ImageVector by lazy {
        icon("Expand", line = "M 14 4 L 20 4 L 20 10 M 20 4 L 13.5 10.5 M 10 20 L 4 20 L 4 14 M 4 20 L 10.5 13.5")
    }
    val Fit: ImageVector by lazy {
        icon("Fit", line = "M 4 9 L 4 4 L 9 4 M 15 4 L 20 4 L 20 9 M 20 15 L 20 20 L 15 20 M 9 20 L 4 20 L 4 15 M 8.5 15 L 11 11 L 13 13.5 L 15.5 9")
    }
    val Moon: ImageVector by lazy {
        icon("Moon", line = "M 19.5 14.5 A 8 8 0 1 1 9.5 4.5 A 6.5 6.5 0 0 0 19.5 14.5 Z")
    }
    val Edit: ImageVector by lazy {
        icon("Edit", line = "M 4 20 L 4.8 16.2 L 15.8 5.2 A 2.1 2.1 0 0 1 18.8 8.2 L 7.8 19.2 Z M 14 7 L 17 10")
    }
    val Import: ImageVector by lazy {
        icon("Import", line = "M 12 3.5 L 12 15 M 7.5 10.5 L 12 15 L 16.5 10.5 M 4 15.5 L 4 19 A 1.5 1.5 0 0 0 5.5 20.5 L 18.5 20.5 A 1.5 1.5 0 0 0 20 19 L 20 15.5")
    }
    val Flag: ImageVector by lazy {
        icon("Flag", line = "M 5 21 L 5 3.5 M 5 4 L 17.5 4 L 15 8 L 17.5 12 L 5 12")
    }
}
