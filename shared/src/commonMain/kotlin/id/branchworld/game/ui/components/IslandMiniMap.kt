package id.branchworld.game.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import id.branchworld.game.ui.theme.Wood
import id.branchworld.simulation.terrain.Biome
import id.branchworld.simulation.terrain.IslandGenerator
import id.branchworld.simulation.terrain.TerrainKey
import id.branchworld.simulation.terrain.TerrainMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A painted map of an island, drawn from its real generated terrain and inlaid in a beech frame: water, beaches,
 * meadows, forests, uplands and wetlands, shaded by height. Generated off the main thread and cached for the session.
 */
@Composable
internal fun IslandMiniMap(key: TerrainKey, modifier: Modifier = Modifier, muted: Boolean = false) {
    val terrain by produceState(MiniMaps[key], key) {
        value = MiniMaps[key] ?: withContext(Dispatchers.Default) { IslandGenerator.generate(key) }.also { MiniMaps[key] = it }
    }
    Canvas(modifier) {
        val inset = 5.dp.toPx()
        drawRoundRect(Wood.BeechEdge, cornerRadius = CornerRadius(12.dp.toPx()))
        drawRoundRect(Color(0xFF2F6E78), Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), CornerRadius(8.dp.toPx()))
        val map = terrain ?: return@Canvas
        val cell = minOf(size.width - inset * 2, size.height - inset * 2) / TerrainMap.Size
        val left = (size.width - cell * TerrainMap.Size) / 2
        val top = (size.height - cell * TerrainMap.Size) / 2
        for (z in TerrainMap.MinCell..TerrainMap.MaxCell) for (x in TerrainMap.MinCell..TerrainMap.MaxCell) {
            val tile = map.cellAt(x, z) ?: continue
            val water = map.watershed?.waterAt(x, z)
            val base = when {
                water != null -> Color(0xFF4C9AA0)
                !tile.land -> continue
                else -> when (tile.biome) {
                    Biome.Water -> continue
                    Biome.Beach -> Color(0xFFE6D3A0)
                    Biome.Meadow -> Color(0xFF9DC06C)
                    Biome.PineForest -> Color(0xFF41704A)
                    Biome.RockyUpland -> Color(0xFFB0A98E)
                    Biome.Wetland -> Color(0xFF73A07C)
                }
            }
            val shaded = lerp(base, Color.White, (tile.elevationTwice.coerceIn(0, 60) / 60f) * 0.2f)
            val color = if (muted) lerp(shaded, Wood.BeechEdge, 0.6f) else shaded
            drawRect(color, Offset(left + (x - TerrainMap.MinCell) * cell, top + (z - TerrainMap.MinCell) * cell), Size(cell + 0.6f, cell + 0.6f))
        }
    }
}

private val MiniMaps = mutableMapOf<TerrainKey, TerrainMap>()

/** Generates the gallery's mini-maps ahead of the menus that show them. Results are stored on the main thread. */
internal suspend fun warmMiniMaps(keys: List<TerrainKey>) {
    for (key in keys) {
        if (key in MiniMaps) continue
        val map = withContext(Dispatchers.Default) { IslandGenerator.generate(key) }
        MiniMaps[key] = map
    }
}
