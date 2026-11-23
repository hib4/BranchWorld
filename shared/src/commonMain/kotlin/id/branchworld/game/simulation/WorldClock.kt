package id.branchworld.game.simulation

import id.branchworld.game.ecology.EcologyEngine
import id.branchworld.game.ecology.EcologyState
import id.branchworld.game.terrain.TerrainMap
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Faster speeds advance several ticks per published frame, so the interface is never asked to redraw faster than it can. */
internal enum class WorldSpeed(val multiplier: Int, val ticksPerStep: Int = 1) {
    Normal(1),
    Double(2),
    Quadruple(4, 2),
    Octuple(8, 4);

    val tickInterval: Duration get() = 1.seconds * ticksPerStep / (WorldTicksPerSecond * multiplier)
}

internal class WorldPlayback(
    val playing: Boolean = false,
    val speed: WorldSpeed = WorldSpeed.Normal,
    val stopAt: Long? = null,
)

internal interface ClockSimulation<S> {
    val state: S
    val tick: Long
    val seed: Long
    fun canSteer(left: Boolean): Boolean
    fun steer(left: Boolean)
    fun advance()
    fun project(previous: WorldRenderProjection?): WorldRenderProjection
}

/** The UI thread owns commands, playback changes, and the lifecycle-bound tick driver. */
internal class WorldClock<S>(private val simulation: ClockSimulation<S>) {
    private val settings = MutableStateFlow(WorldPlayback())
    private val projection = MutableStateFlow(simulation.project(null))

    val playback = settings.asStateFlow()
    val render = projection.asStateFlow()
    val state: S get() = simulation.state
    val seed: Long get() = simulation.seed
    val canSteerLeft get() = simulation.canSteer(left = true)
    val canSteerRight get() = simulation.canSteer(left = false)

    fun togglePlaying() {
        val current = settings.value
        if (current.playing) pause()
        else if (simulation.tick < Long.MAX_VALUE) settings.value = WorldPlayback(true, current.speed)
    }

    fun pause() {
        if (settings.value.playing) settings.value = WorldPlayback(speed = settings.value.speed)
    }

    fun playTo(tick: Long) {
        if (tick > simulation.tick) settings.value = WorldPlayback(true, settings.value.speed, tick)
    }

    fun setSpeed(speed: WorldSpeed) {
        val current = settings.value
        if (current.speed != speed) settings.value = WorldPlayback(current.playing, speed, current.stopAt)
    }

    fun steer(left: Boolean) = simulation.steer(left)

    /** Advances exactly one tick while paused, so something the player just did shows at once. */
    fun step() {
        if (settings.value.playing || simulation.tick == Long.MAX_VALUE) return
        simulation.advance()
        publish()
    }

    fun publish() {
        projection.value = simulation.project(projection.value)
    }

    suspend fun run(isActive: () -> Boolean, wait: suspend (Duration) -> Unit = { delay(it) }) {
        playback.collectLatest { playback ->
            if (!playback.playing) return@collectLatest
            while (isActive() && simulation.tick < Long.MAX_VALUE) {
                wait(playback.speed.tickInterval)
                currentCoroutineContext().ensureActive()
                // A rapid pause and resume still invalidates the wait that was already in flight.
                if (!isActive() || settings.value !== playback) break
                var stepped = 0
                while (stepped < playback.speed.ticksPerStep && simulation.tick < Long.MAX_VALUE && simulation.tick != playback.stopAt) {
                    simulation.advance()
                    stepped++
                    if (settings.value !== playback) break
                }
                publish()
                if (simulation.tick == playback.stopAt || simulation.tick == Long.MAX_VALUE) pause()
            }
        }
    }
}

internal fun legacyClock(seed: Long, terrain: TerrainMap? = null): WorldClock<WorldState> {
    require(terrain == null || seed == terrain.key.seed)
    val engine = if (terrain == null) WorldEngine(WorldState.start(seed)) else WorldEngine.onTerrain(terrain)
    return WorldClock(object : ClockSimulation<WorldState> {
        override val state get() = engine.state
        override val tick get() = state.tick
        override val seed get() = state.seed
        override fun canSteer(left: Boolean) = engine.steeringTarget(left) != null
        override fun steer(left: Boolean) { engine.steeringTarget(left)?.let(engine::submitNext) }
        override fun advance() { engine.advance() }
        override fun project(previous: WorldRenderProjection?) = state.renderProjection(previous, terrain)
    })
}

internal fun ecologyClock(
    terrain: TerrainMap,
    engine: EcologyEngine,
    onTick: () -> Unit = {},
): WorldClock<EcologyState> {
    require(engine.state.terrain.key == terrain.key && engine.state.terrain.digest == terrain.digest)
    return WorldClock(object : ClockSimulation<EcologyState> {
        override val state get() = engine.state
        override val tick get() = state.tick
        override val seed get() = terrain.key.seed
        override fun canSteer(left: Boolean) = engine.steeringTarget(left) != null
        override fun steer(left: Boolean) = engine.steer(left)
        override fun advance() {
            engine.advance()
            onTick()
        }
        override fun project(previous: WorldRenderProjection?) = state.renderProjection(terrain)
    })
}
