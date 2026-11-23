package id.branchworld.game.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Hand-drawn line icons for the Keeper's Atlas interface. Tinted by the Icon that draws them. */
internal object GameIcons {
    private fun icon(name: String, filled: Boolean = false, draw: PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = if (filled) SolidColor(Color.Black) else null,
                stroke = if (filled) null else SolidColor(Color.Black),
                strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                pathBuilder = draw,
            )
        }.build()

    val Play = icon("play", filled = true) { moveTo(8f, 5.5f); lineTo(18.5f, 12f); lineTo(8f, 18.5f); close() }
    val Pause = icon("pause", filled = true) {
        moveTo(7f, 5.5f); lineTo(10.2f, 5.5f); lineTo(10.2f, 18.5f); lineTo(7f, 18.5f); close()
        moveTo(13.8f, 5.5f); lineTo(17f, 5.5f); lineTo(17f, 18.5f); lineTo(13.8f, 18.5f); close()
    }
    val Leaf = icon("shape") {
        moveTo(5f, 19f); curveTo(5f, 10f, 10f, 5f, 19f, 5f); curveTo(19f, 14f, 14f, 19f, 5f, 19f); close()
        moveTo(5f, 19f); lineTo(13f, 11f)
    }
    val Home = icon("village") {
        moveTo(4f, 11.5f); lineTo(12f, 5f); lineTo(20f, 11.5f)
        moveTo(6.5f, 10f); lineTo(6.5f, 19f); lineTo(17.5f, 19f); lineTo(17.5f, 10f)
        moveTo(10.5f, 19f); lineTo(10.5f, 14.5f); lineTo(13.5f, 14.5f); lineTo(13.5f, 19f)
    }
    val Eye = icon("inspect") {
        moveTo(3f, 12f); curveTo(6f, 7f, 18f, 7f, 21f, 12f); curveTo(18f, 17f, 6f, 17f, 3f, 12f); close()
        moveTo(14.5f, 12f); arcTo(2.5f, 2.5f, 0f, true, true, 9.5f, 12f); arcTo(2.5f, 2.5f, 0f, true, true, 14.5f, 12f)
    }
    val Branches = icon("timeline") {
        moveTo(6f, 4f); lineTo(6f, 20f)
        moveTo(6f, 13f); curveTo(6f, 9f, 12f, 10f, 12f, 6f)
        moveTo(6f, 17f); curveTo(6f, 13f, 18f, 15f, 18f, 9f)
        moveTo(12f, 6f); lineTo(12f, 4.5f)
    }
    val Menu = icon("menu") { moveTo(5f, 7f); lineTo(19f, 7f); moveTo(5f, 12f); lineTo(19f, 12f); moveTo(5f, 17f); lineTo(19f, 17f) }
    val Camera = icon("camera") {
        moveTo(4f, 8.5f); lineTo(8f, 8.5f); lineTo(9.5f, 6f); lineTo(14.5f, 6f); lineTo(16f, 8.5f); lineTo(20f, 8.5f)
        lineTo(20f, 18f); lineTo(4f, 18f); close()
        moveTo(15.2f, 13f); arcTo(3.2f, 3.2f, 0f, true, true, 8.8f, 13f); arcTo(3.2f, 3.2f, 0f, true, true, 15.2f, 13f)
    }
    val Bookmark = icon("bookmark") { moveTo(7f, 4f); lineTo(17f, 4f); lineTo(17f, 20f); lineTo(12f, 16f); lineTo(7f, 20f); close() }
    val Book = icon("chronicle") {
        moveTo(12f, 6.5f); curveTo(9.5f, 5f, 6.5f, 5f, 4f, 6f); lineTo(4f, 18.5f); curveTo(6.5f, 17.5f, 9.5f, 17.5f, 12f, 19f)
        curveTo(14.5f, 17.5f, 17.5f, 17.5f, 20f, 18.5f); lineTo(20f, 6f); curveTo(17.5f, 5f, 14.5f, 5f, 12f, 6.5f); lineTo(12f, 19f)
    }
    val Compass = icon("atlas") {
        moveTo(20f, 12f); arcTo(8f, 8f, 0f, true, true, 4f, 12f); arcTo(8f, 8f, 0f, true, true, 20f, 12f)
        moveTo(14.8f, 9.2f); lineTo(13f, 13f); lineTo(9.2f, 14.8f); lineTo(11f, 11f); close()
    }
    val Gear = icon("settings") {
        moveTo(15f, 12f); arcTo(3f, 3f, 0f, true, true, 9f, 12f); arcTo(3f, 3f, 0f, true, true, 15f, 12f)
        moveTo(12f, 3.5f); lineTo(12f, 6f); moveTo(12f, 18f); lineTo(12f, 20.5f)
        moveTo(3.5f, 12f); lineTo(6f, 12f); moveTo(18f, 12f); lineTo(20.5f, 12f)
        moveTo(6f, 6f); lineTo(7.8f, 7.8f); moveTo(16.2f, 16.2f); lineTo(18f, 18f)
        moveTo(18f, 6f); lineTo(16.2f, 7.8f); moveTo(7.8f, 16.2f); lineTo(6f, 18f)
    }
    val Back = icon("back") { moveTo(14.5f, 5.5f); lineTo(8f, 12f); lineTo(14.5f, 18.5f) }
    val Close = icon("close") { moveTo(6.5f, 6.5f); lineTo(17.5f, 17.5f); moveTo(17.5f, 6.5f); lineTo(6.5f, 17.5f) }
    val Shield = icon("protect") {
        moveTo(12f, 3.5f); lineTo(19f, 6.5f); curveTo(19f, 13f, 16f, 17.5f, 12f, 20.5f); curveTo(8f, 17.5f, 5f, 13f, 5f, 6.5f); close()
        moveTo(9f, 12f); lineTo(11.2f, 14.2f); lineTo(15.2f, 9.8f)
    }
    val Seal = icon("close-chapter") {
        moveTo(19f, 10f); arcTo(7f, 7f, 0f, true, true, 5f, 10f); arcTo(7f, 7f, 0f, true, true, 19f, 10f)
        moveTo(8.5f, 15.5f); lineTo(7f, 21f); lineTo(12f, 19f); lineTo(17f, 21f); lineTo(15.5f, 15.5f)
        moveTo(9.2f, 10f); lineTo(11.2f, 12f); lineTo(14.8f, 8.2f)
    }
    val Sparkle = icon("sandbox") {
        moveTo(12f, 3f); lineTo(13.6f, 10.4f); lineTo(21f, 12f); lineTo(13.6f, 13.6f); lineTo(12f, 21f)
        lineTo(10.4f, 13.6f); lineTo(3f, 12f); lineTo(10.4f, 10.4f); close()
    }
    val Rabbit = icon("rabbit") {
        // Two tall ears over a round head and a low body.
        moveTo(9f, 10f); curveTo(7.5f, 6f, 7.8f, 2.8f, 9.2f, 3f); curveTo(10.6f, 3.2f, 10.6f, 6.5f, 10.4f, 10f)
        moveTo(13.4f, 10f); curveTo(13.6f, 6.5f, 14.4f, 3.4f, 15.6f, 3.8f); curveTo(16.8f, 4.2f, 15.8f, 7.5f, 14.6f, 10.4f)
        moveTo(8.6f, 13.6f); curveTo(8.6f, 10.6f, 15.4f, 10.6f, 15.4f, 13.6f); curveTo(15.4f, 15.6f, 13.8f, 16.2f, 12f, 16.2f)
        curveTo(10.2f, 16.2f, 8.6f, 15.6f, 8.6f, 13.6f)
        moveTo(6.5f, 20f); curveTo(6.5f, 16.8f, 17.5f, 16.8f, 17.5f, 20f)
    }
    val Wolf = icon("wolf") {
        // A long muzzle and upright pointed ears.
        moveTo(5f, 9f); lineTo(7.5f, 3.5f); lineTo(10f, 8f); lineTo(14f, 8f); lineTo(16.5f, 3.5f); lineTo(19f, 9f)
        lineTo(18f, 14f); lineTo(14f, 19.5f); lineTo(12f, 20.5f); lineTo(10f, 19.5f); lineTo(6f, 14f); close()
        moveTo(9.5f, 12f); lineTo(9.6f, 12f); moveTo(14.4f, 12f); lineTo(14.5f, 12f)
    }
    val Deer = icon("deer") {
        // Branching antlers over a slender head.
        moveTo(9f, 9f); lineTo(6f, 3.5f); moveTo(7.4f, 6f); lineTo(4.5f, 6.5f)
        moveTo(15f, 9f); lineTo(18f, 3.5f); moveTo(16.6f, 6f); lineTo(19.5f, 6.5f)
        moveTo(8.5f, 9.5f); curveTo(10f, 8f, 14f, 8f, 15.5f, 9.5f); lineTo(14f, 18f); curveTo(13.2f, 20.5f, 10.8f, 20.5f, 10f, 18f); close()
    }
    val Fox = icon("fox") {
        // A sharp triangular face with a white-tipped cheek line.
        moveTo(4f, 5f); lineTo(9f, 8.5f); lineTo(15f, 8.5f); lineTo(20f, 5f); lineTo(18.5f, 12f); lineTo(12f, 19.5f)
        lineTo(5.5f, 12f); close()
        moveTo(7.5f, 12.5f); lineTo(12f, 15f); lineTo(16.5f, 12.5f)
    }
    val Pine = icon("tree") {
        moveTo(12f, 2.8f); lineTo(16.5f, 9.5f); lineTo(14f, 9.5f); lineTo(18f, 15.5f); lineTo(6f, 15.5f); lineTo(10f, 9.5f)
        lineTo(7.5f, 9.5f); close()
        moveTo(12f, 15.5f); lineTo(12f, 20.5f)
    }
    val Grass = icon("grass") {
        moveTo(4f, 20f); lineTo(20f, 20f)
        moveTo(7f, 20f); curveTo(6.5f, 15f, 5f, 12f, 3.5f, 10.5f)
        moveTo(10.5f, 20f); curveTo(10.5f, 13f, 9.5f, 8f, 8.5f, 5.5f)
        moveTo(13.5f, 20f); curveTo(13.8f, 13f, 15f, 8.5f, 16.5f, 6f)
        moveTo(17f, 20f); curveTo(17.5f, 16f, 19f, 13f, 20.5f, 11.5f)
    }
    val Lift = icon("lift-out") {
        // An open hand lifting away.
        moveTo(7f, 13f); lineTo(7f, 7.5f); moveTo(10f, 12f); lineTo(10f, 5f); moveTo(13f, 12f); lineTo(13f, 5.5f)
        moveTo(16f, 12.5f); lineTo(16f, 7.5f)
        moveTo(7f, 13f); curveTo(7f, 18f, 9f, 20.5f, 12.5f, 20.5f); curveTo(16f, 20.5f, 18f, 18.5f, 18f, 15f)
        lineTo(18f, 11f); moveTo(16f, 12.5f); lineTo(18f, 11f)
    }
    val Fast = icon("faster", filled = true) {
        moveTo(4f, 6f); lineTo(12f, 12f); lineTo(4f, 18f); close()
        moveTo(12f, 6f); lineTo(20f, 12f); lineTo(12f, 18f); close()
    }

    val Dice = icon("roll") {
        moveTo(5f, 5f); lineTo(19f, 5f); lineTo(19f, 19f); lineTo(5f, 19f); close()
        moveTo(9.5f, 9f); lineTo(9.5f, 9.1f); moveTo(14.5f, 15f); lineTo(14.5f, 15.1f); moveTo(12f, 12f); lineTo(12f, 12.1f)
    }
}
