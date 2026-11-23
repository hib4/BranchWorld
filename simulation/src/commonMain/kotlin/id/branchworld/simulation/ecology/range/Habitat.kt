package id.branchworld.simulation.ecology.range

import id.branchworld.simulation.core.GridCell
import id.branchworld.simulation.ecology.region
import id.branchworld.simulation.ecology.settlement.record
import kotlinx.serialization.Serializable

internal const val RangeLogLimit = 32
internal const val MaximumRefuges = 4
/** Grazing ticks a herd adds to a settlement's fields when it pushes through. */
internal const val TrampledGrazing = 16

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
data class RangeEvent(val tick: Long, val kind: RangeEventKind, val detail: Int, val cell: GridCell)

@Serializable
data class Refuge(val region: Int, val declaredAt: Long)

/** The herd's seasonal journey. [bound] is 0 before the first season turn, 1 toward the wet range and 2 toward the dry. */
@Serializable
data class HerdPassage(
    val bound: Int = 0,
    val departedAt: Long = 0,
    val arrivedAt: Long? = null,
    val pushedThrough: Boolean = false,
    val crossings: Int = 0,
    val forced: Int = 0,
    val calves: Int = 0,
)

@Serializable
data class HabitatState(
    val chapter: Int = 0,
    val refuges: List<Refuge> = emptyList(),
    val herd: HerdPassage = HerdPassage(),
    val log: List<RangeEvent> = emptyList(),
) {
    fun protects(region: Int) = refuges.any { it.region == region }

    fun record(event: RangeEvent): HabitatState =
        copy(log = if (log.size < RangeLogLimit) log + event else log.drop(1) + event)
}
