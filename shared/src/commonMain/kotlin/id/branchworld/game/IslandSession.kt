package id.branchworld.game

import id.branchworld.game.simulation.WorldClock
import id.branchworld.game.simulation.WorldRenderSource
import id.branchworld.game.simulation.ecologyClock
import id.branchworld.game.ecology.EcologyEngine
import id.branchworld.game.ecology.EcologyState
import id.branchworld.game.terrain.IslandGenerator
import id.branchworld.game.terrain.TerrainKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.time.TimeSource

internal class IslandSession {
    private val load = MutableStateFlow<IslandLoad>(IslandLoad.Empty)
    val state = load.asStateFlow()

    suspend fun open(seed: Long) {
        val current = load.value as? IslandLoad.Ready
        if (current?.clock?.seed == seed) return
        load.value = IslandLoad.Loading
        try {
            load.value = withContext(Dispatchers.Default) {
                val started = TimeSource.Monotonic.markNow()
                val terrain = IslandGenerator.generate(TerrainKey(seed))
                val clock = ecologyClock(terrain, EcologyEngine(terrain))
                val world = WorldRenderSource(terrain, clock.render)
                println("BranchWorldTerrain seed=$seed version=${terrain.key.worldgenVersion} hash=${terrain.digest.toString(16)} props=${terrain.propCount} generationUs=${started.elapsedNow().inWholeMicroseconds}")
                IslandLoad.Ready(clock, world)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            println("BranchWorldTerrain generationFailed seed=$seed ${error.stackTraceToString()}")
            load.value = IslandLoad.Failed(seed)
        }
    }
}

internal sealed interface IslandLoad {
    data object Empty : IslandLoad
    data object Loading : IslandLoad
    data class Ready(val clock: WorldClock<EcologyState>, val world: WorldRenderSource) : IslandLoad
    data class Failed(val seed: Long) : IslandLoad
}
