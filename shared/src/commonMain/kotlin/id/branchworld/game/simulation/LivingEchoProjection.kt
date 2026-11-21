package id.branchworld.game.simulation

import id.branchworld.game.ecology.EcologyState
import id.branchworld.game.ecology.RegionalWater
import id.branchworld.game.ecology.Structure
import id.branchworld.game.ecology.StructureKind
import id.branchworld.game.ecology.VillageState
import id.branchworld.game.ecology.rainCovers
import id.branchworld.game.ecology.rooted
import id.branchworld.game.terrain.TerrainMap
import kotlin.math.abs

/** Locations where the two reconstructed histories differ at the same moment. */
data class LivingEchoProjection(val sites: List<EchoSite>)

/** Homes, Well and Clearing values: 0 absent, 1 under construction, 2 standing, 3 enlarged. Household: members at home. */
enum class EchoKind { Bank, Field, Grove, Homes, WetGround, Well, Clearing, Household }

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
            if (visible.settlements?.village == null && alternate.settlements?.village == null) {
                val localHomes = local?.lots.orEmpty().take(local?.homes ?: 0)
                val otherHomes = other?.lots.orEmpty().take(other?.homes ?: 0)
                for (lot in (localHomes + otherHomes).distinct()) {
                    changed(EchoKind.Homes, lot, if (lot in localHomes) 1 else 0, if (lot in otherHomes) 1 else 0)
                }
            }
        }
        val localVillage = visible.settlements?.village
        val otherVillage = alternate.settlements?.village
        if (localVillage != null || otherVillage != null) {
            fun built(village: VillageState?) = village?.structures.orEmpty()
                .filter { it.kind != StructureKind.Field }
                .associate { (it.kind to it.cell) to it.echoStage() }
            val localBuilt = built(localVillage)
            val otherBuilt = built(otherVillage)
            for (key in (localBuilt.keys + otherBuilt.keys).distinct()) {
                val kind = when (key.first) {
                    StructureKind.Home -> EchoKind.Homes
                    StructureKind.Well -> EchoKind.Well
                    StructureKind.Clearing, StructureKind.Field -> EchoKind.Clearing
                }
                changed(kind, key.second, localBuilt[key] ?: 0, otherBuilt[key] ?: 0)
            }
            fun families(village: VillageState?): Map<GridCell, Int> {
                val homes = village?.structures.orEmpty().associateBy { it.id }
                return village?.households.orEmpty().filter { it.active }
                    .mapNotNull { household -> homes[household.home]?.let { it.cell to household.members } }.toMap()
            }
            val localFamilies = families(localVillage)
            val otherFamilies = families(otherVillage)
            for (cell in (localFamilies.keys + otherFamilies.keys).distinct()) {
                changed(EchoKind.Household, cell, localFamilies[cell] ?: 0, otherFamilies[cell] ?: 0)
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

private fun Structure.echoStage() = when {
    !complete && level == 1 -> 1
    complete && level >= 2 -> 3
    else -> 2
}
