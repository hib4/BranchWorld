package id.branchworld.game.simulation

import id.branchworld.game.terrain.IslandGenerator
import id.branchworld.game.terrain.TerrainKey
import id.branchworld.game.terrain.TerrainMap
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TerrainReplayTest {
    @Test
    fun replayAndCheckpointRetainVersionedTerrainAndDeterministicPaths() {
        for ((seed, hashes) in CheckpointHashes) {
            val map = IslandGenerator.generate(TerrainKey(seed))
            val initial = WorldEngine.onTerrain(map)
            val repeat = WorldEngine.onTerrain(IslandGenerator.generate(map.key))
            val west = assertNotNull(initial.steeringTarget(left = true))
            val east = assertNotNull(initial.steeringTarget(left = false))
            val commands = listOf(
                SetDestination(1, 0, 1, west),
                SetDestination(300, 0, 1, west),
                SetDestination(300, 1, 1, east),
                SetDestination(601, 0, 1, west),
            )
            assertFalse(initial.submit(SetDestination(1, 0, 1, GridCell(31, 31))))
            commands.forEach { assertTrue(initial.submit(it)) }
            commands.reversed().forEach { assertTrue(repeat.submit(it)) }

            for ((index, tick) in listOf(0L, 300L, 600L, 900L).withIndex()) {
                val state = initial.runTo(tick, map)
                assertEquals(hashes[index], state.canonicalHash(), "seed=$seed tick=$tick")
                assertEquals(TerrainRulesVersion, state.rulesVersion)
                assertEquals(TerrainIdentity(map.key, map.digest), state.terrain)
                assertEquals(64, state.canonicalBytes().size)
                assertContentEquals(state.canonicalBytes(), repeat.runTo(tick, map).canonicalBytes())
                if (tick == 300L) {
                    assertEquals(east, state.destination)
                    val restarted = WorldEngine.onTerrain(IslandGenerator.generate(map.key), state)
                    commands.filter { it.tick > tick }.forEach { assertTrue(restarted.submit(it)) }
                    val reference = WorldEngine.onTerrain(map).also { fresh ->
                        commands.forEach { assertTrue(fresh.submit(it)) }
                    }
                    assertContentEquals(
                        reference.runTo(900, map).canonicalBytes(),
                        restarted.runTo(900, map).canonicalBytes(),
                    )
                    val other = IslandGenerator.generate(TerrainKey(seed + 1))
                    assertFailsWith<IllegalArgumentException> { WorldEngine.onTerrain(other, state) }
                }
            }
        }
    }

    private fun WorldEngine.runTo(tick: Long, terrain: TerrainMap): WorldState {
        while (state.tick < tick) {
            val before = state.bunnyCell
            val after = advance().bunnyCell
            assertTrue(assertNotNull(terrain.cellAt(after.x, after.z)).walkable)
            assertTrue(before == after || terrain.canStep(before.x, before.z, after.x, after.z))
        }
        return state
    }

    private companion object {
        val CheckpointHashes = mapOf(
            104729L to listOf("c7c5701022e705d4", "01629a7541a93bfc", "f4272a6a9bc85f62", "fb32873e68a61757"),
            271828L to listOf("267ede3db0e932c2", "54d7e1c31d99a43d", "1028fc84435c92e1", "d149d0b424e9a536"),
            424242L to listOf("e6fbde7a1ec58e35", "d05e9b2a3bf4921e", "602f5c5105602565", "55937180e587af4b"),
        )
    }
}
