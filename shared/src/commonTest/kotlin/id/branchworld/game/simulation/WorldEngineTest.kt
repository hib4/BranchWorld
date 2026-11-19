package id.branchworld.game.simulation

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WorldEngineTest {
    @Test
    fun sameSeedAndCommandsMatchGoldenCheckpoints() {
        for ((seed, expected) in CheckpointHashes) {
            val first = WorldEngine(WorldState.start(seed))
            val second = WorldEngine(WorldState.start(seed))
            FixtureCommands.forEach { assertTrue(first.submit(it)) }
            FixtureCommands.reversed().forEach { assertTrue(second.submit(it)) }

            for ((index, tick) in CheckpointTicks.withIndex()) {
                val state = first.runTo(tick)
                assertEquals(expected[index], state.canonicalHash(), "seed=$seed tick=$tick")
                assertContentEquals(state.canonicalBytes(), second.runTo(tick).canonicalBytes())
                if (seed == 104729L) assertContentEquals(BaselineBytes[index].hexToByteArray(), state.canonicalBytes())
            }
        }
    }

    @Test
    fun sameTickCommandsFollowSequenceBeforeMovementAndSkipRandomDraw() {
        val first = WorldEngine(WorldState.start(104729))
        val second = WorldEngine(first.state)
        val left = command(1, 0, GridCell(-8, 12))
        val right = command(1, 1, GridCell(7, 12))

        assertTrue(first.submit(right))
        assertTrue(first.submit(left))
        assertTrue(first.submit(command(2, 1, GridCell(1, 11))))
        assertEquals(listOf(left, right, command(2, 1, GridCell(1, 11))), first.acceptedCommands())
        assertTrue(second.submit(left.copy(sequence = 1)))
        assertTrue(second.submit(right.copy(sequence = 0)))

        val before = first.state
        assertEquals(GridCell(1, 10), first.advance().bunnyCell)
        assertEquals(right.cell, first.state.destination)
        assertEquals(before.randomState, first.state.randomState)
        assertEquals(GridCell(-1, 10), second.advance().bunnyCell)
        assertNotEquals(first.state.canonicalHash(), second.state.canonicalHash())
        assertEquals(GridCell(1, 11), first.advance().bunnyCell)
        assertEquals(GridCell(0, 10), before.bunnyCell)
    }

    @Test
    fun rejectsInvalidCommandsWithoutChangingWorldOrReplayInputs() {
        val engine = WorldEngine(WorldState.start(Long.MIN_VALUE))
        val start = engine.state.canonicalBytes()
        val valid = command(1, 0, GridCell(7, 12))

        assertFalse(engine.submit(valid.copy(tick = 0)))
        assertFalse(engine.submit(valid.copy(sequence = -1)))
        assertFalse(engine.submit(valid.copy(entityId = 2)))
        assertFalse(engine.submit(valid.copy(cell = GridCell(Int.MIN_VALUE, 10))))
        assertFalse(engine.submit(valid.copy(cell = GridCell(0, 21))))
        assertTrue(engine.acceptedCommands().isEmpty())
        assertTrue(engine.submit(valid))
        assertFalse(engine.submit(valid.copy(cell = GridCell(-8, 12))))
        assertContentEquals(start, engine.state.canonicalBytes())
        engine.advance()
        assertFalse(engine.submit(valid.copy(sequence = 1)))
        assertEquals(listOf(valid), engine.acceptedCommands())
        assertFailsWith<IllegalArgumentException> { WorldState.start(104729, rulesVersion = 2) }
    }

    @Test
    fun canReplayFromSeedOrResumeFromCheckpoint() {
        val live = WorldEngine(WorldState.start(104729))
        FixtureCommands.forEach { assertTrue(live.submit(it)) }
        val checkpoint = live.runTo(300)
        val expected = live.runTo(900).canonicalBytes()
        val replay = WorldEngine(WorldState.start(checkpoint.seed, checkpoint.rulesVersion))
        val resumed = WorldEngine(checkpoint)

        live.acceptedCommands().forEach {
            assertTrue(replay.submit(it))
            if (it.tick > checkpoint.tick) assertTrue(resumed.submit(it))
        }

        assertContentEquals(expected, replay.runTo(900).canonicalBytes())
        assertContentEquals(expected, resumed.runTo(900).canonicalBytes())
    }

    @Test
    fun splitMix64MatchesReferenceVector() {
        val random = SplitMix64(0uL)
        assertEquals(0xe220a8397b1dcdafuL, random.next())
        assertEquals(0x6e789e6aa1b965f4uL, random.next())
        assertEquals(0x06c45d188009454fuL, random.next())
        assertEquals(0xdaa66d2c7ddf743fuL, random.state)
    }

    private fun WorldEngine.runTo(tick: Long): WorldState {
        while (state.tick < tick) {
            val previous = state.bunnyCell
            val current = advance().bunnyCell
            assertTrue(current.isOnIsland())
            assertTrue(kotlin.math.abs(previous.x - current.x) + kotlin.math.abs(previous.z - current.z) <= 1)
        }
        return state
    }

    private companion object {
        fun command(tick: Long, sequence: Long, cell: GridCell) = SetDestination(tick, sequence, 1, cell)

        val FixtureCommands = listOf(
            command(1, 0, GridCell(-8, 12)),
            command(300, 0, GridCell(-8, 12)),
            command(300, 1, GridCell(7, 12)),
            command(601, 0, GridCell(-4, 19)),
        )
        val CheckpointTicks = listOf(0L, 300L, 600L, 900L)
        val CheckpointHashes = mapOf(
            104729L to listOf("6df797389697f555", "9376a03f63a5d26f", "7216d6696920a479", "c1d725be55250b5c"),
            271828L to listOf("c59ad9bf143e3945", "2f042bc72524d396", "865234d1fde6798e", "33bf615113be2058"),
            424242L to listOf("3b27ec20752bf17d", "ecc18fb57f12b999", "d746e501a1878017", "e12717e33bbca6cf"),
        )
        val BaselineBytes = listOf(
            "010000001999010000000000000000000000000019990100000000000100000000000000000000000a000000000000000a000000",
            "0100000019990100000000002c01000000000000652b27ec4950114e01000000000000000000000008000000070000000c000000",
            "0100000019990100000000005802000000000000c63997574d1a5a3a0100000000000000ffffffff0f000000ffffffff0a000000",
            "0100000019990100000000008403000000000000e8d327452477fc4b010000000000000005000000090000000700000005000000",
        )
    }
}
