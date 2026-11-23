package id.branchworld.simulation.ecology.chronicle

import id.branchworld.simulation.core.GridCell
import id.branchworld.simulation.ecology.IslandEcology
import id.branchworld.simulation.ecology.chronicle.EventRef
import id.branchworld.simulation.ecology.chronicle.FireOutCause
import id.branchworld.simulation.ecology.chronicle.KeeperAct
import id.branchworld.simulation.ecology.chronicle.WorldChronicle
import id.branchworld.simulation.ecology.chronicle.WorldEventKind
import id.branchworld.simulation.ecology.environment.CalledShower
import id.branchworld.simulation.ecology.region
import id.branchworld.simulation.ecology.settlement.Hardship
import id.branchworld.simulation.ecology.settlement.record
import kotlinx.serialization.Serializable

/** Recent events kept in a rules-v11 history. Root events survive longest so cause chains stay anchored. */
internal const val WorldChronicleLimit = 96
internal const val MaxCauses = 3

/**
 * Kinds of world events recorded from rules v11. [root] events start a chain: nothing the simulation knows caused
 * them (lightning in the sandbox, the Keeper's own acts) or they frame a story (the dry storm).
 */
@Serializable
enum class WorldEventKind(internal val code: Int, internal val root: Boolean = false) {
    /** A dry storm gathers. [WorldEvent.detail] is the wind direction (0–7) it brings. */
    DryStorm(1, root = true),
    /** Lightning struck dry ground. [WorldEvent.detail] is the struck tile. */
    Lightning(2, root = true),
    /** Lightning found the ground too damp to catch. */
    StrikeFizzled(3),
    /** A fire began. [WorldEvent.subject] is the fire's number on this island. */
    FireStarted(4),
    /** A fire went out. [WorldEvent.subject] is tiles burned, [WorldEvent.detail] a [FireOutCause] code. */
    FireOut(5),
    /** A firebreak stopped a fire's front. */
    FirebreakHeld(6),
    /** A home burned. [WorldEvent.subject] is the structure id. */
    HomeBurned(7),
    /** A family left. [WorldEvent.subject] is the household id, [WorldEvent.detail] a [Hardship] code. */
    HouseholdLeft(8),
    /** A family moved into an empty home after losing its own. [WorldEvent.subject] is the household id. */
    HouseholdSheltered(9),
    /** A burned home was raised again. [WorldEvent.subject] is the structure id. */
    HomeRebuilt(10),
    /** Trees were felled. [WorldEvent.subject] is pines felled, [WorldEvent.detail] tiles cleared. */
    TreesFelled(11),
    /** The Keeper acted. [WorldEvent.detail] is a [KeeperAct] code, [WorldEvent.subject] the region or plan. */
    KeeperAct(12, root = true),
    /** The village settled a petition. [WorldEvent.subject] is the petition id, [WorldEvent.detail] the plan code. */
    PlanChosen(13, root = true),
    /** Grass returned to burned ground. [WorldEvent.subject] is the fire number. */
    MeadowReturned(14),
    /** Young trees rose on burned ground. [WorldEvent.subject] is the fire number. */
    YoungWood(15),
    /** Animals fled a fire. [WorldEvent.subject] is the first animal seen fleeing. */
    AnimalsFled(16),
    /** Rules v12: a predator caught prey. [WorldEvent.subject] is the predator, [WorldEvent.detail] the prey's species code. */
    Hunted(17),
    /** Rules v12: wolves joined into a pack. [WorldEvent.subject] is the pack, [WorldEvent.detail] its members. */
    PackFormed(18),
    /** Rules v12: hunted prey reached safety. [WorldEvent.subject] is the prey, [WorldEvent.detail] the hunter. */
    Escaped(19),
    /** Rules v12: an animal died of hunger. [WorldEvent.subject] is the animal, [WorldEvent.detail] its species code. */
    Starved(20),
    /** Rules v12: the last of a species died. [WorldEvent.detail] is the species code. */
    Vanished(21),
    /** Rules v12: a species doubled. [WorldEvent.subject] is the count, [WorldEvent.detail] the species code. */
    Boom(22),
    /** Rules v12: a species halved. [WorldEvent.subject] is the count, [WorldEvent.detail] the species code. */
    Crash(23),
    /** Rules v12: a placed pair raised young. [WorldEvent.subject] is the mother, [WorldEvent.detail] the species code. */
    FirstLitter(24);

    internal companion object {
        fun of(code: Int) = entries.first { it.code == code }
    }
}

/** What put a fire out. */
enum class FireOutCause(val code: Int) { BurnedOut(1), Rain(2), CalledShower(3) }

/** The Keeper's act recorded in the chronicle. */
enum class KeeperAct(val code: Int) {
    Rain(1), Grove(2), Protect(3), Firebreak(4), Endorse(5),
    /** Rules v12: the player set down, lifted out, planted or cleared. [WorldEvent.subject] is the species or plant code. */
    Place(6), Remove(7), Plant(8), Clear(9),
}

/** A self-describing reference: a chain still reads correctly after the event itself has left the chronicle. */
@Serializable
data class EventRef(val id: Int, val tick: Long, val kind: WorldEventKind)

@Serializable
data class WorldEvent(
    val id: Int,
    val tick: Long,
    val kind: WorldEventKind,
    val cell: GridCell,
    val subject: Int = 0,
    val detail: Int = 0,
    val causes: List<EventRef> = emptyList(),
) {
    val ref: EventRef get() = EventRef(id, tick, kind)
}

@Serializable
data class WorldChronicle(val events: List<WorldEvent> = emptyList(), val nextId: Int = 1) {
    fun find(id: Int): WorldEvent? = events.firstOrNull { it.id == id }

    /** Appends an event and returns the chronicle with it. Only causes a rule actually knows are passed in. */
    fun record(
        tick: Long, kind: WorldEventKind, cell: GridCell, subject: Int = 0, detail: Int = 0, causes: List<EventRef> = emptyList(),
    ): Pair<WorldChronicle, EventRef> {
        val event = WorldEvent(nextId, tick, kind, cell, subject, detail, causes.distinct().take(MaxCauses))
        val kept = if (events.size < WorldChronicleLimit) events else {
            val oldest = events.indexOfFirst { !it.kind.root }.takeIf { it >= 0 } ?: 0
            events.filterIndexed { index, _ -> index != oldest }
        }
        return WorldChronicle(kept + event, nextId + 1) to event.ref
    }

    fun validate(tick: Long, island: IslandEcology, living: Boolean = false) {
        require(events.size <= WorldChronicleLimit && nextId >= 1)
        require(living || events.none { it.kind.code > WorldEventKind.AnimalsFled.code ||
            it.kind == WorldEventKind.KeeperAct && it.detail > KeeperAct.Endorse.code })
        require(events.zipWithNext().all { (a, b) -> a.id < b.id && a.tick <= b.tick })
        require(events.all { event ->
            event.id in 1 until nextId && event.tick in 1..tick && island.terrain.cellAt(event.cell.x, event.cell.z) != null &&
                event.causes.size <= MaxCauses && event.causes.all { it.id < event.id && it.tick <= event.tick }
        })
        // A cause still in the chronicle must describe itself truthfully.
        val byId = events.associateBy { it.id }
        require(events.all { event -> event.causes.all { cause -> byId[cause.id]?.let { it.ref == cause } != false } })
    }
}

/** The chain of recorded causes behind [event], nearest first. Causes no longer in the chronicle appear as references. */
internal fun WorldChronicle.causeChain(event: WorldEvent, depth: Int = 8): List<CauseStep> {
    val steps = mutableListOf<CauseStep>()
    val seen = mutableSetOf(event.id)
    var frontier = event.causes
    var level = 1
    while (frontier.isNotEmpty() && level <= depth) {
        val next = mutableListOf<EventRef>()
        for (cause in frontier) {
            if (!seen.add(cause.id)) continue
            val found = find(cause.id)
            steps.add(CauseStep(level, cause, found))
            if (found != null) next.addAll(found.causes)
        }
        frontier = next
        level++
    }
    return steps
}

internal data class CauseStep(val depth: Int, val ref: EventRef, val event: WorldEvent?)

/** The Keeper's most recent act of [kind] still in the chronicle. */
internal fun WorldChronicle.lastAct(kind: KeeperAct): EventRef? =
    events.lastOrNull { it.kind == WorldEventKind.KeeperAct && it.detail == kind.code }?.ref
