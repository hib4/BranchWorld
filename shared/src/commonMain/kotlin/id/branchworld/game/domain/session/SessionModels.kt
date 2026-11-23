package id.branchworld.game.domain.session

import id.branchworld.simulation.ecology.InterventionResult
import id.branchworld.simulation.ecology.life.PlacementCheck
import id.branchworld.simulation.projection.GroundPoint
import id.branchworld.simulation.projection.HistoryMoment
import id.branchworld.simulation.projection.SharedPast
import id.branchworld.simulation.projection.TimelinePair
import id.branchworld.simulation.timeline.BranchSummary

/** What came of the player's hand in a living world. */
internal sealed interface PlaceOutcome {
    data object Placed : PlaceOutcome
    data class Rejected(val check: PlacementCheck) : PlaceOutcome
    /** The player is looking at the past; a new history must begin there first. */
    data object ViewingPast : PlaceOutcome
    data object Busy : PlaceOutcome
}

internal data class TimelineView(
    val branchId: Int,
    val tick: Long,
    val branches: List<BranchSummary>,
    val editsAllowed: Boolean,
    val canFork: Boolean,
    val commandLimitReached: Boolean,
    val seeking: Boolean,
    val failed: Boolean,
) {
    val active get() = branches.first { it.id == branchId }
}

internal data class ShapeTarget(
    val sites: List<GroundPoint>, val field: GroundPoint?, val bank: GroundPoint?,
    val blocked: id.branchworld.simulation.ecology.environment.ShapeBlock?,
    /** Rules v11: marks along the firebreak line and its length in stretches (2-cell tiles). */
    val cutMarks: List<GroundPoint> = emptyList(),
    val cut: Int = 0,
) {
    val highlights get() = sites + listOfNotNull(field, bank) + cutMarks
}

internal data class InterventionFeedback(val queued: Int, val applied: List<InterventionResult> = emptyList())

internal data class ComparisonTarget(val leftId: Int, val rightId: Int, val tick: Long, val revision: Int)
internal data class ComparisonEvent(val branchId: Int, val tick: Long, val sequence: Long, val revision: Int)

internal data class ComparisonView(
    val left: BranchSummary,
    val right: BranchSummary,
    val range: LongRange,
    val tick: Long?,
    val showRight: Boolean = false,
    val pair: TimelinePair? = null,
    val failed: Boolean = false,
    val event: ComparisonEvent? = null,
    val outcomes: List<InterventionResult>? = null,
    val outcomeFailed: Boolean = false,
    val echoEnabled: Boolean = true,
    val sharedPast: SharedPast? = null,
    val moments: List<HistoryMoment> = emptyList(),
    val revision: Int,
) {
    val target get() = tick?.let { ComparisonTarget(left.id, right.id, it, revision) }
    val visibleBranchId get() = if (showRight) right.id else left.id
}
