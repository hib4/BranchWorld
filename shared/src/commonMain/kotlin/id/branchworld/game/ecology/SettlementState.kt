package id.branchworld.game.ecology

import id.branchworld.game.simulation.GridCell
import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi

internal const val CivilizationRulesVersion = 5
internal const val SettlementTurn = 60L
internal const val HomeCapacity = 4
internal const val MaximumHomes = 4
internal const val StockLimit = 256

@Serializable
internal data class SettlementWorld(
    val sites: List<Settlement> = emptyList(),
    val events: List<SettlementEvent> = emptyList(),
    val gatheredFood: Long = 0,
    val gatheredWood: Long = 0,
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
internal data class Settlement(
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
internal data class SettlementWelfare(
    val bank: GridCell,
    val water: Int,
    val unmet: Int = 0,
    val cropYield: Int = 0,
    val firstShortage: Long? = null,
    val firstRecovery: Long? = null,
) {
    fun collect(tick: Long, residents: Int, stage: Int): SettlementWelfare {
        val demand = residents * 2
        val supply = residents * if (stage >= LowBankStage) 3 else 1
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
internal data class SettlementLabor(val farm: Int = 0, val wood: Int = 0, val stone: Int = 0)

@Serializable
internal enum class SettlementEventKind(internal val code: Int) {
    Founded(1), Shortage(2), Expanded(3), Relocated(4), Abandoned(5),
}

@Serializable
internal data class SettlementEvent(
    val tick: Long,
    val siteId: Int,
    val kind: SettlementEventKind,
    val amount: Int = 0,
)

internal fun SettlementWorld.reservedCells(): List<GridCell> =
    sites.flatMap { listOf(it.center, it.field) + it.lots }
