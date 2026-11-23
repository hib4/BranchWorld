package id.branchworld.simulation.terrain

import id.branchworld.simulation.core.Fnv64

enum class IslandLandscape(internal val id: Int) { Marsh(1), Tarn(2), Valley(3) }

enum class WaterKind(internal val id: Int) { Stream(1), Lake(2), Marsh(3) }

enum class LandmarkKind(internal val id: Int) { Headwaters(1), Basin(2), Estuary(3) }

data class TerrainLandmark(val kind: LandmarkKind, val x: Int, val z: Int)

data class WaterCell(
    val kind: WaterKind,
    val surfaceTwice: Int,
    val downstreamX: Int,
    val downstreamZ: Int,
)

/** Static drainage and access data. Seasonal recharge belongs to each history. */
class Watershed internal constructor(
    val landscape: IslandLandscape,
    private val water: Array<WaterCell?>,
    private val drainage: IntArray,
    banks: List<Pair<Int, Int>>,
    landmarks: List<TerrainLandmark>,
) {
    internal val drinkingBanks = banks.toList()
    val landmarks: List<TerrainLandmark> = landmarks.toList()

    fun waterAt(x: Int, z: Int): WaterCell? =
        if (TerrainMap.containsCell(x, z)) water[TerrainMap.cellIndex(x, z)] else null

    /** 0 for freely draining uplands, up to 12 for buffered floodplain soil. */
    internal fun retentionAt(x: Int, z: Int): Int = drainage[TerrainMap.cellIndex(x, z)]

    internal fun writeDigest(writer: Fnv64) = with(writer) {
        addInt(landscape.id)
        for (index in water.indices) {
            val tile = water[index]
            addInt(tile?.kind?.id ?: 0)
            if (tile != null) {
                addInt(tile.surfaceTwice)
                addInt(tile.downstreamX)
                addInt(tile.downstreamZ)
            }
            addInt(drainage[index])
        }
        addInt(drinkingBanks.size)
        for ((x, z) in drinkingBanks) {
            addInt(x)
            addInt(z)
        }
        addInt(landmarks.size)
        for (landmark in landmarks) {
            addInt(landmark.kind.id)
            addInt(landmark.x)
            addInt(landmark.z)
        }
    }
}
