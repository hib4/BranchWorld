package id.branchworld.simulation.ecology.life

import id.branchworld.simulation.core.GridCell
import id.branchworld.simulation.ecology.Creature
import id.branchworld.simulation.ecology.CreatureAction
import id.branchworld.simulation.ecology.ForageKind
import id.branchworld.simulation.ecology.ForagePatch
import id.branchworld.simulation.ecology.Goal
import id.branchworld.simulation.ecology.MaxReserve
import id.branchworld.simulation.ecology.Need
import id.branchworld.simulation.ecology.Species
import id.branchworld.simulation.ecology.cellId
import id.branchworld.simulation.ecology.environment.availableBrowse
import id.branchworld.simulation.ecology.wildlife.later
import kotlin.math.abs

/** Water, food, rest and burrows, the wolves' day and courtship: what an animal does when nothing hunts it. */
internal class EverydayNeeds(private val ground: LifeGround) {
    fun needs(animal: Creature, step: LifeStep): Creature {
        val tick = step.tick
        val life = checkNotNull(animal.life)
        val prior = animal.goal
        fun wants(need: Need, reserve: Int, startAt: Int, stopAt: Int) =
            reserve <= startAt || (prior?.need == need && reserve < stopAt)
        val herbivore = !animal.species.predator
        val eats = herbivore || animal.species == Species.Fox

        // The most urgent reserve wins; an errand for one never starves the other.
        val thirstFirst = animal.water <= animal.food || !eats
        if (animal.water <= 160 && thirstFirst) water(animal, step)?.let { return it }
        if (animal.food <= 160 && eats) food(animal, step)?.let { return it }
        if (animal.water <= 160) water(animal, step)?.let { return it }
        if (prior?.need == Need.Water && animal.water < 500) water(animal, step)?.let { return it }
        if (prior?.need == Need.Food && animal.food < 500 && eats) food(animal, step)?.let { return it }
        if (animal.water <= 320) water(animal, step)?.let { return it }
        if (animal.food <= 300 && eats) food(animal, step)?.let { return it }
        // A starving hunter keeps looking for prey however tired it is.
        if (animal.energy < 140 && !(animal.species == Species.Wolf && animal.food < 300)) rest(animal, step)?.let { return it }
        if (wants(Need.Water, animal.water, if (animal.species == Species.Rabbit) 360 else 440, 900)) water(animal, step)?.let { return it }
        if (eats && wants(Need.Food, animal.food, if (herbivore) 620 else 480, 940)) food(animal, step)?.let { return it }

        if (animal.species == Species.Wolf) {
            mate(animal, step)?.let { return it }
            wolfDay(animal, step)?.let { return it }
        }
        // A rabbit with no warren nearby digs one as soon as it is settled.
        if (animal.species == Species.Rabbit && animal.food > 400 && animal.water > 350) burrow(animal, step)?.let { return it }

        val night = step.night
        if (wants(Need.Rest, animal.energy, if (night && animal.species != Species.Wolf) 700 else 360, 930)) {
            rest(animal, step)?.let { return it }
        }
        mate(animal, step)?.let { return it }

        // Wander the home range.
        if (prior?.need == Need.Roam && prior.cell != animal.cell && (tick + animal.id) % 150L != 0L) {
            return ground.travel(animal, prior, CreatureAction.Walking, animal.species.gait.walk, moving = true)
        }
        if (prior?.need == Need.Roam && prior.cell == animal.cell && (tick + animal.id) % 40L != 0L) {
            return animal.copy(action = CreatureAction.Idle, life = life.copy(stride = 0))
        }
        val home = when (animal.species) {
            // A rabbit's range centres on the nearest warren once one is dug.
            Species.Rabbit -> step.warrens.filter { chebyshev(it.cell, animal.cell) <= WarrenRange }
                .minWithOrNull(compareBy<Warren> { octile(it.cell, animal.cell) }.thenBy { cellId(it.cell) })?.cell
                ?: checkNotNull(animal.wildlife).home
            Species.Deer -> step.animals.firstOrNull { it.species == Species.Deer && it.id != animal.id &&
                chebyshev(it.cell, animal.cell) <= 20 && it.id < animal.id }?.cell ?: checkNotNull(animal.wildlife).home
            else -> checkNotNull(animal.wildlife).home
        }
        val habitat: (GridCell) -> Boolean = when (animal.species) {
            Species.Rabbit -> { cell -> cell in ground.meadow || cell in ground.edgeSet }
            Species.Deer -> { cell -> cell in ground.naturalCover || cell in ground.meadow }
            Species.Fox -> { cell -> cell in ground.edgeSet || cell in ground.meadow }
            Species.Wolf -> { cell -> cell in ground.reachable }
        }
        val radius = when (animal.species) {
            Species.Rabbit -> 6
            Species.Deer -> 9
            Species.Fox -> 10
            Species.Wolf -> 16
        }
        val target = roamCell(animal, home, radius, habitat, tick)
        return ground.travel(animal, Goal(Need.Roam, target), CreatureAction.Walking, animal.species.gait.walk, moving = true)
    }

    private fun roamCell(animal: Creature, home: GridCell, radius: Int, habitat: (GridCell) -> Boolean, tick: Long): GridCell {
        val options = ArrayList<GridCell>()
        for (dz in -radius..radius) for (dx in -radius..radius) {
            val cell = GridCell(home.x + dx, home.z + dz)
            if (cell != animal.cell && cell in ground.reachable && habitat(cell) && chebyshev(cell, animal.cell) <= 12) options.add(cell)
        }
        if (options.isEmpty()) return if (home in ground.reachable) home else animal.cell
        return options[lifeRoll(ground.seed, tick, animal.id, RollRoam, options.size)]
    }

    private fun water(animal: Creature, step: LifeStep): Creature? {
        val prior = animal.goal
        val bank = if (prior?.need == Need.Water && prior.cell in step.springs) prior.cell else
            step.springs.minWithOrNull(compareBy<GridCell> { octile(animal.cell, it) }.thenBy(::cellId)) ?: return null
        if (bank == animal.cell) {
            return animal.copy(goal = Goal(Need.Water, bank), water = minOf(MaxReserve, animal.water + 31), action = CreatureAction.Drinking,
                life = checkNotNull(animal.life).copy(stride = 0))
        }
        return ground.travel(animal, Goal(Need.Water, bank), CreatureAction.Walking, animal.species.gait.walk, moving = false)
    }

    private fun food(animal: Creature, step: LifeStep): Creature? {
        val prior = animal.goal
        val patches = step.patches
        val current = prior?.takeIf { it.need == Need.Food }?.let { patches.getOrNull(ground.patchCells[it.cell]) }
        // A patch others are already working is worth less; a grazed-out one is left for fresher grass.
        val crowd = HashMap<GridCell, Int>()
        for (other in step.animals) if (other.id != animal.id && other.goal?.need == Need.Food) crowd[other.goal.cell] = (crowd[other.goal.cell] ?: 0) + 1
        val patch = current?.takeIf { edible(it, animal.species) && it.biomass >= FreshGrass } ?: patches.asSequence().filter { edible(it, animal.species) }
            .minWithOrNull(compareBy<ForagePatch> {
                val run = octile(animal.cell, it.cell) + (crowd[it.cell] ?: 0) * 25
                val liked = when (animal.species) {
                    Species.Rabbit -> (if (it.kind == ForageKind.Meadow) 60 else 0) + if (nearCover(it.cell)) 40 else 0
                    Species.Deer -> if (it.kind == ForageKind.Browse) 50 else 0
                    else -> 0
                }
                run - liked - it.biomass / 8
            }.thenBy { it.id }) ?: return null
        if (patch.cell != animal.cell) {
            return ground.travel(animal, Goal(Need.Food, patch.cell), CreatureAction.Walking, animal.species.gait.walk, moving = false)
        }
        val life = checkNotNull(animal.life).copy(stride = 0)
        if ((step.tick + animal.id) % 4L != 0L) return animal.copy(goal = Goal(Need.Food, patch.cell), action = CreatureAction.Eating, life = life)
        val index = ground.patchCells[patch.cell]
        val eaten = minOf(patch.availableBrowse(true), if (animal.species == Species.Fox) 5 else 10)
        val nutrition = when (animal.species) {
            Species.Rabbit -> if (patch.kind == ForageKind.Meadow) 4 else 3
            Species.Deer -> if (patch.kind == ForageKind.Browse) 5 else 3
            else -> 4
        }
        patches[index] = patch.copy(biomass = patch.biomass - eaten)
        // Rabbits take most of their water from the grass they eat.
        val moisture = if (animal.species == Species.Rabbit) eaten * 3 else 0
        return animal.copy(goal = Goal(Need.Food, patch.cell), food = minOf(MaxReserve, animal.food + eaten * nutrition),
            water = minOf(MaxReserve, animal.water + moisture), action = CreatureAction.Eating, life = life)
    }

    private fun edible(patch: ForagePatch, species: Species): Boolean {
        val available = patch.availableBrowse(true)
        return when (species) {
            Species.Rabbit -> available >= 5 && (patch.kind == ForageKind.Meadow || available >= 22)
            Species.Deer -> available >= 5
            Species.Fox -> patch.kind == ForageKind.Browse && available >= 14
            Species.Wolf -> false
        }
    }

    private fun nearCover(cell: GridCell) = cell in ground.cover || ground.navigation.adjacent(cell).any(ground.cover::contains)

    private fun rest(animal: Creature, step: LifeStep): Creature? {
        val tick = step.tick
        val life = checkNotNull(animal.life)
        val shelter: GridCell = when (animal.species) {
            Species.Rabbit -> {
                val warren = step.warrens.filter { chebyshev(it.cell, animal.cell) <= 10 }
                    .minWithOrNull(compareBy<Warren> { octile(it.cell, animal.cell) }.thenBy { cellId(it.cell) })?.cell
                if (warren == null && step.warrens.size < MaxWarrens && animal.matureAt <= tick &&
                    step.warrens.none { chebyshev(it.cell, animal.cell) < WarrenSpacing } && diggable(animal.cell)
                ) {
                    return animal.copy(goal = Goal(Need.Den, animal.cell), action = CreatureAction.Digging,
                        life = life.copy(busyUntil = tick + DigTicks, stride = 0))
                }
                warren ?: nearestCover(animal.cell) ?: animal.cell
            }
            Species.Wolf -> step.packs[step.packOf[animal.id] ?: 0]?.den ?: nearestCover(animal.cell) ?: animal.cell
            else -> nearestCover(animal.cell) ?: animal.cell
        }
        if (shelter == animal.cell || chebyshev(shelter, animal.cell) <= 0) {
            val sleeping = step.night != (animal.species == Species.Wolf)
            return animal.copy(goal = Goal(Need.Rest, animal.cell), energy = minOf(MaxReserve, animal.energy + 29),
                action = if (sleeping) CreatureAction.Sleeping else CreatureAction.Resting, life = life.copy(stride = 0))
        }
        return ground.travel(animal, Goal(Need.Rest, shelter), CreatureAction.Walking, animal.species.gait.walk, moving = true)
    }

    private fun burrow(animal: Creature, step: LifeStep): Creature? {
        val tick = step.tick
        if (animal.matureAt > tick || step.warrens.size >= MaxWarrens) return null
        if (step.warrens.any { chebyshev(it.cell, animal.cell) <= WarrenRange }) return null
        if (!diggable(animal.cell)) return null
        if (step.animals.any { it.id != animal.id && it.action == CreatureAction.Digging && chebyshev(it.cell, animal.cell) < WarrenSpacing }) return null
        return animal.copy(goal = Goal(Need.Den, animal.cell), action = CreatureAction.Digging,
            life = checkNotNull(animal.life).copy(busyUntil = tick + DigTicks, stride = 0))
    }

    private fun diggable(cell: GridCell) = cell in ground.meadow && ground.navigation.adjacent(cell).size >= 3 &&
        (nearCover(cell) || ground.navigation.adjacent8(cell).any { it in ground.edgeSet })

    private fun nearestCover(from: GridCell): GridCell? {
        if (from in ground.cover) return from
        for (ring in 1..12) {
            var best: GridCell? = null
            for (dz in -ring..ring) for (dx in -ring..ring) {
                if (maxOf(abs(dx), abs(dz)) != ring) continue
                val cell = GridCell(from.x + dx, from.z + dz)
                if (cell in ground.cover && cell in ground.reachable && (best == null || cellId(cell) < cellId(best))) best = cell
            }
            if (best != null) return best
        }
        return null
    }

    private fun wolfDay(animal: Creature, step: LifeStep): Creature? {
        val tick = step.tick
        val life = checkNotNull(animal.life)
        val packId = step.packOf[animal.id] ?: 0
        val pack = step.packs[packId]
        if (pack == null) {
            // A lone wolf looks for company.
            val other = step.animals.firstOrNull { it.species == Species.Wolf && it.id != animal.id &&
                (step.packOf[it.id] ?: 0).let { p -> p == 0 || step.animals.count { m -> step.packOf[m.id] == p } < PackLimit } &&
                chebyshev(it.cell, animal.cell) <= LoneSearch }
            if (other != null && chebyshev(other.cell, animal.cell) > 2) {
                return ground.travel(animal, Goal(Need.Rally, other.cell), CreatureAction.Walking, animal.species.gait.walk, moving = true)
            }
            return null
        }
        val members = step.animals.filter { it.species == Species.Wolf && step.packOf[it.id] == packId }
        val leader = members.first()
        val phase = step.phase
        // Dusk: the pack gathers at the den and howls.
        if (phase in 600 until 610 && chebyshev(animal.cell, pack.den) <= 4) {
            return animal.copy(goal = Goal(Need.Den, pack.den), action = CreatureAction.Howling,
                life = life.copy(busyUntil = tick + HowlTicks, stride = 0))
        }
        // By day a fed pack rests at the den.
        if (!step.night && phase < 560 && animal.food >= 420) {
            if (chebyshev(animal.cell, pack.den) <= 1) {
                return animal.copy(goal = Goal(Need.Den, pack.den), energy = minOf(MaxReserve, animal.energy + 20),
                    action = CreatureAction.Sleeping, life = life.copy(stride = 0))
            }
            return ground.travel(animal, Goal(Need.Den, pack.den), CreatureAction.Walking, animal.species.gait.walk, moving = false)
        }
        if (phase in 560 until 600) {
            return ground.travel(animal, Goal(Need.Den, pack.den), CreatureAction.Walking, animal.species.gait.walk, moving = false)
        }
        // Night patrol: the leader ranges the territory; the others keep with it.
        if (animal.id != leader.id) {
            val apart = chebyshev(animal.cell, leader.cell)
            if (apart > 8) return ground.travel(animal, Goal(Need.Rally, leader.cell), CreatureAction.Walking, ground.sprint(animal) * 3 / 4, moving = true)
            if (apart > 3) return ground.travel(animal, Goal(Need.Rally, leader.cell), CreatureAction.Walking, animal.species.gait.walk, moving = true)
            return animal.copy(goal = Goal(Need.Rally, leader.cell), action = CreatureAction.Idle, life = life.copy(stride = 0))
        }
        val prior = animal.goal
        val hunger = members.sumOf { it.food } / members.size
        val range = if (hunger < 450) HungryTerritory else Territory
        val patrol = if (prior?.need == Need.Roam && prior.cell != animal.cell && (tick + animal.id) % 200L != 0L) prior.cell
            else roamCell(animal, pack.den, range, { true }, tick)
        return ground.travel(animal, Goal(Need.Roam, patrol), CreatureAction.Walking, animal.species.gait.walk, moving = true)
    }

    private fun mate(animal: Creature, step: LifeStep): Creature? {
        val tick = step.tick
        val life = checkNotNull(animal.life)
        if (!readyToMate(animal, tick)) return null
        val partner = step.animals.asSequence().filter {
            it.species == animal.species && checkNotNull(it.life).sex != life.sex && readyToMate(it, tick) &&
                chebyshev(it.cell, animal.cell) <= mateReach(animal.species) &&
                (animal.species != Species.Wolf || breedingPair(animal, it, step))
        }.minWithOrNull(compareBy<Creature> { octile(it.cell, animal.cell) }.thenBy { it.id }) ?: return null
        if (chebyshev(partner.cell, animal.cell) <= 1) {
            val female = life.sex == Sex.Female
            val room = step.animals.count { it.species == animal.species } < animal.species.lifeLimit()
            return animal.copy(goal = Goal(Need.Mate, partner.cell), action = CreatureAction.Courting,
                breedAfter = if (female) animal.breedAfter else later(tick, MaleRest),
                life = life.copy(busyUntil = tick + CourtTicks, stride = 0,
                    pregnantUntil = if (female && room) later(tick, animal.species.gestation()) else life.pregnantUntil))
        }
        return ground.travel(animal, Goal(Need.Mate, partner.cell), CreatureAction.Walking, animal.species.gait.walk, moving = true)
    }

    private fun readyToMate(animal: Creature, tick: Long): Boolean {
        val life = checkNotNull(animal.life)
        val reserve = when (animal.species) {
            Species.Rabbit -> 560
            Species.Deer -> 650
            Species.Fox -> 560
            Species.Wolf -> 500
        }
        return animal.matureAt <= tick && animal.breedAfter <= tick && life.pregnantUntil == 0L &&
            animal.action != CreatureAction.Fleeing && (life.threat == 0L || life.heard) &&
            animal.food >= reserve && animal.water >= 380 && animal.energy >= 300
    }

    private fun breedingPair(a: Creature, b: Creature, step: LifeStep): Boolean {
        val pack = step.packOf[a.id] ?: 0
        if (pack == 0 || step.packOf[b.id] != pack) return false
        val members = step.animals.filter { it.species == Species.Wolf && step.packOf[it.id] == pack && it.matureAt <= step.tick }
        val female = members.firstOrNull { checkNotNull(it.life).sex == Sex.Female }?.id
        val male = members.firstOrNull { checkNotNull(it.life).sex == Sex.Male }?.id
        return setOf(a.id, b.id) == setOf(female, male)
    }
}
