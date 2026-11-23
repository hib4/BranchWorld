package id.branchworld.game.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale

@Composable
internal fun PlaceholderIsland(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFFC3E3DE), Color(0xFF80B3AE))))

        val islandScale = minOf(size.width / 340f, size.height / 220f)
        scale(islandScale, pivot = center) {
            // Coordinates are relative to the center of the viewport.
            drawIslandFace(Color(0xFF699D95), -144f to 23f, -14f to -43f, 146f to 25f, 6f to 87f)
            drawIslandFace(Color(0xFF48746A), -116f to 8f, 7f to 43f, 7f to 68f, -116f to 30f)
            drawIslandFace(Color(0xFF386256), 7f to 43f, 117f to 9f, 117f to 30f, 7f to 68f)
            drawIslandFace(Color(0xFF96B677), -116f to 8f, -10f to -49f, 117f to 9f, 7f to 43f)
            drawIslandFace(Color(0xFF759A65), -116f to 8f, -25f to -17f, 7f to 43f)
            drawIslandFace(Color(0xFFADC88B), -25f to -17f, -10f to -49f, 117f to 9f)
            drawIslandFace(Color(0xFF355A4B), -79f to 0f, -56f to -44f, -34f to 0f)
            drawIslandFace(Color(0xFF49765D), -56f to -44f, -51f to 3f, -34f to 0f)
            drawIslandFace(Color(0xFF355A4B), 4f to 17f, 32f to -42f, 60f to 17f)
            drawIslandFace(Color(0xFF49765D), 32f to -42f, 36f to 22f, 60f to 17f)
        }
    }
}

private fun DrawScope.drawIslandFace(color: Color, vararg vertices: Pair<Float, Float>) {
    val path = Path().apply {
        val origin = center
        val (firstX, firstY) = vertices.first()
        moveTo(origin.x + firstX, origin.y + firstY)
        for ((x, y) in vertices.drop(1)) {
            lineTo(origin.x + x, origin.y + y)
        }
        close()
    }
    drawPath(path, color)
}
