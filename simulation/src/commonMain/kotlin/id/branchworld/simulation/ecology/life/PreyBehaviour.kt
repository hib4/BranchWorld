package id.branchworld.simulation.ecology.life

import id.branchworld.simulation.core.GridCell
import id.branchworld.simulation.ecology.Creature
import id.branchworld.simulation.ecology.CreatureAction
import id.branchworld.simulation.ecology.Goal
import id.branchworld.simulation.ecology.Need
import id.branchworld.simulation.ecology.Species
import id.branchworld.simulation.ecology.cellId
import id.branchworld.simulation.ecology.wildlife.later
import kotlin.math.sign

/** Rabbits and deer: noticing danger, sounding the alarm, and running for cover or a warren. */
internal class PreyBehaviour(private val ground: LifeGround) {
    fun perceiveThreat(animal: Creature, step: LifeStep): Creature? {
        val tick = step.tick
        val life = checkNotNull(animal.life)
        val sight = when (animal.species) {
            Species.Rabbit -> RabbitSight
            Species.Deer -> DeerSight
            else -> FoxSight
        }
        val scanning = animal.action != CreatureAction.Eating || (tick + animal.id) % 4L == 0L
        val danger = step.animals.filter { other ->
            other.species.predator && (animal.species in other.species.prey() ||
                animal.species == Species.Fox && other.species == Species.Wolf)
        }
        var seen: Creature? = null
        var best = Int.MAX_VALUE
        for (predator in danger) {
            val range = chebyshev(animal.cell, predator.cell)
            // A resting or feeding predator is watched, not fled; a moving one is a threat; a chasing one is an emergency.
            val alarming = when (predator.action) {
                CreatureAction.Chasing -> sight
                CreatureAction.Stalking -> sight / 2
                CreatureAction.Walking, CreatureAction.Idle, CreatureAction.Alert, CreatureAction.Fleeing -> 6
                else -> 3
            }
            if (range > alarming) continue
            val hunting = predator.action == CreatureAction.Chasing
            val heard = range <= Hearing && predator.action != CreatureAction.Stalking && predator.action != CreatureAction.Sleeping
            val visible = (scanning || hunting || range <= 3) && ground.sees(animal.cell, predator.cell, alarming)
            if (!heard && !visible) continue
            val score = octile(animal.cell, predator.cell)
            if (score < best) {
                best = score
                seen = predator
            }
        }
        if (seen != null) {
            val fresh = life.threat == 0L
            return animal.copy(life = life.copy(threat = seen.id, heard = false, alarmUntil = later(tick, AlarmTicks),
                chaseFrom = if (fresh) tick else life.chaseFrom))
        }
        // The threat it was already running from, if still close.
        val prior = step.byId[life.threat]
        if (prior != null && life.alarmUntil > tick && chebyshev(prior.cell, animal.cell) <= sight + 4) return animal
        // A neighbour's thump.
        if (animal.species == Species.Rabbit && life.threat == 0L) {
            val thump = step.thumpers.firstOrNull { it.id != animal.id && chebyshev(it.cell, animal.cell) <= ThumpReach }
            if (thump != null) {
                return animal.copy(life = life.copy(threat = checkNotNull(thump.life).threat, heard = true,
                    alarmUntil = later(tick, HeardTicks), chaseFrom = tick))
            }
        }
        return null
    }

    fun flee(animal: Creature, step: LifeStep): Creature {
        val tick = step.tick
        val life = checkNotNull(animal.life)
        val threat = step.byId[life.threat]
        if (animal.species == Species.Rabbit && ground.shelter(animal)) {
            return animal.copy(goal = Goal(Need.Hide, animal.cell), action = CreatureAction.Alert, life = life.copy(stride = 0))
        }
        if (threat == null) {
            // A heard alarm for a predator nobody can place: stand up and look.
            return animal.copy(goal = Goal(Need.Hide, animal.cell), action = CreatureAction.Alert, life = life.copy(stride = 0))
        }
        val close = chebyshev(animal.cell, threat.cell)
        val refugeCell = if (animal.species == Species.Rabbit) bestRefuge(animal.cell, threat.cell, step) else null
        // Close pursuit: a rabbit jinks sideways and a hunter overshoots.
        if (animal.species == Species.Rabbit && close <= 2 && lifeRoll(ground.seed, tick, animal.id, RollJink, 100) < 45) {
            val away = sign((animal.cell.x - threat.cell.x).toDouble()).toInt() to sign((animal.cell.z - threat.cell.z).toDouble()).toInt()
            val sides = listOf(GridCell(animal.cell.x - away.second, animal.cell.z + away.first),
                GridCell(animal.cell.x + away.second, animal.cell.z - away.first))
            val side = sides[lifeRoll(ground.seed, tick, animal.id, RollSide, 2)].takeIf { ground.navigation.canStep8(animal.cell, it) }
            if (side != null) return ground.travel(animal, Goal(Need.Flee, side), CreatureAction.Fleeing, ground.sprint(animal), moving = true)
        }
        if (life.heard && close > 6) {
            val goal = refugeCell ?: animal.cell
            return if (goal == animal.cell) animal.copy(goal = Goal(Need.Hide, goal), action = CreatureAction.Alert, life = life.copy(stride = 0))
            else ground.travel(animal, Goal(Need.Flee, goal), CreatureAction.Walking, animal.species.gait.walk, moving = true)
        }
        if (refugeCell != null) return ground.travel(animal, Goal(Need.Flee, refugeCell), CreatureAction.Fleeing, ground.sprint(animal), moving = true)
        // Deer keep with the herd as they run; everything else simply puts distance between itself and the danger.
        val away = ground.navigation.adjacent8(animal.cell).maxWithOrNull(compareBy<GridCell> { octile(it, threat.cell) }
            .thenBy { -cellId(it) }) ?: animal.cell
        return ground.travel(animal, Goal(Need.Flee, away), CreatureAction.Fleeing, ground.sprint(animal), moving = true)
    }

    /** The warren or ground.cover a rabbit should run for: close to it and not toward the danger. */
    private fun bestRefuge(from: GridCell, threat: GridCell, step: LifeStep): GridCell? {
        var best: GridCell? = null
        var bestScore = Int.MAX_VALUE
        fun consider(cell: GridCell, bonus: Int) {
            val run = octile(from, cell)
            if (run > RefugeReach * 10) return
            // Never run past the predator to reach it.
            if (octile(threat, cell) < run - 10) return
            val score = run * 3 - minOf(octile(threat, cell), 120) - bonus
            if (score < bestScore || score == bestScore && best != null && cellId(cell) < cellId(best!!)) {
                best = cell
                bestScore = score
            }
        }
        for (warren in step.warrens) consider(warren.cell, 30)
        for (dz in -RefugeReach..RefugeReach) for (dx in -RefugeReach..RefugeReach) {
            val cell = GridCell(from.x + dx, from.z + dz)
            if (cell in ground.cover && cell in ground.reachable) consider(cell, 0)
        }
        return best
    }
}
