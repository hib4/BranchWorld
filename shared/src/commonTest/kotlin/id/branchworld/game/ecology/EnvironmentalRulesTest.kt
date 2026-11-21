package id.branchworld.game.ecology

import id.branchworld.game.IslandSeeds
import id.branchworld.game.save.WorldCheckpoint
import id.branchworld.game.save.WorldSaveCodec
import id.branchworld.game.simulation.ReforestRegion
import id.branchworld.game.simulation.SetDestination
import id.branchworld.game.simulation.SupplementForage
import id.branchworld.game.simulation.renderProjection
import id.branchworld.game.terrain.IslandGenerator
import id.branchworld.game.terrain.TerrainKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EnvironmentalRulesTest {
    @Test
    fun checkpointAndReversedAdmissionReplayMatchAcrossThreeSeeds() {
        for ((seed, hashes) in GoldenHashes) {
            val terrain = IslandGenerator.generate(TerrainKey(seed))
            val live = EcologyEngine(terrain, rulesVersion = EnvironmentalRulesVersion)
            val replay = EcologyEngine(terrain, rulesVersion = EnvironmentalRulesVersion)
            val region = live.inspectRegions().indexOfFirst { it.canPlant }
            val commands = listOf(
                SetDestination(1, 0, 1, assertNotNull(live.steeringTarget(left = true))),
                ReforestRegion(301, 0, region),
                SupplementForage(301, 1, region),
            )
            assertFalse(live.submit(ReforestRegion(1, 0, RegionCount)))
            commands.forEach { assertTrue(live.submit(it)) }
            assertFalse(live.submit(SupplementForage(301, 0, region)))
            commands.reversed().forEach { assertTrue(replay.submit(it)) }
            var restarted: EcologyEngine? = null
            for ((index, tick) in listOf(0L, 300L, 600L, 900L).withIndex()) {
                live.advanceTo(tick)
                val hash = live.state.canonicalHash()
                assertEquals(hashes[index], hash, "seed=$seed tick=$tick")
                assertEquals(hash, replay.advanceTo(tick).canonicalHash())
                if (tick == 300L) {
                    val bytes = WorldSaveCodec.encode(WorldCheckpoint(live.state, live.pendingCommands()))
                    restarted = WorldSaveCodec.restore(bytes).engine
                    assertEquals(commands.drop(1), restarted.pendingCommands())
                }
                restarted?.let { assertEquals(hash, it.advanceTo(tick).canonicalHash()) }
                println("ENVIRONMENT_HASH,$seed,$tick,$hash")
            }
        }
    }

    @Test
    fun rainDoublesActualGrowthAndDaylightFollowsTick() {
        val terrain = IslandGenerator.generate(TerrainKey(104729))
        val live = EcologyEngine(terrain, rulesVersion = EnvironmentalRulesVersion)
        val dry = live.advanceTo(299)
        assertEquals(Weather.Dry, dry.environment?.weather)
        assertEquals(Weather.Rain, live.advance().environment?.weather)
        val wet = live.state
        val rainEnd = assertNotNull(wet.environment).nextChange
        assertTrue(rainEnd - 300 in 240..360)
        assertEquals(Weather.Dry, live.advanceTo(rainEnd).environment?.weather)
        assertFalse(live.advanceTo(900).renderProjection(terrain).climate!!.isDaylight)
        val last = assertNotNull(live.advanceTo(1199).renderProjection(terrain).climate)
        val next = assertNotNull(live.advanceTo(1200).renderProjection(terrain).climate)
        assertEquals(1199f / 1200f, last.dayPhase)
        assertEquals(0f, next.dayPhase)
        assertEquals(last.day + 1, next.day)
        assertTrue(next.isDaylight)

        val patch = dry.patches.first().copy(biomass = 20)
        val control = dry.copy(patches = listOf(patch), creatures = emptyList())
        val dryResult = EcologyEngine(terrain, control.copy(environment = dry.environment?.copy(nextChange = 301))).advance()
        val rainResult = EcologyEngine(terrain, control).advance()
        assertEquals(patch.regrowth.toLong(), dryResult.ledger.regrown - dry.ledger.regrown)
        assertEquals(patch.regrowth * 2L, rainResult.ledger.regrown - dry.ledger.regrown)
        println("WEATHER_GROWTH,300,${dryResult.patches.single().biomass},${rainResult.patches.single().biomass}")
    }

    @Test
    fun interventionsChangeRealForageAndDeerEatPlantedBrowse() {
        val terrain = IslandGenerator.generate(TerrainKey(104729))
        val control = EcologyEngine(terrain, rulesVersion = EnvironmentalRulesVersion)
        val checkpoint = control.advanceTo(300)
        val region = 6 // B3
        val trees = EcologyEngine(terrain, checkpoint)
        val supply = EcologyEngine(terrain, checkpoint)
        assertTrue(trees.intervene(Intervention.Reforest, region))
        assertFalse(trees.intervene(Intervention.Reforest, region))
        assertTrue(supply.intervene(Intervention.Supplement, region))
        val before = control.state.patches.sumOf { it.biomass }
        val unchanged = control.advance().patches.sumOf { it.biomass }
        val planted = trees.advance().patches.filter { it.planted }
        val supplemented = supply.advance().patches.sumOf { it.biomass }
        assertTrue(planted.size in 1..6)
        assertTrue(planted.all { it.cell.region() == region && it.kind == ForageKind.Browse })
        assertEquals(planted.size * 30L, trees.state.ledger.introduced)
        assertEquals(supplemented - unchanged, supply.state.ledger.introduced.toInt())
        assertTrue(supplemented > unchanged && supplemented - unchanged <= 120)
        assertTrue(trees.state.renderProjection(terrain).forage.any { it.planted })
        println("INTERVENTIONS,301,$region,$before,$unchanged,${planted.size},${trees.state.ledger.introduced},$supplemented")

        repeat(599) {
            val prior = trees.state
            val after = trees.advance()
            val deer = grazingPlanted(prior, after)
            if (deer != null) {
                println("DEER_BROWSE,${after.tick},${deer.id},${deer.cell}")
                assertTrue(trees.advanceTo(900).canonicalHash() != control.advanceTo(900).canonicalHash())
                return
            }
        }
        error("No deer consumed the newly planted browse by tick 900")
    }

    private fun grazingPlanted(prior: EcologyState, after: EcologyState): Creature? = after.creatures.firstOrNull { animal ->
        animal.species == Species.Deer && animal.action == CreatureAction.Eating &&
            after.patches.any { patch ->
                patch.planted && patch.cell == animal.cell &&
                    patch.biomass < (prior.patches.firstOrNull { it.cell == patch.cell }?.biomass ?: 0)
            }
    }

    @Test
    fun threeSeedEcologiesStayBoundedForTenThousandTicks() {
        for (seed in IslandSeeds) {
            val terrain = IslandGenerator.generate(TerrainKey(seed))
            val engine = EcologyEngine(terrain, rulesVersion = EnvironmentalRulesVersion)
            val start = engine.state.patches.sumOf { it.biomass }
            val region = engine.inspectRegions().indexOfFirst { it.canPlant }
            assertTrue(engine.submit(ReforestRegion(301, 0, region)))
            assertTrue(engine.submit(SupplementForage(601, 0, region)))
            repeat(10_000) {
                val state = engine.advance()
                assertTrue(state.patches.all { it.biomass in 0..it.capacity })
                for (species in LegacySpecies) assertTrue(state.creatures.count { it.species == species } in 1..MaxPerSpecies)
                assertEquals(start + state.ledger.introduced + state.ledger.regrown - state.ledger.consumed,
                    state.patches.sumOf { it.biomass }.toLong())
            }
            println("ENVIRONMENT_LONG,$seed,${engine.state.creatures.groupingBy { it.species }.eachCount()},${engine.state.patches.sumOf { it.biomass }}")
        }
    }

    private fun EcologyEngine.advanceTo(tick: Long): EcologyState {
        while (state.tick < tick) advance()
        return state
    }

    private companion object {
        val GoldenHashes = mapOf(
            104729L to listOf("b5f4de1f7a287385", "fa5c5e4499205d37", "8ccda88b601ba261", "33e4fbb96e965e96"),
            271828L to listOf("ebb9ae14855fe4a2", "8c481cf0d1454316", "770900245139c668", "8f8b438fcf783c7e"),
            424242L to listOf("02c833498263081a", "98abfd030a0b6957", "5261c5bc3f9ae40a", "68d2273e116636d8"),
        )
    }
}
