package id.branchworld.simulation.ecology

import id.branchworld.simulation.core.FireRulesVersion
import id.branchworld.simulation.core.RangeRulesVersion
import id.branchworld.simulation.core.WatershedWorldgenVersion
import id.branchworld.simulation.core.canonicalHash
import id.branchworld.simulation.ecology.chapters.CrossingSeed
import id.branchworld.simulation.ecology.chapters.EmberSeed
import id.branchworld.simulation.ecology.chapters.FadingGroveSeed
import id.branchworld.simulation.ecology.chronicle.FireOutCause
import id.branchworld.simulation.ecology.chronicle.WorldEventKind
import id.branchworld.simulation.ecology.environment.Weather
import id.branchworld.simulation.ecology.fire.FireStep
import id.branchworld.simulation.ecology.fire.FireView
import id.branchworld.simulation.ecology.fire.ScrubAge
import id.branchworld.simulation.ecology.fire.TileCount
import id.branchworld.simulation.ecology.fire.tileCell
import id.branchworld.simulation.ecology.fire.tileOf
import id.branchworld.simulation.engine.EcologyEngine
import id.branchworld.simulation.legacy.canonicalHash
import id.branchworld.simulation.runSimulationTest
import id.branchworld.simulation.terrain.IslandGenerator
import id.branchworld.simulation.terrain.TerrainKey
import id.branchworld.simulation.timeline.ReplayRequest
import id.branchworld.simulation.timeline.Timeline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.TimeSource

class FireTest {
    private fun terrain(seed: Long) = IslandGenerator.generate(TerrainKey(seed, WatershedWorldgenVersion))

    @Test
    fun nearbyFiresHaveTheirOwnIdentities() {
        val west = tileOf(-12, 0)
        val east = tileOf(12, 0)
        val burning = IntArray(TileCount).apply { this[west] = 2; this[east] = 5 }
        val view = FireView(burning, emptySet())
        assertEquals(2, view.nearestFire(tileCell(west), 3))
        assertEquals(5, view.nearestFire(tileCell(east), 3))
        assertNull(view.nearestFire(id.branchworld.simulation.core.GridCell(0, 0), 3))
    }

    @Test
    fun earlierRulesNeverCarryFireOrCauses() {
        val timeline = Timeline.start(terrain(EmberSeed), RangeRulesVersion)
        repeat(1200) { timeline.advance() }
        assertNull(timeline.state.fire)
        assertNull(timeline.state.chronicle)
        assertEquals(RangeRulesVersion, timeline.state.rulesVersion)
    }

    @Test
    fun theSameSandboxBurnsTheSameWay() {
        fun run() = Timeline.start(terrain(EmberSeed), FireRulesVersion).also { t -> repeat(6000) { t.advance() } }.state
        val first = run()
        assertEquals(first.canonicalHash(), run().canonicalHash())
        assertEquals(FireRulesVersion, first.rulesVersion)
        assertNull(Timeline.start(terrain(EmberSeed), FireRulesVersion).chapter)
    }

    /** Twenty seasons of wildfire and regrowth on every gallery island, with saved-state checks along the way. */
    @Test
    fun fireAndRegrowthStayBoundedOnEveryGalleryIsland() = runSimulationTest {
        val results = mutableListOf<String>()
        for (seed in listOf(FadingGroveSeed, CrossingSeed, EmberSeed)) {
            val terrain = terrain(seed)
            val island = IslandEcology(terrain)
            val engine = EcologyEngine(terrain, rulesVersion = FireRulesVersion, island = island)
            val opening = engine.state
            val samples = LongArray(Soak.toInt())
            val rainedOut = mutableListOf<Long>()
            for (index in 0 until Soak.toInt()) {
                val started = TimeSource.Monotonic.markNow()
                val state = engine.advance()
                samples[index] = started.elapsedNow().inWholeNanoseconds
                val fire = checkNotNull(state.fire)
                assertTrue(fire.burning.size <= TileCount)
                if (state.chronicle!!.events.lastOrNull()?.let { it.tick == state.tick && it.kind == WorldEventKind.FireOut && it.detail == FireOutCause.Rain.code } == true) {
                    assertEquals(Weather.Rain, state.environment!!.weather)
                    rainedOut.add(state.tick)
                }
                if (state.tick % 2_000L == 0L) EcologyEngine(terrain, state, island = island).validateSavedState()
            }
            val state = engine.state
            val fire = checkNotNull(state.fire)
            for (species in PreLifeSpecies) assertTrue(state.creatures.any { it.species == species }, "$seed lost $species")
            assertTrue(state.settlements!!.village!!.households.any { it.active }, "$seed lost its village")
            assertTrue(fire.fires in 1..12, "$seed had ${fire.fires} fires")
            assertTrue(fire.scars.all { state.tick - it.at < ScrubAge + FireStep })
            samples.sort()
            val p95 = samples[samples.size * 95 / 100] / 1_000
            val replayStart = TimeSource.Monotonic.markNow()
            val replayed = ReplayRequest(terrain, 1, Soak, opening, emptyList()).replay().engine.state
            val replayTicksPerSecond = Soak * 1_000_000 / replayStart.elapsedNow().inWholeMicroseconds.coerceAtLeast(1)
            assertEquals(state.canonicalHash(), replayed.canonicalHash())
            results += "seed=$seed fires=${fire.fires} tiles=${fire.tilesBurned} homes=${fire.homesBurned} rainedOut=${rainedOut.size} " +
                "species=${PreLifeSpecies.map { s -> state.creatures.count { it.species == s } }} households=${state.settlements.village.households.count { it.active }} " +
                "hash=${state.canonicalHash()} medianUs=${samples[samples.size / 2] / 1_000} p95Us=$p95 maxUs=${samples.last() / 1_000} " +
                "replayTicksPerSecond=$replayTicksPerSecond"
            // A generous ceiling that still catches an accidental quadratic fire step. The measured target is in PLAN.md.
            assertTrue(p95 < 8_000, "step p95 $p95 µs")
            assertTrue(replayTicksPerSecond > 1_500, "replay $replayTicksPerSecond ticks/s")
        }
        println("FIRE_SOAK " + results.joinToString(" || "))
    }

    private companion object {
        const val Soak = 24_000L
    }
}
