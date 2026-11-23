package id.branchworld.simulation.ecology.environment

import id.branchworld.simulation.core.GridCell
import id.branchworld.simulation.ecology.ForagePatch
import id.branchworld.simulation.ecology.IslandEcology
import id.branchworld.simulation.ecology.RegionCount
import id.branchworld.simulation.ecology.RegionWidth
import id.branchworld.simulation.ecology.region
import id.branchworld.simulation.ecology.settlement.SettlementTurn
import id.branchworld.simulation.terrain.TerrainMap
import kotlinx.serialization.Serializable

internal const val OpeningDryEnd = 480L
internal const val ShowerLength = 120L
internal const val RefillRecharge = 96
internal const val LowBankStage = 32
private const val WaterLimit = 120

@Serializable
data class ShapingState(
    val spent: Boolean = false,
    val refilledSpell: Long = 0,
    val warningAt: Long? = null,
    val shower: CalledShower? = null,
    val pools: List<RegionalWater> = emptyList(),
) {
    fun advance(tick: Long, environment: Environment, soils: List<RainGround>): ShapingState {
        val shower = shower?.takeIf { tick - it.startedAt < ShowerLength }
        val refill = environment.weather == Weather.Rain && checkNotNull(environment.recharge) >= RefillRecharge &&
            environment.nextChange != refilledSpell
        val pools = if (tick % 10L != 0L || (shower == null && pools.isEmpty())) pools else buildList {
            for (region in 0 until RegionCount) {
                val held = pools.firstOrNull { it.region == region }?.retained ?: 0
                val water = if (shower?.region == region) (held + soils[region].infiltration).coerceAtMost(WaterLimit)
                    else (held - 2).coerceAtLeast(0)
                if (water > 0) add(RegionalWater(region, water))
            }
        }
        if (!refill && shower == this.shower && pools == this.pools) return this
        return copy(spent = if (refill) false else spent, refilledSpell = if (refill) environment.nextChange else refilledSpell,
            shower = shower, pools = pools)
    }

    fun validate(tick: Long, chapter: Boolean) {
        require(refilledSpell >= 0 && (refilledSpell <= tick || refilledSpell - tick <= 360))
        require(warningAt == null || chapter && warningAt in SettlementTurn..tick && warningAt % SettlementTurn == 0L)
        require(shower?.let { it.region in 0 until RegionCount && it.startedAt in 1..tick && tick - it.startedAt < ShowerLength } != false)
        require(pools.size <= RegionCount && pools.zipWithNext().all { (a, b) -> a.region < b.region })
        require(pools.all { it.region in 0 until RegionCount && it.retained in 1..WaterLimit })
    }
}

@Serializable
data class CalledShower(val region: Int, val startedAt: Long)

@Serializable
data class RegionalWater(val region: Int, val retained: Int)

data class RainGround(val available: Boolean, val infiltration: Int)
enum class ShapeBlock { AwaitWarning, Spent, AlreadyRaining, NoGround, NoSites, Reserved, AlreadyProtected, NoFuel }
data class ShapePreview(
    val sites: List<GridCell>,
    val field: GridCell?,
    val bank: GridCell?,
    val blocked: ShapeBlock?,
    /** Rules v11: the cells a firebreak would clear. */
    val cut: List<GridCell> = emptyList(),
)

internal fun rainCovers(region: Int, cell: GridCell): Boolean {
    val x = TerrainMap.MinCell + region % RegionWidth * 16
    val z = TerrainMap.MinCell + region / RegionWidth * 16
    return cell.x in x - 2..x + 17 && cell.z in z - 2..z + 17
}

internal fun rainGround(terrain: TerrainMap, region: Int): RainGround {
    val watershed = checkNotNull(terrain.watershed)
    var cells = 0
    var retention = 0
    val left = TerrainMap.MinCell + region % RegionWidth * 16
    val top = TerrainMap.MinCell + region / RegionWidth * 16
    for (z in top - 2..top + 17) for (x in left - 2..left + 17) {
        if (terrain.cellAt(x, z)?.walkable != true) continue
        cells++
        retention += watershed.retentionAt(x, z)
    }
    return RainGround(available = cells > 0, infiltration = if (cells == 0) 0 else 8 + retention / cells / 3)
}

internal fun ForagePatch.rooted() = planted && biomass >= rootBiomass()

internal fun ForagePatch.availableBrowse(protectRoots: Boolean): Int =
    if (protectRoots && planted) (biomass - rootBiomass()).coerceAtLeast(0) else biomass

private fun ForagePatch.rootBiomass() = (capacity + 1) / 2

internal class GrowingConditions(
    private val terrain: TerrainMap,
    private val base: Int,
    private val pools: List<RegionalWater>,
    patches: List<ForagePatch>,
) {
    private val roots = patches.filter(ForagePatch::rooted).mapTo(HashSet(), ForagePatch::cell)

    fun stage(cell: GridCell): Int {
        var held = 0
        for (pool in pools) {
            if (rainCovers(pool.region, cell)) held = maxOf(held, pool.retained)
        }
        return (base + held).coerceAtMost(255)
    }

    fun moisture(cell: GridCell): Int {
        val soil = terrain.soilMoisture(cell, stage(cell))
        if (roots.isEmpty()) return soil
        for (dz in -2..2) {
            val reach = 2 - kotlin.math.abs(dz)
            for (dx in -reach..reach) {
                if (GridCell(cell.x + dx, cell.z + dz) in roots) return (soil + 8).coerceAtMost(255)
            }
        }
        return soil
    }
}

internal fun IslandEcology.drinkingBank(center: GridCell, springs: List<GridCell>): GridCell? =
    nearest(terrainDistance(center), springs)
