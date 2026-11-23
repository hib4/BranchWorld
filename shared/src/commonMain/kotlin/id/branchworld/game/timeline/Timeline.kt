package id.branchworld.game.timeline

import id.branchworld.game.ecology.EcologyEngine
import id.branchworld.game.ecology.PlacementCheck
import id.branchworld.game.ecology.PlantKind
import id.branchworld.game.ecology.Species
import id.branchworld.game.ecology.WorldStart
import id.branchworld.game.simulation.GridCell
import id.branchworld.game.ecology.EcologyState
import id.branchworld.game.ecology.EnvironmentalRulesVersion
import id.branchworld.game.ecology.FadingGroveSeed
import id.branchworld.game.ecology.Chapter
import id.branchworld.game.ecology.ShapingRulesVersion
import id.branchworld.game.ecology.VillageRulesVersion
import id.branchworld.game.ecology.Intervention
import id.branchworld.game.ecology.InterventionResult
import id.branchworld.game.ecology.SettlementEvent
import id.branchworld.game.ecology.Petition
import id.branchworld.game.ecology.PlanOption
import id.branchworld.game.ecology.VillageEvent
import id.branchworld.game.ecology.VillageEventKind
import id.branchworld.game.simulation.EndorsePlan
import id.branchworld.game.simulation.SetDestination
import id.branchworld.game.simulation.ReforestRegion
import id.branchworld.game.simulation.SupplementForage
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
internal const val FadingGroveRecovery = 900L

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

internal data class BranchSummary(
    val id: Int, val name: String, val parentId: Int?, val start: Long, val head: Long, val interventionCount: Int,
    val milestoneCount: Int,
)

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

    fun summaries() = branches.values.map {
        BranchSummary(it.id, it.name, it.parentId, it.start.tick, it.head.tick, it.interventions,
            it.head.settlements?.events?.size ?: 0)
    }

    /** The current head of every history, for a chapter's closing comparison. */
    fun outcomes(): List<ChapterOutcome> {
        parkPending()
        val summaries = summaries().associateBy { it.id }
        return branches.values.map { ChapterOutcome.of(summaries.getValue(it.id), if (it.id == branchId && state.tick == it.head.tick) state else it.head) }
    }

    val canCloseChapter: Boolean get() = state.tick == active.head.tick && state.chapterCanClose(chapter)

    fun settlementEvents(branchId: Int): List<SettlementEvent> {
        val branch = branches[branchId] ?: return emptyList()
        return branch.head.settlements?.events.orEmpty().filter { it.tick > branch.start.tick }
    }

    /** Rules v9 chronicle recorded after this branch began. */
    fun villageChronicle(branchId: Int): List<VillageEvent> {
        val branch = branches[branchId] ?: return emptyList()
        return branch.head.settlements?.village?.chronicle.orEmpty().filter { it.tick > branch.start.tick }
    }

    fun openPetition(): Petition? = engine.openPetition()

    fun pendingEndorsement(): EndorsePlan? = engine.pendingEndorsement()

    fun endorse(option: PlanOption): Boolean {
        if (!editsAllowed || !engine.endorse(option)) return false
        commandCount++
        interventionCount++
        return true
    }

    fun cancelEndorsement(): Boolean {
        if (state.tick != active.head.tick || !engine.cancelEndorsement()) return false
        commandCount--
        interventionCount--
        return true
    }

    fun renameActive(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.length !in 1..MaxBranchNameLength) return false
        active.name = trimmed
        return true
    }

    fun interventionMarkers(branchId: Int, through: Long = Long.MAX_VALUE, includePending: Boolean = false): List<InterventionMarker> {
        val branch = branches[branchId] ?: return emptyList()
        return buildList {
            for (index in 0 until branch.commands.firstAfter(through)) {
                branch.commands[index].marker(queued = false)?.let(::add)
            }
            if (includePending) {
                val queue = if (branchId == this@Timeline.branchId && state.tick == branch.head.tick) {
                    engine.pendingCommands()
                } else branch.pending
                queue.mapNotNullTo(this) { it.marker(queued = true) }
            }
        }
    }

    fun eventReplay(branchId: Int, tick: Long): ReplayRequest? {
        val branch = branches[branchId] ?: return null
        if (tick <= branch.start.tick || tick > branch.head.tick) return null
        val first = branch.commands.firstAfter(tick - 1)
        val last = branch.commands.firstAfter(tick)
        if ((first until last).none { branch.commands[it].marker(queued = false) != null }) return null
        return seek(branchId, tick - 1)
    }

    fun compare(leftId: Int, rightId: Int, tick: Long): ComparisonReplay? {
        if (leftId == rightId) return null
        val left = seek(leftId, tick) ?: return null
        val right = seek(rightId, tick) ?: return null
        return ComparisonReplay(
            left.throughTarget(), right.throughTarget(),
            interventionTrail(leftId, tick), interventionTrail(rightId, tick),
        )
    }

    private fun lineage(branchId: Int, through: Long): List<SharedPast> = buildList {
        var current = branches[branchId]
        var limit = through
        while (current != null) {
            add(SharedPast(current.id, limit))
            limit = minOf(limit, current.start.tick)
            current = current.parentId?.let(branches::get)
        }
    }

    fun history(branchId: Int): HistoryBrowser {
        var forkState: EcologyState? = null
        val segments = lineage(branchId, Long.MAX_VALUE).map { origin ->
            val branch = branches.getValue(origin.branchId)
            val end = forkState ?: branch.head
            forkState = branch.start
            HistorySegment(branch.id, branch.parentId, branch.start.tick, end.tick,
                listOf(branch.start) + branch.snapshots.filter { it.tick < end.tick } + end,
                interventionMarkers(branch.id, end.tick))
        }
        return HistoryBrowser(segments)
    }

    fun historyLocation(branchId: Int, tick: Long): Pair<Int, Long>? =
        lineage(branchId, tick).lastOrNull { origin ->
            val branch = branches.getValue(origin.branchId)
            tick in branch.start.tick..minOf(branch.head.tick, origin.tick)
        }?.let { it.branchId to tick }

    private fun interventionTrail(branchId: Int, through: Long): List<OwnedIntervention> =
        lineage(branchId, through).asReversed().flatMap { origin ->
            interventionMarkers(origin.branchId, origin.tick).map { OwnedIntervention(origin.branchId, it) }
        }

    fun sharedPast(leftId: Int, rightId: Int): SharedPast? {
        val right = lineage(rightId, Long.MAX_VALUE).associateBy { it.branchId }
        for (left in lineage(leftId, Long.MAX_VALUE)) {
            val other = right[left.branchId] ?: continue
            return left.copy(tick = minOf(left.tick, other.tick))
        }
        return null
    }

    fun comparisonMoments(leftId: Int, rightId: Int, range: LongRange): List<HistoryMoment> {
        if (range.isEmpty()) return emptyList()
        val left = branches.getValue(leftId).head
        val right = branches.getValue(rightId).head
        fun moment(tick: Long?, kind: HistoryMoment.Kind) = tick?.let { HistoryMoment(it, kind) }
        return buildList {
            moment(sharedPast(leftId, rightId)?.tick, HistoryMoment.Kind.Fork)?.let(::add)
            for (head in listOf(left, right)) {
                moment(head.shaping?.warningAt, HistoryMoment.Kind.Warning)?.let(::add)
                for (site in head.settlements?.sites.orEmpty()) {
                    moment(site.welfare?.firstShortage, HistoryMoment.Kind.Shortage)?.let(::add)
                    moment(site.welfare?.firstRecovery, HistoryMoment.Kind.Recovery)?.let(::add)
                }
                head.settlements?.village?.let { village ->
                    village.petitions.mapTo(this) { HistoryMoment(it.raisedAt, HistoryMoment.Kind.Petition) }
                    village.chronicle.filter { it.kind == VillageEventKind.HouseholdLeft }
                        .mapTo(this) { HistoryMoment(it.tick, HistoryMoment.Kind.Departure) }
                }
            }
            for (command in (interventionTrail(leftId, range.last) + interventionTrail(rightId, range.last)).distinct()) {
                add(HistoryMoment(command.marker.tick, HistoryMoment.Kind.Intervention, command))
            }
            if (left.shaping != null && left.life == null && terrain.key.seed == FadingGroveSeed) {
                add(HistoryMoment(FadingGroveRecovery, HistoryMoment.Kind.AfterRain))
            }
        }.filter { it.tick in range }.distinct().sortedBy { it.tick }
    }

    fun inspectRegions() = engine.inspectRegions()

    fun preview(kind: Intervention, region: Int) = engine.preview(kind, region)

    fun recommendedRainRegion() = engine.recommendedRainRegion()

    fun recommendedGroveRegion() = engine.recommendedGroveRegion()

    /** The crafted story this history belongs to, or null in the sandbox. */
    val chapter: Chapter? get() = engine.story

    fun pastureRegion() = engine.pastureRegion()

    fun herdRoute() = engine.herdRoute()

    fun cancelShape(): Boolean {
        if (state.tick != active.head.tick || !engine.cancelShape()) return false
        commandCount--
        interventionCount--
        return true
    }

    fun warningLocation(): Pair<Int, Long>? {
        val tick = state.shaping?.warningAt ?: active.head.shaping?.warningAt ?: return null
        return historyLocation(branchId, tick)
    }

    fun canSteer(left: Boolean) = editsAllowed && engine.steeringTarget(left) != null

    fun steer(left: Boolean) {
        if (editsAllowed && engine.queueSteering(left)) commandCount++
    }

    /** Rules v12: whether [species] could be set down on [cell] at the head of this history. */
    fun creatureCheck(species: Species, cell: GridCell): PlacementCheck =
        if (commandLimitReached) PlacementCheck.IslandFull else engine.creatureCheck(species, cell)

    fun plantCheck(kind: PlantKind, cell: GridCell): PlacementCheck =
        if (commandLimitReached) PlacementCheck.PlantsFull else engine.plantCheck(kind, cell)

    fun place(species: Species, cell: GridCell): Boolean = editsAllowed && engine.place(species, cell).also { if (it) commandCount++ }

    fun plant(kind: PlantKind, cell: GridCell): Boolean = editsAllowed && engine.plant(kind, cell).also { if (it) commandCount++ }

    fun remove(id: Long): Boolean = editsAllowed && engine.remove(id).also { if (it) commandCount++ }

    fun clearPlant(cell: GridCell): Boolean = editsAllowed && engine.clearPlant(cell).also { if (it) commandCount++ }

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
        val appliedInterventions = engine.appliedCommands.count { it !is SetDestination }
        interventionCount -= appliedInterventions
        if (extending) {
            branch.head = next
            branch.commands.addAll(engine.appliedCommands)
            branch.interventions += appliedInterventions
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
        var name = checkpoint.name
        val parentId = checkpoint.parentId
        val start = checkpoint.start
        var head = checkpoint.head
        val snapshots = ArrayDeque(checkpoint.snapshots)
        val commands = checkpoint.commands.toMutableList()
        var interventions = commands.count { it !is SetDestination }
        var pending = checkpoint.pending

        fun checkpoint() = BranchCheckpoint(id, name, parentId, start, head, snapshots.toList(), commands.toList(), pending)
    }

    companion object {
        fun start(
            terrain: TerrainMap,
            rulesVersion: Int = if (terrain.watershed == null) EnvironmentalRulesVersion else VillageRulesVersion,
            chapter: Chapter? = null,
            start: WorldStart = WorldStart.Empty,
        ): Timeline {
            val engine = EcologyEngine(terrain, rulesVersion = rulesVersion, recordHistory = false, newChapter = chapter, newStart = start)
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
