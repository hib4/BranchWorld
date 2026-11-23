package id.branchworld.simulation.ecology.range

import id.branchworld.simulation.core.GridCell
import id.branchworld.simulation.ecology.ForagePatch
import id.branchworld.simulation.ecology.IslandEcology
import id.branchworld.simulation.ecology.cellId
import id.branchworld.simulation.ecology.settlement.SettlementWorld
import id.branchworld.simulation.terrain.LandmarkKind
import kotlin.math.abs

private const val MinimumRouteSteps = 24
private const val PastureRadius = 6
private const val CalvingRadius = 8
internal const val DisturbanceRadius = 1

/**
 * Rules v10 seasonal ranges, rebuilt from terrain alone. The dry-season range is the drinking bank nearest the basin.
 * The wet-season range is the forage that is close across the water but far to walk, so the herd's way between the two
 * runs around the water through whatever land joins them: the crossing.
 */
internal class SeasonalRanges(private val island: IslandEcology, springs: List<GridCell>, patches: List<ForagePatch>) {
    val dry: GridCell = run {
        val basin = island.terrain.watershed?.landmarks?.firstOrNull { it.kind == LandmarkKind.Basin }
            ?.let { GridCell(it.x, it.z) } ?: island.navigation.spawn
        springs.minWith(compareBy<GridCell> { spacing(it, basin) }.thenBy(::cellId))
    }

    val wet: GridCell = run {
        val fromDry = island.terrainDistance(dry)
        // The richest pasture a long walk from the dry-season water: lush in the rains, too far to graze in the dry.
        fun richness(cell: GridCell) = patches.sumOf { if (spacing(it.cell, cell) <= PastureRadius) it.capacity else 0 }
        patches.asSequence().map { it.cell }
            .filter { fromDry[cellId(it)] >= MinimumRouteSteps }
            .maxWithOrNull(compareBy<GridCell> { richness(it) }.thenBy { fromDry[cellId(it)] }.thenByDescending(::cellId))
            ?: patches.maxWith(compareBy<ForagePatch> { fromDry[it.id] }.thenByDescending { it.id }).cell
    }

    /** Cells of the shortest undisturbed way from the dry range to the wet range. */
    val route: List<GridCell> = buildList {
        val field = island.terrainDistance(wet)
        var cell = dry
        add(cell)
        while (cell != wet) {
            cell = island.navigation.step(cell, field)
            add(cell)
        }
    }

    private val routeCells = route.mapTo(HashSet()) { cellId(it) }

    fun anchor(bound: Int): GridCell = if (bound == 1) wet else dry

    fun onRoute(cell: GridCell) = cellId(cell) in routeCells

    fun calving(cell: GridCell) = spacing(cell, wet) <= CalvingRadius

    fun arrived(cell: GridCell, bound: Int) = spacing(cell, anchor(bound)) <= 3

    private var disturbedKey: Set<Int> = emptySet()
    private val avoiding = linkedMapOf<GridCell, IntArray>()

    /** A distance field that never enters disturbed cells, or null when [from] cannot reach [goal] that way. */
    fun avoidingField(goal: GridCell, from: GridCell, disturbed: Set<Int>): IntArray? {
        if (disturbed.isEmpty()) return null
        if (disturbed != disturbedKey) {
            disturbedKey = disturbed
            avoiding.clear()
        }
        val field = avoiding.getOrPut(goal) {
            if (avoiding.size >= 48) avoiding.remove(avoiding.keys.first())
            island.navigation.distancesAvoiding(goal, disturbed)
        }
        return field.takeIf { it[cellId(from)] >= 0 }
    }

    private fun spacing(a: GridCell, b: GridCell) = abs(a.x - b.x) + abs(a.z - b.z)
}

/** Cells deer avoid: every village structure and site centre, with a one-cell margin. */
internal fun SettlementWorld.disturbedCells(island: IslandEcology): Set<Int> {
    val village = village ?: return emptySet()
    val sources = village.structures.map { it.cell } + sites.filter { village.active(it.id).isNotEmpty() }.map { it.center }
    if (sources.isEmpty()) return emptySet()
    return buildSet {
        for (source in sources) for (dz in -DisturbanceRadius..DisturbanceRadius) for (dx in -DisturbanceRadius..DisturbanceRadius) {
            if (abs(dx) + abs(dz) > DisturbanceRadius) continue
            val cell = GridCell(source.x + dx, source.z + dz)
            if (island.navigation.contains(cell)) add(cellId(cell))
        }
    }
}
