package id.branchworld.game.save

import id.branchworld.game.terrain.IslandGenerator
import id.branchworld.game.terrain.TerrainKey
import id.branchworld.game.timeline.Timeline
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TimelineSaveCodecTest {
    @Test
    fun rejectsMalformedOrCorruptTimelineBeforeRestore() = runBlocking<Unit> {
        val timeline = Timeline.start(IslandGenerator.generate(TerrainKey(104729)))
        timeline.steer(left = true)
        timeline.advance()
        assertTrue(timeline.fork("Child"))
        timeline.steer(left = false)
        val checkpoint = timeline.checkpoint()
        val (root, child) = checkpoint.branches
        val bytes = TimelineSaveCodec.encode(checkpoint)
        assertFailsWith<IllegalArgumentException> { TimelineSaveCodec.restore(bytes.copyOf(bytes.size - 5)) }
        val edited = bytes.decodeToString().replace("\"Child\"", "\"Altered\"").encodeToByteArray()
        assertFailsWith<IllegalArgumentException> { TimelineSaveCodec.restore(edited) }
        val invalid = listOf(
            checkpoint.copy(branches = listOf(root, child.copy(parentId = child.id))),
            checkpoint.copy(branches = listOf(root, child.copy(start = root.start, head = root.start, pending = emptyList(),
                parentId = 3))),
            checkpoint.copy(branches = listOf(root, child.copy(pending = child.pending + child.pending))),
            checkpoint.copy(branches = listOf(root.copy(commands = root.commands + root.commands), child)),
            checkpoint.copy(activeBranchId = 0),
            checkpoint.copy(viewedTick = Long.MAX_VALUE),
            checkpoint.copy(branches = listOf(root, child.copy(snapshots = listOf(child.start)))),
            checkpoint.copy(branches = listOf(root, child.copy(start = child.start.copy(randomState = 0uL),
                head = child.head.copy(randomState = 0uL)))),
        )
        for (malformed in invalid) {
            assertFailsWith<IllegalArgumentException> { TimelineSaveCodec.restore(TimelineSaveCodec.encode(malformed)) }
        }
        val future = bytes.decodeToString().replace("\"schemaVersion\":2", "\"schemaVersion\":99").encodeToByteArray()
        assertFailsWith<IncompatibleSave> { TimelineSaveCodec.restore(future) }
    }
}
