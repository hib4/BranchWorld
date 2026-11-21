package id.branchworld.game

import id.branchworld.game.ecology.EcologyState
import id.branchworld.game.ecology.CivilizationRulesVersion
import id.branchworld.game.ecology.Intervention
import id.branchworld.game.ecology.InterventionResult
import id.branchworld.game.ecology.canonicalHash
import id.branchworld.game.save.IncompatibleSave
import id.branchworld.game.save.SaveFileTooLarge
import id.branchworld.game.save.TimelineSaveCodec
import id.branchworld.game.save.WorldSaveStore
import id.branchworld.game.simulation.ClockSimulation
import id.branchworld.game.simulation.WorldClock
import id.branchworld.game.simulation.WorldRenderProjection
import id.branchworld.game.simulation.WorldRenderSource
import id.branchworld.game.simulation.renderProjection
import id.branchworld.game.terrain.IslandGenerator
import id.branchworld.game.terrain.TerrainKey
import id.branchworld.game.terrain.TerrainMap
import id.branchworld.game.timeline.BranchSummary
import id.branchworld.game.timeline.InterventionMarker
import id.branchworld.game.timeline.Timeline
import id.branchworld.game.timeline.TimelinePair
import id.branchworld.game.timeline.commonTicks
import id.branchworld.game.timeline.inTreeOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.time.TimeSource

internal class IslandSession {
    private val load = MutableStateFlow<IslandLoad>(IslandLoad.Empty)
    val state = load.asStateFlow()
    private val storage = MutableStateFlow<SaveStatus?>(null)
    val saveStatus = storage.asStateFlow()

    fun dismissSaveStatus() {
        if (storage.value != SaveStatus.Busy) storage.value = null
    }

    fun acknowledgeRestore(tick: Long) {
        storage.value = SaveStatus.Loaded(tick)
    }

    suspend fun open(seed: Long) {
        val previous = load.value
        val current = previous as? IslandLoad.Ready
        if (current?.clock?.seed == seed) return
        load.value = IslandLoad.Loading
        try {
            load.value = withContext(Dispatchers.Default) {
                val started = TimeSource.Monotonic.markNow()
                val terrain = IslandGenerator.generate(TerrainKey(seed))
                val timeline = Timeline.start(terrain, rulesVersion = CivilizationRulesVersion)
                println("BranchWorldTerrain seed=$seed version=${terrain.key.worldgenVersion} hash=${terrain.digest.toString(16)} props=${terrain.propCount} generationUs=${started.elapsedNow().inWholeMicroseconds}")
                IslandLoad.Ready(terrain, timeline)
            }
        } catch (error: CancellationException) {
            load.value = previous
            throw error
        } catch (error: Exception) {
            println("BranchWorldTerrain generationFailed seed=$seed ${error.stackTraceToString()}")
            load.value = IslandLoad.Failed(seed)
        }
    }

    suspend fun save(store: WorldSaveStore, replace: Boolean = false): Boolean {
        if (storage.value == SaveStatus.Busy) return false
        val island = load.value as? IslandLoad.Ready ?: return false
        val started = TimeSource.Monotonic.markNow()
        // Only the UI owner can capture the committed tick and pending queue together.
        val checkpoint = island.checkpoint()
        val world = island.clock.state
        storage.value = SaveStatus.Busy
        try {
            val bytes = withContext(Dispatchers.Default) { TimelineSaveCodec.encode(checkpoint) }
            storage.value = if (store.write(bytes, replace)) {
                println("BranchWorldSave saved seed=${world.terrain.key.seed} branch=${checkpoint.activeBranchId} tick=${world.tick} hash=${world.canonicalHash()} branches=${checkpoint.branches.size} bytes=${bytes.size} saveMs=${started.elapsedNow().inWholeMilliseconds}")
                SaveStatus.Saved(world.tick)
            } else SaveStatus.ConfirmReplace
        } catch (error: CancellationException) {
            storage.value = null
            throw error
        } catch (error: Exception) {
            println("BranchWorldSave write failed: ${error::class.simpleName}")
            storage.value = SaveStatus.Unwritable
        }
        return storage.value is SaveStatus.Saved
    }

    suspend fun restore(store: WorldSaveStore) {
        if (storage.value == SaveStatus.Busy) return
        storage.value = SaveStatus.Busy
        try {
            val started = TimeSource.Monotonic.markNow()
            val bytes = store.read(TimelineSaveCodec.MaxBytes)
            if (bytes == null) {
                storage.value = SaveStatus.Missing
                return
            }
            val world = withContext(Dispatchers.Default) {
                try {
                    TimelineSaveCodec.restore(bytes)
                } catch (error: IncompatibleSave) {
                    throw error
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    throw InvalidSave(error)
                }
            }
            val island = IslandLoad.Ready(world.terrain, world.timeline)
            load.value = island
            println("BranchWorldSave restored seed=${island.clock.seed} branch=${world.timeline.branchId} tick=${island.clock.state.tick} hash=${island.clock.state.canonicalHash()} restoreMs=${started.elapsedNow().inWholeMilliseconds}")
            storage.value = SaveStatus.Restored(island.clock.seed, island.clock.state.tick)
        } catch (error: CancellationException) {
            storage.value = null
            throw error
        } catch (_: IncompatibleSave) {
            storage.value = SaveStatus.Incompatible
        } catch (_: SaveFileTooLarge) {
            storage.value = SaveStatus.Corrupt
        } catch (_: InvalidSave) {
            storage.value = SaveStatus.Corrupt
        } catch (error: Exception) {
            println("BranchWorldSave read failed: ${error::class.simpleName}")
            storage.value = SaveStatus.Unreadable
        }
    }
}

internal sealed interface IslandLoad {
    data object Empty : IslandLoad
    data object Loading : IslandLoad
    class Ready(terrain: TerrainMap, private val timeline: Timeline) : IslandLoad {
        private val feedback = MutableStateFlow(InterventionFeedback(queued = timeline.queuedInterventions))
        val interventions = feedback.asStateFlow()
        private val timelineState = MutableStateFlow(timelineStatus())
        val timelineView = timelineState.asStateFlow()
        private val comparisonState = MutableStateFlow<ComparisonView?>(null)
        val comparison = comparisonState.asStateFlow()
        private val navigationAvailable get() = !timelineState.value.seeking && comparisonState.value == null
        private val display = MutableStateFlow(timeline.state.renderProjection(terrain).copy(branchId = timeline.branchId))
        private var liveFrame = false
        val clock = WorldClock(object : ClockSimulation<EcologyState> {
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
        val world = WorldRenderSource(terrain, display.asStateFlow())

        fun inspectRegions() = timeline.inspectRegions()
        fun checkpoint() = timeline.checkpoint()

        fun intervene(kind: Intervention, region: Int) {
            if (navigationAvailable && timeline.intervene(kind, region)) {
                feedback.value = feedback.value.copy(queued = timeline.queuedInterventions)
                updateTimeline()
            }
        }

        fun playTo(tick: Long) {
            if (navigationAvailable) clock.playTo(tick)
        }

        suspend fun seek(tick: Long) = navigate(timeline.branchId, tick)

        fun markers(branch: Int) = timeline.interventionMarkers(branch, includePending = true)
        fun settlementEvents(branch: Int) = timeline.settlementEvents(branch)

        suspend fun seekEvent(tick: Long) = navigate(timeline.branchId, tick, inspectEvent = true)

        fun renameActive(name: String): Boolean {
            if (!navigationAvailable || !timeline.renameActive(name)) return false
            updateTimeline()
            return true
        }

        suspend fun switchBranch(id: Int) {
            timelineState.value.branches.firstOrNull { it.id == id }?.let { navigate(id, it.head) }
        }

        fun fork(name: String): Boolean {
            if (!navigationAvailable) return false
            clock.pause()
            if (!timeline.fork(name)) return false
            publishNavigation()
            return true
        }

        private fun advanceTimeline(): Boolean {
            if (!timeline.advance()) {
                clock.pause()
                timelineState.value = timelineStatus(failed = true)
                feedback.value = InterventionFeedback(timeline.queuedInterventions)
                return false
            }
            val queued = timeline.queuedInterventions
            val applied = timeline.interventionResults
            if (applied.isNotEmpty() || queued != feedback.value.queued) {
                feedback.value = InterventionFeedback(queued, applied.ifEmpty { feedback.value.applied })
            }
            updateTimeline()
            return true
        }

        private suspend fun navigate(branch: Int, tick: Long, inspectEvent: Boolean = false) {
            if (!navigationAvailable) return
            val request = (if (inspectEvent) timeline.eventReplay(branch, tick) else timeline.seek(branch, tick)) ?: return
            clock.pause()
            timelineState.value = timelineStatus(seeking = true)
            val started = TimeSource.Monotonic.markNow()
            try {
                val replayed = withContext(Dispatchers.Default) {
                    request.replay().also { if (inspectEvent) it.engine.advance() }
                }
                timeline.show(replayed)
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

        private fun publishNavigation() {
            feedback.value = InterventionFeedback(timeline.queuedInterventions)
            timelineState.value = timelineStatus()
            clock.publish()
        }

        private fun updateTimeline() {
            timelineState.value = timelineStatus(failed = timelineState.value.failed)
        }

        private fun timelineStatus(seeking: Boolean = false, failed: Boolean = false) = TimelineView(
            timeline.branchId, timeline.state.tick, timeline.summaries(), timeline.editsAllowed && !seeking,
            timeline.canFork, timeline.commandLimitReached, seeking, failed,
        )

        fun openComparison() {
            val view = timelineState.value
            if (view.seeking || comparisonState.value != null) return
            val active = view.active
            val alternative = view.branches.inTreeOrder().firstOrNull { it.branch.id != active.id }?.branch ?: return
            clock.pause()
            if (active.parentId != null) selectComparison(active.parentId, active.id)
            else selectComparison(active.id, alternative.id)
        }

        fun closeComparison() {
            comparisonState.value = null
            display.value = clock.render.value
        }

        fun selectComparison(leftId: Int, rightId: Int) {
            if (leftId == rightId) return
            val branches = timelineState.value.branches
            val left = branches.firstOrNull { it.id == leftId } ?: return
            val right = branches.firstOrNull { it.id == rightId } ?: return
            val range = commonTicks(left, right)
            comparisonState.value = ComparisonView(left, right, range, range.last.takeUnless { range.isEmpty() })
        }

        fun compareAt(tick: Long) {
            val prior = comparisonState.value ?: return
            if (tick !in prior.range || tick == prior.tick) return
            comparisonState.value = prior.copy(tick = tick, pair = null, failed = false, event = null, outcomes = null)
        }

        fun showComparisonSide(right: Boolean) {
            val prior = comparisonState.value ?: return
            comparisonState.value = prior.copy(showRight = right)
            prior.pair?.let { display.value = if (right) it.right.projection else it.left.projection }
        }

        suspend fun loadComparison(target: ComparisonTarget) {
            val prior = comparisonState.value
            if (prior?.target != target || prior.pair != null) return
            val request = timeline.compare(target.leftId, target.rightId, target.tick) ?: return
            try {
                val started = TimeSource.Monotonic.markNow()
                val pair = withContext(Dispatchers.Default) { request.replay() }
                val current = comparisonState.value?.takeIf { it.target == target } ?: return
                display.value = if (current.showRight) pair.right.projection else pair.left.projection
                comparisonState.value = current.copy(pair = pair)
                println("BranchWorldComparison tick=${pair.tick} left=${pair.left.hash} right=${pair.right.hash} compareMs=${started.elapsedNow().inWholeMilliseconds}")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                println("BranchWorldComparison failed: ${error::class.simpleName}")
                comparisonState.value?.takeIf { it.target == target }?.let { comparisonState.value = it.copy(failed = true) }
            }
        }

        fun inspectComparisonEvent(branchId: Int, marker: InterventionMarker) {
            val current = comparisonState.value ?: return
            val event = ComparisonEvent(branchId, marker.tick)
            if (current.event != event) {
                comparisonState.value = current.copy(event = event, outcomes = null, outcomeFailed = false)
            }
        }

        suspend fun loadComparisonEvent(event: ComparisonEvent) {
            val prior = comparisonState.value
            if (prior?.event != event || prior.outcomes != null) return
            val request = timeline.eventReplay(event.branchId, event.tick) ?: return
            try {
                val outcomes = withContext(Dispatchers.Default) {
                    request.replay().engine.run {
                        advance()
                        interventionResults
                    }
                }
                val current = comparisonState.value?.takeIf { it.event == event } ?: return
                comparisonState.value = current.copy(outcomes = outcomes)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                println("BranchWorldComparison event failed: ${error::class.simpleName}")
                comparisonState.value?.takeIf { it.event == event }?.let {
                    comparisonState.value = it.copy(outcomeFailed = true)
                }
            }
        }
    }
    data class Failed(val seed: Long) : IslandLoad
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

internal data class InterventionFeedback(val queued: Int, val applied: List<InterventionResult> = emptyList())

internal data class ComparisonTarget(val leftId: Int, val rightId: Int, val tick: Long)
internal data class ComparisonEvent(val branchId: Int, val tick: Long)

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
) {
    val target get() = tick?.let { ComparisonTarget(left.id, right.id, it) }
}

internal sealed interface SaveStatus {
    data object Busy : SaveStatus
    data object ConfirmReplace : SaveStatus
    data object Missing : SaveStatus
    data object Corrupt : SaveStatus
    data object Incompatible : SaveStatus
    data object Unreadable : SaveStatus
    data object Unwritable : SaveStatus
    data class Saved(val tick: Long) : SaveStatus
    data class Loaded(val tick: Long) : SaveStatus
    data class Restored(val seed: Long, val tick: Long) : SaveStatus
}

private class InvalidSave(cause: Exception) : Exception(cause)
