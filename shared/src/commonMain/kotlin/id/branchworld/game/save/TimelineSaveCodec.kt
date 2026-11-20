package id.branchworld.game.save

import id.branchworld.game.ecology.EcologyEngine
import id.branchworld.game.ecology.CivilizationRulesVersion
import id.branchworld.game.ecology.EnvironmentalRulesVersion
import id.branchworld.game.simulation.canonicalHash
import id.branchworld.game.simulation.commandOrder
import id.branchworld.game.terrain.IslandGenerator
import id.branchworld.game.terrain.TerrainMap
import id.branchworld.game.timeline.MaxBranchNameLength
import id.branchworld.game.timeline.MaxBranches
import id.branchworld.game.timeline.MaxCommands
import id.branchworld.game.timeline.RetainedSnapshots
import id.branchworld.game.timeline.SnapshotInterval
import id.branchworld.game.timeline.Timeline
import id.branchworld.game.timeline.TimelineCheckpoint
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class RestoredTimeline(val terrain: TerrainMap, val timeline: Timeline)

internal object TimelineSaveCodec {
    const val MaxBytes = 16 * 1024 * 1024
    private const val SchemaVersion = 3
    private const val LegacyTimelineSchema = 2
    private const val WorldgenVersion = 1
    private val json = Json { encodeDefaults = true }

    @Serializable
    private data class Envelope(
        val schemaVersion: Int,
        val rulesVersion: Int,
        val worldgenVersion: Int,
        val integrity: String,
        val timeline: TimelineCheckpoint,
    )

    fun encode(timeline: TimelineCheckpoint): ByteArray {
        val rules = timeline.branches.first().start.rulesVersion
        require(rules == EnvironmentalRulesVersion || rules == CivilizationRulesVersion)
        val envelope = Envelope(SchemaVersion, rules, WorldgenVersion, integrity(timeline), timeline)
        return json.encodeToString(envelope).encodeToByteArray().also { require(it.size <= MaxBytes) }
    }

    suspend fun restore(bytes: ByteArray): RestoredTimeline {
        require(bytes.size in 1..MaxBytes)
        // Dispatch on the envelope before decoding its version-specific payload.
        val source = bytes.decodeToString(throwOnInvalidSequence = true)
        val header = json.parseToJsonElement(source).jsonObject
        val schema = version(header["schemaVersion"]?.jsonPrimitive?.intOrNull)
        when (schema) {
            1 -> {
                val (terrain, engine) = WorldSaveCodec.restore(bytes)
                val checkpoint = Timeline.root(engine.state, engine.pendingCommands())
                return RestoredTimeline(terrain, Timeline.restore(terrain, checkpoint))
            }
            LegacyTimelineSchema, SchemaVersion -> Unit
            else -> throw IncompatibleSave()
        }
        val rules = version(header["rulesVersion"]?.jsonPrimitive?.intOrNull)
        if ((rules != EnvironmentalRulesVersion && (rules != CivilizationRulesVersion || schema != SchemaVersion)) ||
            version(header["worldgenVersion"]?.jsonPrimitive?.intOrNull) != WorldgenVersion
        ) throw IncompatibleSave()
        val envelope = json.decodeFromJsonElement(Envelope.serializer(), header)
        val checkpoint = envelope.timeline
        require(envelope.integrity == integrity(checkpoint))
        val root = requireNotNull(checkpoint.branches.firstOrNull())
        require(root.start.terrain.key.worldgenVersion == WorldgenVersion)
        val terrain = IslandGenerator.generate(root.start.terrain.key)
        validate(terrain, checkpoint, rules)
        return RestoredTimeline(terrain, Timeline.restore(terrain, checkpoint))
    }

    private fun version(value: Int?): Int = requireNotNull(value)

    private fun integrity(checkpoint: TimelineCheckpoint): String =
        json.encodeToString(checkpoint).encodeToByteArray().canonicalHash()

    private suspend fun validate(terrain: TerrainMap, timeline: TimelineCheckpoint, rules: Int) {
        val branches = timeline.branches
        require(branches.size in 1..MaxBranches)
        require(branches.sumOf { it.commands.size.toLong() + it.pending.size } <= MaxCommands)
        val active = requireNotNull(branches.getOrNull(timeline.activeBranchId - 1))
        require(timeline.viewedTick in active.start.tick..active.head.tick)
        val context = currentCoroutineContext()
        for ((index, branch) in branches.withIndex()) {
            context.ensureActive()
            require(branch.id == index + 1)
            require(branch.name.length in 1..MaxBranchNameLength && branch.name == branch.name.trim())
            require(branch.head.tick >= branch.start.tick)
            if (index == 0) {
                require(branch.parentId == null)
            } else {
                val parentId = requireNotNull(branch.parentId)
                require(parentId in 1 until branch.id)
                val parent = branches[parentId - 1]
                require(branch.start.tick in parent.start.tick..parent.head.tick)
                val knownFork = (parent.snapshots + parent.start + parent.head).firstOrNull { it.tick == branch.start.tick }
                require(knownFork == null || knownFork == branch.start)
            }
            require(branch.snapshots.size <= RetainedSnapshots)
            require(branch.snapshots.zipWithNext().all { (a, b) -> a.tick < b.tick })
            require(branch.snapshots.all {
                it.tick > branch.start.tick && it.tick <= branch.head.tick && it.tick % SnapshotInterval == 0L &&
                    (it.tick != branch.head.tick || it == branch.head)
            })
            require(branch.head.tick != branch.start.tick || branch.head == branch.start)
            require(branch.commands.all { it.tick > branch.start.tick && it.tick <= branch.head.tick })
            require(branch.pending.all { it.tick > branch.head.tick })
            val inputs = branch.commands + branch.pending
            require(inputs.zipWithNext().all { (a, b) -> commandOrder.compare(a, b) < 0 })
            for (state in (branch.snapshots + branch.start + branch.head).distinct()) {
                context.ensureActive()
                require(state.rulesVersion == rules)
                val replayInputs = if (state == branch.start) inputs else emptyList()
                EcologyEngine(terrain, state, replayInputs, recordHistory = false).validateSavedState()
            }
        }
    }
}
