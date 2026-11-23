package id.branchworld.simulation.ecology.life

import id.branchworld.simulation.core.CreatureHeading
import id.branchworld.simulation.core.GridCell
import id.branchworld.simulation.ecology.Creature
import id.branchworld.simulation.ecology.CreatureAction
import id.branchworld.simulation.ecology.Goal
import id.branchworld.simulation.ecology.Need
import id.branchworld.simulation.ecology.Species
import id.branchworld.simulation.ecology.chronicle.WorldChronicle
import id.branchworld.simulation.ecology.chronicle.WorldEventKind
import id.branchworld.simulation.ecology.settlement.record
import id.branchworld.simulation.ecology.wildlife.later

/** Foxes and wolves: choosing prey, the chase and the catch, and the escapes the chronicle remembers. */
internal class PredatorBehaviour(private val ground: LifeGround) {
    /** Escapes noticed while the solo hunters gave up this tick; recorded by the engine pass that owns the chronicle. */
    val pendingEscapes = mutableListOf<Pair<Creature, Creature>>()

    fun hunt(animal: Creature, step: LifeStep): Creature? {
        val tick = step.tick
        val life = checkNotNull(animal.life)
        val packId = step.packOf[animal.id] ?: life.packId
        val pack = step.packs[packId]
        if (pack != null && pack.target != 0L) {
            val target = step.byId[pack.target] ?: return null
            val members = step.animals.filter { it.species == Species.Wolf && step.packOf[it.id] == packId }
            val leader = members.first()
            val range = chebyshev(animal.cell, target.cell)
            val aim = if (animal.id == leader.id || range <= 2) target.cell else intercept(target, step)
            val alarmed = checkNotNull(target.life).threat != 0L
            val chasing = range <= ChaseRange || alarmed || animal.action == CreatureAction.Chasing
            return ground.travel(animal.copy(life = life.copy(chaseFrom = if (life.chaseFrom == 0L) tick else life.chaseFrom)),
                Goal(Need.Hunt, aim), if (chasing) CreatureAction.Chasing else CreatureAction.Stalking,
                if (chasing) ground.sprint(animal) else animal.species.gait.stalk, moving = true)
        }
        if (pack != null) return null
        // Foxes and lone wolves hunt alone.
        val wild = checkNotNull(animal.wildlife)
        val prior = wild.prey?.let(step.byId::get)
        if (wild.prey != null) {
            val tooLong = tick - life.chaseFrom > SoloHuntTicks
            if (prior == null || ground.refuge(prior) || tooLong || life.stamina < GiveUpStamina || chebyshev(prior.cell, animal.cell) > 14) {
                var given = animal.copy(goal = null, action = CreatureAction.Idle,
                    wildlife = wild.copy(prey = null, huntUntil = later(tick, SoloRecovery)), life = life.copy(chaseFrom = 0))
                if (prior != null && tick - life.chaseFrom >= EscapeTicks && animal.action == CreatureAction.Chasing) {
                    pendingEscapes.add(prior to animal)
                }
                return given
            }
            val range = chebyshev(animal.cell, prior.cell)
            val chasing = range <= ChaseRange || checkNotNull(prior.life).threat != 0L || animal.action == CreatureAction.Chasing
            return ground.travel(animal, Goal(Need.Hunt, prior.cell), if (chasing) CreatureAction.Chasing else CreatureAction.Stalking,
                if (chasing) ground.sprint(animal) else animal.species.gait.stalk, moving = true)
        }
        val hungry = animal.food < if (animal.species == Species.Wolf) 720 else 640
        if (!hungry || tick < wild.huntUntil || animal.matureAt > tick || (tick + animal.id) % ScanEvery != 0L ||
            life.stamina < 400 || animal.water < 150
        ) return null
        val sight = if (animal.species == Species.Wolf) WolfSight else FoxSight
        val target = step.animals.asSequence()
            .filter { it.species in animal.species.prey() && (it.species != Species.Deer) && !ground.refuge(it) && ground.sees(animal.cell, it.cell, sight) }
            .minWithOrNull(compareBy<Creature> { octile(animal.cell, it.cell) }.thenBy { it.id }) ?: return null
        return ground.travel(animal.copy(wildlife = wild.copy(prey = target.id, huntUntil = later(tick, SoloHuntTicks.toLong())),
            life = life.copy(chaseFrom = tick)), Goal(Need.Hunt, target.cell), CreatureAction.Stalking, animal.species.gait.stalk, moving = true)
    }

    fun drainEscapes(chronicle: WorldChronicle, tick: Long): WorldChronicle {
        var log = chronicle
        for ((prey, hunter) in pendingEscapes) log = escaped(log, tick, prey, hunter)
        pendingEscapes.clear()
        return log
    }

    fun escaped(chronicle: WorldChronicle, tick: Long, prey: Creature, hunter: Creature): WorldChronicle {
        if (chronicle.events.any { it.kind == WorldEventKind.Escaped && it.subject == prey.id.toSubject() && tick - it.tick < 300 }) {
            return chronicle
        }
        return chronicle.record(tick, WorldEventKind.Escaped, prey.cell, prey.id.toSubject(), hunter.id.toSubject()).first
    }

    /** Where a flanking wolf should run to cut the prey off: a few cells ahead of it, or on its way to safety. */
    private fun intercept(target: GridCell, ahead: CreatureHeading): GridCell {
        val (dx, dz) = ahead.delta()
        for (lead in 3 downTo 1) {
            val cell = GridCell(target.x + dx * lead, target.z + dz * lead)
            if (cell in ground.reachable) return cell
        }
        return target
    }

    private fun intercept(target: Creature, step: LifeStep): GridCell {
        val goal = target.goal?.takeIf { it.need == Need.Flee || it.need == Need.Hide }?.cell
        if (goal != null && goal != target.cell && ground.reachable.contains(goal)) {
            // Run for the point between the prey and the shelter it is making for.
            val mid = GridCell((target.cell.x + goal.x) / 2, (target.cell.z + goal.z) / 2)
            if (mid in ground.reachable) return mid
        }
        return intercept(target.cell, target.heading)
    }
}
