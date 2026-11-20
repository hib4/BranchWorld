package id.branchworld.game.ecology

import id.branchworld.game.IslandSeeds
import id.branchworld.game.simulation.GridCell
import id.branchworld.game.simulation.SetDestination
import id.branchworld.game.terrain.IslandGenerator
import id.branchworld.game.terrain.TerrainKey
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EcologyEngineTest {
    @Test
    fun canonicalReplayMatchesAcrossAdmissionOrdersAndCheckpoint() {
        for ((seed, hashes) in GoldenHashes) {
            val terrain = IslandGenerator.generate(TerrainKey(seed))
            val first = EcologyEngine(terrain)
            val replay = EcologyEngine(terrain)
            val west = assertNotNull(first.steeringTarget(left = true))
            val east = assertNotNull(first.steeringTarget(left = false))
            val commands = listOf(
                SetDestination(1, 0, 1, west),
                SetDestination(300, 0, 1, west),
                SetDestination(300, 1, 1, east),
                SetDestination(601, 0, 1, west),
            )
            assertFalse(first.submit(SetDestination(1, 0, 1, GridCell(31, 31))))
            assertFalse(first.submit(SetDestination(1, 0, 5, west)))
            commands.forEach { assertTrue(first.submit(it)) }
            assertFalse(first.submit(commands.first()))
            commands.reversed().forEach { assertTrue(replay.submit(it)) }
            assertEquals(commands, first.acceptedCommands())
            var checkpoint = first.state
            for ((index, tick) in Checkpoints.withIndex()) {
                val state = first.advanceTo(tick)
                assertEquals(hashes[index], state.canonicalHash(), "seed=$seed tick=$tick")
                assertContentEquals(state.canonicalBytes(), replay.advanceTo(tick).canonicalBytes())
                if (tick == 300L) {
                    assertEquals(east, state.creatures.first { it.id == 1L }.waypoint)
                    checkpoint = state
                }
            }

            val restarted = EcologyEngine(
                IslandGenerator.generate(TerrainKey(seed)),
                checkpoint,
                commands.filter { it.tick > checkpoint.tick },
            )
            assertContentEquals(first.state.canonicalBytes(), restarted.advanceTo(900).canonicalBytes())
            assertFailsWith<IllegalArgumentException> {
                EcologyEngine(IslandGenerator.generate(TerrainKey(seed + 1)), checkpoint)
            }
        }
    }

    @Test
    fun consumedPatchRecoversWhileGrazerIsAwayAndIsReused() {
        val terrain = IslandGenerator.generate(TerrainKey(104729))
        val habitat = IslandEcology(terrain)
        val initial = habitat.initialState()
        val patch = initial.patches.first { it.kind == ForageKind.Meadow }
        val rabbit = Creature(1, Species.Rabbit, patch.cell, food = 500)
        val case = initial.copy(patches = listOf(patch), creatures = listOf(rabbit))
        val grazed = EcologyEngine(terrain, case)
        val control = EcologyEngine(terrain, case.copy(creatures = emptyList()))
        val afterBites = grazed.advanceTo(32)
        val depleted = afterBites.patches.single().biomass
        assertTrue(depleted < patch.capacity)
        assertEquals(patch.capacity, control.advanceTo(32).patches.single().biomass)

        val away = habitat.reachable.maxBy { habitat.terrainDistance(patch.cell)[cellId(it)] }
        assertTrue(grazed.submit(SetDestination(33, 0, rabbit.id, away)))
        val awayState = grazed.advanceTo(52)
        assertTrue(awayState.creatures.single().cell != patch.cell)
        val recovered = awayState.patches.single().biomass
        assertTrue(recovered > depleted)
        assertEquals(afterBites.ledger.consumed, awayState.ledger.consumed)

        assertTrue(grazed.submit(SetDestination(53, 0, rabbit.id, patch.cell)))
        val reused = grazed.advanceTo(160)
        assertTrue(reused.ledger.consumed > awayState.ledger.consumed)
        assertEquals(patch.capacity, control.advanceTo(160).patches.single().biomass)
        assertEquals(
            patch.capacity + reused.ledger.regrown - reused.ledger.consumed,
            reused.patches.single().biomass.toLong(),
        )
        println("ECOLOGY_PATCH,${patch.cell.x},${patch.cell.z},${patch.capacity},$depleted,$recovered,${reused.ledger.consumed}")
    }

    @Test
    fun contestedBiteGoesToLowerIdAndDepletionCausesDeath() {
        val terrain = IslandGenerator.generate(TerrainKey(104729))
        val base = IslandEcology(terrain).initialState()
        val patch = base.patches.first { it.kind == ForageKind.Meadow }.copy(biomass = 7)
        val rabbit = Creature(1, Species.Rabbit, patch.cell, food = 350, goal = Goal(Need.Food, patch.cell))
        val competition = EcologyEngine(terrain, base.copy(
            tick = 3,
            creatures = listOf(rabbit, rabbit.copy(id = 2)),
            patches = listOf(patch),
        )).advance()
        assertEquals(0, competition.patches.single().biomass)
        assertEquals(7L, competition.ledger.consumed)
        assertEquals(369, competition.creatures.first().food)
        assertEquals(348, competition.creatures.last().food)

        val deprived = EcologyEngine(terrain, base.copy(springs = emptyList(), creatures = listOf(rabbit.copy(water = 0))))
        assertEquals(1, deprived.advanceTo(99).creatures.size)
        assertEquals(emptyList(), deprived.advanceTo(100).creatures)
        assertEquals(1L, deprived.state.ledger.deaths)
    }

    @Test
    fun twoProfilesChooseDifferentForageAndUseNeedsToRestAndDrink() {
        val terrain = IslandGenerator.generate(TerrainKey(104729))
        val habitat = IslandEcology(terrain)
        val base = habitat.initialState()
        val meadow = base.patches.first { meadow ->
            meadow.kind == ForageKind.Meadow && base.patches.any {
                it.kind == ForageKind.Browse && habitat.terrainDistance(meadow.cell)[it.id] <= 14
            }
        }
        val rabbit = Creature(1, Species.Rabbit, meadow.cell, food = 300)
        val deer = rabbit.copy(id = 2, species = Species.Deer)
        val state = EcologyEngine(terrain, base.copy(creatures = listOf(rabbit, deer))).advance()
        val patches = base.patches.associateBy { it.cell }
        assertEquals(ForageKind.Meadow, patches[state.creatures[0].goal?.cell]?.kind)
        assertEquals(ForageKind.Browse, patches[state.creatures[1].goal?.cell]?.kind)

        val spring = base.springs.first()
        val forest = habitat.forest.first()
        val water = rabbit.copy(cell = spring, food = 850, water = 300)
        val rest = deer.copy(cell = forest, food = 850, energy = 200)
        val recovery = EcologyEngine(terrain, base.copy(creatures = listOf(water, rest))).advance().creatures
        assertEquals(CreatureAction.Drinking, recovery[0].action)
        assertTrue(recovery[0].water > water.water)
        assertEquals(CreatureAction.Resting, recovery[1].action)
        assertTrue(recovery[1].energy > rest.energy)
    }

    @Test
    fun twoMatureNeighborsBreedWithMonotonicIdAndParentCosts() {
        val terrain = IslandGenerator.generate(TerrainKey(104729))
        val habitat = IslandEcology(terrain)
        val base = habitat.initialState()
        val first = Creature(1, Species.Rabbit, habitat.navigation.spawn, food = 900, water = 900, energy = 900)
        val second = first.copy(id = 2, cell = habitat.navigation.adjacent(first.cell).first())
        val state = EcologyEngine(terrain, base.copy(tick = 119, creatures = listOf(first, second))).advance()
        val baby = state.creatures.last()
        assertEquals(1L, state.ledger.births)
        assertEquals(base.nextEntityId, baby.id)
        assertEquals(baby.id + 1, state.nextEntityId)
        assertEquals(Species.Rabbit, baby.species)
        assertEquals(360L, baby.matureAt)
        assertTrue(baby.cell in habitat.navigation.adjacent(state.creatures.first().cell))
        assertTrue(state.creatures.take(2).all { it.food == 808 && it.energy == 823 && it.breedAfter == 720L })
    }

    @Test
    fun populationsAndBiomassStayBoundedForTenThousandTicks() {
        var births = 0L
        for (seed in IslandSeeds) {
            val terrain = IslandGenerator.generate(TerrainKey(seed))
            val engine = EcologyEngine(terrain)
            val startBiomass = engine.state.patches.sumOf { it.biomass }
            val initialPopulation = engine.state.creatures.size
            assertEquals(2, engine.state.springs.distinct().size)
            assertTrue(engine.state.patches.any { it.kind == ForageKind.Meadow })
            assertTrue(engine.state.patches.any { it.kind == ForageKind.Browse })
            trace(seed, engine.state)
            repeat(10000) {
                val previous = engine.state.creatures.associateBy { it.id }
                val state = engine.advance()
                val rabbits = state.creatures.count { it.species == Species.Rabbit }
                val deer = state.creatures.count { it.species == Species.Deer }
                assertTrue(rabbits in 1..MaxPerSpecies && deer in 1..MaxPerSpecies, "seed=$seed tick=${state.tick}")
                assertTrue(state.patches.all { it.biomass in 0..it.capacity })
                assertEquals(initialPopulation + state.ledger.births - state.ledger.deaths, state.creatures.size.toLong())
                assertEquals(startBiomass + state.ledger.regrown - state.ledger.consumed, state.patches.sumOf { it.biomass }.toLong())
                for (animal in state.creatures) {
                    assertTrue(animal.food in 0..MaxReserve && animal.water in 0..MaxReserve && animal.energy in 0..MaxReserve)
                    val old = previous[animal.id]
                    assertTrue(terrain.cellAt(animal.cell.x, animal.cell.z)?.walkable == true)
                    if (old != null && old.cell != animal.cell) {
                        assertTrue(terrain.canStep(old.cell.x, old.cell.z, animal.cell.x, animal.cell.z))
                    } else if (old == null) {
                        assertEquals(750, animal.food)
                        assertEquals(state.tick + MaturityTicks, animal.matureAt)
                    }
                }
                if (state.tick in Checkpoints || state.tick % 1000L == 0L) trace(seed, state)
            }
            births += engine.state.ledger.births
            assertTrue(engine.state.ledger.consumed > 0 && engine.state.ledger.regrown > 0)
        }
        assertTrue(births > 0)
    }

    private fun trace(seed: Long, state: EcologyState) {
        val rabbits = state.creatures.count { it.species == Species.Rabbit }
        val deer = state.creatures.count { it.species == Species.Deer }
        val biomass = state.patches.sumOf { it.biomass }
        val ledger = state.ledger
        println("ECOLOGY_TRACE,$seed,${state.tick},$rabbits,$deer,$biomass,${ledger.consumed},${ledger.regrown},${ledger.births},${ledger.deaths}")
    }

    private fun EcologyEngine.advanceTo(tick: Long): EcologyState {
        while (state.tick < tick) advance()
        return state
    }

    private companion object {
        val Checkpoints = listOf(0L, 300L, 600L, 900L)
        val GoldenHashes = mapOf(
            104729L to listOf("dd193f96f5107601", "819bf8a8b5f90aba", "21e624c12d88a162", "36968c3f49a728af"),
            271828L to listOf("d7b6836173a38c25", "c1ba93a6c272d09f", "56a447cbd4053d00", "5190d373e78e5010"),
            424242L to listOf("8c4db97a20ac6ec5", "3e175a81e63824b6", "314a894dacf3a30c", "47c358228e7a8363"),
        )
    }
}
