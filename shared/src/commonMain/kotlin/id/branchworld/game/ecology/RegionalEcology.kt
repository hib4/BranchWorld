package id.branchworld.game.ecology

import id.branchworld.game.simulation.GridCell
import id.branchworld.game.terrain.Biome
import id.branchworld.game.terrain.TerrainMap
import kotlin.math.abs

internal const val RegionWidth = 4
internal const val RegionCount = RegionWidth * RegionWidth

internal fun GridCell.region(): Int =
    (z - TerrainMap.MinCell) / 16 * RegionWidth + (x - TerrainMap.MinCell) / 16

internal enum class Intervention { Reforest, Supplement }

internal data class InterventionResult(val kind: Intervention, val region: Int, val tick: Long, val amount: Int)

internal data class RegionReadout(val forage: Int, val saplings: Int, val canPlant: Boolean, val canSupplement: Boolean)

internal class RegionalEcology(private val island: IslandEcology) {
    private val sites = island.reachable.filter {
        island.terrain.terrainCell(it).biome in PlantingBiomes
    }.groupBy(GridCell::region)

    fun inspect(state: EcologyState): List<RegionReadout> {
        val patches = state.patches.groupBy { it.cell.region() }
        val occupied = state.patches.mapTo(state.springs.toMutableSet()) { it.cell }
        return List(RegionCount) { region ->
            val local = patches[region].orEmpty()
            RegionReadout(
                local.sumOf { it.biomass },
                local.count { it.planted },
                sites[region].orEmpty().any { eligible(it, occupied) },
                local.any { it.biomass < it.capacity },
            )
        }
    }

    fun plant(region: Int, patches: MutableList<ForagePatch>, springs: List<GridCell>): Int {
        val occupied = patches.mapTo(springs.toMutableSet()) { it.cell }
        var planted = 0
        for (cell in sites[region].orEmpty()) {
            if (planted == PlantingLimit) break
            if (!eligible(cell, occupied)) continue
            val moisture = island.terrain.terrainCell(cell).moisture
            patches.add(ForagePatch(cellId(cell), cell, ForageKind.Browse, 160 + moisture / 3,
                1 + moisture / 96, SaplingBiomass, planted = true))
            occupied.add(cell)
            planted++
        }
        if (planted > 0) patches.sortBy(ForagePatch::id)
        return planted
    }

    fun supplement(region: Int, patches: MutableList<ForagePatch>): Int {
        val depleted = patches.indices
            .filter { patches[it].cell.region() == region && patches[it].biomass < patches[it].capacity }
            .sortedWith(compareByDescending<Int> { patches[it].capacity - patches[it].biomass }.thenBy { patches[it].id })
        var remaining = 120
        for (index in depleted) {
            if (remaining == 0) break
            val patch = patches[index]
            val added = minOf(remaining, patch.capacity - patch.biomass)
            patches[index] = patch.copy(biomass = patch.biomass + added)
            remaining -= added
        }
        return 120 - remaining
    }

    fun validSapling(patch: ForagePatch, springs: List<GridCell>): Boolean {
        val ground = island.terrain.terrainCell(patch.cell)
        return patch.kind == ForageKind.Browse && ground.biome in PlantingBiomes && patch.cell !in springs &&
            patch.capacity == 160 + ground.moisture / 3 && patch.regrowth == 1 + ground.moisture / 96
    }

    private fun eligible(cell: GridCell, occupied: Set<GridCell>): Boolean {
        for (dz in -2..2) {
            val reach = 2 - abs(dz)
            for (dx in -reach..reach) {
                if (GridCell(cell.x + dx, cell.z + dz) in occupied) return false
            }
        }
        return true
    }

    companion object {
        const val SaplingBiomass = 30
        private const val PlantingLimit = 6
        private val PlantingBiomes = setOf(Biome.Meadow, Biome.PineForest)
    }
}
