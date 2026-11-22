package id.branchworld.game.ecology

import id.branchworld.game.simulation.GridCell
import kotlinx.serialization.Serializable

/**
 * Rules v10: seasonal deer ranges, the crossing between them, settlements that disturb the way and refuges the Keeper
 * can declare. Earlier rules never carry a habitat section, so their canonical bytes and hashes are unchanged.
 */
internal const val RangeRulesVersion = 10
internal const val RangeLogLimit = 32
internal const val MaximumRefuges = 4
/** Grazing ticks a herd adds to a settlement's fields when it pushes through. */
internal const val TrampledGrazing = 16

/** A crafted world-story whose rules the engine applies. The Fading Grove is implied by rules v8–v9 on its seed. */
enum class Chapter(internal val code: Int, val seed: Long) {
    FadingGrove(1, FadingGroveSeed),
    LastCrossing(2, CrossingSeed);

    internal companion object {
        fun of(code: Int): Chapter? = entries.firstOrNull { it.code == code }
    }
}

internal const val CrossingSeed = 271828L

/** The chapter a history belongs to. Sandbox histories on rules v10 have none. */
internal fun chapterOf(rulesVersion: Int, seed: Long, habitat: HabitatState?): Chapter? = when {
    rulesVersion in ShapingRulesVersion..VillageRulesVersion -> Chapter.FadingGrove.takeIf { seed == FadingGroveSeed }
    rulesVersion >= RangeRulesVersion -> habitat?.chapter?.let(Chapter::of)
    else -> null
}

@Serializable
enum class RangeEventKind(internal val code: Int) {
    /** The herd leader set out for the season's range. [RangeEvent.detail] is the range: 1 wet, 2 dry. */
    HerdSetOut(1),
    /** The herd reached its range along an undisturbed way. [RangeEvent.detail] is the ticks spent travelling. */
    HerdCrossed(2),
    /** No undisturbed way existed, so the herd pushed through a settlement. [RangeEvent.detail] is the site id. */
    HerdPushedThrough(3),
    /** A calf was born on the wet-season meadow. [RangeEvent.detail] is the calf's id modulo 2^31. */
    CalfBorn(4),
    /** The Keeper declared a refuge. [RangeEvent.detail] is the region. */
    RefugeDeclared(5),
    /** The herd reached a wet-season pasture that a settlement now disturbs, so it cannot calve there. */
    PastureSettled(6),
}

@Serializable
internal data class RangeEvent(val tick: Long, val kind: RangeEventKind, val detail: Int, val cell: GridCell)

@Serializable
internal data class Refuge(val region: Int, val declaredAt: Long)

/** The herd's seasonal journey. [bound] is 0 before the first season turn, 1 toward the wet range and 2 toward the dry. */
@Serializable
internal data class HerdPassage(
    val bound: Int = 0,
    val departedAt: Long = 0,
    val arrivedAt: Long? = null,
    val pushedThrough: Boolean = false,
    val crossings: Int = 0,
    val forced: Int = 0,
    val calves: Int = 0,
)

@Serializable
internal data class HabitatState(
    val chapter: Int = 0,
    val refuges: List<Refuge> = emptyList(),
    val herd: HerdPassage = HerdPassage(),
    val log: List<RangeEvent> = emptyList(),
) {
    fun protects(region: Int) = refuges.any { it.region == region }

    fun record(event: RangeEvent): HabitatState =
        copy(log = if (log.size < RangeLogLimit) log + event else log.drop(1) + event)
}
