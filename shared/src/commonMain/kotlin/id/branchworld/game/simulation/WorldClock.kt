package id.branchworld.game.simulation

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
internal class WorldClock(val seed: Long) {
    private val engine = WorldEngine(WorldState.start(seed))
    private val settings = MutableStateFlow(WorldPlayback())
    private val projection = MutableStateFlow(engine.state.renderProjection())

    val playback = settings.asStateFlow()
    val render = projection.asStateFlow()
    val state: WorldState get() = engine.state

    fun togglePlaying() {
        val current = settings.value
        settings.value = WorldPlayback(!current.playing, current.speed)
    }

    fun setSpeed(speed: WorldSpeed) {
        val current = settings.value
        if (current.speed != speed) settings.value = WorldPlayback(current.playing, speed)
    }

    fun steer(left: Boolean) {
        engine.submitNext(GridCell(if (left) MinIslandX else MaxIslandX, SteeringZ))
    }

    suspend fun run(isActive: () -> Boolean, wait: suspend (Duration) -> Unit = { delay(it) }) {
        playback.collectLatest { playback ->
            if (!playback.playing) return@collectLatest
            while (isActive() && state.tick < Long.MAX_VALUE) {
                wait(playback.speed.tickInterval)
                currentCoroutineContext().ensureActive()
                // A rapid pause and resume still invalidates the wait that was already in flight.
                if (!isActive() || settings.value !== playback) break
                projection.value = engine.advance().renderProjection(projection.value)
            }
        }
    }

    private companion object {
        const val SteeringZ = 12
    }
}
