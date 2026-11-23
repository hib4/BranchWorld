package id.branchworld.simulation.ecology.settlement

import id.branchworld.simulation.core.GridCell
import id.branchworld.simulation.ecology.environment.LowBankStage
import id.branchworld.simulation.ecology.settlement.SettlementTurn
import id.branchworld.simulation.ecology.settlement.SettlementWorld
import id.branchworld.simulation.ecology.settlement.StockLimit
import id.branchworld.simulation.ecology.settlement.VillageState
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

const val SettlementTurn = 60L
internal const val HomeCapacity = 4
internal const val MaximumHomes = 4
internal const val StockLimit = 256

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class SettlementWorld(
    val sites: List<Settlement> = emptyList(),
    val events: List<SettlementEvent> = emptyList(),
    val gatheredFood: Long = 0,
    val gatheredWood: Long = 0,
    // Omitted before rules v9 so earlier saves keep their exact integrity bytes.
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val village: VillageState? = null,
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class Settlement(
    val id: Int,
    val foundedAt: Long,
    val center: GridCell,
    val field: GridCell,
    val lots: List<GridCell>,
    val residents: Int = 4,
    val homes: Int = 1,
    val food: Int = 16,
    val wood: Int = 0,
    val stone: Int = 0,
    val stoneRemaining: Int,
    val shortage: Int = 0,
    val surplus: Int = 0,
    val labor: SettlementLabor = SettlementLabor(),
    val foodBalance: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val welfare: SettlementWelfare? = null,
)

@Serializable
data class SettlementWelfare(
    val bank: GridCell,
    val water: Int,
    val unmet: Int = 0,
    val cropYield: Int = 0,
    val firstShortage: Long? = null,
    val firstRecovery: Long? = null,
) {
    fun collect(tick: Long, residents: Int, stage: Int, extra: Int = 0): SettlementWelfare {
        val demand = residents * 2
        val supply = residents * (if (stage >= LowBankStage) 3 else 1) + extra
        val balance = water + supply - demand
        return copy(
            water = balance.coerceIn(0, demand * 2),
            unmet = (-balance).coerceAtLeast(0),
            firstShortage = firstShortage ?: tick.takeIf { balance < 0 },
            firstRecovery = firstRecovery ?: tick.takeIf { firstShortage != null && balance >= 0 },
        )
    }
}

@Serializable
data class SettlementLabor(val farm: Int = 0, val wood: Int = 0, val stone: Int = 0)

@Serializable
enum class SettlementEventKind(internal val code: Int) {
    Founded(1), Shortage(2), Expanded(3), Relocated(4), Abandoned(5),
}

@Serializable
data class SettlementEvent(
    val tick: Long,
    val siteId: Int,
    val kind: SettlementEventKind,
    val amount: Int = 0,
)

internal fun SettlementWorld.reservedCells(): List<GridCell> {
    val base = sites.flatMap { listOf(it.center, it.field) + it.lots }
    val extra = village?.structures?.map { it.cell }?.filter { it !in base }.orEmpty()
    return if (extra.isEmpty()) base else base + extra.distinct()
}

/** Timber from felled pines goes to the first lived-in settlement's stores. */
internal fun SettlementWorld.giveWood(amount: Int): SettlementWorld {
    if (amount <= 0) return this
    val index = sites.indexOfFirst { it.residents > 0 }.takeIf { it >= 0 } ?: return this
    return copy(sites = sites.mapIndexed { i, site -> if (i == index) site.copy(wood = minOf(StockLimit, site.wood + amount)) else site })
}
