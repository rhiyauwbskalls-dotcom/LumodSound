package com.lumodsound.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Iconos propios (los que no vienen en material-icons). */
object AppIcons {

    private fun icon(name: String, path: String, evenOdd: Boolean = false): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).addPath(
            pathData = addPathNodes(path),
            pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
            fill = SolidColor(Color.Black)
        ).build()

    /** Globo de texto con líneas: "Letra". */
    val Lyrics: ImageVector by lazy {
        icon(
            "Lyrics",
            "M20,2H4C2.9,2 2,2.9 2,4V22L6,18H20C21.1,18 22,17.1 22,16V4C22,2.9 21.1,2 20,2Z " +
                "M6,7H18V9H6Z M6,11H14V13H6Z",
            evenOdd = true
        )
    }

    /** Lista con triángulo de reproducir: "Cola de reproducción". */
    val Queue: ImageVector by lazy {
        icon("Queue", "M3,6H15V8H3Z M3,10H15V12H3Z M3,14H11V16H3Z M17,10V20L22,15Z")
    }

    /** Dos barras: asa para arrastrar y reordenar. */
    val DragHandle: ImageVector by lazy {
        icon("DragHandle", "M3,9H21V11H3Z M3,13H21V15H3Z")
    }

    /** Ondas de radio: "Radio infinita". */
    val Radio: ImageVector by lazy {
        ImageVector.Builder(
            name = "Radio",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).addPath(
            pathData = addPathNodes(
                "M16.24,7.76 A6,6 0 0,1 16.24,16.24 M7.76,16.24 A6,6 0 0,1 7.76,7.76 " +
                    "M19.07,4.93 A10,10 0 0,1 19.07,19.07 M4.93,19.07 A10,10 0 0,1 4.93,4.93"
            ),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ).addPath(
            pathData = addPathNodes("M10,12 A2,2 0 1,0 14,12 A2,2 0 1,0 10,12 Z"),
            fill = SolidColor(Color.Black)
        ).build()
    }
}
