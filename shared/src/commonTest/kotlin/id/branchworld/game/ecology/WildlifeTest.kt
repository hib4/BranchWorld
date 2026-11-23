package id.branchworld.game.ecology

import id.branchworld.game.IslandLoad
import id.branchworld.game.IslandSession
import id.branchworld.game.IslandSessionKey
import id.branchworld.game.save.TimelineSaveCodec
import id.branchworld.game.simulation.renderProjection
import id.branchworld.game.simulation.GridCell
import id.branchworld.game.terrain.Biome
import id.branchworld.game.terrain.IslandGenerator
import id.branchworld.game.terrain.TerrainKey
import id.branchworld.game.timeline.Timeline
import id.branchworld.game.runSimulationTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.TimeSource

class WildlifeTest {
    @Test
    fun competingFoxesResolveOneCatchAndCoverProtectsTheRabbit() {
        val terrain = terrain()
        val island = IslandEcology(terrain)
        val start = EcologyEngine(terrain, rulesVersion = WildlifeRulesVersion).state
        fun pursue(cell: GridCell): EcologyState {
            val prey = start.creatures.first().copy(cell = cell)
            val foxes = start.creatures.filter { it.species == Species.Fox }.map {
                it.copy(cell = cell, food = 400, action = CreatureAction.Chasing,
                    wildlife = checkNotNull(it.wildlife).copy(prey = prey.id, huntUntil = 20))
            }
            // The sixth tick is a skipped step while fleeing.
            return EcologyEngine(terrain, start.copy(tick = 5, creatures = listOf(prey) + foxes)).advance()
        }
        val exposed = island.reachable.first { terrain.terrainCell(it).biome == Biome.Meadow }
        val caught = pursue(exposed)
        assertEquals(listOf(8L, 9L), caught.creatures.map { it.id })
        assertEquals(1L, caught.wildlife?.catches)
        assertEquals(WildlifeMilestone.Caught, caught.wildlife?.events?.single()?.kind)
        assertEquals(CreatureAction.Eating, caught.creatures[0].action)
        assertEquals(1L, caught.ledger.deaths)
        assertTrue(caught.creatures.all { it.wildlife?.prey == null })
        val sheltered = pursue(island.forest.first())
        assertEquals(listOf(1L, 8L, 9L), sheltered.creatures.map { it.id })
        assertEquals(CreatureAction.Fleeing, sheltered.creatures.first().action)
        assertTrue(sheltered.creatures.filter { it.species == Species.Fox }.all { it.action == CreatureAction.Stalking })
        assertEquals(0L, sheltered.wildlife?.catches)
    }

    @Test
    fun droughtAndScarcityCanChangeForageChoiceAndCauseLocalLoss() {
        val terrain = terrain()
        val island = IslandEcology(terrain)
        val opening = EcologyEngine(terrain, rulesVersion = WildlifeRulesVersion).state
        val rabbit = opening.creatures.first().copy(food = 500)
        val meadows = opening.patches.filter { it.kind == ForageKind.Meadow }
        fun siteAt(recharge: Int, animal: Creature, patches: List<ForagePatch>) =
            EcologyEngine(terrain, opening.copy(
                creatures = listOf(animal), patches = patches, environment = opening.environment?.copy(recharge = recharge),
            )).advance().creatures.single().goal?.cell
        val firstSite = assertNotNull(meadows.firstNotNullOfOrNull { first ->
            meadows.firstNotNullOfOrNull { second ->
                if (first.id >= second.id) return@firstNotNullOfOrNull null
                val patches = listOf(first, second)
                val dry = siteAt(4, rabbit, patches)
                val wet = siteAt(230, rabbit, patches)
                if (dry != wet) patches else null
            }
        }, "Local moisture should favor different patches at the start and end of a drought")
        assertNotEquals(siteAt(4, rabbit, firstSite), siteAt(230, rabbit, firstSite))
        val hungry = rabbit.copy(cell = island.navigation.spawn, food = 0, depletedTicks = SurvivalTicks - 1)
        val noForage = EcologyEngine(terrain, opening.copy(creatures = listOf(hungry), patches = emptyList())).advance()
        assertTrue(noForage.creatures.isEmpty())
        assertEquals(WildlifeMilestone.Scarcity, noForage.wildlife?.events?.single()?.kind)
    }

    @Test
    fun allGalleryHabitatsSustainWildlifeAcrossTwentyDays() {
        val summaries = gallery.mapIndexed { islandIndex, seed ->
            val terrain = terrain(seed)
            val engine = EcologyEngine(terrain, rulesVersion = WildlifeRulesVersion)
            val opening = engine.state
            val initialBiomass = opening.patches.sumOf { it.biomass }
            val actions = mutableSetOf<CreatureAction>()
            val moments = mutableSetOf<WildlifeMilestone>()
            var flight: Pair<Long, Long>? = null
            val timing = LongArray(24_000)
            repeat(timing.size) { index ->
                val prior = engine.state
                val start = TimeSource.Monotonic.markNow()
                val state = engine.advance()
                timing[index] = start.elapsedNow().inWholeMicroseconds
                val history = assertNotNull(state.wildlife)
                assertTrue(history.events.size <= 32)
                for (animal in state.creatures) {
                    actions.add(animal.action)
                    val old = prior.creatures.firstOrNull { it.id == animal.id }
                    if (old != null && old.cell != animal.cell) {
                        assertTrue(terrain.canStep(old.cell.x, old.cell.z, animal.cell.x, animal.cell.z),
                            "seed=$seed tick=${state.tick} species=${animal.species}")
                    }
                }
                for (event in history.events) moments.add(event.kind)
                if (flight == null) {
                    state.creatures.firstOrNull { it.action == CreatureAction.Fleeing && it.cell != it.goal?.cell }
                        ?.let { flight = state.tick to it.id }
                }
                if (index % 300 == 0) {
                    assertEquals(initialBiomass + state.ledger.regrown + state.ledger.introduced,
                        state.patches.sumOf { it.biomass.toLong() } + state.ledger.consumed)
                    engine.validateSavedState()
                }
            }
            val state = engine.state
            val live = PreLifeSpecies.associateWith { species -> state.creatures.count { it.species == species } }
            val sampled = timing.sorted()
            println("Wildlife long run seed=$seed live=$live catches=${state.wildlife?.catches} " +
                "births=${state.ledger.births} deaths=${state.ledger.deaths} events=$moments firstFlight=$flight actions=$actions " +
                "hash=${state.canonicalHash()} medianUs=${sampled[sampled.size / 2]} p95Us=${sampled[sampled.size * 95 / 100]}")
            assertEquals(longRunHashes[islandIndex], state.canonicalHash())
            Triple(live, actions, moments)
        }
        summaries.forEachIndexed { index, (live, actions, moments) ->
            val seed = gallery[index]
            for ((species, count) in live) assertTrue(count in 1..species.populationLimit(), "seed=$seed: $live")
            assertTrue(CreatureAction.Stalking in actions && CreatureAction.Fleeing in actions, "seed=$seed")
            assertTrue(WildlifeMilestone.Birth in moments && WildlifeMilestone.OldAge in moments, "seed=$seed")
        }
        assertTrue(summaries.any { WildlifeMilestone.Caught in it.third })
    }

    @Test
    fun plantedAndUntouchedHistoriesReplayAtTheSameTime() = runSimulationTest {
        val terrain = terrain()
        val timeline = Timeline.start(terrain, rulesVersion = WildlifeRulesVersion)
        repeat(900) { timeline.advance() }
        timeline.show(assertNotNull(timeline.seek(1, 260)).replay())
        val region = 9 // C2.
        val tick = 900L
        val untouched = assertNotNull(timeline.seek(1, tick)).replay().engine.state
        assertTrue(timeline.fork("Woodland refuge"))
        assertTrue(timeline.intervene(Intervention.Reforest, region))
        var usedRestoredHabitat = false
        while (timeline.state.tick < tick) {
            timeline.advance()
            val state = timeline.state
            if (!usedRestoredHabitat) {
                usedRestoredHabitat = state.patches.any { patch ->
                    patch.planted && state.creatures.any {
                        it.cell == patch.cell && (it.action == CreatureAction.Eating || it.action == CreatureAction.Resting)
                    }
                }
            }
        }
        assertTrue(usedRestoredHabitat, "Wildlife used the planted patch for forage or shelter")
        val hash = timeline.state.canonicalHash()
        val pair = assertNotNull(timeline.compare(1, 2, tick)).replay()
        println("Valley C2 at $tick main=${pair.left.hash} grove=${pair.right.hash} " +
            "${trajectory(untouched)} -> ${trajectory(timeline.state)}")
        assertEquals(listOf(6L, 4L, 3L, 4L, 0L, 0L), trajectory(untouched))
        assertEquals(listOf(8L, 4L, 3L, 6L, 0L, 0L), trajectory(timeline.state))
        assertEquals("a53706087e3542d9", pair.left.hash)
        assertEquals("bdb89cca564bb231", pair.right.hash)
        assertTrue(untouched.creatures.any { original ->
            timeline.state.creatures.any { variant -> variant.id == original.id && variant.goal != original.goal }
        })
        assertEquals(hash, pair.right.hash)
        assertNotEquals(pair.left.hash, pair.right.hash)
        val bytes = TimelineSaveCodec.encode(timeline.checkpoint())
        assertTrue(bytes.decodeToString().startsWith("""{"schemaVersion":5,"rulesVersion":7"""))
        val restored = TimelineSaveCodec.restore(bytes)
        assertEquals(timeline.checkpoint(), restored.timeline.checkpoint())
        assertEquals(timeline.state.renderProjection(terrain).wildlife, restored.timeline.state.renderProjection(terrain).wildlife)
        assertEquals(hash, restored.timeline.state.canonicalHash())
        val reloadedPair = assertNotNull(restored.timeline.compare(1, 2, tick)).replay()
        assertEquals(pair, reloadedPair)
    }

    @Test
    fun legacyAndModernValleysKeepDistinctSessions() = runSimulationTest {
        val session = IslandSession()
        val terrain = terrain()
        val oldKey = IslandSessionKey(terrain.key, WatershedRulesVersion)
        val modernKey = IslandSessionKey(terrain.key)
        session.open(oldKey)
        val legacy = session.state.value as IslandLoad.Ready
        assertEquals(null, legacy.clock.state.wildlife)
        session.open(modernKey)
        val modern = session.state.value as IslandLoad.Ready
        assertNotNull(modern.clock.state.wildlife)
        assertEquals(legacy.world.terrain.digest, modern.world.terrain.digest)
        session.open(oldKey)
        assertSame(legacy, session.state.value)
        session.open(modernKey)
        assertSame(modern, session.state.value)
    }

    private fun trajectory(state: EcologyState): List<Long> =
        PreLifeSpecies.map { species -> state.creatures.count { it.species == species }.toLong() } +
            listOf(state.ledger.births, state.ledger.deaths, assertNotNull(state.wildlife).catches)

    private fun terrain(seed: Long = 104729) = IslandGenerator.generate(TerrainKey(seed, 2))

    private val longRunHashes = listOf("f2bc429d02f45a8d", "4a69d3a852cf1a14", "daffc693157f3e49")
    private val gallery = listOf(104729L, 271828L, 424242L)
}
