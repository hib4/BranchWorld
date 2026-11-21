package id.branchworld.game.ecology

import id.branchworld.game.simulation.GridCell
import id.branchworld.game.simulation.SplitMix64
import id.branchworld.game.simulation.TerrainIdentity
import id.branchworld.game.simulation.mix64
import id.branchworld.game.terrain.Biome
import id.branchworld.game.terrain.TerrainMap
import id.branchworld.game.terrain.TerrainNavigation
import kotlin.math.abs

/** Static habitat queries and rebuildable distance fields for a single island. */
internal class IslandEcology(val terrain: TerrainMap) {
    val navigation = TerrainNavigation(terrain)
    val identity = TerrainIdentity(terrain.key, terrain.digest)
    val reachable = navigation.reachableCells()
    val forest = reachable.filter { terrain.biome(it) == Biome.PineForest }.ifEmpty { reachable }
    private val meadow = reachable.filter { terrain.biome(it) == Biome.Meadow }.ifEmpty { reachable }
    private val fields = linkedMapOf<GridCell, IntArray>()

    fun initialState(): EcologyState {
        val placement = SplitMix64(mix64(terrain.key.seed.toULong() xor PlacementSalt))
        val springs = springs()
        val patches = (patches(meadow, Biome.Meadow, ForageKind.Meadow, 32, springs, placement) +
            patches(forest, Biome.PineForest, ForageKind.Browse, 24, springs, placement)).sortedBy { it.id }
        val animals = mutableListOf<Creature>()
        for (species in Species.entries) {
            val count = if (species == Species.Rabbit) 4 else 3
            val habitat = roamCells(species)
            val nearSpawn = terrainDistance(navigation.spawn)
            val candidates = (habitat + reachable).distinct().sortedWith(
                compareBy<GridCell> { if (it in habitat) 0 else 1 }.thenBy { nearSpawn[cellId(it)] },
            )
            repeat(count) {
                val cell = if (animals.isEmpty()) navigation.spawn else {
                    candidates.firstOrNull { candidate -> animals.all { spacing(candidate, it.cell) >= 3 } }
                        ?: candidates.firstOrNull { candidate -> animals.none { it.cell == candidate } }
                        ?: navigation.spawn
                }
                animals.add(Creature(animals.size + 1L, species, cell))
            }
        }
        return EcologyState(
            identity, 0, mix64(terrain.key.seed.toULong() xor DecisionSalt), patches, springs, animals.toList(), animals.size + 1L,
        )
    }

    fun roamCells(species: Species): List<GridCell> = if (species == Species.Rabbit) meadow else forest

    fun terrainDistance(from: GridCell): IntArray = fields.getOrPut(from) {
        if (fields.size >= MaxCachedFields) fields.remove(fields.keys.first())
        navigation.distancesTo(from)
    }

    fun nearest(distance: IntArray, cells: List<GridCell>): GridCell? =
        cells.minByOrNull { distance[cellId(it)] }

    private fun springs(): List<GridCell> {
        terrain.watershed?.let { return it.drinkingBanks.map { (x, z) -> GridCell(x, z) } }
        val distance = terrainDistance(navigation.spawn)
        val byMoisture = reachable.sortedByDescending { terrain.terrainCell(it).moisture }
        val candidates = byMoisture.take((reachable.size + 1) / 2).sortedByDescending {
            terrain.terrainCell(it).moisture - 8 * distance[cellId(it)]
        }
        val first = candidates.first()
        val second = candidates.firstOrNull { spacing(first, it) >= 10 } ?: byMoisture.firstOrNull { it != first }
        return listOfNotNull(first, second).sortedBy(::cellId)
    }

    private fun patches(
        cells: List<GridCell>,
        biome: Biome,
        kind: ForageKind,
        limit: Int,
        springs: List<GridCell>,
        random: SplitMix64,
    ): List<ForagePatch> {
        val candidates = cells.filter { terrain.biome(it) == biome && it !in springs }
            .map { it to random.next() }.sortedWith(compareBy<Pair<GridCell, ULong>> { it.second }.thenBy { cellId(it.first) })
        val patches = mutableListOf<ForagePatch>()
        for ((cell, _) in candidates) {
            if (patches.size == limit) break
            if (patches.any { spacing(cell, it.cell) < 3 }) continue
            val moisture = terrain.terrainCell(cell).moisture
            patches.add(ForagePatch(cellId(cell), cell, kind, 160 + moisture / 3, 1 + moisture / 96))
        }
        return patches
    }

    private companion object {
        const val MaxCachedFields = 48
        const val PlacementSalt = 0xafce345db2397f51uL
        const val DecisionSalt = 0x5bf9adf3136e9bc3uL
    }
}

internal fun cellId(cell: GridCell) = TerrainMap.cellIndex(cell.x, cell.z)

private fun spacing(first: GridCell, second: GridCell) = abs(first.x - second.x) + abs(first.z - second.z)

internal fun TerrainMap.terrainCell(cell: GridCell) = checkNotNull(cellAt(cell.x, cell.z))

private fun TerrainMap.biome(cell: GridCell) = terrainCell(cell).biome
