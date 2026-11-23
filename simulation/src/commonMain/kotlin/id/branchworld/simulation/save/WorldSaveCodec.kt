package id.branchworld.simulation.save

import id.branchworld.simulation.command.WorldCommand
import id.branchworld.simulation.command.commandOrder
import id.branchworld.simulation.core.EnvironmentalRulesVersion
import id.branchworld.simulation.core.canonicalHash
import id.branchworld.simulation.ecology.EcologyState
import id.branchworld.simulation.ecology.canonicalHash
import id.branchworld.simulation.engine.EcologyEngine
import id.branchworld.simulation.legacy.canonicalHash
import id.branchworld.simulation.terrain.IslandGenerator
import id.branchworld.simulation.terrain.TerrainMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
internal data class WorldCheckpoint(val world: EcologyState, val pending: List<WorldCommand>)

internal data class RestoredWorld(val terrain: TerrainMap, val engine: EcologyEngine)

class IncompatibleSave : Exception()

internal object WorldSaveCodec {
    const val MaxBytes = 1_048_576
    private const val SchemaVersion = 1
    private const val WorldgenVersion = 1
    private val json = Json { encodeDefaults = true }

    @Serializable
    private data class SaveEnvelope(
        val schemaVersion: Int,
        val rulesVersion: Int,
        val worldgenVersion: Int,
        val integrity: String,
        val checkpoint: WorldCheckpoint,
    )

    fun encode(checkpoint: WorldCheckpoint): ByteArray {
        require(checkpoint.world.rulesVersion == EnvironmentalRulesVersion)
        require(checkpoint.world.terrain.key.worldgenVersion == WorldgenVersion)
        return json.encodeToString(
            SaveEnvelope(SchemaVersion, EnvironmentalRulesVersion, WorldgenVersion, integrity(checkpoint), checkpoint),
        ).encodeToByteArray().also { require(it.size <= MaxBytes) }
    }

    fun restore(bytes: ByteArray): RestoredWorld {
        require(bytes.size in 1..MaxBytes)
        val element = json.parseToJsonElement(bytes.decodeToString(throwOnInvalidSequence = true))
        val header = element.jsonObject
        val schema = requireNotNull(header["schemaVersion"]?.jsonPrimitive?.intOrNull)
        if (schema != SchemaVersion) throw IncompatibleSave()
        val rules = requireNotNull(header["rulesVersion"]?.jsonPrimitive?.intOrNull)
        val worldgen = requireNotNull(header["worldgenVersion"]?.jsonPrimitive?.intOrNull)
        if (rules != EnvironmentalRulesVersion || worldgen != WorldgenVersion) throw IncompatibleSave()
        val (world, pending) = json.decodeFromJsonElement<SaveEnvelope>(element).let {
            require(it.integrity == integrity(it.checkpoint))
            it.checkpoint
        }
        require(world.rulesVersion == rules && world.terrain.key.worldgenVersion == worldgen)
        require(pending.zipWithNext().all { (first, next) -> commandOrder.compare(first, next) < 0 })
        val terrain = IslandGenerator.generate(world.terrain.key)
        val engine = EcologyEngine(terrain, world, pending)
        engine.validateSavedState()
        return RestoredWorld(terrain, engine)
    }

    private fun integrity(checkpoint: WorldCheckpoint): String =
        json.encodeToString(checkpoint).encodeToByteArray().canonicalHash()
}
