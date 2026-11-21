package id.branchworld.game.terrain

import id.branchworld.game.ecology.EcologyEngine
import id.branchworld.game.ecology.Intervention
import id.branchworld.game.ecology.IslandEcology
import id.branchworld.game.ecology.Season
import id.branchworld.game.ecology.SettlementEconomy
import id.branchworld.game.ecology.WatershedRulesVersion
import id.branchworld.game.ecology.Weather
import id.branchworld.game.ecology.canonicalHash
import id.branchworld.game.ecology.seasonAt
import id.branchworld.game.save.IncompatibleSave
import id.branchworld.game.save.TimelineSaveCodec
import id.branchworld.game.timeline.Timeline
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.math.abs

class WatershedTest {
    @Test
    fun islandsDrainToTheCoastAndHaveConnectedHabitats() {
        val gallery = listOf(104729L, 271828L, 424242L)
        val digests = mutableSetOf<ULong>()
        val seeds = gallery + (-24L..24L).toList() + listOf(Long.MIN_VALUE, Long.MAX_VALUE)
        for (seed in seeds) {
            val key = TerrainKey(seed, WatershedWorldgenVersion)
            val terrain = IslandGenerator.generate(key)
            val watershed = assertNotNull(terrain.watershed, "seed=$seed")
            assertTrue(digests.add(terrain.digest))
            if (seed in gallery || seed in listOf(Long.MIN_VALUE, Long.MAX_VALUE)) {
                assertEquals(terrain.digest, IslandGenerator.generate(key).digest)
            }
            val reachable = TerrainNavigation(terrain).reachableCells()
            assertEquals(2, watershed.drinkingBanks.size)
            assertTrue(reachable.size >= 350, "seed=$seed")
            for ((x, z) in watershed.drinkingBanks) assertTrue(reachable.any { it.x == x && it.z == z })
            for (z in TerrainMap.MinCell..TerrainMap.MaxCell) for (x in TerrainMap.MinCell..TerrainMap.MaxCell) {
                val tile = watershed.waterAt(x, z) ?: continue
                assertEquals(1, abs(x - tile.downstreamX) + abs(z - tile.downstreamZ))
                val target = watershed.waterAt(tile.downstreamX, tile.downstreamZ)
                if (target == null) {
                    assertTrue(assertNotNull(terrain.cellAt(tile.downstreamX, tile.downstreamZ)).elevationTwice <= 0)
                } else assertTrue(target.surfaceTwice <= tile.surfaceTwice)
                assertTrue(terrain.cellAt(x, z)?.walkable == false)
            }
            for (landmark in watershed.landmarks) {
                var x = landmark.x
                var z = landmark.z
                var previous = Int.MAX_VALUE
                var length = 0
                var tile = watershed.waterAt(x, z)
                assertNotNull(tile)
                while (tile != null) {
                    assertTrue(length++ < TerrainMap.Size * TerrainMap.Size, "drainage cycle on seed=$seed")
                    assertTrue(tile.surfaceTwice <= previous, "upstream step on seed=$seed")
                    previous = tile.surfaceTwice
                    x = tile.downstreamX
                    z = tile.downstreamZ
                    tile = watershed.waterAt(x, z)
                }
                assertTrue(assertNotNull(terrain.cellAt(x, z)).elevationTwice <= 0, "outlet on dry ground for seed=$seed")
            }
        }
        val landscapes = gallery.map { assertNotNull(IslandGenerator.generate(TerrainKey(it, 2)).watershed).landscape }
        assertEquals(listOf(IslandLandscape.Valley, IslandLandscape.Tarn, IslandLandscape.Marsh), landscapes)
    }

    @Test
    fun dryBanksRecoverAndFeedTheEcologyAfterRain() {
        val terrain = IslandGenerator.generate(TerrainKey(104729, 2))
        val engine = EcologyEngine(terrain, rulesVersion = WatershedRulesVersion)
        val start = engine.state
        repeat(299) { engine.advance() }
        val dry = engine.state
        repeat(180) { engine.advance() }
        val rain = engine.state
        assertEquals(64, start.environment?.recharge)
        assertEquals(6, dry.environment?.recharge)
        assertEquals(Weather.Rain, rain.environment?.weather)
        val recharge = assertNotNull(rain.environment?.recharge)
        assertTrue(recharge > 90)
        assertTrue(rain.ledger.regrown - dry.ledger.regrown > 0)
        val field = assertNotNull(rain.settlements?.sites?.firstOrNull()).field
        val economy = SettlementEconomy(IslandEcology(terrain))
        assertTrue(economy.cropYield(field, 3, Weather.Rain, recharge) >
            economy.cropYield(field, 3, Weather.Dry, dry.environment?.recharge))
        repeat(1_200 - 479) { engine.advance() }
        assertEquals(Season.Wet, seasonAt(engine.state.tick))
        engine.validateSavedState()
    }

    @Test
    fun saveAndReplayPreserveBothHistoriesAcrossRain(): Unit = runBlocking {
        val terrain = IslandGenerator.generate(TerrainKey(104729, 2))
        val timeline = Timeline.start(terrain, WatershedRulesVersion)
        repeat(900) { timeline.advance() }
        val untouched = timeline.state.canonicalHash()
        timeline.show(assertNotNull(timeline.seek(1, 260)).replay())
        assertTrue(timeline.fork("Riverside grove"))
        assertTrue(timeline.intervene(Intervention.Reforest, 9)) // C2 in the player picker.
        repeat(640) { timeline.advance() }
        val planted = timeline.state.canonicalHash()
        assertNotEquals(untouched, planted)
        val pair = assertNotNull(timeline.compare(1, 2, 900)).replay()
        assertEquals(untouched, pair.left.hash)
        assertEquals(planted, pair.right.hash)
        assertEquals(0xb9916f92a21d5e42uL, terrain.digest)
        assertEquals("aaed96a98a819688", untouched)
        assertEquals("283d1af71c324474", planted)
        val bytes = TimelineSaveCodec.encode(timeline.checkpoint())
        val restored = TimelineSaveCodec.restore(bytes)
        assertEquals(terrain.digest, restored.terrain.digest)
        assertEquals(timeline.checkpoint(), restored.timeline.checkpoint())
        assertEquals(planted, restored.timeline.state.canonicalHash())
        assertEquals(untouched, assertNotNull(restored.timeline.seek(1, 900)).replay().engine.state.canonicalHash())
        val wrongVersion = bytes.decodeToString().replaceFirst("\"worldgenVersion\":2", "\"worldgenVersion\":1")
        assertFailsWith<IncompatibleSave> { TimelineSaveCodec.restore(wrongVersion.encodeToByteArray()) }
    }

    @Test
    fun gallerySwitchKeepsTerrainVersionsAndAlreadyExploredHistoriesDistinct(): Unit = runBlocking {
        val session = id.branchworld.game.IslandSession()
        val key = TerrainKey(104729, 2)
        session.open(key)
        val valley = session.state.value as id.branchworld.game.IslandLoad.Ready
        session.open(TerrainKey(104729))
        val coast = session.state.value as id.branchworld.game.IslandLoad.Ready
        assertEquals(0xcb401f464692a079uL, coast.world.terrain.digest)
        assertNotEquals(valley.world.terrain.digest, coast.world.terrain.digest)
        session.open(TerrainKey(271828, 2))
        session.open(key)
        assertTrue(session.state.value === valley)
    }
}
