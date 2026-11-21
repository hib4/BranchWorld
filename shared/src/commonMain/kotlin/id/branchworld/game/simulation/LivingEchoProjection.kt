package id.branchworld.game.simulation

import id.branchworld.game.ecology.EcologyState
import id.branchworld.game.ecology.RegionalWater
import id.branchworld.game.ecology.rainCovers
import id.branchworld.game.ecology.rooted
import id.branchworld.game.terrain.TerrainMap
import kotlin.math.abs

/** Locations where the two reconstructed histories differ at the same moment. */
data class LivingEchoProjection(val sites: List<EchoSite>)

enum class EchoKind { Bank, Field, Grove, Homes, WetGround }

data class EchoSite(
    val kind: EchoKind,
    val ground: GroundPoint,
    val here: Int,
    val other: Int,
)

internal fun livingEcho(terrain: TerrainMap, visible: EcologyState, alternate: EcologyState): LivingEchoProjection {
    val sites = buildList {
        fun changed(kind: EchoKind, cell: GridCell, here: Int, other: Int) {
            if (here != other) add(EchoSite(kind, cell.project(terrain), here, other))
        }

        fun grove(state: EcologyState) = state.patches.filter { it.planted }.associate {
            it.cell to if (it.rooted()) 2 else 1
        }
        val localGrove = grove(visible)
        val otherGrove = grove(alternate)
        for (cell in localGrove.keys + otherGrove.keys) {
            changed(EchoKind.Grove, cell, localGrove[cell] ?: 0, otherGrove[cell] ?: 0)
        }

        val localSettlements = visible.settlements?.sites.orEmpty().associateBy { it.center }
        val otherSettlements = alternate.settlements?.sites.orEmpty().associateBy { it.center }
        for (center in localSettlements.keys + otherSettlements.keys) {
            val local = localSettlements[center]
            val other = otherSettlements[center]
            val a = local?.welfare
            val b = other?.welfare
            val bank = a?.bank ?: b?.bank
            val field = local?.field ?: other?.field
            if (bank != null && (a?.water != b?.water || a?.unmet != b?.unmet || a?.bank != b?.bank)) {
                if (b != null && a != null && a.bank != b.bank) {
                    changed(EchoKind.Bank, a.bank, a.water + 1, 0)
                    changed(EchoKind.Bank, b.bank, 0, b.water + 1)
                } else {
                    val here = a?.let { it.water - it.unmet } ?: 0
                    val there = b?.let { it.water - it.unmet } ?: 0
                    changed(EchoKind.Bank, bank, here, there)
                }
            }
            if (field != null) changed(EchoKind.Field, field, a?.cropYield ?: 0, b?.cropYield ?: 0)
            val localHomes = local?.lots.orEmpty().take(local?.homes ?: 0)
            val otherHomes = other?.lots.orEmpty().take(other?.homes ?: 0)
            for (lot in (localHomes + otherHomes).distinct()) {
                changed(EchoKind.Homes, lot, if (lot in localHomes) 1 else 0, if (lot in otherHomes) 1 else 0)
            }
        }

        val localPools = visible.shaping?.pools.orEmpty()
        val otherPools = alternate.shaping?.pools.orEmpty()
        if (localPools != otherPools) {
            fun held(pools: List<RegionalWater>, cell: GridCell) =
                pools.filter { rainCovers(it.region, cell) }.maxOfOrNull { it.retained } ?: 0

            // Points along real wet banks, spaced to leave the rest of the landscape readable.
            val watershed = terrain.watershed
            if (watershed != null) for (z in TerrainMap.MinCell..TerrainMap.MaxCell step 2) {
                for (x in TerrainMap.MinCell..TerrainMap.MaxCell step 2) {
                    if (terrain.cellAt(x, z)?.walkable != true) continue
                    if (watershed.waterAt(x, z) == null && watershed.waterAt(x + 1, z) == null &&
                        watershed.waterAt(x - 1, z) == null && watershed.waterAt(x, z + 1) == null &&
                        watershed.waterAt(x, z - 1) == null) continue
                    val cell = GridCell(x, z)
                    val here = held(localPools, cell)
                    val there = held(otherPools, cell)
                    if (abs(here - there) >= 16) changed(EchoKind.WetGround, cell, here, there)
                }
            }
        }
    }
    return LivingEchoProjection(sites.sortedWith(compareBy({ it.kind }, { it.ground.z }, { it.ground.x })))
}
