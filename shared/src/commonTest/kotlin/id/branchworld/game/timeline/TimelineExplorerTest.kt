package id.branchworld.game.timeline

import id.branchworld.game.IslandLoad
import id.branchworld.game.ecology.Intervention
import id.branchworld.game.save.TimelineSaveCodec
import id.branchworld.game.simulation.ReforestRegion
import id.branchworld.game.simulation.SupplementForage
import id.branchworld.game.terrain.IslandGenerator
import id.branchworld.game.terrain.TerrainKey
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimelineExplorerTest {
    @Test
    fun northStarComparisonIsCanonicalAndLeavesHistoriesAndQueuesIntact() = runBlocking {
        val terrain = terrain()
        val timeline = Timeline.start(terrain)
        timeline.advanceTo(900)
        timeline.steer(left = true)
        timeline.goTo(1, 300)
        assertTrue(timeline.fork("Alternative"))
        assertTrue(timeline.renameActive(" Restored Forest "))
        assertFalse(timeline.renameActive(" "))
        assertTrue(timeline.intervene(Intervention.Reforest, 6))
        assertEquals(true, timeline.interventionMarkers(2, includePending = true).single().queued)
        assertNull(timeline.eventReplay(2, 301))
        timeline.advanceTo(900)
        timeline.steer(left = false)
        timeline.goTo(2, 700)

        val saved = timeline.checkpoint()
        val pair = assertNotNull(timeline.compare(1, 2, 900)).replay()
        assertEquals(900L, pair.tick)
        assertEquals("689a2ab6f9063004", pair.left.hash)
        assertEquals("6af1190dbe1f4a7f", pair.right.hash)
        assertEquals(9893L, pair.left.metrics.forage)
        assertEquals(11105L, pair.right.metrics.forage)
        assertEquals(6L, pair.right.metrics.saplings - pair.left.metrics.saplings)
        assertEquals(4L, pair.left.metrics.rabbits)
        assertEquals(3L, pair.right.metrics.deer)
        assertTrue(pair.left.events.isEmpty())
        assertEquals(301L, pair.right.events.single().tick)
        assertEquals(6, pair.right.events.single().region)
        assertEquals(saved, timeline.checkpoint())
        val reloaded = TimelineSaveCodec.restore(TimelineSaveCodec.encode(saved)).timeline
        assertEquals("Restored Forest", reloaded.summaries().last().name)
        assertEquals(pair, assertNotNull(reloaded.compare(1, 2, 900)).replay())
        assertEquals(saved, reloaded.checkpoint())
        println("EXPLORER_900,${pair.left.hash},${pair.right.hash},${pair.left.metrics.forage},${pair.right.metrics.forage}")
    }

    @Test
    fun eventAuditReplaysBeforeAnExactCheckpointAndSurvivesEviction() = runBlocking {
        val terrain = terrain()
        val startingState = Timeline.start(terrain).state
        val base = Timeline.restore(terrain, Timeline.root(startingState, listOf(
            SupplementForage(1, 0, 6), ReforestRegion(1, 1, 6), ReforestRegion(300, 0, 9),
        )))
        val first = assertNotNull(base.interventionMarkers(1, includePending = true).firstOrNull())
        assertTrue(first.queued)
        base.advanceTo(1)
        val auditAtOne = assertNotNull(base.eventReplay(1, 1)).replay().engine
        auditAtOne.advance()
        assertEquals(listOf(Intervention.Supplement, Intervention.Reforest), auditAtOne.interventionResults.map { it.kind })
        assertEquals(listOf(0, 6), auditAtOne.interventionResults.map { it.amount })

        base.advanceTo(300)
        val outcome = base.interventionResults
        assertTrue(outcome.isNotEmpty())
        val state = base.state
        base.advanceTo(3_300)
        assertTrue(base.checkpoint().branches.single().snapshots.first().tick > 300)
        val audit = assertNotNull(base.eventReplay(1, 300)).replay().engine
        assertEquals(299L, audit.state.tick)
        audit.advance()
        assertEquals(state, audit.state)
        assertEquals(outcome, audit.interventionResults)
        assertNull(base.eventReplay(1, 2))
    }

    @Test
    fun treeOrderingRangesAndScrubberRetainEndpoints() = runBlocking {
        val timeline = Timeline.start(terrain())
        timeline.advanceTo(600)
        timeline.goTo(1, 300)
        assertTrue(timeline.fork("First"))
        timeline.advanceTo(900)
        timeline.goTo(1, 600)
        assertTrue(timeline.fork("Sibling"))
        timeline.goTo(2, 900)
        assertTrue(timeline.fork("Nested"))
        val tree = timeline.summaries().inTreeOrder()
        assertEquals(listOf(1, 2, 4, 3), tree.map { it.branch.id })
        assertEquals(listOf(0, 1, 2, 1), tree.map { it.depth })
        assertEquals(300L..600L, commonTicks(tree[0].branch, tree[1].branch))
        assertTrue(commonTicks(tree[0].branch, tree[2].branch).isEmpty())
        assertNull(timeline.compare(1, 4, 900))
        assertNull(timeline.compare(2, 2, 900))
        assertEquals(900L, (300L..900L).tickAt(1f))
        assertEquals(300L, (300L..900L).tickAt(0f))
        assertEquals(600L, (300L..900L).tickAt(0.5f))
        assertEquals(Long.MAX_VALUE, (1L..Long.MAX_VALUE).tickAt(1f))
        assertEquals(1f, (1L..Long.MAX_VALUE).fractionAt(Long.MAX_VALUE))
    }

    @Test
    fun supersededResultsCannotReplacePreviewAndClosingRestoresTheLiveProjection() = runBlocking {
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
    }

    private fun terrain() = IslandGenerator.generate(TerrainKey(104729))

    private fun Timeline.advanceTo(tick: Long) {
        while (state.tick < tick) assertTrue(advance())
    }

    private suspend fun Timeline.goTo(branch: Int, tick: Long) = show(assertNotNull(seek(branch, tick)).replay())
}
