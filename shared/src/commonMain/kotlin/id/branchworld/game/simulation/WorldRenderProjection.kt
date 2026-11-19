package id.branchworld.game.simulation

import id.branchworld.game.terrain.TerrainMap
import kotlinx.coroutines.flow.StateFlow

class WorldRenderSource(val terrain: TerrainMap, val poses: StateFlow<WorldRenderProjection>)

/** The renderer receives only a snapshot of the current tick and actor pose. */
data class WorldRenderProjection(
    val tick: Long,
    val bunnyId: Long,
    val x: Int,
    val z: Int,
    val heading: BunnyHeading,
    val elevationTwice: Int = 0,
    val moving: Boolean = false,
)

enum class BunnyHeading { PositiveX, NegativeX, PositiveZ, NegativeZ }

internal fun WorldState.renderProjection(
    previous: WorldRenderProjection? = null,
    terrain: TerrainMap? = null,
): WorldRenderProjection {
    val heading = when {
        previous == null -> BunnyHeading.PositiveZ
        bunnyCell.x > previous.x -> BunnyHeading.PositiveX
        bunnyCell.x < previous.x -> BunnyHeading.NegativeX
        bunnyCell.z > previous.z -> BunnyHeading.PositiveZ
        bunnyCell.z < previous.z -> BunnyHeading.NegativeZ
        else -> previous.heading
    }
    val elevation = terrain?.let { checkNotNull(it.cellAt(bunnyCell.x, bunnyCell.z)).elevationTwice } ?: 0
    val moving = previous != null && (previous.x != bunnyCell.x || previous.z != bunnyCell.z)
    return WorldRenderProjection(tick, bunnyId, bunnyCell.x, bunnyCell.z, heading, elevation, moving)
}
