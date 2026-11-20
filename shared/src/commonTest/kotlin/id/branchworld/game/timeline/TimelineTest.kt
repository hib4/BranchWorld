package id.branchworld.game.timeline

import id.branchworld.game.ecology.Intervention
import id.branchworld.game.ecology.Species
import id.branchworld.game.ecology.canonicalHash
import id.branchworld.game.save.TimelineSaveCodec
import id.branchworld.game.save.WorldCheckpoint
import id.branchworld.game.save.WorldSaveCodec
import id.branchworld.game.terrain.IslandGenerator
import id.branchworld.game.terrain.TerrainKey
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimelineTest {
    @Test
    fun rewindReplaysOrderedCommandsAtTheSameHashes() = runBlocking {
        for ((seed, expected) in ReplayHashes) {
            val timeline = Timeline.start(IslandGenerator.generate(TerrainKey(seed)))
            val initial = timeline.state.canonicalHash()
            timeline.steer(left = true)
            timeline.advanceTo(299)
            timeline.steer(left = true)
            timeline.steer(left = false)
            timeline.advanceTo(300)
            val at300 = timeline.state.canonicalHash()
            val region = timeline.inspectRegions().indexOfFirst { it.canPlant }
            assertTrue(timeline.intervene(Intervention.Reforest, region))
            timeline.advanceTo(500)
            timeline.steer(left = false)
            timeline.advanceTo(600)
            val at600 = timeline.state.canonicalHash()
            assertEquals(expected, listOf(initial, at300, at600), "seed=$seed")
            val recorded = timeline.checkpoint()

            timeline.goTo(1, 0)
            assertEquals(initial, timeline.state.canonicalHash())
            timeline.advanceTo(300)
            assertEquals(at300, timeline.state.canonicalHash())
            timeline.goTo(1, 299)
            timeline.advanceTo(300)
            assertFalse(timeline.editsAllowed)
            assertFalse(timeline.intervene(Intervention.Reforest, region))
            timeline.advanceTo(600)
            assertEquals(at600, timeline.state.canonicalHash())
            assertEquals(recorded, timeline.checkpoint())
            println("TIMELINE_REPLAY,$seed,$initial,$at300,$at600")
        }
    }

    @Test
    fun northStarForkAndSaveRemainIndependentAtMatchedTicks() = runBlocking {
        val timeline = newTimeline()
        timeline.advanceTo(600)
        val parentBefore = timeline.checkpoint().branches.single()
        timeline.goTo(1, 300)
        val forkHash = timeline.state.canonicalHash()
        assertTrue(timeline.fork("Restored Forest"))
        assertEquals(forkHash, timeline.state.canonicalHash())
        assertTrue(timeline.intervene(Intervention.Reforest, 6))
        timeline.advanceTo(900)
        val child = timeline.checkpoint().branches.last()
        val childHash = timeline.state.canonicalHash()
        assertEquals(6, child.head.patches.count { it.planted })
        assertEquals(parentBefore, timeline.checkpoint().branches.first())
        assertNull(timeline.seek(2, 299))

        timeline.goTo(1, 600)
        timeline.advanceTo(900)
        val parentHash = timeline.state.canonicalHash()
        assertEquals("689a2ab6f9063004", parentHash)
        assertEquals("6af1190dbe1f4a7f", childHash)
        assertEquals(0, timeline.state.patches.count { it.planted })
        assertEquals(child, timeline.checkpoint().branches.last())
        val branchHeads = timeline.checkpoint()
        for (branch in branchHeads.branches) {
            val state = branch.head
            println("TIMELINE_900,${branch.id},${state.canonicalHash()},${state.patches.count { it.planted }}," +
                "${state.patches.sumOf { it.biomass }},${state.creatures.count { it.species == Species.Rabbit }}," +
                "${state.creatures.count { it.species == Species.Deer }}")
        }

        timeline.goTo(2, 900)
        assertEquals(childHash, timeline.state.canonicalHash())
        timeline.steer(left = false)
        val pending = timeline.checkpoint().branches.last().pending
        assertEquals(1, pending.size)
        timeline.goTo(2, 700)
        val at700 = timeline.state.canonicalHash()
        val captured = timeline.checkpoint()
        val bytes = TimelineSaveCodec.encode(captured)
        val restored = TimelineSaveCodec.restore(bytes).timeline
        assertEquals(2, restored.branchId)
        assertEquals(at700, restored.state.canonicalHash())
        assertEquals(captured, restored.checkpoint())
        assertContentEquals(bytes, TimelineSaveCodec.encode(restored.checkpoint()))
        restored.advanceTo(900)
        assertEquals(childHash, restored.state.canonicalHash())
        assertEquals(pending, restored.checkpoint().branches.last().pending)
        restored.advanceTo(901)
        timeline.advanceTo(901)
        assertEquals(timeline.state.canonicalHash(), restored.state.canonicalHash())
        assertTrue(restored.checkpoint().branches.last().pending.isEmpty())
        restored.goTo(1, 900)
        assertEquals(parentHash, restored.state.canonicalHash())
    }

    @Test
    fun snapshotsEvictAndRepeatedForksKeepTheirOwnQueues() = runBlocking {
        val timeline = newTimeline()
        timeline.steer(left = true)
        timeline.advanceTo(250)
        val older = timeline.state.canonicalHash()
        timeline.advanceTo(3_300)
        val latest = timeline.state.canonicalHash()
        assertEquals(RetainedSnapshots, timeline.checkpoint().branches.single().snapshots.size)
        assertTrue(timeline.checkpoint().branches.single().snapshots.first().tick > 250)
        timeline.goTo(1, 250)
        assertEquals(older, timeline.state.canonicalHash())
        timeline.advanceTo(3_300)
        assertEquals(latest, timeline.state.canonicalHash())
        timeline.steer(left = false)
        val root = timeline.checkpoint().branches.first()
        repeat(MaxBranches - 1) {
            val parentId = timeline.branchId
            assertTrue(timeline.fork("Nested ${it + 1}"))
            val branch = timeline.checkpoint().branches.last()
            assertEquals(parentId, branch.parentId)
            assertTrue(branch.pending.isEmpty())
            timeline.steer(left = true)
        }
        assertFalse(timeline.canFork)
        assertFalse(timeline.fork("Unavailable"))
        assertEquals(root, timeline.checkpoint().branches.first())
        val restored = TimelineSaveCodec.restore(TimelineSaveCodec.encode(timeline.checkpoint())).timeline
        assertEquals(timeline.checkpoint(), restored.checkpoint())
    }

    @Test
    fun phase06ImportKeepsTheCheckpointAndNextInputs() = runBlocking {
        val live = newTimeline()
        live.advanceTo(353)
        live.steer(left = true)
        val pending = live.checkpoint().branches.single().pending
        val original = WorldSaveCodec.encode(WorldCheckpoint(live.state, pending))
        val imported = TimelineSaveCodec.restore(original).timeline
        assertEquals(353L, imported.state.tick)
        assertEquals(live.state.canonicalHash(), imported.state.canonicalHash())
        assertEquals(pending, imported.checkpoint().branches.single().pending)
        assertNull(imported.seek(1, 352))
        live.advanceTo(600)
        imported.advanceTo(600)
        assertEquals(live.state.canonicalHash(), imported.state.canonicalHash())
    }

    private fun newTimeline() = Timeline.start(IslandGenerator.generate(TerrainKey(104729)))

    private suspend fun Timeline.goTo(id: Int, tick: Long) {
        show(assertNotNull(seek(id, tick)).replay())
    }

    private fun Timeline.advanceTo(tick: Long) {
        while (state.tick < tick) assertTrue(advance())
    }

    private companion object {
        val ReplayHashes = mapOf(
            104729L to listOf("b5f4de1f7a287385", "669595dc45e4888a", "b8900e6fd6de6c0d"),
            271828L to listOf("ebb9ae14855fe4a2", "e252473cf0ad169d", "4da987a1397c2b78"),
            424242L to listOf("02c833498263081a", "5c29243cb0166773", "9515493d75bf6066"),
        )
    }
}
