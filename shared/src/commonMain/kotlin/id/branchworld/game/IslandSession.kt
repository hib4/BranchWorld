package id.branchworld.game

import id.branchworld.game.simulation.WorldClock
import id.branchworld.game.simulation.WorldRenderSource
import id.branchworld.game.simulation.ecologyClock
import id.branchworld.game.ecology.EcologyEngine
import id.branchworld.game.ecology.EcologyState
import id.branchworld.game.ecology.EnvironmentalRulesVersion
import id.branchworld.game.ecology.Intervention
import id.branchworld.game.ecology.InterventionResult
import id.branchworld.game.ecology.canonicalHash
import id.branchworld.game.save.IncompatibleSave
import id.branchworld.game.save.SaveFileTooLarge
import id.branchworld.game.save.WorldCheckpoint
import id.branchworld.game.save.WorldSaveCodec
import id.branchworld.game.save.WorldSaveStore
import id.branchworld.game.simulation.SetDestination
import id.branchworld.game.terrain.IslandGenerator
import id.branchworld.game.terrain.TerrainKey
import id.branchworld.game.terrain.TerrainMap
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
                val engine = EcologyEngine(terrain, rulesVersion = EnvironmentalRulesVersion)
                println("BranchWorldTerrain seed=$seed version=${terrain.key.worldgenVersion} hash=${terrain.digest.toString(16)} props=${terrain.propCount} generationUs=${started.elapsedNow().inWholeMicroseconds}")
                IslandLoad.Ready(terrain, engine)
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
        storage.value = SaveStatus.Busy
        try {
            val bytes = withContext(Dispatchers.Default) { WorldSaveCodec.encode(checkpoint) }
            storage.value = if (store.write(bytes, replace)) {
                val world = checkpoint.world
                println("BranchWorldSave saved seed=${world.terrain.key.seed} tick=${world.tick} hash=${world.canonicalHash()} pending=${checkpoint.pending.size}")
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
            val bytes = store.read(WorldSaveCodec.MaxBytes)
            if (bytes == null) {
                storage.value = SaveStatus.Missing
                return
            }
            val world = withContext(Dispatchers.Default) {
                try {
                    WorldSaveCodec.restore(bytes)
                } catch (error: IncompatibleSave) {
                    throw error
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    throw InvalidSave(error)
                }
            }
            val island = IslandLoad.Ready(world.terrain, world.engine)
            load.value = island
            println("BranchWorldSave restored seed=${island.clock.seed} tick=${island.clock.state.tick} hash=${island.clock.state.canonicalHash()} pending=${island.checkpoint().pending.size}")
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
    class Ready(terrain: TerrainMap, private val engine: EcologyEngine) : IslandLoad {
        private val feedback = MutableStateFlow(InterventionFeedback(queued = queuedCount()))
        val interventions = feedback.asStateFlow()
        val clock: WorldClock<EcologyState> = ecologyClock(terrain, engine) {
            if (engine.interventionResults.isNotEmpty()) {
                feedback.value = InterventionFeedback(queuedCount(), engine.interventionResults)
            }
        }
        val world = WorldRenderSource(terrain, clock.render)

        fun inspectRegions() = engine.inspectRegions()
        fun checkpoint() = WorldCheckpoint(engine.state, engine.pendingCommands())

        fun intervene(kind: Intervention, region: Int) {
            if (engine.intervene(kind, region)) feedback.value = feedback.value.copy(queued = queuedCount())
        }

        private fun queuedCount() = engine.pendingCommands().count { it !is SetDestination }
    }
    data class Failed(val seed: Long) : IslandLoad
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
