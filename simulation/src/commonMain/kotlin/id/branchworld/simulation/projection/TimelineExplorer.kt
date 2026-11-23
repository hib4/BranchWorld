package id.branchworld.simulation.projection

import id.branchworld.simulation.command.CallRainRegion
import id.branchworld.simulation.command.EndorsePlan
import id.branchworld.simulation.command.ProtectRegion
import id.branchworld.simulation.command.ReforestRegion
import id.branchworld.simulation.command.SupplementForage
import id.branchworld.simulation.command.WorldCommand
import id.branchworld.simulation.core.canonicalHash
import id.branchworld.simulation.ecology.EcologyState
import id.branchworld.simulation.ecology.ForageKind
import id.branchworld.simulation.ecology.Intervention
import id.branchworld.simulation.ecology.Species
import id.branchworld.simulation.ecology.canonicalHash
import id.branchworld.simulation.ecology.settlement.PlanOption
import id.branchworld.simulation.ecology.settlement.SettlementEvent
import id.branchworld.simulation.ecology.settlement.VillageState
import id.branchworld.simulation.legacy.canonicalHash
import id.branchworld.simulation.timeline.BranchSummary
import id.branchworld.simulation.timeline.ReplayRequest

const val RecentInterventions = 5

data class BranchTreeEntry(val branch: BranchSummary, val depth: Int)

fun List<BranchSummary>.inTreeOrder(): List<BranchTreeEntry> {
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

fun commonTicks(left: BranchSummary, right: BranchSummary): LongRange =
    maxOf(left.start, right.start)..minOf(left.head, right.head)

fun LongRange.tickAt(fraction: Float): Long = when {
    fraction <= 0f -> first
    fraction >= 1f -> last
    else -> first + ((last - first).toDouble() * fraction).toLong().coerceIn(0L, last - first)
}

fun LongRange.fractionAt(tick: Long): Float =
    if (first == last) 0f else ((tick.coerceIn(this) - first).toDouble() / (last - first)).toFloat()

data class InterventionMarker(
    val tick: Long,
    val sequence: Long,
    val kind: Intervention,
    val region: Int,
    val queued: Boolean,
    val plan: PlanOption? = null,
)

data class OwnedIntervention(val branchId: Int, val marker: InterventionMarker)

data class SharedPast(val branchId: Int, val tick: Long)

data class HistoryMoment(val tick: Long, val kind: Kind, val intervention: OwnedIntervention? = null) {
    enum class Kind { Fork, Warning, Intervention, Shortage, Recovery, AfterRain, Petition, Departure }
}

internal fun WorldCommand.marker(queued: Boolean): InterventionMarker? = when (this) {
    is CallRainRegion -> InterventionMarker(tick, sequence, Intervention.Rain, region, queued)
    is ReforestRegion -> InterventionMarker(tick, sequence, Intervention.Reforest, region, queued)
    is SupplementForage -> InterventionMarker(tick, sequence, Intervention.Supplement, region, queued)
    is EndorsePlan -> InterventionMarker(tick, sequence, Intervention.Plan, -1, queued, option)
    is ProtectRegion -> InterventionMarker(tick, sequence, Intervention.Protect, region, queued)
    is id.branchworld.simulation.command.CutFirebreak -> InterventionMarker(tick, sequence, Intervention.Firebreak, region, queued)
    else -> null
}

data class EcologyMetrics(
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

data class ComparedBranch(
    val projection: WorldRenderProjection,
    val hash: String,
    val metrics: EcologyMetrics,
    val events: List<InterventionMarker>,
    val settlement: SettlementMetrics?,
    val settlementEvents: List<SettlementEvent>,
    val village: VillageState? = null,
)

data class SettlementMetrics(val residents: Long, val food: Long, val homes: Long, val wood: Long)

data class TimelinePair(
    val tick: Long,
    val left: ComparedBranch,
    val right: ComparedBranch,
    val leftTrail: List<OwnedIntervention>,
    val rightTrail: List<OwnedIntervention>,
    val sharedTrail: List<OwnedIntervention>,
)

data class ComparisonReplay(
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
