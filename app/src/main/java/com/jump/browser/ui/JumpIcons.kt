package com.jump.browser.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Hand-drawn, single weight line icons matching Jump's 24dp chrome. */
object JumpIcons {
    private fun vector(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.line(
        width: Float = 1.7f,
        block: PathBuilder.() -> Unit,
    ) {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        )
    }

    private fun ImageVector.Builder.solid(block: PathBuilder.() -> Unit) {
        path(fill = SolidColor(Color.Black), pathBuilder = block)
    }

    val Search = vector("search") {
        line {
            moveTo(10.7f, 4.2f)
            curveTo(7.1f, 4.2f, 4.2f, 7.1f, 4.2f, 10.7f)
            curveTo(4.2f, 14.3f, 7.1f, 17.2f, 10.7f, 17.2f)
            curveTo(14.3f, 17.2f, 17.2f, 14.3f, 17.2f, 10.7f)
            curveTo(17.2f, 7.1f, 14.3f, 4.2f, 10.7f, 4.2f)
            close()
            moveTo(15.2f, 15.2f)
            lineTo(20f, 20f)
        }
    }

    val Back = vector("back") {
        line(1.8f) {
            moveTo(14.9f, 4f)
            lineTo(8.1f, 12f)
            lineTo(14.9f, 20f)
        }
    }

    val Forward = vector("forward") {
        line(1.8f) {
            moveTo(9.1f, 4f)
            lineTo(15.9f, 12f)
            lineTo(9.1f, 20f)
        }
    }

    val Home = vector("home") {
        line(1.8f) {
            moveTo(4.2f, 10.6f)
            lineTo(12f, 3.8f)
            lineTo(19.8f, 10.6f)
            lineTo(19.8f, 20.2f)
            lineTo(4.2f, 20.2f)
            close()
        }
    }

    val Tabs = vector("tabs") {
        line(1.6f) {
            moveTo(6.2f, 3.8f)
            lineTo(17.8f, 3.8f)
            quadTo(20.2f, 3.8f, 20.2f, 6.2f)
            lineTo(20.2f, 17.8f)
            quadTo(20.2f, 20.2f, 17.8f, 20.2f)
            lineTo(6.2f, 20.2f)
            quadTo(3.8f, 20.2f, 3.8f, 17.8f)
            lineTo(3.8f, 6.2f)
            quadTo(3.8f, 3.8f, 6.2f, 3.8f)
            close()
        }
    }

    val Menu = vector("menu") {
        line(1.8f) {
            moveTo(4.5f, 5f)
            lineTo(19.5f, 5f)
            moveTo(4.5f, 12f)
            lineTo(19.5f, 12f)
            moveTo(4.5f, 19f)
            lineTo(19.5f, 19f)
        }
    }

    val More = vector("more") {
        solid {
            moveTo(12f, 6.2f)
            curveTo(11.2f, 6.2f, 10.6f, 6.8f, 10.6f, 7.6f)
            curveTo(10.6f, 8.4f, 11.2f, 9f, 12f, 9f)
            curveTo(12.8f, 9f, 13.4f, 8.4f, 13.4f, 7.6f)
            curveTo(13.4f, 6.8f, 12.8f, 6.2f, 12f, 6.2f)
            close()
            moveTo(12f, 10.6f)
            curveTo(11.2f, 10.6f, 10.6f, 11.2f, 10.6f, 12f)
            curveTo(10.6f, 12.8f, 11.2f, 13.4f, 12f, 13.4f)
            curveTo(12.8f, 13.4f, 13.4f, 12.8f, 13.4f, 12f)
            curveTo(13.4f, 11.2f, 12.8f, 10.6f, 12f, 10.6f)
            close()
            moveTo(12f, 15f)
            curveTo(11.2f, 15f, 10.6f, 15.6f, 10.6f, 16.4f)
            curveTo(10.6f, 17.2f, 11.2f, 17.8f, 12f, 17.8f)
            curveTo(12.8f, 17.8f, 13.4f, 17.2f, 13.4f, 16.4f)
            curveTo(13.4f, 15.6f, 12.8f, 15f, 12f, 15f)
            close()
        }
    }

    val Refresh = vector("refresh") {
        line(1.75f) {
            moveTo(19.4f, 10.3f)
            curveTo(18.8f, 6.6f, 15.7f, 3.9f, 12f, 3.9f)
            curveTo(7.5f, 3.9f, 3.9f, 7.5f, 3.9f, 12f)
            curveTo(3.9f, 16.5f, 7.5f, 20.1f, 12f, 20.1f)
            curveTo(15.7f, 20.1f, 18.8f, 17.4f, 19.4f, 13.7f)
            moveTo(19.4f, 3.9f)
            lineTo(19.4f, 10.4f)
            lineTo(12.9f, 10.4f)
        }
    }

    val Close = vector("close") {
        line(1.8f) {
            moveTo(5.5f, 5.5f)
            lineTo(18.5f, 18.5f)
            moveTo(18.5f, 5.5f)
            lineTo(5.5f, 18.5f)
        }
    }

    val Globe = vector("globe") {
        line(1.55f) {
            moveTo(12f, 3.2f)
            curveTo(7.14f, 3.2f, 3.2f, 7.14f, 3.2f, 12f)
            curveTo(3.2f, 16.86f, 7.14f, 20.8f, 12f, 20.8f)
            curveTo(16.86f, 20.8f, 20.8f, 16.86f, 20.8f, 12f)
            curveTo(20.8f, 7.14f, 16.86f, 3.2f, 12f, 3.2f)
            close()
            moveTo(3.4f, 12f)
            lineTo(20.6f, 12f)
            moveTo(12f, 3.2f)
            curveTo(9.6f, 5.7f, 8.5f, 8.7f, 8.5f, 12f)
            curveTo(8.5f, 15.3f, 9.6f, 18.3f, 12f, 20.8f)
            curveTo(14.4f, 18.3f, 15.5f, 15.3f, 15.5f, 12f)
            curveTo(15.5f, 8.7f, 14.4f, 5.7f, 12f, 3.2f)
            close()
        }
    }

    val Star = vector("star") {
        line(1.55f) {
            moveTo(12f, 3.2f)
            lineTo(14.75f, 8.9f)
            lineTo(21f, 9.8f)
            lineTo(16.5f, 14.2f)
            lineTo(17.6f, 20.4f)
            lineTo(12f, 17.4f)
            lineTo(6.4f, 20.4f)
            lineTo(7.5f, 14.2f)
            lineTo(3f, 9.8f)
            lineTo(9.25f, 8.9f)
            close()
        }
    }

    val StarFilled = vector("star_filled") {
        solid {
            moveTo(12f, 3.2f)
            lineTo(14.75f, 8.9f)
            lineTo(21f, 9.8f)
            lineTo(16.5f, 14.2f)
            lineTo(17.6f, 20.4f)
            lineTo(12f, 17.4f)
            lineTo(6.4f, 20.4f)
            lineTo(7.5f, 14.2f)
            lineTo(3f, 9.8f)
            lineTo(9.25f, 8.9f)
            close()
        }
    }

    val Clock = vector("clock") {
        line(1.6f) {
            moveTo(12f, 3.2f)
            curveTo(7.14f, 3.2f, 3.2f, 7.14f, 3.2f, 12f)
            curveTo(3.2f, 16.86f, 7.14f, 20.8f, 12f, 20.8f)
            curveTo(16.86f, 20.8f, 20.8f, 16.86f, 20.8f, 12f)
            curveTo(20.8f, 7.14f, 16.86f, 3.2f, 12f, 3.2f)
            close()
            moveTo(12f, 6.8f)
            lineTo(12f, 12.3f)
            lineTo(15.8f, 14.6f)
        }
    }

    val Download = vector("download") {
        line(1.7f) {
            moveTo(12f, 3.6f)
            lineTo(12f, 15f)
            moveTo(7.2f, 10.4f)
            lineTo(12f, 15.2f)
            lineTo(16.8f, 10.4f)
            moveTo(3.8f, 17.2f)
            lineTo(3.8f, 19.2f)
            quadTo(3.8f, 20.4f, 5f, 20.4f)
            lineTo(19f, 20.4f)
            quadTo(20.2f, 20.4f, 20.2f, 19.2f)
            lineTo(20.2f, 17.2f)
        }
    }

    val Incognito = vector("incognito") {
        line(1.55f) {
            moveTo(3.2f, 10.3f)
            lineTo(20.8f, 10.3f)
            lineTo(19.9f, 14.8f)
            curveTo(19.5f, 17f, 17.7f, 18.4f, 15.8f, 18.4f)
            curveTo(14.2f, 18.4f, 13.1f, 17.4f, 13.1f, 15.8f)
            lineTo(13.1f, 14.6f)
            lineTo(10.9f, 14.6f)
            lineTo(10.9f, 15.8f)
            curveTo(10.9f, 17.4f, 9.8f, 18.4f, 8.2f, 18.4f)
            curveTo(6.3f, 18.4f, 4.5f, 17f, 4.1f, 14.8f)
            close()
            moveTo(2.8f, 10.3f)
            lineTo(5.6f, 5.8f)
            quadTo(6.1f, 5f, 7.1f, 5f)
            lineTo(16.9f, 5f)
            quadTo(17.9f, 5f, 18.4f, 5.8f)
            lineTo(21.2f, 10.3f)
        }
    }

    val Share = vector("share") {
        line(1.6f) {
            moveTo(17.5f, 4.4f)
            curveTo(16.1f, 4.4f, 15f, 5.5f, 15f, 6.9f)
            curveTo(15f, 8.3f, 16.1f, 9.4f, 17.5f, 9.4f)
            curveTo(18.9f, 9.4f, 20f, 8.3f, 20f, 6.9f)
            curveTo(20f, 5.5f, 18.9f, 4.4f, 17.5f, 4.4f)
            close()
            moveTo(6.5f, 9.5f)
            curveTo(5.1f, 9.5f, 4f, 10.6f, 4f, 12f)
            curveTo(4f, 13.4f, 5.1f, 14.5f, 6.5f, 14.5f)
            curveTo(7.9f, 14.5f, 9f, 13.4f, 9f, 12f)
            curveTo(9f, 10.6f, 7.9f, 9.5f, 6.5f, 9.5f)
            close()
            moveTo(17.5f, 14.6f)
            curveTo(16.1f, 14.6f, 15f, 15.7f, 15f, 17.1f)
            curveTo(15f, 18.5f, 16.1f, 19.6f, 17.5f, 19.6f)
            curveTo(18.9f, 19.6f, 20f, 18.5f, 20f, 17.1f)
            curveTo(20f, 15.7f, 18.9f, 14.6f, 17.5f, 14.6f)
            close()
            moveTo(8.7f, 11.3f)
            lineTo(14.8f, 7.7f)
            moveTo(8.7f, 12.7f)
            lineTo(14.8f, 16.3f)
        }
    }

    val Desktop = vector("desktop") {
        line(1.6f) {
            moveTo(3.6f, 4.7f)
            lineTo(20.4f, 4.7f)
            lineTo(20.4f, 16.2f)
            lineTo(3.6f, 16.2f)
            close()
            moveTo(9f, 20f)
            lineTo(15f, 20f)
            moveTo(12f, 16.2f)
            lineTo(12f, 20f)
        }
    }

    val Tools = vector("tools") {
        line(1.55f) {
            moveTo(3.4f, 8.4f)
            lineTo(20.6f, 8.4f)
            lineTo(20.6f, 19.2f)
            lineTo(3.4f, 19.2f)
            close()
            moveTo(8.2f, 8.4f)
            lineTo(8.2f, 5.4f)
            quadTo(8.2f, 4.2f, 9.4f, 4.2f)
            lineTo(14.6f, 4.2f)
            quadTo(15.8f, 4.2f, 15.8f, 5.4f)
            lineTo(15.8f, 8.4f)
            moveTo(3.4f, 12.9f)
            lineTo(20.6f, 12.9f)
            moveTo(10.7f, 12.9f)
            lineTo(10.7f, 15f)
            lineTo(13.3f, 15f)
            lineTo(13.3f, 12.9f)
        }
    }

    val Settings = vector("settings") {
        solid {
            moveTo(19.14f, 12.94f)
            curveTo(19.18f, 12.64f, 19.2f, 12.33f, 19.2f, 12f)
            curveTo(19.2f, 11.68f, 19.18f, 11.35f, 19.13f, 11.06f)
            lineTo(21.16f, 9.48f)
            curveTo(21.34f, 9.34f, 21.39f, 9.07f, 21.27f, 8.86f)
            lineTo(19.35f, 5.53f)
            curveTo(19.23f, 5.32f, 18.97f, 5.24f, 18.75f, 5.32f)
            lineTo(16.36f, 6.28f)
            curveTo(15.87f, 5.9f, 15.34f, 5.59f, 14.77f, 5.36f)
            lineTo(14.41f, 2.81f)
            curveTo(14.38f, 2.58f, 14.18f, 2.4f, 13.95f, 2.4f)
            lineTo(10.11f, 2.4f)
            curveTo(9.88f, 2.4f, 9.68f, 2.58f, 9.65f, 2.81f)
            lineTo(9.29f, 5.36f)
            curveTo(8.72f, 5.59f, 8.19f, 5.9f, 7.7f, 6.28f)
            lineTo(5.31f, 5.32f)
            curveTo(5.09f, 5.24f, 4.83f, 5.32f, 4.71f, 5.53f)
            lineTo(2.79f, 8.86f)
            curveTo(2.66f, 9.07f, 2.72f, 9.34f, 2.9f, 9.48f)
            lineTo(4.93f, 11.06f)
            curveTo(4.88f, 11.35f, 4.85f, 11.68f, 4.85f, 12f)
            curveTo(4.85f, 12.33f, 4.88f, 12.64f, 4.93f, 12.94f)
            lineTo(2.9f, 14.52f)
            curveTo(2.72f, 14.66f, 2.66f, 14.93f, 2.79f, 15.14f)
            lineTo(4.71f, 18.47f)
            curveTo(4.83f, 18.68f, 5.09f, 18.76f, 5.31f, 18.68f)
            lineTo(7.7f, 17.72f)
            curveTo(8.19f, 18.1f, 8.72f, 18.41f, 9.29f, 18.64f)
            lineTo(9.65f, 21.19f)
            curveTo(9.68f, 21.42f, 9.88f, 21.6f, 10.11f, 21.6f)
            lineTo(13.95f, 21.6f)
            curveTo(14.18f, 21.6f, 14.38f, 21.42f, 14.41f, 21.19f)
            lineTo(14.77f, 18.64f)
            curveTo(15.34f, 18.41f, 15.87f, 18.1f, 16.36f, 17.72f)
            lineTo(18.75f, 18.68f)
            curveTo(18.97f, 18.76f, 19.23f, 18.68f, 19.35f, 18.47f)
            lineTo(21.27f, 15.14f)
            curveTo(21.39f, 14.93f, 21.34f, 14.66f, 21.16f, 14.52f)
            close()
            moveTo(12f, 15.6f)
            curveTo(10.02f, 15.6f, 8.4f, 13.98f, 8.4f, 12f)
            curveTo(8.4f, 10.02f, 10.02f, 8.4f, 12f, 8.4f)
            curveTo(13.98f, 8.4f, 15.6f, 10.02f, 15.6f, 12f)
            curveTo(15.6f, 13.98f, 13.98f, 15.6f, 12f, 15.6f)
            close()
        }
    }

    val Moon = vector("moon") {
        solid {
            moveTo(12f, 3f)
            curveTo(7.03f, 3f, 3f, 7.03f, 3f, 12f)
            curveTo(3f, 16.97f, 7.03f, 21f, 12f, 21f)
            curveTo(14.4f, 21f, 16.58f, 20.03f, 18.18f, 18.48f)
            curveTo(17.45f, 18.7f, 16.68f, 18.82f, 15.9f, 18.82f)
            curveTo(11.5f, 18.82f, 7.92f, 15.24f, 7.92f, 10.84f)
            curveTo(7.92f, 8.36f, 9.03f, 6.14f, 10.75f, 4.67f)
            curveTo(10.43f, 4.6f, 10.1f, 4.58f, 9.77f, 4.58f)
            curveTo(9.77f, 4.58f, 9.77f, 4.58f, 9.77f, 4.58f)
            curveTo(9.77f, 4.58f, 9.77f, 4.58f, 9.77f, 4.58f)
            curveTo(10.5f, 3.57f, 11.2f, 3f, 12f, 3f)
            close()
        }
    }

    val Power = vector("power") {
        solid {
            moveTo(16.56f, 5.44f)
            lineTo(15.11f, 6.89f)
            curveTo(16.84f, 7.94f, 18f, 9.83f, 18f, 12f)
            curveTo(18f, 15.31f, 15.31f, 18f, 12f, 18f)
            curveTo(8.69f, 18f, 6f, 15.31f, 6f, 12f)
            curveTo(6f, 9.83f, 7.16f, 7.94f, 8.88f, 6.88f)
            lineTo(7.44f, 5.44f)
            curveTo(5.36f, 6.88f, 4f, 9.28f, 4f, 12f)
            curveTo(4f, 16.42f, 7.58f, 20f, 12f, 20f)
            curveTo(16.42f, 20f, 20f, 16.42f, 20f, 12f)
            curveTo(20f, 9.28f, 18.64f, 6.88f, 16.56f, 5.44f)
            close()
            moveTo(11f, 3f)
            lineTo(11f, 13f)
            lineTo(13f, 13f)
            lineTo(13f, 3f)
            close()
        }
    }

    val ChevronDown = vector("chevron_down") {
        line(1.8f) {
            moveTo(5f, 8.5f)
            lineTo(12f, 15.5f)
            lineTo(19f, 8.5f)
        }
    }

    val ChevronUp = vector("chevron_up") {
        line(1.8f) {
            moveTo(5f, 15.5f)
            lineTo(12f, 8.5f)
            lineTo(19f, 15.5f)
        }
    }

    val Minus = vector("minus") {
        line(1.8f) {
            moveTo(4.5f, 12f)
            lineTo(19.5f, 12f)
        }
    }

    val ChevronRight = vector("chevron_right") {
        line(1.8f) {
            moveTo(9f, 5f)
            lineTo(16f, 12f)
            lineTo(9f, 19f)
        }
    }

    val Plus = vector("plus") {
        line(1.8f) {
            moveTo(12f, 4.5f)
            lineTo(12f, 19.5f)
            moveTo(4.5f, 12f)
            lineTo(19.5f, 12f)
        }
    }

    val Check = vector("check") {
        line(1.9f) {
            moveTo(4.8f, 12.5f)
            lineTo(9.6f, 17.2f)
            lineTo(19.2f, 6.8f)
        }
    }

    val Edit = vector("edit") {
        line(1.6f) {
            moveTo(4f, 16.7f)
            lineTo(4f, 20f)
            lineTo(7.3f, 20f)
            lineTo(18.4f, 8.9f)
            lineTo(15.1f, 5.6f)
            close()
            moveTo(13.9f, 6.8f)
            lineTo(17.2f, 10.1f)
        }
    }

    val Trash = vector("trash") {
        line(1.55f) {
            moveTo(4.5f, 6.5f)
            lineTo(19.5f, 6.5f)
            moveTo(9.2f, 3.8f)
            lineTo(14.8f, 3.8f)
            moveTo(7f, 6.5f)
            lineTo(7.9f, 20.2f)
            lineTo(16.1f, 20.2f)
            lineTo(17f, 6.5f)
            moveTo(10f, 10f)
            lineTo(10.4f, 17f)
            moveTo(14f, 10f)
            lineTo(13.6f, 17f)
        }
    }

    val External = vector("external") {
        line(1.6f) {
            moveTo(19.5f, 12.5f)
            lineTo(19.5f, 19.5f)
            lineTo(4.5f, 19.5f)
            lineTo(4.5f, 4.5f)
            lineTo(11.5f, 4.5f)
            moveTo(14f, 4.5f)
            lineTo(19.5f, 4.5f)
            lineTo(19.5f, 10f)
            moveTo(19.5f, 4.5f)
            lineTo(11.5f, 12.5f)
        }
    }

    val OpenArrow = vector("open_arrow") {
        line(1.7f) {
            moveTo(5f, 19f)
            lineTo(19f, 5f)
            moveTo(9f, 5f)
            lineTo(19f, 5f)
            lineTo(19f, 15f)
        }
    }

    val Copy = vector("copy") {
        line(1.55f) {
            moveTo(8.2f, 8.2f)
            lineTo(19.8f, 8.2f)
            lineTo(19.8f, 19.8f)
            lineTo(8.2f, 19.8f)
            close()
            moveTo(5.8f, 15.8f)
            lineTo(4.2f, 15.8f)
            lineTo(4.2f, 4.2f)
            lineTo(15.8f, 4.2f)
            lineTo(15.8f, 5.8f)
        }
    }

    val Info = vector("info") {
        line(1.6f) {
            moveTo(12f, 3.2f)
            curveTo(7.14f, 3.2f, 3.2f, 7.14f, 3.2f, 12f)
            curveTo(3.2f, 16.86f, 7.14f, 20.8f, 12f, 20.8f)
            curveTo(16.86f, 20.8f, 20.8f, 16.86f, 20.8f, 12f)
            curveTo(20.8f, 7.14f, 16.86f, 3.2f, 12f, 3.2f)
            close()
            moveTo(12f, 10.5f)
            lineTo(12f, 17f)
            moveTo(12f, 7f)
            lineTo(12f, 7.2f)
        }
    }

    val Lock = vector("lock") {
        line(1.55f) {
            moveTo(6f, 10.2f)
            lineTo(18f, 10.2f)
            lineTo(18f, 20f)
            lineTo(6f, 20f)
            close()
            moveTo(8.5f, 10.2f)
            lineTo(8.5f, 7.5f)
            curveTo(8.5f, 5.6f, 10.1f, 4f, 12f, 4f)
            curveTo(13.9f, 4f, 15.5f, 5.6f, 15.5f, 7.5f)
            lineTo(15.5f, 10.2f)
        }
    }

    val Reader = vector("reader") {
        line(1.55f) {
            moveTo(12f, 6.4f)
            curveTo(10.3f, 4.8f, 7.7f, 4.2f, 4.2f, 4.8f)
            lineTo(4.2f, 18.8f)
            curveTo(7.7f, 18.2f, 10.3f, 18.8f, 12f, 20.4f)
            curveTo(13.7f, 18.8f, 16.3f, 18.2f, 19.8f, 18.8f)
            lineTo(19.8f, 4.8f)
            curveTo(16.3f, 4.2f, 13.7f, 4.8f, 12f, 6.4f)
            close()
            moveTo(12f, 6.4f)
            lineTo(12f, 20.4f)
        }
    }

    val Find = vector("find") {
        line(1.55f) {
            moveTo(4.2f, 6f)
            lineTo(14f, 6f)
            moveTo(4.2f, 12f)
            lineTo(11f, 12f)
            moveTo(4.2f, 18f)
            lineTo(8f, 18f)
            moveTo(16.5f, 9.2f)
            curveTo(18.8f, 9.2f, 20.6f, 11f, 20.6f, 13.3f)
            curveTo(20.6f, 15.6f, 18.8f, 17.4f, 16.5f, 17.4f)
            curveTo(14.2f, 17.4f, 12.4f, 15.6f, 12.4f, 13.3f)
            curveTo(12.4f, 11f, 14.2f, 9.2f, 16.5f, 9.2f)
            close()
        }
    }

    val Save = vector("save") {
        line(1.55f) {
            moveTo(4.2f, 4.2f)
            lineTo(17.2f, 4.2f)
            lineTo(20f, 7f)
            lineTo(20f, 20f)
            lineTo(4.2f, 20f)
            close()
            moveTo(7.4f, 4.2f)
            lineTo(7.4f, 9.2f)
            lineTo(16f, 9.2f)
            lineTo(16f, 4.2f)
            moveTo(7.4f, 20f)
            lineTo(7.4f, 13.2f)
            lineTo(16.6f, 13.2f)
            lineTo(16.6f, 20f)
        }
    }

    val Image = vector("image") {
        line(1.55f) {
            moveTo(3.6f, 4.6f)
            lineTo(20.4f, 4.6f)
            lineTo(20.4f, 19.4f)
            lineTo(3.6f, 19.4f)
            close()
            moveTo(7.2f, 9.4f)
            curveTo(7.2f, 8.5f, 7.9f, 7.8f, 8.8f, 7.8f)
            curveTo(9.7f, 7.8f, 10.4f, 8.5f, 10.4f, 9.4f)
            curveTo(10.4f, 10.3f, 9.7f, 11f, 8.8f, 11f)
            curveTo(7.9f, 11f, 7.2f, 10.3f, 7.2f, 9.4f)
            close()
            moveTo(4f, 17f)
            lineTo(9f, 12f)
            lineTo(13f, 16f)
            lineTo(15.8f, 13.2f)
            lineTo(20f, 17.4f)
        }
    }

    val Offline = vector("offline") {
        line(1.55f) {
            moveTo(4.2f, 7f)
            lineTo(4.2f, 20f)
            lineTo(19.8f, 20f)
            lineTo(19.8f, 7f)
            moveTo(3f, 7f)
            lineTo(7f, 4f)
            lineTo(17f, 4f)
            lineTo(21f, 7f)
            moveTo(8f, 4f)
            lineTo(8f, 9f)
            lineTo(16f, 9f)
            lineTo(16f, 4f)
        }
    }

    val Code = vector("code") {
        line(1.7f) {
            moveTo(8.5f, 7.5f)
            lineTo(4f, 12f)
            lineTo(8.5f, 16.5f)
            moveTo(15.5f, 7.5f)
            lineTo(20f, 12f)
            lineTo(15.5f, 16.5f)
        }
    }

    val Speaker = vector("speaker") {
        line(1.6f) {
            moveTo(4f, 9.2f)
            lineTo(8f, 9.2f)
            lineTo(13f, 4.8f)
            lineTo(13f, 19.2f)
            lineTo(8f, 14.8f)
            lineTo(4f, 14.8f)
            close()
            moveTo(16.2f, 8.5f)
            curveTo(17.6f, 10.5f, 17.6f, 13.5f, 16.2f, 15.5f)
            moveTo(18.8f, 6.2f)
            curveTo(21.1f, 9.6f, 21.1f, 14.4f, 18.8f, 17.8f)
        }
    }

    val Sparkle = vector("sparkle") {
        line(1.55f) {
            moveTo(12f, 3.2f)
            lineTo(14.1f, 9.9f)
            lineTo(20.8f, 12f)
            lineTo(14.1f, 14.1f)
            lineTo(12f, 20.8f)
            lineTo(9.9f, 14.1f)
            lineTo(3.2f, 12f)
            lineTo(9.9f, 9.9f)
            close()
            moveTo(18.4f, 3.2f)
            lineTo(19f, 5f)
            lineTo(20.8f, 5.6f)
            lineTo(19f, 6.2f)
            lineTo(18.4f, 8f)
            lineTo(17.8f, 6.2f)
            lineTo(16f, 5.6f)
            lineTo(17.8f, 5f)
            close()
        }
    }

    val Rotate = vector("rotate") {
        line(1.55f) {
            moveTo(7f, 3.8f)
            lineTo(17f, 3.8f)
            lineTo(17f, 20.2f)
            lineTo(7f, 20.2f)
            close()
            moveTo(16.8f, 7.2f)
            curveTo(20.4f, 8.2f, 22f, 11.7f, 20.8f, 15.2f)
            moveTo(20.9f, 17.7f)
            lineTo(20.8f, 15.1f)
            lineTo(18.3f, 15.9f)
        }
    }

    val Block = vector("block") {
        line(1.6f) {
            moveTo(12f, 3.2f)
            lineTo(20.4f, 7.5f)
            lineTo(20.4f, 14f)
            curveTo(20.4f, 18f, 16.5f, 20.7f, 12f, 22f)
            curveTo(7.5f, 20.7f, 3.6f, 18f, 3.6f, 14f)
            lineTo(3.6f, 7.5f)
            close()
            moveTo(7f, 12.8f)
            lineTo(17f, 12.8f)
        }
    }

    val Flag = vector("flag") {
        line(1.6f) {
            moveTo(6f, 21f)
            lineTo(6f, 3.4f)
            moveTo(6f, 4.4f)
            curveTo(10f, 2.4f, 13.2f, 6.4f, 18f, 4.4f)
            lineTo(18f, 12.4f)
            curveTo(13.2f, 14.4f, 10f, 10.4f, 6f, 12.4f)
        }
    }

    val TextSize = vector("text_size") {
        line(1.65f) {
            moveTo(3.2f, 18f)
            lineTo(8.2f, 5f)
            lineTo(13.2f, 18f)
            moveTo(5f, 14f)
            lineTo(11.4f, 14f)
            moveTo(14.6f, 18f)
            lineTo(17.4f, 10f)
            lineTo(20.2f, 18f)
            moveTo(15.5f, 15.6f)
            lineTo(19.3f, 15.6f)
        }
    }

    val Grid = vector("grid") {
        line(1.55f) {
            moveTo(4f, 4f)
            lineTo(9.2f, 4f)
            lineTo(9.2f, 9.2f)
            lineTo(4f, 9.2f)
            close()
            moveTo(14.8f, 4f)
            lineTo(20f, 4f)
            lineTo(20f, 9.2f)
            lineTo(14.8f, 9.2f)
            close()
            moveTo(4f, 14.8f)
            lineTo(9.2f, 14.8f)
            lineTo(9.2f, 20f)
            lineTo(4f, 20f)
            close()
            moveTo(14.8f, 14.8f)
            lineTo(20f, 14.8f)
            lineTo(20f, 20f)
            lineTo(14.8f, 20f)
            close()
        }
    }

    val RefreshSmall = Refresh
    val Chat = vector("chat") {
        line(1.55f) {
            moveTo(4.2f, 5f)
            lineTo(19.8f, 5f)
            lineTo(19.8f, 16.4f)
            lineTo(9f, 16.4f)
            lineTo(4.2f, 20f)
            close()
        }
    }

    val Secure = vector("secure") {
        line(1.55f) {
            moveTo(12f, 3.4f)
            lineTo(19.6f, 6.1f)
            lineTo(19.6f, 11.3f)
            curveTo(19.6f, 15.6f, 16.8f, 18.9f, 12f, 20.7f)
            curveTo(7.2f, 18.9f, 4.4f, 15.6f, 4.4f, 11.3f)
            lineTo(4.4f, 6.1f)
            close()
            moveTo(8.2f, 11.7f)
            lineTo(10.8f, 14.3f)
            lineTo(15.8f, 9.2f)
        }
    }

    val Scanner = vector("scanner") {
        line(2f) {
            moveTo(9f, 3.5f)
            lineTo(6.3f, 3.5f)
            curveTo(4.7f, 3.5f, 3.5f, 4.7f, 3.5f, 6.3f)
            lineTo(3.5f, 9f)
            moveTo(9f, 20.5f)
            lineTo(6.3f, 20.5f)
            curveTo(4.7f, 20.5f, 3.5f, 19.3f, 3.5f, 17.7f)
            lineTo(3.5f, 15f)
            moveTo(15f, 3.5f)
            lineTo(17.7f, 3.5f)
            curveTo(19.3f, 3.5f, 20.5f, 4.7f, 20.5f, 6.3f)
            lineTo(20.5f, 9f)
            moveTo(15f, 20.5f)
            lineTo(17.7f, 20.5f)
            curveTo(19.3f, 20.5f, 20.5f, 19.3f, 20.5f, 17.7f)
            lineTo(20.5f, 15f)
            moveTo(8f, 12f)
            lineTo(16f, 12f)
        }
    }

    val QR = vector("qr") {
        line(1.75f) {
            moveTo(4f, 9f)
            lineTo(4f, 4f)
            lineTo(9f, 4f)
            moveTo(15f, 4f)
            lineTo(20f, 4f)
            lineTo(20f, 9f)
            moveTo(20f, 15f)
            lineTo(20f, 20f)
            lineTo(15f, 20f)
            moveTo(9f, 20f)
            lineTo(4f, 20f)
            lineTo(4f, 15f)
            moveTo(9.4f, 9.4f)
            lineTo(14.6f, 9.4f)
            lineTo(14.6f, 14.6f)
            lineTo(9.4f, 14.6f)
            close()
        }
    }

    val Translate = vector("translate") {
        line(1.55f) {
            moveTo(3.2f, 5.4f)
            lineTo(12.2f, 5.4f)
            moveTo(7.7f, 3.2f)
            lineTo(7.7f, 5.4f)
            moveTo(10.7f, 5.4f)
            curveTo(9.6f, 9.2f, 7.2f, 12.3f, 3.8f, 14.2f)
            moveTo(5.2f, 9.2f)
            curveTo(6.4f, 11.5f, 8.6f, 13.4f, 11.4f, 14.6f)
            moveTo(12.6f, 20.4f)
            lineTo(16.4f, 11f)
            lineTo(20.2f, 20.4f)
            moveTo(13.9f, 17.2f)
            lineTo(18.9f, 17.2f)
        }
    }

    val AddHome = vector("add_home") {
        line(1.55f) {
            moveTo(3.6f, 10.2f)
            lineTo(11f, 3.6f)
            lineTo(18.4f, 10.2f)
            lineTo(18.4f, 19.6f)
            lineTo(3.6f, 19.6f)
            close()
            moveTo(17.8f, 15.2f)
            lineTo(22f, 15.2f)
            moveTo(19.9f, 13.1f)
            lineTo(19.9f, 17.3f)
        }
    }

    val Expand = vector("expand") {
        line(1.7f) {
            moveTo(14f, 4f)
            lineTo(20f, 4f)
            lineTo(20f, 10f)
            moveTo(10f, 20f)
            lineTo(4f, 20f)
            lineTo(4f, 14f)
        }
    }

    val Fullscreen = vector("fullscreen") {
        line(1.7f) {
            moveTo(4f, 9f)
            lineTo(4f, 4f)
            lineTo(9f, 4f)
            moveTo(15f, 4f)
            lineTo(20f, 4f)
            lineTo(20f, 9f)
            moveTo(20f, 15f)
            lineTo(20f, 20f)
            lineTo(15f, 20f)
            moveTo(9f, 20f)
            lineTo(4f, 20f)
            lineTo(4f, 15f)
        }
    }

    val Printer = vector("printer") {
        line(1.5f) {
            moveTo(7f, 8f)
            lineTo(7f, 3.8f)
            lineTo(17f, 3.8f)
            lineTo(17f, 8f)
            moveTo(5f, 8f)
            lineTo(19f, 8f)
            quadTo(20.6f, 8f, 20.6f, 9.6f)
            lineTo(20.6f, 15.6f)
            lineTo(17.4f, 15.6f)
            moveTo(6.6f, 15.6f)
            lineTo(3.4f, 15.6f)
            lineTo(3.4f, 9.6f)
            quadTo(3.4f, 8f, 5f, 8f)
            moveTo(6.6f, 12.4f)
            lineTo(17.4f, 12.4f)
            lineTo(17.4f, 20.2f)
            lineTo(6.6f, 20.2f)
            close()
        }
    }

    val Clear = Close
    val BookmarkAdd = vector("bookmark_add") {
        line(1.5f) {
            moveTo(11f, 3.4f)
            lineTo(13.5f, 8.5f)
            lineTo(19.1f, 9.3f)
            lineTo(15f, 13.3f)
            lineTo(16f, 18.9f)
            lineTo(11f, 16.3f)
            lineTo(6f, 18.9f)
            lineTo(7f, 13.3f)
            lineTo(2.9f, 9.3f)
            lineTo(8.5f, 8.5f)
            close()
            moveTo(18.4f, 15.2f)
            lineTo(22f, 15.2f)
            moveTo(20.2f, 13.4f)
            lineTo(20.2f, 17f)
        }
    }
}
