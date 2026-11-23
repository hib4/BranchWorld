package id.branchworld.game.ecology

import id.branchworld.game.save.TimelineSaveCodec
import id.branchworld.game.terrain.IslandGenerator
import id.branchworld.game.terrain.TerrainKey
import id.branchworld.game.terrain.WatershedWorldgenVersion
import id.branchworld.game.timeline.ChapterOutcome
import id.branchworld.game.timeline.Timeline
import id.branchworld.game.runSimulationTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Phase 09 gate scenario: one dry storm over Heron Reach, four answers, four different woods and villages. */
class EmberSeasonTest {
    private val terrain = IslandGenerator.generate(TerrainKey(EmberSeed, WatershedWorldgenVersion))

    @Test
    fun fireAndItsChronicleArePartOfReplayIdentity() {
        val state = Timeline.start(terrain, FireRulesVersion, Chapter.EmberSeason).state
        val fire = checkNotNull(state.fire)
        val chronicle = checkNotNull(state.chronicle)
        assertTrue(state.canonicalHash() != state.copy(fire = fire.copy(randomState = fire.randomState xor 1uL)).canonicalHash())
        assertTrue(state.canonicalHash() != state.copy(chronicle = chronicle.copy(nextId = chronicle.nextId + 1)).canonicalHash())
    }

    @Test
    fun theDryStormGivesTheVillageAndTheKeeperAChoice() {
        val timeline = Timeline.start(terrain, FireRulesVersion, Chapter.EmberSeason)
        assertEquals(FireRulesVersion, timeline.state.rulesVersion)
        assertEquals(Chapter.EmberSeason, timeline.chapter)
        while (timeline.state.shaping?.warningAt == null) assertTrue(timeline.advance())
        val state = timeline.state
        assertEquals(EmberHollow, state.settlements!!.sites.first().center)
        val fire = assertNotNull(state.fire)
        assertEquals(2, fire.strikes.size)
        assertTrue(fire.strikes.map { tileCell(it.tile).region() }.distinct().size == 2)
        val storm = state.chronicle!!.events.single { it.kind == WorldEventKind.DryStorm }
        assertEquals(fire.wind, storm.detail)
        val petition = assertNotNull(timeline.openPetition())
        assertEquals(PetitionKind.Ember, petition.kind)
        assertEquals(listOf(PlanOption.FellAroundHomes, PlanOption.KeepGrove), petition.options)
        assertFalse(timeline.canCloseChapter)
    }

    @Test
    fun oneStormFourFutures() = runSimulationTest {
        val timeline = Timeline.start(terrain, FireRulesVersion, Chapter.EmberSeason)
        while (timeline.state.shaping?.warningAt == null) assertTrue(timeline.advance())
        val warning = timeline.state.tick
        val mainId = timeline.branchId
        val strikeRegion = tileCell(timeline.state.fire!!.strikes.first().tile).region()
        val villageRegion = timeline.state.settlements!!.sites.first().center.region()
        val preview = assertNotNull(timeline.preview(Intervention.Firebreak, villageRegion))
        assertTrue(preview.cut.isNotEmpty() && preview.blocked == null)

        suspend fun play(name: String, act: Timeline.() -> Unit): EcologyState {
            timeline.show(checkNotNull(timeline.seek(mainId, warning)).replay())
            assertTrue(timeline.fork(name))
            timeline.act()
            while (timeline.state.tick < Horizon) assertTrue(timeline.advance())
            assertTrue(timeline.canCloseChapter)
            return timeline.state
        }
        val felled = play("The village fells") { }
        val firebreak = play("A firebreak") {
            assertTrue(endorse(PlanOption.KeepGrove)); assertTrue(intervene(Intervention.Firebreak, villageRegion))
        }
        val burned = play("Let it burn") { assertTrue(endorse(PlanOption.KeepGrove)) }
        val rained = play("Rain on the ridge") {
            assertTrue(endorse(PlanOption.KeepGrove)); assertTrue(intervene(Intervention.Rain, strikeRegion))
        }

        fun EcologyState.outcome() = ChapterOutcome.of(timeline.summaries().first(), this)
        val outcomes = listOf(felled, firebreak, burned, rained).map { it.outcome() }
        println("EMBER_GATE " + outcomes.map { listOf(it.emberPlan, it.homesBurned, it.pinesFelled, it.pinesBurned, it.people, it.departed) } +
            " tiles=" + listOf(felled, firebreak, burned, rained).map { it.fire!!.tilesBurned } +
            " hashes=" + listOf(felled, firebreak, burned, rained).map { it.canonicalHash() })

        // Left to itself the village fells its woods: the fire is held at the cleared ground and no home burns.
        assertEquals(PlanOption.FellAroundHomes, outcomes[0].emberPlan)
        assertTrue(outcomes[0].pinesFelled > 0 && outcomes[0].homesBurned == 0)
        assertTrue(felled.chronicle!!.events.any { event ->
            event.kind == WorldEventKind.FirebreakHeld && event.causes.any { it.kind == WorldEventKind.TreesFelled }
        })
        // The Keeper's firebreak holds the fire too, without the village losing the woods by its doors.
        assertEquals(0, outcomes[1].homesBurned)
        assertTrue(firebreak.chronicle!!.events.any { event ->
            event.kind == WorldEventKind.FirebreakHeld && event.causes.any { it.kind == WorldEventKind.KeeperAct }
        })
        // Keeping the grove and letting it burn costs a home, and the family that lived there leaves.
        assertTrue(outcomes[2].homesBurned > 0)
        assertEquals(1, outcomes[2].departed[Hardship.Fire])
        assertTrue(burned.fire!!.tilesBurned > felled.fire!!.tilesBurned && burned.fire!!.tilesBurned > firebreak.fire!!.tilesBurned)
        // Rain over the ridge douses the first strike but not the second.
        val fizzled = rained.chronicle!!.events.single { it.kind == WorldEventKind.StrikeFizzled }
        assertTrue(fizzled.causes.any { it.kind == WorldEventKind.KeeperAct })
        assertTrue(rained.chronicle!!.events.any { it.kind == WorldEventKind.FireStarted })

        // The fire family's departure traces back through the burned home and the strike to the dry storm.
        val log = burned.chronicle!!
        val left = log.events.first { it.kind == WorldEventKind.HouseholdLeft }
        val chain = log.causeChain(left).map { it.ref.kind }
        assertEquals(listOf(WorldEventKind.HomeBurned, WorldEventKind.FireStarted, WorldEventKind.Lightning, WorldEventKind.DryStorm), chain)

        assertEquals(PinnedHashes, listOf(felled, firebreak, burned, rained).map { it.canonicalHash() })

        // Schema 9 keeps every history; restoring replays and validates each branch.
        val bytes = TimelineSaveCodec.encode(timeline.checkpoint())
        val restored = TimelineSaveCodec.restore(bytes).timeline
        assertEquals(timeline.checkpoint(), restored.checkpoint())
        for (branch in restored.summaries().drop(1)) {
            val head = assertNotNull(restored.seek(branch.id, Horizon)).replay()
            restored.show(head)
            assertTrue(restored.state.canonicalHash() in PinnedHashes)
        }
    }

    private companion object {
        const val Horizon = 4_800L
        val PinnedHashes = listOf("6b6564a9441bbc48", "d9469247b508b9bc", "6dee3355c163c9e7", "97f7c3a5c313e5ef")
    }
}
