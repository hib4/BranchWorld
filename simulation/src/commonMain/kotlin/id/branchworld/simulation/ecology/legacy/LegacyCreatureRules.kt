package id.branchworld.simulation.ecology.legacy

import id.branchworld.simulation.core.CreatureHeading
import id.branchworld.simulation.core.GridCell
import id.branchworld.simulation.core.SplitMix64
import id.branchworld.simulation.ecology.Creature
import id.branchworld.simulation.ecology.CreatureAction
import id.branchworld.simulation.ecology.CreatureTurn
import id.branchworld.simulation.ecology.EcologyState
import id.branchworld.simulation.ecology.ForageKind
import id.branchworld.simulation.ecology.ForagePatch
import id.branchworld.simulation.ecology.Goal
import id.branchworld.simulation.ecology.IslandEcology
import id.branchworld.simulation.ecology.LegacySpecies
import id.branchworld.simulation.ecology.MaturityTicks
import id.branchworld.simulation.ecology.MaxPerSpecies
import id.branchworld.simulation.ecology.MaxReserve
import id.branchworld.simulation.ecology.Need
import id.branchworld.simulation.ecology.Species
import id.branchworld.simulation.ecology.cellId
import id.branchworld.simulation.ecology.legacy.LegacyCreatureRules
import id.branchworld.simulation.ecology.wildlife.SurvivalTicks
import id.branchworld.simulation.ecology.wildlife.later
import id.branchworld.simulation.terrain.TerrainMap

/**
 * Rabbits and deer under rules v3–v6: each animal walks to food, water or shade as its needs fall, and adults of a
 * species breed every 120 ticks while the island has room. Later rules keep these histories replaying exactly.
 */
internal class LegacyCreatureRules(private val island: IslandEcology) {
    fun advance(
        previous: EcologyState,
        tick: Long,
        patches: MutableList<ForagePatch>,
        waypoints: Map<Long, GridCell>,
        random: SplitMix64,
    ): CreatureTurn {
        val animals = ArrayList<Creature>(previous.creatures.size)
        for (creature in previous.creatures) {
            val waypoint = waypoints[creature.id]
            val commanded = if (waypoint != null && creature.species == Species.Rabbit) {
                creature.copy(waypoint = waypoint)
            } else creature
            val animal = act(commanded, tick, patches, previous.springs, random)
            if (animal.depletedTicks < SurvivalTicks) animals.add(animal)
        }
        var nextId = previous.nextEntityId
        if (tick % 120L == 0L) {
            for (species in LegacySpecies) {
                if (nextId < Long.MAX_VALUE && breed(animals, species, tick, nextId)) nextId++
            }
        }
        return CreatureTurn(animals, nextId)
    }

    private fun act(
        animal: Creature,
        tick: Long,
        patches: MutableList<ForagePatch>,
        springs: List<GridCell>,
        random: SplitMix64,
    ): Creature {
        val rabbit = animal.species == Species.Rabbit
        val tired = animal.copy(
            food = (animal.food - if (rabbit) 2 else 1).coerceAtLeast(0),
            water = (animal.water - 1).coerceAtLeast(0),
            energy = (animal.energy - 1).coerceAtLeast(0),
            waypoint = animal.waypoint?.takeUnless { it == animal.cell },
        )
        val goal = decide(tired, patches, springs, random)
        val result = when {
            goal.cell != tired.cell -> {
                val pace = if (rabbit) 2L else 3L
                if (tick % pace == 0L) {
                    val next = island.navigation.step(tired.cell, island.terrainDistance(goal.cell))
                    tired.copy(
                        cell = next,
                        heading = heading(tired.cell, next),
                        energy = (tired.energy - 1).coerceAtLeast(0),
                        action = CreatureAction.Walking,
                    )
                } else {
                    tired.copy(action = CreatureAction.Walking)
                }
            }
            goal.need == Need.Food -> eat(tired, tick, patches)
            goal.need == Need.Water -> tired.copy(
                water = minOf(MaxReserve, tired.water + 30),
                action = CreatureAction.Drinking,
            )
            goal.need == Need.Rest -> tired.copy(
                energy = minOf(MaxReserve, tired.energy + 24),
                action = CreatureAction.Resting,
            )
            else -> tired.copy(action = CreatureAction.Idle)
        }
        return result.copy(
            goal = goal,
            depletedTicks = if (result.food == 0 || result.water == 0) result.depletedTicks + 1 else 0,
            waypoint = result.waypoint?.takeUnless { it == result.cell },
        )
    }

    private fun decide(
        animal: Creature,
        patches: List<ForagePatch>,
        springs: List<GridCell>,
        random: SplitMix64,
    ): Goal {
        val radius = if (animal.species == Species.Rabbit) 8 else 14
        val prior = animal.goal
        fun wants(need: Need, value: Int, low: Int, high: Int) =
            value <= low || (prior?.need == need && value < high)
        fun water() = resourceGoal(Need.Water, animal, springs)
        fun rest() = resourceGoal(Need.Rest, animal, island.forest)
        fun food() = forageGoal(animal, patches, radius)

        if (animal.water == 0) water()?.let { return it }
        if (animal.food == 0) food()?.let { return it }
        // Finish urgent trips through the recovery threshold before changing direction.
        if (prior?.need == Need.Water && animal.water < 400) water()?.let { return it }
        if (prior?.need == Need.Food && animal.food < 400) food()?.let { return it }
        if (animal.water <= 180) water()?.let { return it }
        if (animal.food <= 180) food()?.let { return it }
        if (animal.energy <= 110) rest()?.let { return it }
        animal.waypoint?.let { return Goal(Need.Waypoint, it) }

        if (wants(Need.Water, animal.water, 400, 880)) water()?.let { return it }
        if (wants(Need.Food, animal.food, 600, 850)) food()?.let { return it }
        if (wants(Need.Rest, animal.energy, 300, 850)) rest()?.let { return it }
        if (prior?.need == Need.Roam && prior.cell != animal.cell) return prior
        val distance = island.terrainDistance(animal.cell)
        val choices = island.roamCells(animal.species).filter { distance[cellId(it)] in 1..radius }
        val target = if (choices.isEmpty()) {
            island.navigation.pickDestination(random, animal.cell)
        } else {
            choices[random.nextBounded(choices.size)]
        }
        return Goal(Need.Roam, target)
    }

    private fun resourceGoal(need: Need, animal: Creature, cells: List<GridCell>): Goal? {
        val prior = animal.goal
        if (prior?.need == need && prior.cell in cells) return prior
        if (cells.isEmpty()) return null
        return island.nearest(island.terrainDistance(animal.cell), cells)?.let { Goal(need, it) }
    }

    private fun forageGoal(animal: Creature, patches: List<ForagePatch>, radius: Int): Goal? {
        val rabbit = animal.species == Species.Rabbit
        val prior = animal.goal
        if (prior?.need == Need.Food && patches.any {
                it.cell == prior.cell && it.biomass > 0 && (!rabbit || it.kind == ForageKind.Meadow)
            }
        ) return prior

        val distance = island.terrainDistance(animal.cell)
        var best: ForagePatch? = null
        var bestScore = Int.MAX_VALUE
        for (patch in patches) {
            if (patch.biomass < Bite || (rabbit && patch.kind != ForageKind.Meadow)) continue
            val steps = distance[patch.id]
            val outsideRange = if (steps > radius) TerrainMap.Size * TerrainMap.Size * 2 else 0
            val nonpreferred = if (!rabbit && patch.kind == ForageKind.Meadow) TerrainMap.Size * TerrainMap.Size else 0
            val score = outsideRange + nonpreferred + steps
            if (score < bestScore) {
                best = patch
                bestScore = score
            }
        }
        return best?.let { Goal(Need.Food, it.cell) }
    }

    private fun eat(animal: Creature, tick: Long, patches: MutableList<ForagePatch>): Creature {
        val index = patches.indexOfFirst { it.cell == animal.cell }
        if (index < 0) return animal.copy(action = CreatureAction.Idle)
        if (tick % 4L != 0L) return animal.copy(action = CreatureAction.Eating)
        val patch = patches[index]
        val amount = minOf(Bite, patch.biomass)
        val multiplier = if (animal.species == Species.Deer && patch.kind == ForageKind.Browse) 4 else 3
        patches[index] = patch.copy(biomass = patch.biomass - amount)
        return animal.copy(
            food = minOf(MaxReserve, animal.food + amount * multiplier),
            action = if (amount > 0) CreatureAction.Eating else CreatureAction.Idle,
        )
    }

    private fun breed(animals: MutableList<Creature>, species: Species, tick: Long, id: Long): Boolean {
        if (animals.count { it.species == species } >= MaxPerSpecies) return false
        val adults = animals.indices.filter {
            val candidate = animals[it]
            candidate.species == species && candidate.matureAt <= tick && candidate.breedAfter <= tick &&
                candidate.food >= 710 && candidate.water >= 680 && candidate.energy >= 650
        }
        if (adults.size < 2) return false
        val occupied = animals.mapTo(mutableSetOf()) { it.cell }
        for (firstIndex in adults) {
            val first = animals[firstIndex]
            val adjacent = island.navigation.adjacent(first.cell).firstOrNull { it !in occupied } ?: continue
            val distance = island.terrainDistance(first.cell)
            val secondIndex = adults.firstOrNull {
                it > firstIndex && distance[cellId(animals[it].cell)] in 0..6
            } ?: continue
            for (index in listOf(firstIndex, secondIndex)) {
                val parent = animals[index]
                animals[index] = parent.copy(food = parent.food - 90, energy = parent.energy - 75, breedAfter = later(tick, 600))
            }
            animals.add(Creature(id, species, adjacent, food = 750, water = 750, energy = 750, matureAt = later(tick, MaturityTicks)))
            return true
        }
        return false
    }

    private fun heading(from: GridCell, to: GridCell) = when {
        to.x > from.x -> CreatureHeading.PositiveX
        to.x < from.x -> CreatureHeading.NegativeX
        to.z > from.z -> CreatureHeading.PositiveZ
        else -> CreatureHeading.NegativeZ
    }

    private companion object {
        const val Bite = 10
    }
}
