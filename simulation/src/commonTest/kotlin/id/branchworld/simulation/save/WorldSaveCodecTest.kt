package id.branchworld.simulation.save

import id.branchworld.simulation.command.ReforestRegion
import id.branchworld.simulation.core.EnvironmentalRulesVersion
import id.branchworld.simulation.core.canonicalHash
import id.branchworld.simulation.ecology.Intervention
import id.branchworld.simulation.ecology.canonicalHash
import id.branchworld.simulation.engine.EcologyEngine
import id.branchworld.simulation.legacy.canonicalHash
import id.branchworld.simulation.terrain.IslandGenerator
import id.branchworld.simulation.terrain.TerrainKey
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WorldSaveCodecTest {
    @Test
    fun savedWorldReencodesIdenticallyAndRetainsPendingCommands() {
        val live = engine()
        assertTrue(live.intervene(Intervention.Reforest, live.inspectRegions().indexOfFirst { it.canPlant }))
        live.advance()
        repeat(313) { live.advance() }
        live.steer(left = true)
        assertTrue(live.intervene(Intervention.Reforest, live.inspectRegions().indexOfFirst { it.canPlant }))
        val bytes = save(live)
        val restored = WorldSaveCodec.restore(bytes).engine
        assertEquals(live.state, restored.state)
        assertEquals(live.pendingCommands(), restored.pendingCommands())
        assertContentEquals(bytes, save(restored))
        repeat(600) {
            assertEquals(live.advance().canonicalHash(), restored.advance().canonicalHash())
        }
    }

    @Test
    fun incompleteInvalidAndIncompatibleSavesAreRejected() {
        val live = engine()
        val valid = save(live).decodeToString()
        for (damaged in listOf(
            ByteArray(0),
            ByteArray(WorldSaveCodec.MaxBytes + 1),
            valid.dropLast(2).encodeToByteArray(),
            valid.replace("\"tick\":0", "\"tick\":1").encodeToByteArray(),
        )) {
            assertFailsWith<IllegalArgumentException> { WorldSaveCodec.restore(damaged) }
        }
        for ((field, version) in listOf("schemaVersion" to 1, "rulesVersion" to 4, "worldgenVersion" to 1)) {
            assertFailsWith<IncompatibleSave> {
                WorldSaveCodec.restore(valid.replaceFirst("\"$field\":$version", "\"$field\":99").encodeToByteArray())
            }
        }
        val reordered = WorldCheckpoint(live.state, listOf(ReforestRegion(4, 1, 1), ReforestRegion(4, 0, 1)))
        assertFailsWith<IllegalArgumentException> { WorldSaveCodec.restore(WorldSaveCodec.encode(reordered)) }
        val mismatched = live.state.copy(terrain = live.state.terrain.copy(digest = 0uL))
        assertFailsWith<IllegalArgumentException> {
            WorldSaveCodec.restore(WorldSaveCodec.encode(WorldCheckpoint(mismatched, emptyList())))
        }
        val unsound = live.state.copy(ledger = live.state.ledger.copy(introduced = 99))
        assertFailsWith<IllegalArgumentException> {
            WorldSaveCodec.restore(WorldSaveCodec.encode(WorldCheckpoint(unsound, emptyList())))
        }
    }

    private fun engine() = EcologyEngine(
        IslandGenerator.generate(TerrainKey(104729)),
        rulesVersion = EnvironmentalRulesVersion,
    )

    private fun save(engine: EcologyEngine) = WorldSaveCodec.encode(WorldCheckpoint(engine.state, engine.pendingCommands()))
}
