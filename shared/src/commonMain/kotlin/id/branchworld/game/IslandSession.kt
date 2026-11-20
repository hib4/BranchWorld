package id.branchworld.game

import id.branchworld.game.ecology.EcologyState
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
import id.branchworld.game.timeline.Timeline
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
                val timeline = Timeline.start(terrain)
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

    suspend fun save(store: WorldSaveStore, replace: Boolean = false) {
        if (storage.value == SaveStatus.Busy) return
        val island = load.value as? IslandLoad.Ready ?: return
        // Only the UI owner can capture the committed tick and pending queue together.
        val checkpoint = island.checkpoint()
        val world = island.clock.state
        storage.value = SaveStatus.Busy
        try {
            val bytes = withContext(Dispatchers.Default) { TimelineSaveCodec.encode(checkpoint) }
            storage.value = if (store.write(bytes, replace)) {
                println("BranchWorldSave saved seed=${world.terrain.key.seed} branch=${checkpoint.activeBranchId} tick=${world.tick} hash=${world.canonicalHash()} branches=${checkpoint.branches.size} bytes=${bytes.size}")
                SaveStatus.Saved(world.tick)
            } else SaveStatus.ConfirmReplace
        } catch (error: CancellationException) {
            storage.value = null
            throw error
        } catch (error: Exception) {
            println("BranchWorldSave write failed: ${error::class.simpleName}")
            storage.value = SaveStatus.Unwritable
        }
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
        val clock = WorldClock(object : ClockSimulation<EcologyState> {
            override val state get() = timeline.state
            override val tick get() = state.tick
            override val seed get() = terrain.key.seed
            override fun canSteer(left: Boolean) = !timelineState.value.seeking && timeline.canSteer(left)
            override fun steer(left: Boolean) {
                if (!timelineState.value.seeking) timeline.steer(left)
                updateTimeline()
            }
            override fun advance() = advanceTimeline()
            override fun project(previous: WorldRenderProjection?) =
                state.renderProjection(terrain).copy(branchId = timeline.branchId)
        })
        val world = WorldRenderSource(terrain, clock.render)

        fun inspectRegions() = timeline.inspectRegions()
        fun checkpoint() = timeline.checkpoint()

        fun intervene(kind: Intervention, region: Int) {
            if (!timelineState.value.seeking && timeline.intervene(kind, region)) {
                feedback.value = feedback.value.copy(queued = timeline.queuedInterventions)
                updateTimeline()
            }
        }

        fun playTo(tick: Long) {
            if (!timelineState.value.seeking) clock.playTo(tick)
        }

        suspend fun seek(tick: Long) = navigate(timeline.branchId, tick)

        suspend fun switchBranch(id: Int) {
            timelineState.value.branches.firstOrNull { it.id == id }?.let { navigate(id, it.head) }
        }

        fun fork(name: String): Boolean {
            if (timelineState.value.seeking) return false
            clock.pause()
            if (!timeline.fork(name)) return false
            publishNavigation()
            return true
        }

        private fun advanceTimeline() {
            if (!timeline.advance()) {
                clock.pause()
                timelineState.value = timelineStatus(failed = true)
                feedback.value = InterventionFeedback(timeline.queuedInterventions)
                return
            }
            val queued = timeline.queuedInterventions
            val applied = timeline.interventionResults
            if (applied.isNotEmpty() || queued != feedback.value.queued) {
                feedback.value = InterventionFeedback(queued, applied.ifEmpty { feedback.value.applied })
            }
            updateTimeline()
        }

        private suspend fun navigate(branch: Int, tick: Long) {
            if (timelineState.value.seeking) return
            val request = timeline.seek(branch, tick) ?: return
            clock.pause()
            timelineState.value = timelineStatus(seeking = true)
            val started = TimeSource.Monotonic.markNow()
            try {
                timeline.show(withContext(Dispatchers.Default) { request.replay() })
                publishNavigation()
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
