package id.branchworld.simulation.projection

import id.branchworld.simulation.ecology.Creature
import id.branchworld.simulation.ecology.CreatureAction
import id.branchworld.simulation.ecology.EcologyState
import id.branchworld.simulation.ecology.Need
import id.branchworld.simulation.ecology.Species
import id.branchworld.simulation.ecology.life.Origin
import id.branchworld.simulation.ecology.life.Sex
import id.branchworld.simulation.ecology.life.WorldStart
import id.branchworld.simulation.ecology.life.gait
import id.branchworld.simulation.ecology.wildlife.lifespan
import id.branchworld.simulation.terrain.TerrainMap

/** What a rules-v12 creature is doing and why, in words a player can follow. Derived from state, never stored. */
enum class ThoughtKind {
    Arriving, Grazing, Browsing, Drinking, Resting, Sleeping, Wandering, Watchful, Alarmed, Fleeing, Hiding,
    FleeingFire, Stalking, Chasing, Hunting, Feeding, LookingForMate, Courting, Digging, Howling, Patrolling,
    Regrouping, ReturningToDen, SeekingCompany, Thirsty, Hungry, Starving,
}

/** [subjectId] names the other animal involved, when there is one. */
data class CreatureThought(
    val kind: ThoughtKind,
    val subjectId: Long? = null,
    val subjectSpecies: Species? = null,
    val withPack: Boolean = false,
)

/** Rules v12 detail for one creature's card. */
data class CreatureLifePose(
    val sex: Sex,
    val placed: Boolean,
    val bornAt: Long,
    /** Ticks it can expect to live in all, from its birth. */
    val lifespan: Long,
    val food: Int,
    val water: Int,
    val energy: Int,
    val stamina: Int,
    val packId: Int,
    val carrying: Boolean,
    val kills: Int,
    val litters: Int,
    /** Centi-cells it covers per tick on its current gait; 0 while it stands. The renderer eases each step over this. */
    val pace: Int,
)

data class PackPose(val id: Int, val den: GroundPoint, val members: List<Long>, val hunting: Long?)

/** Rules v12 living world: census, packs and warrens for the HUD and the diorama. */
data class LifePose(
    val start: WorldStart,
    val census: Map<Species, Int>,
    val packs: List<PackPose>,
    val warrens: List<GroundPoint>,
    val placed: Long,
    /** Animals set down on the latest tick, so the renderer can drop them in. */
    val arrivals: Set<Long>,
)

internal fun EcologyState.lifePose(terrain: TerrainMap): LifePose? {
    val life = life ?: return null
    return LifePose(
        life.start,
        Species.entries.associateWith { species -> creatures.count { it.species == species } },
        life.packs.map { pack ->
            PackPose(pack.id, pack.den.project(terrain), creatures.filter { it.life?.packId == pack.id }.map { it.id },
                pack.target.takeIf { it != 0L })
        },
        life.warrens.map { it.cell.project(terrain) },
        life.placed,
        creatures.filter { it.life?.origin == Origin.Placed && it.wildlife?.bornAt == tick }.mapTo(HashSet()) { it.id },
    )
}

internal fun Creature.lifePose(): CreatureLifePose? {
    val life = life ?: return null
    val gait = species.gait
    val pace = when (action) {
        CreatureAction.Fleeing, CreatureAction.Chasing -> if (life.stamina > 0) gait.sprint else gait.walk
        CreatureAction.Stalking -> gait.stalk
        CreatureAction.Walking -> gait.walk
        else -> 0
    }
    return CreatureLifePose(life.sex, life.origin != Origin.Born, wildlife?.bornAt ?: 0, lifespan(), food, water, energy,
        life.stamina, life.packId, life.pregnantUntil != 0L, life.kills, life.litters, pace)
}

/** The plain-language reading of what [animal] is doing on this tick. */
internal fun EcologyState.thought(animal: Creature): CreatureThought? {
    val life = animal.life ?: return null
    val pack = this.life?.packs?.firstOrNull { it.id == life.packId }
    val packTarget = pack?.target?.takeIf { it != 0L }
    val prey = packTarget ?: animal.wildlife?.prey
    fun other(id: Long?) = id?.let { wanted -> creatures.firstOrNull { it.id == wanted } }
    val goal = animal.goal?.need
    val placedRecently = life.origin != Origin.Born && tick - (animal.wildlife?.bornAt ?: 0) < 40 && (animal.wildlife?.bornAt ?: 0) > 0
    return when {
        goal == Need.Flee && life.threat == 0L && animal.action == CreatureAction.Fleeing -> CreatureThought(ThoughtKind.FleeingFire)
        life.threat != 0L -> {
            val threat = other(life.threat)
            when {
                goal == Need.Hide -> CreatureThought(if (life.heard && threat == null) ThoughtKind.Watchful else ThoughtKind.Hiding,
                    threat?.id, threat?.species)
                life.heard && animal.action != CreatureAction.Fleeing -> CreatureThought(ThoughtKind.Alarmed, threat?.id, threat?.species)
                else -> CreatureThought(ThoughtKind.Fleeing, threat?.id, threat?.species)
            }
        }
        prey != null && (animal.action == CreatureAction.Chasing || animal.action == CreatureAction.Stalking) -> {
            val target = other(prey)
            CreatureThought(if (animal.action == CreatureAction.Chasing) ThoughtKind.Chasing else ThoughtKind.Stalking,
                target?.id, target?.species, withPack = packTarget != null)
        }
        animal.action == CreatureAction.Eating && animal.species.let { it == Species.Wolf } -> CreatureThought(ThoughtKind.Feeding)
        animal.action == CreatureAction.Courting -> CreatureThought(ThoughtKind.Courting, null, null)
        animal.action == CreatureAction.Digging -> CreatureThought(ThoughtKind.Digging)
        animal.action == CreatureAction.Howling -> CreatureThought(ThoughtKind.Howling, withPack = true)
        animal.food < 150 && animal.species == Species.Wolf -> CreatureThought(ThoughtKind.Starving)
        placedRecently -> CreatureThought(ThoughtKind.Arriving)
        animal.action == CreatureAction.Drinking -> CreatureThought(ThoughtKind.Drinking)
        animal.action == CreatureAction.Eating -> CreatureThought(
            if (animal.species == Species.Deer && patches.any { it.cell == animal.cell && it.kind == id.branchworld.simulation.ecology.ForageKind.Browse })
                ThoughtKind.Browsing else ThoughtKind.Grazing)
        animal.action == CreatureAction.Sleeping -> CreatureThought(ThoughtKind.Sleeping)
        animal.action == CreatureAction.Resting -> CreatureThought(ThoughtKind.Resting)
        goal == Need.Mate -> CreatureThought(ThoughtKind.LookingForMate)
        goal == Need.Rally && pack == null -> CreatureThought(ThoughtKind.SeekingCompany)
        goal == Need.Rally -> CreatureThought(ThoughtKind.Regrouping, withPack = true)
        goal == Need.Den -> CreatureThought(ThoughtKind.ReturningToDen, withPack = pack != null)
        goal == Need.Water -> CreatureThought(ThoughtKind.Thirsty)
        goal == Need.Food -> CreatureThought(ThoughtKind.Hungry)
        goal == Need.Rest -> CreatureThought(ThoughtKind.Resting)
        animal.species == Species.Wolf && pack != null && goal == Need.Roam -> CreatureThought(ThoughtKind.Patrolling, withPack = true)
        animal.species.let { it == Species.Wolf || it == Species.Fox } && animal.food < 500 -> CreatureThought(ThoughtKind.Hunting)
        else -> CreatureThought(ThoughtKind.Wandering)
    }
}
