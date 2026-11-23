package id.branchworld.game.ecology

import id.branchworld.game.save.TimelineSaveCodec
import id.branchworld.game.simulation.GridCell
import id.branchworld.game.simulation.PlaceCreature
import id.branchworld.game.simulation.renderProjection
import id.branchworld.game.simulation.ThoughtKind
import id.branchworld.game.terrain.Biome
import id.branchworld.game.terrain.IslandGenerator
import id.branchworld.game.terrain.TerrainKey
import id.branchworld.game.terrain.TerrainMap
import id.branchworld.game.terrain.WatershedWorldgenVersion
import id.branchworld.game.timeline.Timeline
import id.branchworld.game.timeline.WorldCode
import id.branchworld.game.runSimulationTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Rules v12, the living sandbox: the player sets animals down and every consequence comes from the shared rules. */
class LifeRulesTest {
    private fun terrain(seed: Long) = IslandGenerator.generate(TerrainKey(seed, WatershedWorldgenVersion))

    /** Six rabbits on the meadow by the island's heart and three wolves out at the forest's distance. */
    private fun rabbitsAndWolves(engine: EcologyEngine, island: IslandEcology, wolves: Int = 3, rabbits: Int = 6) {
        val spawn = island.navigation.spawn
        val meadow = island.roamCells(Species.Rabbit).sortedBy { octile(it, spawn) }
        val far = island.reachable.filter { octile(it, spawn) in 110..140 }.sortedBy { octile(it, spawn) * 1000 + cellId(it) }
        meadow.filterIndexed { index, _ -> index % 5 == 0 }.take(rabbits).forEach { assertTrue(engine.place(Species.Rabbit, it)) }
        repeat(wolves) { assertTrue(engine.place(Species.Wolf, far[it * 3])) }
    }

    @Test
    fun emptyLandHasTerrainAndPlantsButNoAnimalsVillageOrStory() {
        val timeline = Timeline.start(terrain(FadingGroveSeed), LifeRulesVersion)
        val state = timeline.state
        assertEquals(LifeRulesVersion, state.rulesVersion)
        assertTrue(state.creatures.isEmpty())
        assertNull(state.settlements)
        assertNull(state.habitat)
        assertNull(timeline.chapter)
        assertTrue(state.patches.isNotEmpty())
        assertEquals(WorldStart.Empty, state.life?.start)
        repeat(1200) { timeline.advance() }
        assertTrue(timeline.state.creatures.isEmpty())
    }

    @Test
    fun theWildStartBringsRabbitsDeerAndFoxesButLeavesWolvesToThePlayer() {
        val timeline = Timeline.start(terrain(CrossingSeed), LifeRulesVersion, start = WorldStart.Wild)
        val counts = PreLifeSpecies.associateWith { species -> timeline.state.creatures.count { it.species == species } }
        assertEquals(mapOf(Species.Rabbit to 8, Species.Deer to 4, Species.Fox to 2), counts)
        assertTrue(timeline.state.creatures.none { it.species == Species.Wolf })
        assertEquals(4, timeline.state.creatures.count { it.species == Species.Rabbit && it.life?.sex == Sex.Female })
        repeat(600) { timeline.advance() }
        EcologyEngine(terrain(CrossingSeed), timeline.state).validateSavedState()
    }

    @Test
    fun placementNamesTheReasonItCannotHappen() {
        val terrain = terrain(EmberSeed)
        val island = IslandEcology(terrain)
        val engine = EcologyEngine(terrain, rulesVersion = LifeRulesVersion, island = island)
        val water = (TerrainMap.MinCell..TerrainMap.MinCell + TerrainMap.Size - 1).firstNotNullOf { x ->
            GridCell(x, 0).takeIf { terrain.cellAt(x, 0)?.biome == Biome.Water }
        }
        assertEquals(PlacementCheck.Water, engine.creatureCheck(Species.Rabbit, water))
        val land = island.navigation.spawn
        assertEquals(PlacementCheck.Ok, engine.creatureCheck(Species.Wolf, land))
        repeat(Species.Wolf.lifeLimit()) { assertTrue(engine.place(Species.Wolf, land)) }
        assertEquals(PlacementCheck.SpeciesFull, engine.creatureCheck(Species.Wolf, land))
        assertFalse(engine.place(Species.Wolf, land))
        engine.advance()
        val wolves = engine.state.creatures.filter { it.species == Species.Wolf }
        assertEquals(Species.Wolf.lifeLimit(), wolves.size)
        // Two animals set down one after the other always make a pair.
        assertEquals(wolves.size / 2, wolves.count { it.life?.sex == Sex.Female })
        assertTrue(wolves.all { it.life?.origin == Origin.Placed && it.matureAt == 1L })
        engine.validateSavedState()
    }

    @Test
    fun earlierRulesRefuseTheLivingHand() {
        val terrain = terrain(EmberSeed)
        val engine = EcologyEngine(terrain, rulesVersion = FireRulesVersion)
        assertEquals(PlacementCheck.NotLiving, engine.creatureCheck(Species.Rabbit, IslandEcology(terrain).navigation.spawn))
        assertFalse(engine.place(Species.Rabbit, IslandEcology(terrain).navigation.spawn))
        assertFalse(engine.submit(PlaceCreature(1, 0, Species.Rabbit, IslandEcology(terrain).navigation.spawn)))
    }

    /**
     * The first essential experience: set down rabbits and wolves and leave them. Wolves form a pack, hunt, and some
     * rabbits get away; rabbits dig warrens and breed. Nothing in the rules names these outcomes.
     */
    @Test
    fun rabbitsAndWolvesLiveTheirOwnLivesOnEveryGalleryIsland() {
        for (seed in listOf(FadingGroveSeed, CrossingSeed, EmberSeed)) {
            val terrain = terrain(seed)
            val island = IslandEcology(terrain)
            val engine = EcologyEngine(terrain, rulesVersion = LifeRulesVersion, island = island)
            rabbitsAndWolves(engine, island)
            val thoughts = mutableSetOf<ThoughtKind>()
            var fled = false
            var chased = false
            repeat(3600) {
                val state = engine.advance()
                if (state.creatures.any { it.action == CreatureAction.Fleeing }) fled = true
                if (state.creatures.any { it.action == CreatureAction.Chasing }) chased = true
                if (state.tick % 20 == 0L) state.renderProjection(terrain).creatures.mapNotNullTo(thoughts) { it.thought?.kind }
                if (state.tick % 300 == 0L) engine.validateSavedState()
            }
            val log = checkNotNull(engine.state.chronicle).events.map { it.kind }.toSet()
            assertTrue(WorldEventKind.PackFormed in log, "$seed: no pack")
            assertTrue(WorldEventKind.Hunted in log, "$seed: no hunt")
            assertTrue(WorldEventKind.FirstLitter in log, "$seed: no litter")
            assertTrue(fled && chased, "$seed: fled=$fled chased=$chased")
            assertTrue(engine.state.life!!.warrens.isNotEmpty(), "$seed: no warren")
            assertTrue(engine.state.wildlife!!.catches > 0)
            assertTrue(thoughts.containsAll(listOf(ThoughtKind.Grazing, ThoughtKind.Fleeing, ThoughtKind.Chasing)), "$seed: $thoughts")
        }
    }

    @Test
    fun wolvesWithNothingToHuntStarveAndTheChronicleSaysSo() {
        val terrain = terrain(CrossingSeed)
        val island = IslandEcology(terrain)
        val engine = EcologyEngine(terrain, rulesVersion = LifeRulesVersion, island = island)
        rabbitsAndWolves(engine, island, wolves = 2, rabbits = 0)
        repeat(4000) { engine.advance() }
        assertTrue(engine.state.creatures.none { it.species == Species.Wolf })
        val log = checkNotNull(engine.state.chronicle).events
        // Any pups they raised before the hunger set in starve with them.
        assertEquals(2 + engine.state.ledger.births.toInt(), log.count { it.kind == WorldEventKind.Starved })
        assertTrue(log.any { it.kind == WorldEventKind.Vanished && it.detail == Species.Wolf.canonicalId })
        engine.validateSavedState()
    }

    @Test
    fun removingAnimalsAndClearingPlantsKeepsEveryLedgerBalanced() {
        val terrain = terrain(FadingGroveSeed)
        val island = IslandEcology(terrain)
        val engine = EcologyEngine(terrain, rulesVersion = LifeRulesVersion, island = island)
        val spawn = island.navigation.spawn
        val meadow = island.roamCells(Species.Rabbit).sortedBy { octile(it, spawn) }
        assertTrue(engine.place(Species.Rabbit, meadow[0]))
        assertTrue(engine.place(Species.Deer, meadow[4]))
        val bare = meadow.first { cell -> engine.plantCheck(PlantKind.Tree, cell) == PlacementCheck.Ok }
        assertTrue(engine.plant(PlantKind.Tree, bare))
        val grass = meadow.first { cell -> cell != bare && engine.plantCheck(PlantKind.Meadow, cell) == PlacementCheck.Ok }
        assertTrue(engine.plant(PlantKind.Meadow, grass))
        assertEquals(PlacementCheck.Occupied, engine.plantCheck(PlantKind.Tree, bare))
        repeat(40) { engine.advance() }
        engine.validateSavedState()
        val deer = engine.state.creatures.first { it.species == Species.Deer }
        assertTrue(engine.remove(deer.id))
        assertTrue(engine.clearPlant(grass))
        repeat(40) { engine.advance() }
        val state = engine.state
        assertEquals(listOf(Species.Rabbit), state.creatures.map { it.species })
        assertEquals(1, state.life!!.removed)
        assertEquals(1, state.patches.count { it.planted })
        assertTrue(state.wildlife!!.events.any { it.kind == WildlifeMilestone.Removed })
        engine.validateSavedState()
    }

    /** The same placements on the same island always give the same world, through saves, replays and share codes. */
    @Test
    fun aLivingWorldReplaysSavesAndSharesExactly() = runSimulationTest {
        val terrain = terrain(EmberSeed)
        val island = IslandEcology(terrain)
        fun history(): Timeline {
            val timeline = Timeline.start(terrain, LifeRulesVersion)
            val spawn = island.navigation.spawn
            val meadow = island.roamCells(Species.Rabbit).sortedBy { octile(it, spawn) }
            meadow.filterIndexed { index, _ -> index % 5 == 0 }.take(4).forEach { assertTrue(timeline.place(Species.Rabbit, it)) }
            repeat(200) { timeline.advance() }
            val far = island.reachable.filter { octile(it, spawn) in 110..140 }.sortedBy { octile(it, spawn) * 1000 + cellId(it) }
            assertTrue(timeline.place(Species.Wolf, far[0]))
            assertTrue(timeline.place(Species.Wolf, far[3]))
            repeat(1000) { timeline.advance() }
            return timeline
        }
        val first = history()
        val second = history()
        assertEquals(first.state.canonicalHash(), second.state.canonicalHash())
        assertEquals(LivingHash, first.state.canonicalHash())

        val bytes = TimelineSaveCodec.encode(first.checkpoint())
        val restored = TimelineSaveCodec.restore(bytes).timeline
        assertEquals(first.state.canonicalHash(), restored.state.canonicalHash())
        repeat(300) { first.advance(); restored.advance() }
        assertEquals(first.state.canonicalHash(), restored.state.canonicalHash())

        // Replaying the start with the recorded commands arrives at the same head.
        val root = first.checkpoint().branches.first()
        val replay = EcologyEngine(terrain, root.start, root.commands + root.pending, island = island)
        while (replay.state.tick < root.head.tick) replay.advance()
        assertEquals(root.head.canonicalHash(), replay.state.canonicalHash())

        val code = WorldCode.encode(first.checkpoint())
        val decoded = WorldCode.decode(code)
        assertEquals(first.state.canonicalHash(), decoded.world.timeline.state.canonicalHash())
        assertEquals(WorldStart.Empty, decoded.world.timeline.state.life?.start)
    }

    @Test
    fun aRabbitReadsAsWhatItIsDoing() {
        val terrain = terrain(FadingGroveSeed)
        val island = IslandEcology(terrain)
        val engine = EcologyEngine(terrain, rulesVersion = LifeRulesVersion, island = island)
        assertTrue(engine.place(Species.Rabbit, island.navigation.spawn))
        engine.advance()
        val arriving = engine.state.renderProjection(terrain).creatures.single()
        assertEquals(ThoughtKind.Arriving, arriving.thought?.kind)
        assertNotNull(arriving.life)
        assertEquals(setOf(arriving.id), engine.state.renderProjection(terrain).life?.arrivals)
    }

    private companion object {
        const val LivingHash = "0e8f9645aa123fff"
    }
}
