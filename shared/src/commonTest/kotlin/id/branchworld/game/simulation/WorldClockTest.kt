package id.branchworld.game.simulation

import id.branchworld.game.IslandSeeds
import id.branchworld.game.ecology.EcologyEngine
import id.branchworld.game.ecology.canonicalBytes
import id.branchworld.game.terrain.IslandGenerator
import id.branchworld.game.terrain.TerrainKey
import id.branchworld.game.terrain.TerrainMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import id.branchworld.game.runSimulationTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class WorldClockTest {
    @Test
    fun equalLegacyTicksAcrossRatesAndRenderSampling() = runSimulationTest { verifyPlayback(null) }

    @Test
    fun equalTerrainTicksAcrossRatesAndRenderSampling() = runSimulationTest {
        verifyPlayback(IslandGenerator.generate(TerrainKey(104729)))
    }

    @Test
    fun ecologyPlaybackRatesProduceIdenticalCheckpoints() = runSimulationTest {
        IslandSeeds.forEach { verifyEcologyPlayback(it) }
    }

    private suspend fun verifyEcologyPlayback(seed: Long) = coroutineScope {
        withTimeout(15.seconds) {
            val terrain = IslandGenerator.generate(TerrainKey(seed))
            val inputs = mapOf(0L to listOf(true), 300L to listOf(true, false), 600L to listOf(true))
            val checkpoints = listOf(0L, 300L, 600L, 900L)
            val engine = EcologyEngine(terrain)
            val expected = checkpoints.associateWith { target ->
                while (engine.state.tick < target) {
                    inputs[engine.state.tick]?.forEach(engine::steer)
                    engine.advance()
                }
                engine.state.canonicalBytes()
            }
            for (speed in WorldSpeed.entries) {
                val clock = ecologyClock(terrain, EcologyEngine(terrain))
                val waits = Channel<Wait>()
                clock.setSpeed(speed)
                val driver = launch { clock.run({ true }) { waits.pace(it) } }
                clock.togglePlaying()
                // Fast speeds advance several ticks per frame; every frame still lands on the same ticks and states.
                for (tick in 0L..900L step speed.ticksPerStep.toLong()) {
                    val wait = waits.receive()
                    assertEquals(tick, clock.state.tick)
                    assertEquals(50.milliseconds * speed.ticksPerStep / speed.multiplier, wait.interval)
                    expected[tick]?.let { assertContentEquals(it, clock.state.canonicalBytes()) }
                    run {
                        val projection = clock.render.value
                        assertEquals(tick, projection.tick)
                        assertEquals(clock.state.creatures.map { it.id }, projection.creatures.map { it.id })
                        for (pose in projection.creatures) {
                            val ground = pose.ground
                            assertEquals(terrain.cellAt(ground.x, ground.z)?.elevationTwice, ground.elevationTwice)
                        }
                    }
                    inputs[tick]?.forEach(clock::steer)
                    if (tick < 900) wait.release.complete(Unit)
                }
                driver.cancelAndJoin()
            }
        }
    }

    private suspend fun verifyPlayback(terrain: TerrainMap?) = coroutineScope {
        withTimeout(10.seconds) {
            val checkpoints = listOf(300L, 600L, 900L)
            val reference = terrain?.let { WorldEngine.onTerrain(it) } ?: WorldEngine(WorldState.start(104729))
            val inputs = mapOf(0L to listOf(true), 300L to listOf(true, false), 600L to listOf(true))
            val expected = checkpoints.associateWith { tick ->
                while (reference.state.tick < tick) {
                    inputs[reference.state.tick]?.forEach { left ->
                        assertTrue(reference.submitNext(assertNotNull(reference.steeringTarget(left))))
                    }
                    reference.advance()
                }
                reference.state.canonicalBytes()
            }

            for (speed in WorldSpeed.entries) {
                val clock = legacyClock(104729, terrain)
                val waits = Channel<Wait>()
                clock.setSpeed(speed)
                val driver = launch { clock.run({ true }) { waits.pace(it) } }
                clock.togglePlaying()
                for (tick in 0L..900L step speed.ticksPerStep.toLong()) {
                    val wait = waits.receive()
                    assertEquals(tick, clock.state.tick)
                    assertEquals(50.milliseconds * speed.ticksPerStep / speed.multiplier, wait.interval)
                    expected[tick]?.let { assertContentEquals(it, clock.state.canonicalBytes()) }
                    if (tick > 0L) {
                        assertEquals(tick, clock.render.value.tick)
                        assertEquals(clock.state.bunnyCell.x, clock.render.value.leadRabbit?.ground?.x)
                        if (terrain != null) {
                            val pose = assertNotNull(clock.render.value.leadRabbit).ground
                            assertEquals(terrain.cellAt(pose.x, pose.z)?.elevationTwice, pose.elevationTwice)
                        }
                    }
                    inputs[tick]?.forEach(clock::steer)
                    if (tick < 900) wait.release.complete(Unit)
                }
                driver.cancelAndJoin()
            }
        }
    }

    @Test
    fun pauseAndInactiveDriverDropPendingPulseAndKeepNextTickCommands() = runSimulationTest {
        withTimeout(10.seconds) {
            val clock = legacyClock(104729)
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
            assertEquals(1, clock.render.value.leadRabbit?.ground?.x)
            assertEquals(CreatureHeading.PositiveX, clock.render.value.leadRabbit?.heading)

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
            assertEquals(25.milliseconds, resumed.interval)
            assertEquals(1L, clock.render.value.tick)
            active = false
            resumed.release.complete(Unit)
            driver.cancelAndJoin()
            assertEquals(1L, clock.render.value.tick)

            // Back to one tick a frame so the kept command is checked on the very next tick.
            clock.setSpeed(WorldSpeed.Normal)
            val reopened = launch { clock.run({ true }) { waits.pace(it) } }
            waits.receive().release.complete(Unit)
            waits.receive()
            assertEquals(2L, clock.render.value.tick)
            assertEquals(0, clock.render.value.leadRabbit?.ground?.x)
            assertEquals(CreatureHeading.NegativeX, clock.render.value.leadRabbit?.heading)
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
