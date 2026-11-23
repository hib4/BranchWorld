package id.branchworld.game.ecology

import id.branchworld.game.save.TimelineSaveCodec
import id.branchworld.game.simulation.EndorsePlan
import id.branchworld.game.terrain.IslandGenerator
import id.branchworld.game.terrain.TerrainKey
import id.branchworld.game.terrain.WatershedWorldgenVersion
import id.branchworld.game.timeline.ReplayRequest
import id.branchworld.game.timeline.Timeline
import id.branchworld.game.runSimulationTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.TimeSource

class VillageTest {
    private fun terrain(seed: Long = FadingGroveSeed) = IslandGenerator.generate(TerrainKey(seed, WatershedWorldgenVersion))

    @Test
    fun hollowmereIsFoundedByNamedHouseholds() {
        val timeline = Timeline.start(terrain())
        assertEquals(VillageRulesVersion, timeline.state.rulesVersion)
        timeline.advanceTo(60)
        val world = assertNotNull(timeline.state.settlements)
        val village = assertNotNull(world.village)
        val site = world.sites.single()
        assertEquals(listOf(Trade.Farmer, Trade.Woodcutter, Trade.Mason), village.households.map { it.trade })
        assertEquals(3, village.households.map { it.name }.distinct().size)
        assertEquals(site.residents, village.households.sumOf { it.members })
        assertEquals(3, site.homes)
        assertEquals(VillageEventKind.Founded, village.chronicle.single().kind)
        // Names follow the island seed, not the wildlife random stream.
        val again = Timeline.start(terrain())
        again.advanceTo(60)
        assertEquals(village.households.map { it.name }, again.state.settlements?.village?.households?.map { it.name })
    }

    /** Phase 07 gate scenario: one warning, three futures, recorded causes, save and full replay. */
    @Test
    fun theSameWarningLeadsToDifferentVillages() = runSimulationTest {
        val terrain = terrain()
        val timeline = Timeline.start(terrain)
        timeline.advanceTo(240)
        val warning = assertNotNull(timeline.state.shaping?.warningAt)

        assertTrue(timeline.fork("Rain at Hollowmere"))
        assertTrue(timeline.intervene(Intervention.Rain, timeline.recommendedRainRegion()))
        timeline.advanceTo(1500)
        val rain = timeline.state
        val rainId = timeline.branchId

        timeline.show(assertNotNull(timeline.seek(1, warning)).replay())
        assertTrue(timeline.fork("A grove for wildlife"))
        assertTrue(timeline.intervene(Intervention.Reforest, 9))
        timeline.advanceTo(420)
        val petition = assertNotNull(timeline.openPetition())
        assertEquals(PetitionKind.Water, petition.kind)
        assertEquals(listOf(PlanOption.Well, PlanOption.RiverFlats), petition.options)
        val groveId = timeline.branchId
        assertTrue(timeline.fork("The Pells go downriver"))
        assertFalse(timeline.endorse(PlanOption.ClearWoods))
        assertTrue(timeline.endorse(PlanOption.RiverFlats))
        assertFalse(timeline.endorse(PlanOption.Well))
        assertTrue(timeline.cancelEndorsement())
        assertTrue(timeline.endorse(PlanOption.RiverFlats))
        timeline.advanceTo(1500)
        val flats = timeline.state
        val flatsId = timeline.branchId

        timeline.show(assertNotNull(timeline.seek(groveId, 420)).replay())
        timeline.advanceTo(1500)
        val grove = timeline.state

        fun VillageState.has(kind: VillageEventKind) = chronicle.filter { it.kind == kind }
        val rainVillage = assertNotNull(rain.settlements?.village)
        val groveVillage = assertNotNull(grove.settlements?.village)
        val flatsVillage = assertNotNull(flats.settlements?.village)

        // Rain at the warning: nobody suffers thirst and Hollowmere never needs to ask about water.
        assertTrue(rainVillage.has(VillageEventKind.Thirst).isEmpty())
        assertTrue(rainVillage.has(VillageEventKind.HouseholdLeft).isEmpty())
        assertTrue(rainVillage.petitions.none { it.kind == PetitionKind.Water })

        // The grove alone does not reach the village in time: a household leaves because of thirst.
        val left = groveVillage.has(VillageEventKind.HouseholdLeft).single()
        assertEquals(Hardship.Thirst.code, left.detail)
        assertEquals(Hardship.Thirst, groveVillage.households.single { it.id == left.subject }.leftBecause)
        val decided = groveVillage.petitions.single { it.kind == PetitionKind.Water }
        assertFalse(decided.byKeeper)
        assertEquals(PlanOption.Well, decided.choice)
        assertTrue(groveVillage.structures.any { it.kind == StructureKind.Well && it.complete })

        // The Keeper's endorsement founds a hamlet in another place and keeps every household.
        assertTrue(flatsVillage.has(VillageEventKind.HouseholdLeft).none { it.tick <= 540 })
        assertEquals(2, flats.settlements?.sites?.size)
        val hamlet = flats.settlements!!.sites[1]
        assertNotEquals(flats.settlements.sites[0].center, hamlet.center)
        assertTrue(flatsVillage.petitions.single().byKeeper)
        assertTrue(flatsVillage.has(VillageEventKind.HamletFounded).isNotEmpty())
        assertTrue(flatsVillage.structures.none { it.kind == StructureKind.Well })

        val residents = listOf(rain, grove, flats).map { state -> state.settlements!!.sites.sumOf { it.residents } }
        val homes = listOf(rain, grove, flats).map { state -> state.settlements!!.sites.sumOf { it.homes } }
        val hashes = listOf(rain, grove, flats).map { it.canonicalHash() }
        println("HOLLOWMERE_DIVERGENCE residents=$residents homes=$homes hashes=$hashes")
        println("HOLLOWMERE_CHRONICLE rain=${rainVillage.chronicle}\ngrove=${groveVillage.chronicle}\nflats=${flatsVillage.chronicle}")
        assertEquals(3, hashes.distinct().size)
        // Pinned from matching JVM, Android host and shared iOS runs of rules v9.
        assertEquals(listOf("5b833cb3915a6c38", "b9bef37b5d213fe0", "0b45d2a4adaf82c4"), hashes)

        val bytes = TimelineSaveCodec.encode(timeline.checkpoint())
        assertTrue(bytes.decodeToString().startsWith("""{"schemaVersion":7,"rulesVersion":9"""))
        val restored = TimelineSaveCodec.restore(bytes).timeline
        assertEquals(timeline.checkpoint(), restored.checkpoint())
        for (branch in restored.checkpoint().branches) {
            val rebuilt = ReplayRequest(terrain, branch.id, branch.head.tick, branch.start, branch.commands).replay().engine.state
            assertEquals(branch.head.canonicalHash(), rebuilt.canonicalHash())
        }
        assertEquals(rain.canonicalHash(), assertNotNull(restored.seek(rainId, 1500)).replay().engine.state.canonicalHash())
        assertEquals(flats.canonicalHash(), assertNotNull(restored.seek(flatsId, 1500)).replay().engine.state.canonicalHash())
        assertTrue(restored.checkpoint().branches.single { it.id == flatsId }.commands.any { it is EndorsePlan })
    }

    @Test
    fun anUnansweredPetitionIsDecidedByTheVillageAtItsDeadline() {
        val timeline = Timeline.start(terrain())
        timeline.advanceTo(420)
        val petition = assertNotNull(timeline.openPetition())
        timeline.advanceTo(petition.deadline - 1)
        assertNotNull(timeline.openPetition())
        timeline.advanceTo(petition.deadline)
        assertNull(timeline.openPetition())
        assertFalse(timeline.endorse(PlanOption.Well))
        val decided = timeline.state.settlements!!.village!!.petitions.single()
        assertEquals(petition.deadline, decided.decidedAt)
        assertFalse(decided.byKeeper)
        assertTrue(timeline.state.settlements!!.village!!.chronicle.any {
            it.kind == VillageEventKind.PlanChosen && it.subject == petition.id && it.tick == petition.deadline
        })
    }

    @Test
    fun clearingTheWoodsTradesBrowseForFarmland() = runSimulationTest {
        val terrain = terrain()
        val timeline = Timeline.start(terrain)
        timeline.advanceTo(240)
        assertTrue(timeline.intervene(Intervention.Rain, timeline.recommendedRainRegion()))
        while (timeline.openPetition()?.kind != PetitionKind.Growth) assertTrue(timeline.advance())
        val before = timeline.state
        assertTrue(timeline.endorse(PlanOption.ClearWoods))
        timeline.advanceTo(before.tick + 600)
        val village = assertNotNull(timeline.state.settlements?.village)
        assertEquals(2, village.cleared.size)
        assertTrue(timeline.state.patches.none { it.id in village.cleared })
        assertEquals(2, village.fields(1).size)
        val browseBefore = before.patches.filter { it.kind == ForageKind.Browse }.sumOf { it.capacity }
        val browseAfter = timeline.state.patches.filter { it.kind == ForageKind.Browse }.sumOf { it.capacity }
        assertTrue(browseAfter < browseBefore)
        EcologyEngine(terrain, timeline.state, recordHistory = false).validateSavedState()
    }

    @Test
    fun villageRulesStayBoundedOnEveryGalleryIsland() {
        val grazing = mutableListOf<Long>()
        for (seed in listOf(FadingGroveSeed, 271828L, 424242L)) {
            val terrain = terrain(seed)
            val engine = EcologyEngine(terrain, rulesVersion = VillageRulesVersion, recordHistory = false)
            val started = TimeSource.Monotonic.markNow()
            var grazed = false
            while (engine.state.tick < 24_000) {
                val state = engine.advance()
                if ((state.settlements?.village?.grazing ?: 0) > 0) grazed = true
                if (state.tick % 1_200L == 0L) EcologyEngine(terrain, state, recordHistory = false).validateSavedState()
            }
            val state = engine.state
            val village = assertNotNull(state.settlements?.village)
            println("VILLAGE_SOAK,$seed,${state.canonicalHash()},residents=${state.settlements!!.sites.map { it.residents }}," +
                "households=${village.households.count { it.active }},structures=${village.structures.size}," +
                "species=${PreLifeSpecies.map { s -> state.creatures.count { it.species == s } }},ms=${started.elapsedNow().inWholeMilliseconds}")
            assertTrue(state.settlements.sites.isNotEmpty())
            assertTrue(PreLifeSpecies.all { species -> state.creatures.any { it.species == species } })
            if (grazed) grazing.add(seed)
            println("VILLAGE_SOAK_EVENTS,$seed,${village.chronicle.groupingBy { it.kind }.eachCount()}")
        }
        // Wildlife only raids fields where herbivores actually feed nearby; the valley's headwater field is one.
        assertTrue(FadingGroveSeed in grazing)
    }

    private fun Timeline.advanceTo(tick: Long) {
        while (state.tick < tick) assertTrue(advance())
    }
}
