package id.branchworld.game.timeline

import id.branchworld.game.ecology.EcologyEngine
import id.branchworld.game.ecology.EcologyState
import id.branchworld.game.ecology.EnvironmentalRulesVersion
import id.branchworld.game.ecology.Intervention
import id.branchworld.game.ecology.InterventionResult
import id.branchworld.game.simulation.SetDestination
import id.branchworld.game.simulation.WorldCommand
import id.branchworld.game.terrain.TerrainMap
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable

internal const val MaxBranches = 16
internal const val MaxCommands = 16_384
internal const val SnapshotInterval = 300L
internal const val RetainedSnapshots = 8
internal const val MaxBranchNameLength = 40

@Serializable
internal data class BranchCheckpoint(
    val id: Int,
    val name: String,
    val parentId: Int?,
    val start: EcologyState,
    val head: EcologyState,
    val snapshots: List<EcologyState>,
    val commands: List<WorldCommand>,
    val pending: List<WorldCommand>,
)

@Serializable
internal data class TimelineCheckpoint(
    val activeBranchId: Int,
    val viewedTick: Long,
    val branches: List<BranchCheckpoint>,
)

internal data class BranchSummary(val id: Int, val name: String, val parentId: Int?, val start: Long, val head: Long)

/** Owned by the world clock. A background seek receives an immutable replay request. */
internal class Timeline private constructor(
    private val terrain: TerrainMap,
    checkpoint: TimelineCheckpoint,
    private var engine: EcologyEngine,
) {
    private val branches = checkpoint.branches.associateTo(linkedMapOf()) { it.id to Branch(it) }
    private var commandCount = checkpoint.branches.sumOf { it.commands.size + it.pending.size }
    private var interventionCount = engine.countPendingInterventions()
    var branchId = checkpoint.activeBranchId
        private set

    private val active get() = branches.getValue(branchId)
    val state get() = engine.state
    val editsAllowed get() = state.tick == active.head.tick && state.tick < Long.MAX_VALUE && commandCount < MaxCommands
    val commandLimitReached get() = commandCount >= MaxCommands
    val canFork get() = branches.size < MaxBranches
    val interventionResults: List<InterventionResult> get() = engine.interventionResults
    val queuedInterventions: Int get() = if (state.tick == active.head.tick) interventionCount else 0

    fun summaries() = branches.values.map { BranchSummary(it.id, it.name, it.parentId, it.start.tick, it.head.tick) }

    fun inspectRegions() = engine.inspectRegions()

    fun canSteer(left: Boolean) = editsAllowed && engine.steeringTarget(left) != null

    fun steer(left: Boolean) {
        if (editsAllowed && engine.queueSteering(left)) commandCount++
    }

    fun intervene(kind: Intervention, region: Int): Boolean {
        if (!editsAllowed || !engine.intervene(kind, region)) return false
        commandCount++
        interventionCount++
        return true
    }

    fun advance(): Boolean {
        val branch = active
        val extending = state.tick == branch.head.tick
        val next = engine.advance()
        interventionCount -= engine.appliedCommands.count { it !is SetDestination }
        if (extending) {
            branch.head = next
            branch.commands.addAll(engine.appliedCommands)
            if (next.tick % SnapshotInterval == 0L) {
                branch.snapshots.addLast(next)
                if (branch.snapshots.size > RetainedSnapshots) branch.snapshots.removeFirst()
            }
        } else if (next.tick == branch.head.tick && next != branch.head) {
            engine = EcologyEngine(terrain, branch.head, branch.pending, recordHistory = false)
            interventionCount = engine.countPendingInterventions()
            return false
        }
        return true
    }

    fun seek(branchId: Int, tick: Long): ReplayRequest? {
        val branch = branches[branchId] ?: return null
        if (tick !in branch.start.tick..branch.head.tick) return null
        val pending = if (branchId == this.branchId && state.tick == branch.head.tick) engine.pendingCommands()
        else branch.pending
        val start = if (tick == branch.head.tick) branch.head
        else branch.snapshots.lastOrNull { it.tick <= tick } ?: branch.start
        val firstCommand = branch.commands.firstAfter(start.tick)
        return ReplayRequest(terrain, branchId, tick, start, branch.commands.subList(firstCommand, branch.commands.size).toList() + pending)
    }

    fun show(replayed: ReplayResult) {
        parkPending()
        branchId = replayed.branchId
        engine = replayed.engine
        interventionCount = engine.countPendingInterventions()
    }

    fun fork(name: String): Boolean {
        val trimmed = name.trim()
        if (!canFork || trimmed.length !in 1..MaxBranchNameLength) return false
        parkPending()
        val id = branches.size + 1
        val start = state
        val child = BranchCheckpoint(id, trimmed, branchId, start, start, emptyList(), emptyList(), emptyList())
        branches[id] = Branch(child)
        branchId = id
        engine = EcologyEngine(terrain, start, recordHistory = false)
        interventionCount = 0
        return true
    }

    fun checkpoint(): TimelineCheckpoint {
        parkPending()
        return TimelineCheckpoint(branchId, state.tick, branches.values.map(Branch::checkpoint))
    }

    private fun parkPending() {
        if (state.tick == active.head.tick) active.pending = engine.pendingCommands()
    }

    private class Branch(checkpoint: BranchCheckpoint) {
        val id = checkpoint.id
        val name = checkpoint.name
        val parentId = checkpoint.parentId
        val start = checkpoint.start
        var head = checkpoint.head
        val snapshots = ArrayDeque(checkpoint.snapshots)
        val commands = checkpoint.commands.toMutableList()
        var pending = checkpoint.pending

        fun checkpoint() = BranchCheckpoint(id, name, parentId, start, head, snapshots.toList(), commands.toList(), pending)
    }

    companion object {
        fun start(terrain: TerrainMap): Timeline {
            val engine = EcologyEngine(terrain, rulesVersion = EnvironmentalRulesVersion, recordHistory = false)
            return Timeline(terrain, root(engine.state, emptyList()), engine)
        }

        fun root(start: EcologyState, pending: List<WorldCommand>) = TimelineCheckpoint(
            1, start.tick, listOf(BranchCheckpoint(1, "Main", null, start, start, emptyList(), emptyList(), pending)),
        )

        suspend fun restore(terrain: TerrainMap, checkpoint: TimelineCheckpoint): Timeline {
            val branch = checkpoint.branches.first { it.id == checkpoint.activeBranchId }
            val headEngine = EcologyEngine(terrain, branch.head, branch.pending, recordHistory = false)
            val timeline = Timeline(terrain, checkpoint, headEngine)
            if (checkpoint.viewedTick != branch.head.tick) {
                // Restore a replay cursor while retaining the branch head and its pending inputs.
                val request = checkNotNull(timeline.seek(branch.id, checkpoint.viewedTick))
                timeline.show(request.replay())
            }
            return timeline
        }
    }
}

internal data class ReplayRequest(
    val terrain: TerrainMap,
    val branchId: Int,
    val tick: Long,
    val start: EcologyState,
    val inputs: List<WorldCommand>,
) {
    suspend fun replay(): ReplayResult {
        val context = currentCoroutineContext()
        val engine = EcologyEngine(terrain, start, inputs, recordHistory = false)
        while (engine.state.tick < tick) {
            context.ensureActive()
            engine.advance()
        }
        return ReplayResult(branchId, engine)
    }
}

internal data class ReplayResult(val branchId: Int, val engine: EcologyEngine)

private fun List<WorldCommand>.firstAfter(tick: Long): Int {
    var low = 0
    var high = size
    while (low < high) {
        val mid = (low + high) / 2
        if (this[mid].tick <= tick) low = mid + 1 else high = mid
    }
    return low
}

private fun EcologyEngine.countPendingInterventions() = pendingCommands().count { it !is SetDestination }
