package id.branchworld.game.save

import id.branchworld.game.ecology.EcologyEngine
import id.branchworld.game.ecology.IslandEcology
import id.branchworld.game.ecology.CivilizationRulesVersion
import id.branchworld.game.ecology.EnvironmentalRulesVersion
import id.branchworld.game.ecology.WatershedRulesVersion
import id.branchworld.game.ecology.WildlifeRulesVersion
import id.branchworld.game.ecology.ShapingRulesVersion
import id.branchworld.game.ecology.VillageRulesVersion
import id.branchworld.game.ecology.RangeRulesVersion
import id.branchworld.game.ecology.FireRulesVersion
import id.branchworld.game.ecology.LifeRulesVersion
import id.branchworld.game.simulation.living
import id.branchworld.game.simulation.CallRainRegion
import id.branchworld.game.simulation.CutFirebreak
import id.branchworld.game.simulation.EndorsePlan
import id.branchworld.game.simulation.ProtectRegion
import id.branchworld.game.simulation.ReforestRegion
import id.branchworld.game.simulation.canonicalHash
import id.branchworld.game.simulation.commandOrder
import id.branchworld.game.terrain.IslandGenerator
import id.branchworld.game.terrain.TerrainMap
import id.branchworld.game.terrain.WatershedWorldgenVersion
import id.branchworld.game.timeline.MaxBranchNameLength
import id.branchworld.game.timeline.MaxBranches
import id.branchworld.game.timeline.MaxCommands
import id.branchworld.game.timeline.RetainedSnapshots
import id.branchworld.game.timeline.SnapshotInterval
import id.branchworld.game.timeline.Timeline
import id.branchworld.game.timeline.TimelineCheckpoint
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class RestoredTimeline(val terrain: TerrainMap, val timeline: Timeline, val sharedOrigin: String? = null)

internal object TimelineSaveCodec {
    const val MaxBytes = 16 * 1024 * 1024
    private const val LifeSchema = 10
    private const val FireSchema = 9
    private const val RangeSchema = 8
    private const val VillageSchema = 7
    private const val ShapingSchema = 6
    private const val WildlifeSchema = 5
    private const val WatershedSchema = 4
    private const val CivilizationSchema = 3
    private val json = Json { encodeDefaults = true }

    @Serializable
    @OptIn(ExperimentalSerializationApi::class)
    private data class Envelope(
        val schemaVersion: Int,
        val rulesVersion: Int,
        val worldgenVersion: Int,
        val integrity: String,
        val timeline: TimelineCheckpoint,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val sharedOrigin: String? = null,
    )

    fun encode(timeline: TimelineCheckpoint, sharedOrigin: String? = null): ByteArray {
        val root = timeline.branches.first().start
        val rules = root.rulesVersion
        val worldgen = root.terrain.key.worldgenVersion
        val schema = when (rules) {
            LifeRulesVersion -> LifeSchema
            FireRulesVersion -> FireSchema
            RangeRulesVersion -> RangeSchema
            VillageRulesVersion -> VillageSchema
            ShapingRulesVersion -> ShapingSchema
            WildlifeRulesVersion -> WildlifeSchema
            WatershedRulesVersion -> WatershedSchema
            else -> CivilizationSchema
        }
        require(isSupported(schema, rules, worldgen))
        val envelope = Envelope(schema, rules, worldgen, integrity(timeline, sharedOrigin), timeline, sharedOrigin)
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
            2, CivilizationSchema, WatershedSchema, WildlifeSchema, ShapingSchema, VillageSchema, RangeSchema, FireSchema, LifeSchema -> Unit
            else -> throw IncompatibleSave()
        }
        val rules = version(header["rulesVersion"]?.jsonPrimitive?.intOrNull)
        val worldgen = version(header["worldgenVersion"]?.jsonPrimitive?.intOrNull)
        if (!isSupported(schema, rules, worldgen)) throw IncompatibleSave()
        val envelope = json.decodeFromJsonElement(Envelope.serializer(), header)
        val checkpoint = envelope.timeline
        require(envelope.integrity == integrity(checkpoint, envelope.sharedOrigin))
        val root = requireNotNull(checkpoint.branches.firstOrNull())
        require(root.start.terrain.key.worldgenVersion == worldgen)
        val terrain = IslandGenerator.generate(root.start.terrain.key)
        validate(terrain, checkpoint, rules)
        return RestoredTimeline(terrain, Timeline.restore(terrain, checkpoint), envelope.sharedOrigin)
    }

    private fun version(value: Int?): Int = requireNotNull(value)

    private fun isSupported(schema: Int, rules: Int, worldgen: Int): Boolean = when (schema) {
        2 -> worldgen == 1 && rules == EnvironmentalRulesVersion
        CivilizationSchema -> worldgen == 1 && (rules == EnvironmentalRulesVersion || rules == CivilizationRulesVersion)
        WatershedSchema -> worldgen == WatershedWorldgenVersion && rules == WatershedRulesVersion
        ShapingSchema -> worldgen == WatershedWorldgenVersion && rules == ShapingRulesVersion
        VillageSchema -> worldgen == WatershedWorldgenVersion && rules == VillageRulesVersion
        RangeSchema -> worldgen == WatershedWorldgenVersion && rules == RangeRulesVersion
        FireSchema -> worldgen == WatershedWorldgenVersion && rules == FireRulesVersion
        LifeSchema -> worldgen == WatershedWorldgenVersion && rules == LifeRulesVersion
        WildlifeSchema -> worldgen == WatershedWorldgenVersion && rules == WildlifeRulesVersion
        else -> false
    }

    private fun integrity(checkpoint: TimelineCheckpoint, sharedOrigin: String?): String {
        val timeline = json.encodeToString(checkpoint)
        val payload = if (sharedOrigin == null) timeline else "$sharedOrigin\n$timeline"
        return payload.encodeToByteArray().canonicalHash()
    }

    private suspend fun validate(terrain: TerrainMap, timeline: TimelineCheckpoint, rules: Int) {
        val branches = timeline.branches
        require(branches.size in 1..MaxBranches)
        require(branches.sumOf { it.commands.size.toLong() + it.pending.size } <= MaxCommands)
        val active = requireNotNull(branches.getOrNull(timeline.activeBranchId - 1))
        require(timeline.viewedTick in active.start.tick..active.head.tick)
        val context = currentCoroutineContext()
        // One island's static habitat serves every saved state; validation runs on this coroutine alone.
        val island = IslandEcology(terrain)
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
            if (rules < LifeRulesVersion) require((branch.commands + branch.pending).none { it.living })
            else require((branch.commands + branch.pending).all { it.living })
            if (rules in ShapingRulesVersion until LifeRulesVersion) {
                val queued = branch.pending.filter { it is CallRainRegion || it is ReforestRegion || it is ProtectRegion || it is CutFirebreak }
                require(queued.size <= 1 && queued.all { it.tick - branch.head.tick == 1L })
                val endorsed = branch.pending.filterIsInstance<EndorsePlan>()
                require(endorsed.size <= 1 && endorsed.all { it.tick - branch.head.tick == 1L })
            }
            if (rules < VillageRulesVersion) require((branch.commands + branch.pending).none { it is EndorsePlan })
            if (rules < RangeRulesVersion) require((branch.commands + branch.pending).none { it is ProtectRegion })
            if (rules < FireRulesVersion) require((branch.commands + branch.pending).none { it is CutFirebreak })
            val inputs = branch.commands + branch.pending
            require(inputs.zipWithNext().all { (a, b) -> commandOrder.compare(a, b) < 0 })
            for (state in (branch.snapshots + branch.start + branch.head).distinct()) {
                context.ensureActive()
                require(state.rulesVersion == rules)
                val replayInputs = if (state == branch.start) inputs else emptyList()
                EcologyEngine(terrain, state, replayInputs, recordHistory = false, island = island).validateSavedState()
            }
        }
    }
}
