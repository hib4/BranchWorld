package id.branchworld.game.simulation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class WorldClockTest {
    @Test
    fun equalTicksAcrossRatesAndRenderSampling() = runBlocking {
        withTimeout(10.seconds) {
            val checkpoints = listOf(300L, 600L, 900L)
            val reference = WorldEngine(WorldState.start(104729))
            val inputs = mapOf(0L to listOf(true), 299L to listOf(true, false), 600L to listOf(true))
            val expected = checkpoints.associateWith { tick ->
                while (reference.state.tick < tick) {
                    inputs[reference.state.tick]?.forEach { left ->
                        assertTrue(reference.submitNext(GridCell(if (left) -8 else 7, 12)))
                    }
                    reference.advance()
                }
                reference.state.canonicalBytes()
            }

            for (speed in WorldSpeed.entries) {
                val clock = WorldClock(104729)
                val waits = Channel<Wait>()
                clock.setSpeed(speed)
                val driver = launch { clock.run({ true }) { waits.pace(it) } }
                clock.togglePlaying()
                for (tick in 0L..900L) {
                    val wait = waits.receive()
                    assertEquals(tick, clock.state.tick)
                    assertEquals(50.milliseconds / speed.multiplier, wait.interval)
                    expected[tick]?.let { assertContentEquals(it, clock.state.canonicalBytes()) }
                    if (tick % speed.multiplier == 0L) {
                        assertEquals(tick, clock.render.value.tick)
                        assertEquals(clock.state.bunnyCell.x, clock.render.value.x)
                    }
                    inputs[tick]?.forEach(clock::steer)
                    if (tick < 900) wait.release.complete(Unit)
                }
                driver.cancelAndJoin()
            }
        }
    }

    @Test
    fun pauseAndInactiveDriverDropPendingPulseAndKeepNextTickCommands() = runBlocking {
        withTimeout(10.seconds) {
            val clock = WorldClock(104729)
            val waits = Channel<Wait>()
            var active = true
            val driver = launch { clock.run({ active }) { waits.pace(it) } }
            assertFalse(clock.playback.value.playing)
            assertEquals(WorldSpeed.Normal, clock.playback.value.speed)
            clock.steer(left = true)
            clock.steer(left = false)
            clock.togglePlaying()
            val first = waits.receive()
            assertEquals(0L, clock.render.value.tick)
            first.release.complete(Unit)
            val second = waits.receive()
            assertEquals(1L, clock.render.value.tick)
            assertEquals(1, clock.render.value.x)
            assertEquals(BunnyHeading.PositiveX, clock.render.value.heading)

            clock.togglePlaying()
            clock.togglePlaying()
            second.release.complete(Unit)
            val replacement = waits.receive()
            assertEquals(50.milliseconds, replacement.interval)
            assertEquals(1L, clock.render.value.tick)

            clock.togglePlaying()
            clock.steer(left = true)
            replacement.release.complete(Unit)
            assertEquals(1L, clock.render.value.tick)
            clock.setSpeed(WorldSpeed.Quadruple)
            clock.togglePlaying()
            val resumed = waits.receive()
            assertEquals(12.5.milliseconds, resumed.interval)
            assertEquals(1L, clock.render.value.tick)
            active = false
            resumed.release.complete(Unit)
            driver.cancelAndJoin()
            assertEquals(1L, clock.render.value.tick)

            val reopened = launch { clock.run({ true }) { waits.pace(it) } }
            waits.receive().release.complete(Unit)
            waits.receive()
            assertEquals(2L, clock.render.value.tick)
            assertEquals(0, clock.render.value.x)
            assertEquals(BunnyHeading.NegativeX, clock.render.value.heading)
            val uninterrupted = WorldEngine(WorldState.start(104729))
            assertTrue(uninterrupted.submitNext(GridCell(7, 12)))
            uninterrupted.advance()
            assertTrue(uninterrupted.submitNext(GridCell(-8, 12)))
            assertContentEquals(uninterrupted.advance().canonicalBytes(), clock.state.canonicalBytes())
            reopened.cancelAndJoin()
        }
    }

    private suspend fun Channel<Wait>.pace(interval: Duration) {
        val wait = Wait(interval)
        send(wait)
        wait.release.await()
    }

    private class Wait(val interval: Duration, val release: CompletableDeferred<Unit> = CompletableDeferred())
}
