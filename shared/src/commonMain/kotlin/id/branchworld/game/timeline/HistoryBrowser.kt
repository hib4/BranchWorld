package id.branchworld.game.timeline

import id.branchworld.game.ecology.EcologyState
import id.branchworld.game.ecology.EventRef
import id.branchworld.game.ecology.RangeEvent
import id.branchworld.game.ecology.RangeEventKind
import id.branchworld.game.ecology.SettlementEvent
import id.branchworld.game.ecology.SettlementEventKind
import id.branchworld.game.ecology.SettlementWorld
import id.branchworld.game.ecology.VillageEvent
import id.branchworld.game.ecology.VillageEventKind
import id.branchworld.game.ecology.WildlifeEvent
import id.branchworld.game.ecology.WorldChronicle
import id.branchworld.game.ecology.WorldEvent
import id.branchworld.game.ecology.WorldEventKind
import id.branchworld.game.ecology.causeChain

internal enum class HistoryCategory { Wildlife, Village, Land, Keeper }

internal sealed interface HistoryEntry {
    val branchId: Int
    val tick: Long
    val category: HistoryCategory

    data class World(override val branchId: Int, val event: WorldEvent) : HistoryEntry {
        override val tick get() = event.tick
        override val category get() = when (event.kind) {
            WorldEventKind.AnimalsFled -> HistoryCategory.Wildlife
            WorldEventKind.HomeBurned, WorldEventKind.HouseholdLeft, WorldEventKind.HouseholdSheltered,
            WorldEventKind.HomeRebuilt, WorldEventKind.PlanChosen -> HistoryCategory.Village
            WorldEventKind.KeeperAct -> HistoryCategory.Keeper
            else -> HistoryCategory.Land
        }
    }

    data class Village(override val branchId: Int, val event: VillageEvent, val world: SettlementWorld) : HistoryEntry {
        override val tick get() = event.tick
        override val category get() = HistoryCategory.Village
    }

    data class Settlement(override val branchId: Int, val event: SettlementEvent, val world: SettlementWorld) : HistoryEntry {
        override val tick get() = event.tick
        override val category get() = HistoryCategory.Village
    }

    data class Wildlife(override val branchId: Int, val event: WildlifeEvent) : HistoryEntry {
        override val tick get() = event.tick
        override val category get() = HistoryCategory.Wildlife
    }

    data class Range(override val branchId: Int, val event: RangeEvent) : HistoryEntry {
        override val tick get() = event.tick
        override val category get() = HistoryCategory.Wildlife
    }

    data class Command(override val branchId: Int, val marker: InterventionMarker) : HistoryEntry {
        override val tick get() = marker.tick
        override val category get() = HistoryCategory.Keeper
    }

    data class Moment(override val branchId: Int, val moment: HistoryMoment) : HistoryEntry {
        override val tick get() = moment.tick
        override val category get() = if (moment.kind == HistoryMoment.Kind.Petition) HistoryCategory.Village else HistoryCategory.Land
    }
}

internal data class HistorySegment(
    val branchId: Int,
    val parentId: Int?,
    val start: Long,
    val end: Long,
    val states: List<EcologyState>,
    val commands: List<InterventionMarker>,
) {
    fun owns(tick: Long) = tick in start..end && (tick != start || parentId == null)
}

internal data class HistoryCause(val branchId: Int?, val ref: EventRef, val event: WorldEvent?, val depth: Int)

internal class HistoryBrowser(private val segments: List<HistorySegment>) {
    private val worldEvents = segments.flatMap { segment ->
        segment.states.flatMap { it.chronicle?.events.orEmpty() }.filter { segment.owns(it.tick) }
    }.distinctBy { it.id }

    private val chronology = WorldChronicle(worldEvents)

    val entries: List<HistoryEntry> = segments.flatMap(::entriesIn)
        .sortedWith(compareByDescending<HistoryEntry> { it.tick }.thenByDescending { it.branchId })

    fun causesOf(event: WorldEvent): List<HistoryCause> = chronology.causeChain(event).map { step ->
        val owner = segments.firstOrNull { it.owns(step.ref.tick) }
        HistoryCause(owner?.branchId, step.ref, step.event, step.depth)
    }

    private fun entriesIn(segment: HistorySegment): List<HistoryEntry> = buildList {
        val branch = segment.branchId
        val commands = segment.commands
        val commandTicks = commands.mapTo(HashSet()) { it.tick }
        val world = worldEvents.filter { segment.owns(it.tick) }
        val worldKinds = world.mapTo(HashSet()) { Triple(it.tick, it.kind, it.subject) }
        world.filter { it.kind != WorldEventKind.KeeperAct || it.tick !in commandTicks }
            .mapTo(this) { HistoryEntry.World(branch, it) }
        commands.mapTo(this) { HistoryEntry.Command(branch, it) }
        val settlements = segment.states.mapNotNull { it.settlements }.asReversed()
        val village = settlements.flatMap { state ->
            state.village?.chronicle.orEmpty().map { HistoryEntry.Village(branch, it, state) }
        }.filter { segment.owns(it.tick) }.distinctBy { it.event }
        val villageFoundings = village.filter { it.event.kind == VillageEventKind.Founded }
            .mapTo(HashSet()) { it.tick to it.event.siteId }
        village.filter { entry ->
            val kind = when (entry.event.kind) {
                VillageEventKind.HomeBurned -> WorldEventKind.HomeBurned
                VillageEventKind.WorkCompleted -> WorldEventKind.HomeRebuilt
                VillageEventKind.HouseholdLeft -> WorldEventKind.HouseholdLeft
                VillageEventKind.PlanChosen -> WorldEventKind.PlanChosen
                else -> null
            }
            Triple(entry.tick, kind, entry.event.subject) !in worldKinds
        }.forEach(::add)
        settlements.flatMap { state -> state.events.map { HistoryEntry.Settlement(branch, it, state) } }
            .filter { segment.owns(it.tick) && (it.event.kind != SettlementEventKind.Founded ||
                (it.tick to it.event.siteId) !in villageFoundings) }
            .distinctBy { it.event }.forEach(::add)
        segment.states.flatMap { it.wildlife?.events.orEmpty() }.filter { segment.owns(it.tick) }
            .distinct().mapTo(this) { HistoryEntry.Wildlife(branch, it) }
        segment.states.flatMap { it.habitat?.log.orEmpty() }.filter { segment.owns(it.tick) }
            .filterNot { it.kind == RangeEventKind.RefugeDeclared && it.tick in commandTicks }
            .distinct().mapTo(this) { HistoryEntry.Range(branch, it) }
        segment.states.mapNotNull { it.shaping?.warningAt }.filter(segment::owns).distinct()
            .filterNot { tick -> world.any { it.tick == tick && it.kind == WorldEventKind.DryStorm } }
            .mapTo(this) { HistoryEntry.Moment(branch, HistoryMoment(it, HistoryMoment.Kind.Warning)) }
        if (segment.parentId != null) add(HistoryEntry.Moment(branch, HistoryMoment(segment.start, HistoryMoment.Kind.Fork)))
    }
}
