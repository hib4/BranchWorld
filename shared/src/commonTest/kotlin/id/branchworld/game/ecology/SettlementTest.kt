package id.branchworld.game.ecology

import id.branchworld.game.save.TimelineSaveCodec
import id.branchworld.game.terrain.IslandGenerator
import id.branchworld.game.terrain.TerrainKey
import id.branchworld.game.timeline.Timeline
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.TimeSource

class SettlementTest {
    @Test
    fun foundingAndGrowthAreAutonomousAndHarvestIsConserved() {
        val terrain = terrain()
        val timeline = Timeline.start(terrain, CivilizationRulesVersion)
        val initial = timeline.state.patches.sumOf { it.biomass }
        timeline.advanceTo(59)
        assertTrue(timeline.state.settlements!!.sites.isEmpty())
        timeline.advanceTo(60)
        val first = timeline.state.settlements!!.sites.single()
        assertEquals(4, first.residents)
        assertEquals(SettlementEventKind.Founded, timeline.state.settlements!!.events.single().kind)
        timeline.advanceTo(1_800)
        val world = timeline.state.settlements!!
        println("SETTLEMENT_GROWTH,${first.center.region()},${first.center},${world.sites.last()},${world.events}")
        assertTrue(world.gatheredFood > 0 && world.gatheredWood > 0)
        assertTrue(world.events.any { it.kind == SettlementEventKind.Expanded })
        assertEquals(initial.toLong() + timeline.state.ledger.introduced + timeline.state.ledger.regrown - timeline.state.ledger.consumed,
            timeline.state.patches.sumOf { it.biomass }.toLong())
        assertTrue(world.gatheredFood + world.gatheredWood <= timeline.state.ledger.consumed)
    }

    @Test
    fun rainRaisesCropYieldAndScarcityCanRelocateThenAbandon() {
        val terrain = terrain()
        val timeline = Timeline.start(terrain, CivilizationRulesVersion)
        timeline.advanceTo(60)
        val start = timeline.state
        val first = start.settlements!!.sites.single()
        val island = IslandEcology(terrain)
        val economy = SettlementEconomy(island)
        assertEquals(3, economy.cropYield(first.field, 3, Weather.Rain) - economy.cropYield(first.field, 3, Weather.Dry))
        val depleted = start.patches.map {
            if (it.cell.region() == first.center.region() && it.kind == ForageKind.Meadow) it.copy(biomass = 0) else it
        }.toMutableList()
        val short = start.settlements.copy(sites = listOf(first.copy(residents = 16, homes = 4, food = 0, shortage = 2)))
        val moved = economy.advance(short, 120, Weather.Dry, depleted, start.springs)
        println("SETTLEMENT_SCARCITY,${moved.sites.map { it.center.region() }},${moved.events}")
        assertEquals(listOf(SettlementEventKind.Shortage, SettlementEventKind.Relocated, SettlementEventKind.Abandoned,
            SettlementEventKind.Founded), moved.events.drop(1).map { it.kind })
        assertEquals(16, moved.sites.sumOf { it.residents })
        val poorField = island.reachable.first { terrain.terrainCell(it).moisture < 120 }
        var abandoned = moved.copy(sites = listOf(moved.sites.first(), moved.sites.last().copy(
            field = poorField, residents = 2, shortage = 2,
        )))
        val noForage = depleted.map { it.copy(biomass = 0) }.toMutableList()
        abandoned = economy.advance(abandoned, 180, Weather.Dry, noForage, start.springs)
        assertEquals(1, abandoned.sites.last().residents)
        abandoned = economy.advance(abandoned, 240, Weather.Dry, noForage, start.springs)
        assertEquals(0, abandoned.sites.last().residents)
        assertEquals(SettlementEventKind.Abandoned, abandoned.events.last().kind)
    }

    @Test
    fun childReforestationChangesEconomyAndRoundsTripsIndependently() = runBlocking {
        val timeline = Timeline.start(terrain(), CivilizationRulesVersion)
        timeline.advanceTo(900)
        val original = timeline.checkpoint().branches.single()
        timeline.show(assertNotNull(timeline.seek(1, 300)).replay())
        val fork = timeline.state
        assertTrue(timeline.fork("Restored Forest"))
        val region = fork.settlements!!.sites.last().center.region()
        assertTrue(timeline.intervene(Intervention.Reforest, region))
        timeline.advanceTo(900)
        val pair = assertNotNull(timeline.compare(1, 2, 900)).replay()
        val child = timeline.state.settlements!!
        val parent = original.head.settlements!!
        println("SETTLEMENT_PAIR,${pair.left.hash},${pair.right.hash},$region,${parent.sites.last()},${child.sites.last()}")
        assertNotEquals(parent.sites.last(), child.sites.last())
        assertEquals(76L, pair.left.settlement?.wood)
        assertEquals(124L, pair.right.settlement?.wood)
        assertEquals("5dce8f94926c905d", pair.left.hash)
        assertEquals("4689c1bc6a8d2df2", pair.right.hash)
        assertEquals(original, timeline.checkpoint().branches.first())
        val checkpoint = timeline.checkpoint()
        assertEquals(checkpoint, TimelineSaveCodec.restore(TimelineSaveCodec.encode(checkpoint)).timeline.checkpoint())
        val at600 = assertNotNull(timeline.seek(2, 600)).replay()
        timeline.show(at600)
        timeline.advanceTo(900)
        assertEquals(pair.right.hash, timeline.state.canonicalHash())
    }

    @Test
    fun releaseCheckpointsAgreeAfterRewindAndRestore() = runBlocking {
        val ticks = listOf(0L, 300L, 600L, 900L)
        val timeline = Timeline.start(terrain(), CivilizationRulesVersion)
        val originals = ticks.associateWith {
            timeline.advanceTo(it)
            timeline.state.canonicalHash()
        }
        val restored = TimelineSaveCodec.restore(TimelineSaveCodec.encode(timeline.checkpoint())).timeline
        for ((tick, hash) in originals) {
            assertEquals(hash, assertNotNull(timeline.seek(1, tick)).replay().engine.state.canonicalHash())
            assertEquals(hash, assertNotNull(restored.seek(1, tick)).replay().engine.state.canonicalHash())
        }
        restored.show(assertNotNull(restored.seek(1, 300)).replay())
        restored.advanceTo(600)
        assertEquals(originals[600], restored.state.canonicalHash())
        assertEquals(timeline.checkpoint().branches, restored.checkpoint().branches)
        println("RELEASE_CHECKPOINTS," + originals.entries.joinToString(",") { "${it.key}:${it.value}" })
    }

    @Test
    fun tenThousandTicksKeepCohortsAndStocksBounded() = runBlocking {
        for (seed in listOf(104729L, 271828L, 424242L)) {
            val terrain = terrain(seed)
            for (rules in listOf(EnvironmentalRulesVersion, CivilizationRulesVersion)) {
                val timeline = Timeline.start(terrain, rules)
                val started = TimeSource.Monotonic.markNow()
                repeat(10_000) {
                    assertTrue(timeline.advance())
                    val sites = timeline.state.settlements?.sites.orEmpty()
                    assertTrue(sites.size <= 2)
                    assertTrue(sites.count { it.residents > 0 } <= 1)
                    assertTrue(sites.all { it.residents in 0..16 && it.food in 0..256 && it.wood in 0..256 && it.stone in 0..256 })
                }
                println("SETTLEMENT_SOAK,$seed,$rules,${timeline.state.canonicalHash()},${started.elapsedNow().inWholeMilliseconds}")
                if (rules == CivilizationRulesVersion) assertFalse(timeline.state.settlements!!.events.isEmpty())
                val request = assertNotNull(timeline.seek(1, 9_899))
                val replayStarted = TimeSource.Monotonic.markNow()
                assertEquals(9_899L, request.replay().engine.state.tick)
                println("SETTLEMENT_REPLAY,$seed,$rules,${replayStarted.elapsedNow().inWholeMicroseconds}")
            }
        }
    }

    private fun terrain(seed: Long = 104729L) = IslandGenerator.generate(TerrainKey(seed))
    private fun Timeline.advanceTo(tick: Long) {
        while (state.tick < tick) assertTrue(advance())
    }
}
