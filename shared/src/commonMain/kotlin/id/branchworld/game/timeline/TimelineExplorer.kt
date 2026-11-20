package id.branchworld.game.timeline

import id.branchworld.game.ecology.EcologyState
import id.branchworld.game.ecology.ForageKind
import id.branchworld.game.ecology.Intervention
import id.branchworld.game.ecology.Species
import id.branchworld.game.ecology.canonicalHash
import id.branchworld.game.simulation.ReforestRegion
import id.branchworld.game.simulation.SupplementForage
import id.branchworld.game.simulation.WorldCommand
import id.branchworld.game.simulation.WorldRenderProjection
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
)

internal fun WorldCommand.marker(queued: Boolean): InterventionMarker? = when (this) {
    is ReforestRegion -> InterventionMarker(tick, sequence, Intervention.Reforest, region, queued)
    is SupplementForage -> InterventionMarker(tick, sequence, Intervention.Supplement, region, queued)
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
)

internal data class TimelinePair(val tick: Long, val left: ComparedBranch, val right: ComparedBranch)

internal data class ComparisonReplay(
    val left: ReplayRequest,
    val right: ReplayRequest,
    val leftEvents: List<InterventionMarker>,
    val rightEvents: List<InterventionMarker>,
) {
    suspend fun replay(): TimelinePair = TimelinePair(
        left.tick, left.read(leftEvents), right.read(rightEvents),
    )

    private suspend fun ReplayRequest.read(events: List<InterventionMarker>): ComparedBranch {
        val state = replay().engine.state
        return ComparedBranch(
            state.renderProjection(terrain).copy(branchId = branchId), state.canonicalHash(), state.metrics(), events,
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
