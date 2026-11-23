package id.branchworld.game.domain.session

import id.branchworld.simulation.core.ShapingRulesVersion
import id.branchworld.simulation.projection.InterventionMarker
import id.branchworld.simulation.projection.TimelinePair
import id.branchworld.simulation.projection.commonTicks
import id.branchworld.simulation.projection.inTreeOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.TimeSource

/** Two histories side by side at one moment: picking them, moving through their shared ticks, and returning. */
internal interface BranchComparing {
    fun openComparison()
    fun closeComparison()
    fun selectComparison(leftId: Int, rightId: Int)
    fun compareAt(tick: Long)
    fun retryComparison()

    /** Leaves the comparison and carries on from the side on view. */
    suspend fun continueComparison()

    /** Leaves the comparison for the last moment both histories shared. */
    suspend fun revisitSharedPast()
    fun toggleEcho()
    fun showComparisonSide(right: Boolean)
    suspend fun loadComparison(target: ComparisonTarget)
    fun inspectComparisonEvent(branchId: Int, marker: InterventionMarker)
    suspend fun loadComparisonEvent(event: ComparisonEvent)
}

internal class BranchComparisons(private val session: WorldSessionCore) : BranchComparing {
    private val timeline get() = session.timeline
    private val state get() = session.comparisonState
    private var revision = 0

    override fun openComparison() {
        val view = session.timelineState.value
        if (view.seeking || state.value != null) return
        val active = view.active
        val alternative = view.branches.inTreeOrder().firstOrNull { it.branch.id != active.id }?.branch ?: return
        session.clock.pause()
        val parentId = active.parentId
        if (parentId != null) selectComparison(parentId, active.id)
        else selectComparison(active.id, alternative.id)
    }

    override fun closeComparison() {
        state.value = null
        session.display.value = session.clock.render.value
    }

    override fun selectComparison(leftId: Int, rightId: Int) {
        if (leftId == rightId || session.timelineState.value.seeking) return
        val branches = session.timelineState.value.branches
        val left = branches.firstOrNull { it.id == leftId } ?: return
        val right = branches.firstOrNull { it.id == rightId } ?: return
        val range = commonTicks(left, right)
        val previous = state.value
        val rules = session.key.rules
        val current = previous?.tick ?: timeline.state.tick.takeIf { rules >= ShapingRulesVersion }
        state.value = ComparisonView(left, right, range,
            current?.takeIf { it in range } ?: range.last.takeUnless { range.isEmpty() },
            showRight = (previous?.visibleBranchId ?: timeline.branchId.takeIf { rules >= ShapingRulesVersion }) == rightId,
            echoEnabled = previous?.echoEnabled ?: true,
            sharedPast = timeline.sharedPast(leftId, rightId),
            moments = timeline.comparisonMoments(leftId, rightId, range),
            revision = ++revision)
    }

    override fun compareAt(tick: Long) {
        val prior = state.value ?: return
        if (tick !in prior.range || tick == prior.tick || session.timelineState.value.seeking) return
        state.value = prior.copy(tick = tick, pair = null, failed = false, event = null, outcomes = null,
            outcomeFailed = false, revision = ++revision)
    }

    override fun retryComparison() {
        val prior = state.value?.takeIf { it.failed } ?: return
        state.value = prior.copy(failed = false, revision = ++revision)
    }

    override suspend fun continueComparison() {
        val view = state.value ?: return
        val pair = view.pair ?: return
        session.navigate(view.visibleBranchId, pair.tick, fromComparison = view)
    }

    override suspend fun revisitSharedPast() {
        val view = state.value ?: return
        val shared = view.sharedPast ?: return
        session.navigate(shared.branchId, shared.tick, fromComparison = view)
    }

    override fun toggleEcho() {
        val prior = state.value?.takeUnless { session.timelineState.value.seeking } ?: return
        val next = prior.copy(echoEnabled = !prior.echoEnabled)
        state.value = next
        next.pair?.let { showPair(next, it) }
    }

    override fun showComparisonSide(right: Boolean) {
        val prior = state.value?.takeUnless { session.timelineState.value.seeking } ?: return
        val next = prior.copy(showRight = right)
        state.value = next
        next.pair?.let { showPair(next, it) }
    }

    private fun showPair(view: ComparisonView, pair: TimelinePair) {
        val pose = if (view.showRight) pair.right.projection else pair.left.projection
        session.display.value = if (view.echoEnabled) pose else pose.copy(livingEcho = null)
    }

    override suspend fun loadComparison(target: ComparisonTarget) {
        val prior = state.value
        if (prior?.target != target || prior.pair != null) return
        val request = timeline.compare(target.leftId, target.rightId, target.tick) ?: return
        try {
            val started = TimeSource.Monotonic.markNow()
            val pair = withContext(Dispatchers.Default) { request.replay() }
            val current = state.value?.takeIf { it.target == target } ?: return
            showPair(current, pair)
            state.value = current.copy(pair = pair)
            println("BranchWorldComparison tick=${pair.tick} left=${pair.left.hash} right=${pair.right.hash} compareMs=${started.elapsedNow().inWholeMilliseconds}")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            println("BranchWorldComparison failed: ${error::class.simpleName}")
            state.value?.takeIf { it.target == target }?.let { state.value = it.copy(failed = true) }
        }
    }

    override fun inspectComparisonEvent(branchId: Int, marker: InterventionMarker) {
        val current = state.value ?: return
        if (session.timelineState.value.seeking) return
        val event = ComparisonEvent(branchId, marker.tick, marker.sequence, current.revision)
        if (current.event != event) {
            state.value = current.copy(event = event, outcomes = null, outcomeFailed = false)
        }
    }

    override suspend fun loadComparisonEvent(event: ComparisonEvent) {
        val prior = state.value
        if (prior?.event != event || prior.outcomes != null) return
        val request = timeline.eventReplay(event.branchId, event.tick) ?: return
        val index = timeline.interventionMarkers(event.branchId, event.tick)
            .filter { it.tick == event.tick }.indexOfFirst { it.sequence == event.sequence }
        try {
            val outcomes = withContext(Dispatchers.Default) {
                request.replay().engine.run {
                    advance()
                    listOfNotNull(interventionResults.getOrNull(index))
                }
            }
            val current = state.value?.takeIf { it.event == event } ?: return
            state.value = current.copy(outcomes = outcomes)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            println("BranchWorldComparison event failed: ${error::class.simpleName}")
            state.value?.takeIf { it.event == event }?.let { state.value = it.copy(outcomeFailed = true) }
        }
    }
}
