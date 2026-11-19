package id.branchworld.game.simulation

/** The renderer receives only a snapshot of the current tick and actor pose. */
data class WorldRenderProjection(
    val tick: Long,
    val bunnyId: Long,
    val x: Int,
    val z: Int,
    val heading: BunnyHeading,
) {
    companion object {
        const val SceneUnitsPerCell = 0.1f
    }
}

enum class BunnyHeading { PositiveX, NegativeX, PositiveZ, NegativeZ }

internal fun WorldState.renderProjection(previous: WorldRenderProjection? = null): WorldRenderProjection {
    val heading = when {
        previous == null -> BunnyHeading.PositiveZ
        bunnyCell.x > previous.x -> BunnyHeading.PositiveX
        bunnyCell.x < previous.x -> BunnyHeading.NegativeX
        bunnyCell.z > previous.z -> BunnyHeading.PositiveZ
        bunnyCell.z < previous.z -> BunnyHeading.NegativeZ
        else -> previous.heading
    }
    return WorldRenderProjection(tick, bunnyId, bunnyCell.x, bunnyCell.z, heading)
}
