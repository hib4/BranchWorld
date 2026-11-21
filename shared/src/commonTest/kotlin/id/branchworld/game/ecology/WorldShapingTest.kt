package id.branchworld.game.ecology

import id.branchworld.game.save.TimelineSaveCodec
import id.branchworld.game.simulation.CallRainRegion
import id.branchworld.game.simulation.EchoKind
import id.branchworld.game.simulation.ReforestRegion
import id.branchworld.game.simulation.GridCell
import id.branchworld.game.terrain.IslandGenerator
import id.branchworld.game.terrain.TerrainKey
import id.branchworld.game.timeline.ReplayRequest
import id.branchworld.game.timeline.Timeline
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorldShapingTest {
    private fun terrain() = IslandGenerator.generate(TerrainKey(FadingGroveSeed, 2))

    @Test
    fun droughtHasThreeDistinctHistoriesAndNaturalRecovery(): Unit = runBlocking {
        val terrain = terrain()
        val timeline = Timeline.start(terrain)
        assertEquals(ShapeBlock.AwaitWarning, timeline.preview(Intervention.Rain, 5)?.blocked)
        assertFalse(timeline.intervene(Intervention.Reforest, 9))
        repeat(900) { timeline.advance() }
        val untreated = timeline.state
        assertEquals(240L, untreated.shaping?.warningAt)
        val forkTick = assertNotNull(untreated.shaping?.warningAt)
        val untreatedHarvest = assertNotNull(timeline.seek(1, 360)).replay().engine.state
        val untreatedWelfare = assertNotNull(untreatedHarvest.settlements?.sites?.first()?.welfare)
        assertTrue(untreatedWelfare.unmet > 0)
        assertNotNull(untreated.settlements?.sites?.first()?.welfare?.firstRecovery)

        timeline.show(assertNotNull(timeline.seek(1, forkTick)).replay())
        assertTrue(timeline.fork("Rain at Hollowmere"))
        assertEquals(1 to forkTick, timeline.warningLocation())
        val region = timeline.recommendedRainRegion()
        val target = assertNotNull(timeline.preview(Intervention.Rain, region))
        assertNotNull(target.field)
        assertNotNull(target.bank)
        assertNull(target.blocked)
        assertTrue(timeline.intervene(Intervention.Rain, region))
        assertEquals(ShapeBlock.Reserved, timeline.preview(Intervention.Reforest, 9)?.blocked)
        val pending = TimelineSaveCodec.restore(TimelineSaveCodec.encode(timeline.checkpoint())).timeline
        assertEquals(timeline.checkpoint(), pending.checkpoint())
        while (timeline.state.tick < 360L) timeline.advance()
        val shower = timeline.state
        val watered = assertNotNull(shower.settlements?.sites?.first()?.welfare)
        assertTrue(watered.water > untreatedWelfare.water)
        assertTrue(watered.cropYield > untreatedWelfare.cropYield)
        assertEquals(0, watered.unmet)
        assertNotNull(shower.shaping?.shower)
        assertEquals(untreatedHarvest.environment, shower.environment)
        assertEquals(timeline.checkpoint(), TimelineSaveCodec.restore(TimelineSaveCodec.encode(timeline.checkpoint())).timeline.checkpoint())
        while (timeline.state.tick < 900L) timeline.advance()
        val rain = timeline.state
        assertFalse(assertNotNull(rain.shaping).spent)
        assertNull(rain.shaping.shower)

        timeline.show(assertNotNull(timeline.seek(1, forkTick)).replay())
        assertTrue(timeline.fork("A grove for wildlife"))
        val sites = assertNotNull(timeline.preview(Intervention.Reforest, 9)).sites
        assertEquals(6, sites.size)
        assertTrue(timeline.intervene(Intervention.Reforest, 9))
        timeline.advance()
        assertEquals(sites.toSet(), timeline.state.patches.filter { it.planted }.map { it.cell }.toSet())
        var usedRootedHabitat = false
        while (timeline.state.tick < 900L) {
            timeline.advance()
            if (timeline.state.tick == 360L) {
                val groveWelfare = assertNotNull(timeline.state.settlements?.sites?.first()?.welfare)
                assertTrue(watered.water > groveWelfare.water)
                assertTrue(watered.cropYield > groveWelfare.cropYield)
            }
            if (timeline.state.patches.any { patch ->
                    patch.rooted() && timeline.state.creatures.any { animal ->
                        animal.cell == patch.cell && animal.action in listOf(CreatureAction.Resting, CreatureAction.Eating)
                    }
                }) usedRootedHabitat = true
        }
        val grove = timeline.state
        val hashes = listOf(untreated, rain, grove).map { it.canonicalHash() }
        val recovery = untreated.settlements.sites.first().welfare?.firstRecovery
        println("Drought baseline=$untreatedWelfare watered=$watered rooted=${grove.patches.count { it.rooted() }} " +
            "recovery=$recovery hashes=$hashes")
        assertEquals(360L, untreatedWelfare.firstShortage)
        assertEquals(540L, recovery)
        assertEquals(12, watered.water)
        assertEquals(6, watered.cropYield)
        assertEquals(3, grove.patches.count { it.rooted() })
        assertTrue(usedRootedHabitat)
        assertEquals(listOf("5c6715ee9bf1559f", "ee5d2afb4f668209", "872f8b772885c3d5"), hashes)
        val bytes = TimelineSaveCodec.encode(timeline.checkpoint())
        assertTrue(bytes.decodeToString().startsWith("""{"schemaVersion":6,"rulesVersion":8"""))
        val restored = TimelineSaveCodec.restore(bytes).timeline
        assertEquals(timeline.checkpoint(), restored.checkpoint())
        val comparison = assertNotNull(restored.compare(2, 3, 900)).replay()
        assertEquals(rain.canonicalHash(), comparison.left.hash)
        assertEquals(grove.canonicalHash(), comparison.right.hash)
        assertEquals(untreated.canonicalHash(), assertNotNull(restored.seek(1, 900)).replay().engine.state.canonicalHash())
        val echo = assertNotNull(comparison.left.projection.livingEcho)
        val groveSites = echo.sites.filter { it.kind == EchoKind.Grove }
        assertEquals(sites.toSet(), groveSites.map { GridCell(it.ground.x, it.ground.z) }.toSet())
        assertEquals(3, groveSites.count { it.here == 0 && it.other == 2 })
        assertEquals(echo.sites.map { it.copy(here = it.other, other = it.here) },
            comparison.right.projection.livingEcho?.sites)
        val warning = assertNotNull(restored.compare(2, 3, forkTick)).replay()
        assertTrue(assertNotNull(warning.left.projection.livingEcho).sites.isEmpty())
        assertTrue(warning.sharedTrail.isEmpty())
        val harvest = assertNotNull(restored.compare(2, 3, 360)).replay()
        val wet = assertNotNull(harvest.right.projection.livingEcho).sites
        assertEquals(6, wet.single { it.kind == EchoKind.Field }.other)
        assertEquals(2, wet.single { it.kind == EchoKind.Field }.here)
        assertEquals(12, wet.single { it.kind == EchoKind.Bank }.other)
        assertTrue(wet.any { it.kind == EchoKind.WetGround && it.other > it.here })
        assertEquals(5, harvest.leftTrail.single().marker.region)
        assertEquals(9, harvest.rightTrail.single().marker.region)
        assertEquals(1, restored.sharedPast(2, 3)?.branchId)
        assertEquals(forkTick, restored.sharedPast(2, 3)?.tick)
        val checkpoint = restored.checkpoint()
        for (branch in checkpoint.branches) {
            val rebuilt = ReplayRequest(terrain, branch.id, branch.head.tick, branch.start, branch.commands)
                .replay().engine.state
            assertEquals(branch.head.canonicalHash(), rebuilt.canonicalHash())
        }
    }

    @Test
    fun queueCanBeCancelledAndHistoricalForkInheritsTheSpend(): Unit = runBlocking {
        val timeline = Timeline.start(terrain())
        repeat(240) { timeline.advance() }
        assertFalse(timeline.intervene(Intervention.Rain, -1))
        assertFalse(timeline.intervene(Intervention.Supplement, 9))
        val region = timeline.recommendedRainRegion()
        assertTrue(timeline.intervene(Intervention.Rain, region))
        assertFalse(timeline.intervene(Intervention.Reforest, 9))
        assertTrue(timeline.cancelShape())
        assertFalse(timeline.cancelShape())
        assertEquals(0, timeline.queuedInterventions)
        assertTrue(timeline.intervene(Intervention.Reforest, 9))
        timeline.advance()
        assertEquals(listOf(Intervention.Reforest), timeline.interventionMarkers(1).map { it.kind })
        assertTrue(timeline.fork("Inherited grove"))
        assertEquals(ShapeBlock.Spent, timeline.preview(Intervention.Rain, region)?.blocked)
        assertEquals(1 to 240L, timeline.warningLocation())
        assertFalse(timeline.intervene(Intervention.Rain, region))
    }

    @Test
    fun sequencingSpendsOnceAndNaturalRainRefillsOncePerSpell() {
        val terrain = terrain()
        val engine = EcologyEngine(terrain, rulesVersion = ShapingRulesVersion)
        repeat(240) { engine.advance() }
        val contested = EcologyEngine(terrain, engine.state, listOf(ReforestRegion(241, 1, 9), CallRainRegion(241, 0, 5)))
        assertNotNull(contested.advance().shaping?.shower)
        assertEquals(listOf(1, 0), contested.interventionResults.map { it.amount })
        assertTrue(contested.state.patches.none { it.planted })
        while (contested.state.shaping?.spent == true) contested.advance()
        assertEquals(Weather.Rain, contested.state.environment?.weather)
        assertEquals(ShapeBlock.AlreadyRaining, contested.preview(Intervention.Rain, 5)?.blocked)
        assertTrue(contested.intervene(Intervention.Reforest, 9))
        val spell = contested.state.environment?.nextChange
        contested.advance()
        assertTrue(contested.state.shaping?.spent == true)
        assertEquals(spell, contested.state.shaping?.refilledSpell)
    }
}
