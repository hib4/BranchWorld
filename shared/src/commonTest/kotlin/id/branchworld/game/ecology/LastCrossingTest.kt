package id.branchworld.game.ecology

import id.branchworld.game.save.TimelineSaveCodec
import id.branchworld.game.simulation.ProtectRegion
import id.branchworld.game.terrain.IslandGenerator
import id.branchworld.game.terrain.TerrainKey
import id.branchworld.game.terrain.WatershedWorldgenVersion
import id.branchworld.game.timeline.ReplayRequest
import id.branchworld.game.timeline.Timeline
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.TimeSource

class LastCrossingTest {
    private fun terrain(seed: Long = CrossingSeed) = IslandGenerator.generate(TerrainKey(seed, WatershedWorldgenVersion))

    @Test
    fun theHerdFollowsTheSeasonsAcrossCairnwater() {
        val timeline = Timeline.start(terrain(), RangeRulesVersion, Chapter.LastCrossing)
        assertEquals(RangeRulesVersion, timeline.state.rulesVersion)
        assertEquals(Chapter.LastCrossing, timeline.chapter)
        val route = timeline.herdRoute()
        assertTrue(route.size >= 24)
        assertEquals(timeline.pastureRegion(), route.last().region())
        timeline.advanceTo(1500)
        val herd = assertNotNull(timeline.state.habitat).herd
        assertEquals(1, herd.bound)
        assertEquals(2, herd.crossings)
        assertNotNull(herd.arrivedAt)
        // The ranges are rebuilt from terrain alone, so the sandbox on the same island shares them.
        assertEquals(route, Timeline.start(terrain(), RangeRulesVersion).herdRoute())
        assertNull(Timeline.start(terrain(), RangeRulesVersion).chapter)
    }

    /** Phase 08 gate scenario: one request from the village, three futures for the families and the herd. */
    @Test
    fun theSamePetitionDividesVillageAndHerd(): Unit = runBlocking {
        val terrain = terrain()
        val timeline = Timeline.start(terrain, RangeRulesVersion, Chapter.LastCrossing)
        while (timeline.state.shaping?.warningAt == null) assertTrue(timeline.advance())
        val warning = timeline.state.tick
        val petition = assertNotNull(timeline.openPetition())
        assertEquals(listOf(PlanOption.Pasture, PlanOption.BuildWithin), petition.options)
        val pasture = assertNotNull(timeline.pastureRegion())
        val mainId = timeline.branchId

        assertTrue(timeline.fork("A refuge for the herd"))
        assertTrue(timeline.intervene(Intervention.Protect, pasture))
        assertFalse(timeline.intervene(Intervention.Rain, timeline.recommendedRainRegion()))
        timeline.advanceTo(Horizon)
        val refuge = timeline.state
        val refugeId = timeline.branchId

        timeline.show(assertNotNull(timeline.seek(mainId, warning)).replay())
        assertTrue(timeline.fork("Build within"))
        assertTrue(timeline.endorse(PlanOption.BuildWithin))
        timeline.advanceTo(Horizon)
        val within = timeline.state

        timeline.show(assertNotNull(timeline.seek(mainId, warning)).replay())
        timeline.advanceTo(Horizon)
        val settled = timeline.state

        fun EcologyState.events(kind: RangeEventKind) = checkNotNull(habitat).log.filter { it.kind == kind }
        fun EcologyState.left(cause: Hardship) = checkNotNull(settlements?.village).households.count { it.leftBecause == cause }
        fun EcologyState.deer() = creatures.count { it.species == Species.Deer }
        fun EcologyState.people() = checkNotNull(settlements).sites.sumOf { it.residents }

        // Left to itself, Cairnwater settles the pasture: the herd still crosses but raises no calves there.
        assertEquals(PlanOption.Pasture, settled.settlements!!.village!!.petitions.single().choice)
        assertFalse(settled.settlements.village!!.petitions.single().byKeeper)
        assertEquals(2, settled.settlements.sites.size)
        assertTrue(checkNotNull(settled.habitat).herd.calves < checkNotNull(refuge.habitat).herd.calves)
        assertTrue(settled.events(RangeEventKind.PastureSettled).isNotEmpty())

        // The refuge keeps the pasture quiet: calves are born, but crowded families leave to find room elsewhere.
        assertEquals(1, refuge.settlements!!.sites.size)
        assertEquals(pasture, checkNotNull(refuge.habitat).refuges.single().region)
        assertTrue(checkNotNull(refuge.habitat).herd.calves > 0)
        assertTrue(refuge.left(Hardship.Crowding) > 0)
        assertTrue(refuge.events(RangeEventKind.PastureSettled).isEmpty())

        // Building within only delays the move: the families take the pasture later, after the first calf.
        val withinVillage = within.settlements!!.village!!
        assertTrue(withinVillage.petitions.single().byKeeper)
        assertEquals(2, within.settlements.sites.size)
        val hamlet = withinVillage.chronicle.single { it.kind == VillageEventKind.HamletFounded }
        assertTrue(hamlet.tick > warning + PetitionTurns * SettlementTurn)
        assertTrue(checkNotNull(within.habitat).herd.calves in 1 until checkNotNull(refuge.habitat).herd.calves)

        println("LAST_CROSSING_VALUES deer=${listOf(settled, refuge, within).map { it.deer() }} " +
            "calves=${listOf(settled, refuge, within).map { it.habitat!!.herd.calves }} people=${listOf(settled, refuge, within).map { it.people() }}")
        assertTrue(refuge.deer() > within.deer() && refuge.deer() > settled.deer())
        assertTrue(refuge.people() < within.people() && refuge.people() < settled.people())
        val hashes = listOf(settled, refuge, within).map { it.canonicalHash() }
        println("LAST_CROSSING warning=$warning deer=${listOf(settled, refuge, within).map { it.deer() }} " +
            "people=${listOf(settled, refuge, within).map { it.people() }} hashes=$hashes")
        assertEquals(3, hashes.distinct().size)
        // Pinned from the JVM run of rules v10; the Android host and shared iOS runs must match exactly.
        assertEquals(listOf("12c4be589ed70975", "4e8533e3ea535f68", "94d59b38d4f8d27a"), hashes)

        val bytes = TimelineSaveCodec.encode(timeline.checkpoint())
        assertTrue(bytes.decodeToString().startsWith("""{"schemaVersion":8,"rulesVersion":10"""))
        val restored = TimelineSaveCodec.restore(bytes).timeline
        assertEquals(timeline.checkpoint(), restored.checkpoint())
        for (branch in restored.checkpoint().branches) {
            val rebuilt = ReplayRequest(terrain, branch.id, branch.head.tick, branch.start, branch.commands).replay().engine.state
            assertEquals(branch.head.canonicalHash(), rebuilt.canonicalHash())
        }
        assertEquals(refuge.canonicalHash(), assertNotNull(restored.seek(refugeId, Horizon)).replay().engine.state.canonicalHash())
        assertTrue(restored.checkpoint().branches.single { it.id == refugeId }.commands.any { it is ProtectRegion })
    }

    @Test
    fun rangeRulesStayBoundedOnEveryGalleryIsland() {
        for (seed in listOf(FadingGroveSeed, CrossingSeed, 424242L)) {
            val terrain = terrain(seed)
            val chapter = if (seed == CrossingSeed) Chapter.LastCrossing else null
            val engine = EcologyEngine(terrain, rulesVersion = RangeRulesVersion, recordHistory = false, newChapter = chapter)
            val started = TimeSource.Monotonic.markNow()
            while (engine.state.tick < 24_000) {
                val state = engine.advance()
                if (state.tick % 1_200L == 0L) EcologyEngine(terrain, state, recordHistory = false).validateSavedState()
            }
            val state = engine.state
            val habitat = assertNotNull(state.habitat)
            println("RANGE_SOAK,$seed,${state.canonicalHash()},residents=${state.settlements!!.sites.map { it.residents }}," +
                "species=${PreLifeSpecies.map { s -> state.creatures.count { it.species == s } }},herd=${habitat.herd}," +
                "ms=${started.elapsedNow().inWholeMilliseconds}")
            assertTrue(state.settlements.sites.isNotEmpty())
            assertTrue(PreLifeSpecies.all { species -> state.creatures.any { it.species == species } })
            assertTrue(habitat.herd.crossings >= 18)
        }
    }

    private fun Timeline.advanceTo(tick: Long) {
        while (state.tick < tick) assertTrue(advance())
    }

    private companion object {
        const val Horizon = 7_200L
    }
}
