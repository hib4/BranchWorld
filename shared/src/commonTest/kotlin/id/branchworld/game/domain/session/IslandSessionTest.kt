package id.branchworld.game.domain.session

import id.branchworld.game.runSimulationTest
import id.branchworld.game.domain.IslandSessionKey
import id.branchworld.game.data.WorldRepository
import id.branchworld.simulation.command.ReforestRegion
import id.branchworld.simulation.command.SupplementForage
import id.branchworld.simulation.core.WatershedRulesVersion
import id.branchworld.simulation.ecology.Intervention
import id.branchworld.simulation.terrain.IslandGenerator
import id.branchworld.simulation.terrain.TerrainKey
import id.branchworld.simulation.timeline.Timeline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A world's session over the shared rules: comparison state, cached islands and versioned histories. */
class IslandSessionTest {
    @Test
    fun supersededResultsCannotReplacePreviewAndClosingRestoresTheLiveProjection() = runSimulationTest {
        val terrain = terrain()
        val timeline = Timeline.start(terrain)
        timeline.advanceTo(600)
        timeline.goTo(1, 300)
        assertTrue(timeline.fork("Child"))
        timeline.advanceTo(600)
        timeline.goTo(2, 450)
        val island = IslandLoad.Ready(terrain, timeline)
        val live = island.world.poses.value
        island.openComparison()
        val before = assertNotNull(island.comparison.value?.target)
        island.closeComparison()
        island.openComparison()
        island.loadComparison(before)
        assertNull(island.comparison.value?.pair)
        island.compareAt(400)
        island.compareAt(350)
        island.loadComparison(before)
        assertNull(island.comparison.value?.pair)
        island.loadComparison(assertNotNull(island.comparison.value?.target))
        assertEquals(350L, island.world.poses.value.tick)
        assertEquals(1, island.world.poses.value.branchId)
        island.showComparisonSide(right = true)
        assertEquals(2, island.world.poses.value.branchId)
        assertEquals(350L, island.world.poses.value.tick)
        island.closeComparison()
        assertEquals(live, island.world.poses.value)
        assertEquals(450L, island.checkpoint().viewedTick)
        island.openComparison()
        island.compareAt(350)
        island.loadComparison(assertNotNull(island.comparison.value?.target))
        assertNotNull(island.world.poses.value.livingEcho)
        island.toggleEcho()
        assertNull(island.world.poses.value.livingEcho)
        island.continueComparison()
        assertEquals(1, island.checkpoint().activeBranchId)
        assertEquals(350L, island.checkpoint().viewedTick)
        assertNull(island.comparison.value)
        assertEquals(false, island.clock.playback.value.playing)
    }

    @Test
    fun sharedTrailClipsAtTheForkAndAuditsOnlyTheSelectedCommand() = runSimulationTest {
        val terrain = terrain()
        val initial = Timeline.start(terrain).state
        val timeline = Timeline.restore(terrain, Timeline.root(initial, listOf(
            SupplementForage(1, 0, 6), ReforestRegion(1, 1, 6), ReforestRegion(3, 0, 9),
        )))
        timeline.advanceTo(4)
        timeline.goTo(1, 2)
        assertTrue(timeline.fork("Left"))
        timeline.advanceTo(4)
        timeline.goTo(1, 2)
        assertTrue(timeline.fork("Right"))
        timeline.advanceTo(4)
        val branchPair = assertNotNull(timeline.compare(2, 3, 4)).replay()
        assertEquals(listOf(1, 1), branchPair.sharedTrail.map { it.branchId })
        assertEquals(listOf(0L, 1L), branchPair.sharedTrail.map { it.marker.sequence })
        assertTrue(branchPair.leftTrail.isEmpty())
        assertTrue(branchPair.rightTrail.isEmpty())
        val againstParent = assertNotNull(timeline.compare(1, 3, 4)).replay()
        assertEquals(3L, againstParent.leftTrail.single().marker.tick)
        val island = IslandLoad.Ready(terrain, timeline)
        island.openComparison()
        island.selectComparison(2, 3)
        val saved = island.checkpoint()
        val command = branchPair.sharedTrail.last()
        island.inspectComparisonEvent(command.branchId, command.marker)
        island.loadComparisonEvent(assertNotNull(island.comparison.value?.event))
        val results = assertNotNull(island.comparison.value?.outcomes)
        assertEquals(Intervention.Reforest, results.single().kind)
        assertEquals(6, results.single().amount)
        assertEquals(saved, island.checkpoint())
    }

    @Test
    fun legacyAndModernValleysKeepDistinctSessions() = runSimulationTest {
        val session = WorldRepository()
        val terrain = IslandGenerator.generate(TerrainKey(104729, 2))
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

    @Test
    fun gallerySwitchKeepsTerrainVersionsAndAlreadyExploredHistoriesDistinct() = runSimulationTest {
        val session = WorldRepository()
        val key = TerrainKey(104729, 2)
        session.open(key)
        val valley = session.state.value as IslandLoad.Ready
        session.open(TerrainKey(104729))
        val coast = session.state.value as IslandLoad.Ready
        assertEquals(0xcb401f464692a079uL, coast.world.terrain.digest)
        assertNotEquals(valley.world.terrain.digest, coast.world.terrain.digest)
        session.open(TerrainKey(271828, 2))
        session.open(key)
        assertTrue(session.state.value === valley)
    }

    private fun terrain() = IslandGenerator.generate(TerrainKey(104729))

    private fun Timeline.advanceTo(tick: Long) {
        while (state.tick < tick) assertTrue(advance())
    }

    private suspend fun Timeline.goTo(branch: Int, tick: Long) = show(assertNotNull(seek(branch, tick)).replay())
}
