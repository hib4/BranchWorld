package id.branchworld.game.simulation

import id.branchworld.game.terrain.TerrainMap
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal enum class WorldSpeed(val multiplier: Int) {
    Normal(1),
    Double(2),
    Quadruple(4);

    val tickInterval: Duration get() = 1.seconds / (WorldTicksPerSecond * multiplier)
}

internal class WorldPlayback(val playing: Boolean = false, val speed: WorldSpeed = WorldSpeed.Normal)

/** The UI thread owns commands, playback changes, and the lifecycle-bound tick driver. */
internal class WorldClock(seed: Long, private val terrain: TerrainMap? = null) {
    init {
        require(terrain == null || seed == terrain.key.seed)
    }

    private val engine = if (terrain == null) WorldEngine(WorldState.start(seed)) else WorldEngine.onTerrain(terrain)
    private val settings = MutableStateFlow(WorldPlayback())
    private val projection = MutableStateFlow(engine.state.renderProjection(terrain = terrain))

    val playback = settings.asStateFlow()
    val render = projection.asStateFlow()
    val state: WorldState get() = engine.state
    val seed: Long get() = engine.state.seed
    val canSteerLeft = engine.steeringTarget(left = true) != null
    val canSteerRight = engine.steeringTarget(left = false) != null

    fun togglePlaying() {
        val current = settings.value
        settings.value = WorldPlayback(!current.playing, current.speed)
    }

    fun setSpeed(speed: WorldSpeed) {
        val current = settings.value
        if (current.speed != speed) settings.value = WorldPlayback(current.playing, speed)
    }

    fun steer(left: Boolean) {
        engine.steeringTarget(left)?.let(engine::submitNext)
    }

    suspend fun run(isActive: () -> Boolean, wait: suspend (Duration) -> Unit = { delay(it) }) {
        playback.collectLatest { playback ->
            if (!playback.playing) return@collectLatest
            while (isActive() && state.tick < Long.MAX_VALUE) {
                wait(playback.speed.tickInterval)
                currentCoroutineContext().ensureActive()
                // A rapid pause and resume still invalidates the wait that was already in flight.
                if (!isActive() || settings.value !== playback) break
                projection.value = engine.advance().renderProjection(projection.value, terrain)
            }
        }
    }
}
