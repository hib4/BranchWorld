package id.branchworld.game.timeline

import id.branchworld.game.ecology.EcologyState
import id.branchworld.game.ecology.ForageKind
import id.branchworld.game.ecology.Intervention
import id.branchworld.game.ecology.Species
import id.branchworld.game.ecology.SettlementEvent
import id.branchworld.game.ecology.VillageState
import id.branchworld.game.ecology.canonicalHash
import id.branchworld.game.ecology.PlanOption
import id.branchworld.game.simulation.CallRainRegion
import id.branchworld.game.simulation.EndorsePlan
import id.branchworld.game.simulation.ReforestRegion
import id.branchworld.game.simulation.SupplementForage
import id.branchworld.game.simulation.WorldCommand
import id.branchworld.game.simulation.WorldRenderProjection
import id.branchworld.game.simulation.livingEcho
import id.branchworld.game.simulation.renderProjection

internal const val RecentInterventions = 5

internal data class BranchTreeEntry(val branch: BranchSummary, val depth: Int)

internal fun List<BranchSummary>.inTreeOrder(): List<BranchTreeEntry> {
    val children = sortedBy(BranchSummary::id).groupBy(BranchSummary::parentId)
    return buildList {
        fun append(parent: Int?, depth: Int) {
            for (branch in children[parent].orEmpty()) {
                add(BranchTreeEntry(branch, depth))
                append(branch.id, depth + 1)
            }
        }
        append(null, 0)
    }
}

internal fun commonTicks(left: BranchSummary, right: BranchSummary): LongRange =
    maxOf(left.start, right.start)..minOf(left.head, right.head)

internal fun LongRange.tickAt(fraction: Float): Long = when {
    fraction <= 0f -> first
    fraction >= 1f -> last
    else -> first + ((last - first).toDouble() * fraction).toLong().coerceIn(0L, last - first)
}

internal fun LongRange.fractionAt(tick: Long): Float =
    if (first == last) 0f else ((tick.coerceIn(this) - first).toDouble() / (last - first)).toFloat()

internal data class InterventionMarker(
    val tick: Long,
    val sequence: Long,
    val kind: Intervention,
    val region: Int,
    val queued: Boolean,
    val plan: PlanOption? = null,
)

internal data class OwnedIntervention(val branchId: Int, val marker: InterventionMarker)

internal data class SharedPast(val branchId: Int, val tick: Long)

internal data class HistoryMoment(val tick: Long, val kind: Kind, val intervention: OwnedIntervention? = null) {
    enum class Kind { Fork, Warning, Intervention, Shortage, Recovery, AfterRain, Petition, Departure }
}

internal fun WorldCommand.marker(queued: Boolean): InterventionMarker? = when (this) {
    is CallRainRegion -> InterventionMarker(tick, sequence, Intervention.Rain, region, queued)
    is ReforestRegion -> InterventionMarker(tick, sequence, Intervention.Reforest, region, queued)
    is SupplementForage -> InterventionMarker(tick, sequence, Intervention.Supplement, region, queued)
    is EndorsePlan -> InterventionMarker(tick, sequence, Intervention.Plan, -1, queued, option)
    else -> null
}

internal data class EcologyMetrics(
    val saplings: Long,
    val browse: Long,
    val forage: Long,
    val rabbits: Long,
    val deer: Long,
    val births: Long,
    val deaths: Long,
) {
    fun values() = listOf(saplings, browse, forage, rabbits, deer, births, deaths)
}

internal data class ComparedBranch(
    val projection: WorldRenderProjection,
    val hash: String,
    val metrics: EcologyMetrics,
    val events: List<InterventionMarker>,
    val settlement: SettlementMetrics?,
    val settlementEvents: List<SettlementEvent>,
    val village: VillageState? = null,
)

internal data class SettlementMetrics(val residents: Long, val food: Long, val homes: Long, val wood: Long)

internal data class TimelinePair(
    val tick: Long,
    val left: ComparedBranch,
    val right: ComparedBranch,
    val leftTrail: List<OwnedIntervention>,
    val rightTrail: List<OwnedIntervention>,
    val sharedTrail: List<OwnedIntervention>,
)

internal data class ComparisonReplay(
    val left: ReplayRequest,
    val right: ReplayRequest,
    val leftEvents: List<OwnedIntervention>,
    val rightEvents: List<OwnedIntervention>,
) {
    suspend fun replay(): TimelinePair {
        val a = left.replay().engine.state
        val b = right.replay().engine.state
        val shared = leftEvents.intersect(rightEvents.toSet())
        val echo = livingEcho(left.terrain, a, b)
        return TimelinePair(left.tick,
            left.read(a, leftEvents).let { it.copy(projection = it.projection.copy(livingEcho = echo)) },
            right.read(b, rightEvents).let { side ->
                side.copy(projection = side.projection.copy(livingEcho = echo.copy(sites = echo.sites.map {
                    it.copy(here = it.other, other = it.here)
                })))
            },
            leftEvents.filterNot { it in shared }, rightEvents.filterNot { it in shared }, shared.toList())
    }

    private fun ReplayRequest.read(state: EcologyState, events: List<OwnedIntervention>): ComparedBranch {
        val occupied = state.settlements?.sites?.filter { it.residents > 0 }
        return ComparedBranch(
            state.renderProjection(terrain).copy(branchId = branchId), state.canonicalHash(), state.metrics(),
            events.asSequence().filter { it.branchId == branchId }.toList()
                .takeLast(RecentInterventions).asReversed().map { it.marker },
            occupied?.let { SettlementMetrics(it.sumOf { site -> site.residents.toLong() },
                it.sumOf { site -> site.food.toLong() }, it.sumOf { site -> site.homes.toLong() },
                it.sumOf { site -> site.wood.toLong() }) },
            state.settlements?.events.orEmpty(),
            state.settlements?.village,
        )
    }
}

internal fun ReplayRequest.throughTarget() = copy(inputs = inputs.takeWhile { it.tick <= tick })

private fun EcologyState.metrics() = EcologyMetrics(
    saplings = patches.count { it.planted }.toLong(),
    browse = patches.sumOf { if (it.kind == ForageKind.Browse) it.biomass.toLong() else 0L },
    forage = patches.sumOf { it.biomass.toLong() },
    rabbits = creatures.count { it.species == Species.Rabbit }.toLong(),
    deer = creatures.count { it.species == Species.Deer }.toLong(),
    births = ledger.births,
    deaths = ledger.deaths,
)
