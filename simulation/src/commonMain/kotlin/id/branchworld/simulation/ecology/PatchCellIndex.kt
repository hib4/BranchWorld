package id.branchworld.simulation.ecology

import id.branchworld.simulation.core.GridCell
import id.branchworld.simulation.terrain.TerrainMap

/** Patches have unique cell IDs. Biomass changes in place, while planting and clearing can change the layout. */
internal class PatchCellIndex {
    private val positions = IntArray(TerrainMap.Size * TerrainMap.Size) { -1 }

    fun rebuild(patches: List<ForagePatch>): IntArray {
        positions.fill(-1)
        for ((index, patch) in patches.withIndex()) positions[patch.id] = index
        return positions
    }

    operator fun get(cell: GridCell): Int = positions[cellId(cell)]
}
