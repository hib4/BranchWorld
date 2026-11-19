package id.branchworld.game.terrain

import id.branchworld.game.simulation.Fnv64
import kotlin.math.abs

data class TerrainKey(val seed: Long, val worldgenVersion: Int = 1)

enum class Biome(internal val id: Int) {
    Water(0), Beach(1), Meadow(2), PineForest(3), RockyUpland(4),
}

enum class PropKind(internal val id: Int) { Pine(1), Rock(2) }

/** Land means the entire cell is above sea level. Mixed beach cells form the shoreline. */
data class TerrainCell(
    val elevationTwice: Int,
    val biome: Biome,
    val moisture: Int,
    val temperature: Int,
    val land: Boolean,
    val occupied: Boolean,
    val walkable: Boolean,
)

data class TerrainProp(
    val kind: PropKind,
    val x: Int,
    val z: Int,
    val scalePercent: Int,
    val yawDegrees: Int,
)

/** Coordinates address cells in [-32, 31] or corners in [-32, 32]. */
class TerrainMap internal constructor(
    val key: TerrainKey,
    private val elevations: IntArray,
    private val cells: Array<TerrainCell>,
    private val props: List<TerrainProp>,
) {
    val propCount: Int get() = props.size
    val digest: ULong = digestTerrain()

    fun prop(index: Int): TerrainProp = props[index]

    fun cellAt(x: Int, z: Int): TerrainCell? = if (containsCell(x, z)) cells[cellIndex(x, z)] else null

    fun cornerElevation(x: Int, z: Int): Int {
        require(x in MinCell..MaxCorner && z in MinCell..MaxCorner)
        return elevations[cornerIndex(x, z)]
    }

    fun canStep(x: Int, z: Int, toX: Int, toZ: Int): Boolean {
        val from = cellAt(x, z) ?: return false
        val to = cellAt(toX, toZ) ?: return false
        return abs(x - toX) + abs(z - toZ) == 1 && from.walkable && to.walkable &&
            abs(from.elevationTwice - to.elevationTwice) <= MaxEdgeRiseTwice
    }

    private fun digestTerrain(): ULong = Fnv64().apply {
        addInt(key.worldgenVersion)
        addLong(key.seed.toULong())
        addInt(Size)
        elevations.forEach(::addInt)
        for (cell in cells) {
            addInt(cell.elevationTwice)
            addInt(cell.biome.id)
            addInt(cell.moisture)
            addInt(cell.temperature)
            addInt(if (cell.land) 1 else 0)
            addInt(if (cell.occupied) 1 else 0)
            addInt(if (cell.walkable) 1 else 0)
        }
        addInt(props.size)
        for (prop in props) {
            addInt(prop.kind.id)
            addInt(prop.x)
            addInt(prop.z)
            addInt(prop.scalePercent)
            addInt(prop.yawDegrees)
        }
    }.value

    companion object {
        const val Size = 64
        const val MinCell = -Size / 2
        const val MaxCell = MinCell + Size - 1
        const val MaxCorner = MaxCell + 1
        const val MinElevation = -24
        const val MaxElevation = 42
        private const val MaxEdgeRiseTwice = 12

        internal fun containsCell(x: Int, z: Int) = x in MinCell..MaxCell && z in MinCell..MaxCell
        internal fun cellIndex(x: Int, z: Int) = (z - MinCell) * Size + x - MinCell
        internal fun cornerIndex(x: Int, z: Int) = (z - MinCell) * (Size + 1) + x - MinCell
    }
}
