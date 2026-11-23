package id.branchworld.simulation.ecology.life

import id.branchworld.simulation.core.GridCell
import id.branchworld.simulation.core.mix64
import id.branchworld.simulation.ecology.Species
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** How a new living world begins. */
@Serializable
enum class WorldStart(val code: Int) {
    /** Terrain and its wild plants; every animal comes from the player. */
    Empty(1),
    /** A starter ecosystem of rabbits, deer and foxes. Wolves are left for the player to bring. */
    Wild(2),
}

@Serializable
enum class Sex(internal val code: Int) { Female(1), Male(2) }

/** Where a rules-v12 creature came from. */
@Serializable
enum class Origin(val code: Int) { Seeded(1), Placed(2), Born(3) }

/** What the player can plant. */
@Serializable
enum class PlantKind(val code: Int) {
    /** A young pine. Once rooted it shelters animals and browsers feed on it. */
    Tree(1),
    /** A patch of grass that rabbits and deer graze. */
    Meadow(2),
}

/** Most stamina a creature can hold; sprinting spends it and walking restores it. */
internal const val MaxStamina = 1000

/** Centi-cells a stride needs: 100 for an orthogonal step, 141 for a diagonal. */
internal const val OrthogonalStride = 100
internal const val DiagonalStride = 141

@Serializable
data class CreatureLife(
    val sex: Sex,
    val origin: Origin,
    val stamina: Int = MaxStamina,
    /** Progress toward the next cell, in centi-cells. */
    val stride: Int = 0,
    /** The predator this animal is running from, or 0. */
    val threat: Long = 0,
    val alarmUntil: Long = 0,
    /** The alarm came from a neighbour's thump rather than the animal's own eyes; heard alarms are not passed on. */
    val heard: Boolean = false,
    /** The wolf pack this animal belongs to, or 0. */
    val packId: Int = 0,
    /** Digging, howling or courting holds the animal until this tick. */
    val busyUntil: Long = 0,
    /** A carrying mother gives birth on this tick; 0 when not carrying. */
    val pregnantUntil: Long = 0,
    /** The tick the current hunt or flight began, or 0. */
    val chaseFrom: Long = 0,
    val kills: Int = 0,
    val litters: Int = 0,
)

/** A wolf pack: its den, territory and the prey it hunts together. */
@Serializable
data class Pack(
    val id: Int,
    val formedAt: Long,
    val den: GridCell,
    val target: Long = 0,
    val huntFrom: Long = 0,
    val lastKillAt: Long = -1,
)

/** A rabbit warren: safety, a nursery and a place to rest. */
@Serializable
data class Warren(val cell: GridCell, val dugAt: Long)

/** The last low and high count of a species, for booms and crashes. */
@Serializable
data class Census(val species: Species, val low: Int, val high: Int)

@Serializable
data class LifeState(
    val start: WorldStart,
    /** Animals the player set down, plus the Wild starters. */
    val placed: Long = 0,
    val removed: Long = 0,
    /** Plants the player has planted and later cleared. */
    val planted: Int = 0,
    val cleared: Int = 0,
    val packs: List<Pack> = emptyList(),
    val nextPackId: Int = 1,
    val warrens: List<Warren> = emptyList(),
    val census: List<Census> = emptyList(),
    /** One-shot milestones already recorded, as a bit set. */
    val firsts: Int = 0,
)

internal const val MaxPacks = 8
internal const val MaxWarrens = 24
internal const val MaxPlanted = 192
internal const val IslandCreatureLimit = 80
internal const val CensusInterval = 600L

/** Rules v12 population limits. Provisional until the measured performance experiment (E-L1). */
internal fun Species.lifeLimit() = when (this) {
    Species.Rabbit -> 40
    Species.Deer -> 14
    Species.Fox -> 8
    Species.Wolf -> 12
}

/** What each predator hunts in rules v12. */
internal fun Species.prey(): Set<Species> = when (this) {
    Species.Fox -> setOf(Species.Rabbit)
    Species.Wolf -> setOf(Species.Rabbit, Species.Deer)
    else -> emptySet()
}

internal val Species.predator get() = this == Species.Fox || this == Species.Wolf

/** Male on even ids, female on odd: two animals set down one after the other always make a pair. */
internal fun sexOf(id: Long) = if (id % 2L == 0L) Sex.Male else Sex.Female

/** Centi-cells per tick for a walk, a stalk and a sprint. */
internal data class Gait(val walk: Int, val stalk: Int, val sprint: Int, val sprintCost: Int, val recovery: Int)

internal val Species.gait get() = when (this) {
    Species.Rabbit -> Gait(walk = 45, stalk = 30, sprint = 96, sprintCost = 22, recovery = 9)
    Species.Deer -> Gait(walk = 35, stalk = 25, sprint = 95, sprintCost = 10, recovery = 6)
    Species.Fox -> Gait(walk = 40, stalk = 25, sprint = 88, sprintCost = 12, recovery = 6)
    Species.Wolf -> Gait(walk = 45, stalk = 25, sprint = 100, sprintCost = 8, recovery = 5)
}

/** Octile distance in tenths of a cell: 10 per orthogonal step and 14 per diagonal. */
internal fun octile(a: GridCell, b: GridCell): Int {
    val dx = abs(a.x - b.x)
    val dz = abs(a.z - b.z)
    return 10 * max(dx, dz) + 4 * min(dx, dz)
}

/** Steps on an eight-way grid. */
internal fun chebyshev(a: GridCell, b: GridCell) = max(abs(a.x - b.x), abs(a.z - b.z))

/**
 * A counter-based roll in 0 until [bound] for one decision. It depends only on the world seed, the tick, the creature
 * and the purpose, so placing an animal on one side of the island never reshuffles a decision on the other.
 */
internal fun lifeRoll(seed: Long, tick: Long, id: Long, purpose: Int, bound: Int): Int {
    val mixed = mix64(seed.toULong() xor LifeSalt xor mix64(tick.toULong() * 0x9e3779b97f4a7c15uL xor
        (id.toULong() shl 8) xor purpose.toULong()))
    return (mixed % bound.toULong()).toInt()
}

private const val LifeSalt = 0x3c6ef372fe94f82buL
