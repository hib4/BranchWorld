package id.branchworld.game.domain.session

import id.branchworld.game.domain.IslandSessionKey
import id.branchworld.simulation.core.RangeRulesVersion
import id.branchworld.simulation.ecology.EcologyState
import id.branchworld.simulation.ecology.canonicalHash
import id.branchworld.simulation.projection.WorldRenderProjection
import id.branchworld.simulation.projection.WorldRenderSource
import id.branchworld.simulation.projection.project
import id.branchworld.simulation.projection.renderProjection
import id.branchworld.simulation.runtime.ClockSimulation
import id.branchworld.simulation.runtime.WorldClock
import id.branchworld.simulation.save.WorldCode
import id.branchworld.simulation.terrain.TerrainMap
import id.branchworld.simulation.timeline.Timeline
import id.branchworld.simulation.timeline.TimelineCheckpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.time.TimeSource

/** An open world as the interface sees it: who it is, its clock, and what the view and panels show. */
internal interface OpenWorld {
    val key: IslandSessionKey
    val sharedOrigin: String?
    val clock: WorldClock<EcologyState>
    val world: WorldRenderSource
    val interventions: StateFlow<InterventionFeedback>
    val timelineView: StateFlow<TimelineView>
    val comparison: StateFlow<ComparisonView?>

    fun checkpoint(): TimelineCheckpoint
}

/**
 * The state one open world shares between its roles (placing, navigating history, comparing branches and the
 * earlier chapters): the [Timeline], the clock that advances it, and the flows the interface observes.
 */
internal class WorldSessionCore(
    terrain: TerrainMap,
    val timeline: Timeline,
    override val sharedOrigin: String?,
    worldSlot: String?,
) : OpenWorld {
    override val key = IslandSessionKey(terrain.key, timeline.state.rulesVersion,
        timeline.chapter?.takeIf { timeline.state.rulesVersion >= RangeRulesVersion }, sharedOrigin?.let(WorldCode::slotForOrigin),
        worldSlot.takeIf { sharedOrigin == null }, timeline.state.life?.start)
    val feedback = MutableStateFlow(InterventionFeedback(queued = timeline.queuedInterventions))
    override val interventions = feedback.asStateFlow()
    val timelineState = MutableStateFlow(timelineStatus())
    override val timelineView = timelineState.asStateFlow()
    val comparisonState = MutableStateFlow<ComparisonView?>(null)
    override val comparison = comparisonState.asStateFlow()

    /** History can be changed or moved through only while no replay is running and no comparison is open. */
    val navigationAvailable get() = !timelineState.value.seeking && comparisonState.value == null

    /** What the 3D view shows: the live world, or one side of a comparison. */
    val display = MutableStateFlow(timeline.state.renderProjection(terrain).copy(branchId = timeline.branchId))
    private var liveFrame = false
    override val clock = WorldClock(object : ClockSimulation<EcologyState> {
        override val state get() = timeline.state
        override val tick get() = state.tick
        override val seed get() = terrain.key.seed
        override fun canSteer(left: Boolean) = navigationAvailable && timeline.canSteer(left)
        override fun steer(left: Boolean) {
            if (navigationAvailable) timeline.steer(left)
            updateTimeline()
        }
        override fun advance() { liveFrame = advanceTimeline() }
        override fun project(previous: WorldRenderProjection?) =
            state.renderProjection(terrain).copy(branchId = timeline.branchId, interpolateCreatures = liveFrame).also {
                liveFrame = false
                if (comparisonState.value == null) display.value = it
            }
    })
    override val world = WorldRenderSource(terrain, display.asStateFlow(), timeline.herdRoute().map { it.project(terrain) })

    override fun checkpoint() = timeline.checkpoint()

    /** After a queued intervention changes: report the queue and refresh the timeline. */
    fun queueChanged() {
        feedback.value = feedback.value.copy(queued = timeline.queuedInterventions)
        updateTimeline()
    }

    private fun advanceTimeline(): Boolean {
        if (!timeline.advance()) {
            clock.pause()
            timelineState.value = timelineStatus(failed = true)
            feedback.value = InterventionFeedback(timeline.queuedInterventions)
            return false
        }
        // A new petition is a turning point: hold time so the Keeper can answer it.
        if (timeline.openPetition()?.raisedAt == timeline.state.tick) clock.pause()
        val queued = timeline.queuedInterventions
        val applied = timeline.interventionResults
        if (applied.isNotEmpty() || queued != feedback.value.queued) {
            feedback.value = InterventionFeedback(queued, applied.ifEmpty { feedback.value.applied })
        }
        updateTimeline()
        return true
    }

    /**
     * Replays to [tick] on [branch] off the main thread and shows it. With [inspectEvent] the replay stops just before
     * the commands at [tick] and applies them, so their results can be read. A replay started from a comparison is
     * dropped if that comparison has changed meanwhile.
     */
    suspend fun navigate(branch: Int, tick: Long, inspectEvent: Boolean = false, fromComparison: ComparisonView? = null) {
        if (fromComparison == null && !navigationAvailable || timelineState.value.seeking) return
        val request = (if (inspectEvent) timeline.eventReplay(branch, tick) else timeline.seek(branch, tick)) ?: return
        clock.pause()
        timelineState.value = timelineStatus(seeking = true)
        val started = TimeSource.Monotonic.markNow()
        try {
            val replayed = withContext(Dispatchers.Default) {
                request.replay().also { if (inspectEvent) it.engine.advance() }
            }
            if (fromComparison != null && comparisonState.value !== fromComparison) {
                timelineState.value = timelineStatus()
                return
            }
            timeline.show(replayed)
            if (fromComparison != null) comparisonState.value = null
            publishNavigation()
            if (inspectEvent) {
                feedback.value = feedback.value.copy(applied = replayed.engine.interventionResults)
            }
            println("BranchWorldTimeline seek branch=$branch tick=$tick hash=${timeline.state.canonicalHash()} seekMs=${started.elapsedNow().inWholeMilliseconds}")
        } catch (error: CancellationException) {
            timelineState.value = timelineStatus()
            throw error
        } catch (error: Exception) {
            println("BranchWorldTimeline seek failed: ${error::class.simpleName}")
            timelineState.value = timelineStatus(failed = true)
        }
    }

    fun publishNavigation() {
        feedback.value = InterventionFeedback(timeline.queuedInterventions)
        timelineState.value = timelineStatus()
        clock.publish()
    }

    fun updateTimeline() {
        timelineState.value = timelineStatus(failed = timelineState.value.failed)
    }

    private fun timelineStatus(seeking: Boolean = false, failed: Boolean = false) = TimelineView(
        timeline.branchId, timeline.state.tick, timeline.summaries(), timeline.editsAllowed && !seeking,
        timeline.canFork, timeline.commandLimitReached, seeking, failed,
    )
}
