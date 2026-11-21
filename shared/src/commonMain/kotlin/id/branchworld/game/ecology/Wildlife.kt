package id.branchworld.game.ecology

import id.branchworld.game.simulation.CreatureHeading
import id.branchworld.game.simulation.GridCell
import id.branchworld.game.simulation.SplitMix64
import id.branchworld.game.terrain.Biome
import kotlin.math.abs

/** Rules v7. Paths and cover indices are rebuildable from the authoritative tick. */
internal class Wildlife(private val island: IslandEcology) {
    private val terrain = island.terrain
    private val forest = island.reachable.filter { terrain.terrainCell(it).biome == Biome.PineForest }
    private val naturalCover = forest.toHashSet()
    private val edges = forest.filter { cell -> island.navigation.adjacent(cell).any { it !in naturalCover } }
        .ifEmpty { island.roamCells(Species.Deer) }
    private var plantedCover = emptyList<GridCell>()
    private var cover = naturalCover
    private var shelters: List<GridCell> = forest

    fun populate(state: EcologyState): EcologyState {
        val animals = state.creatures.map { animal ->
            animal.copy(wildlife = WildlifeLife(0, animal.cell))
        }.toMutableList()
        val rabbits = animals.filter { it.species == Species.Rabbit }
        repeat(2) {
            val lastFox = animals.last().takeIf { it.species == Species.Fox }
            val fromLead = island.terrainDistance(rabbits.first().cell)
            val fromFox = lastFox?.let { island.terrainDistance(it.cell) }
            val candidates = edges.filter { cell -> animals.none { it.cell == cell } }.ifEmpty { edges }
            val selected = candidates.minWith(
                compareBy<GridCell> { cell ->
                    val index = cellId(cell)
                    val nearestRabbit = rabbits.minOf { spacing(cell, it.cell) }
                    val mentorDistance = fromFox?.get(index) ?: 14
                    val browseDistance = state.patches.asSequence().filter { it.kind == ForageKind.Browse }
                        .minOfOrNull { spacing(cell, it.cell) } ?: 0
                    abs(fromLead[index] - if (lastFox == null) 12 else 19) +
                        abs(mentorDistance - 14) + 2 * (5 - nearestRabbit).coerceAtLeast(0) + browseDistance
                }.thenBy(::cellId),
            )
            animals.add(Creature(animals.size + 1L, Species.Fox, selected, food = 740,
                wildlife = WildlifeLife(0, selected, huntUntil = 35 + it * 175L)))
        }
        return state.copy(creatures = animals, nextEntityId = animals.size + 1L, wildlife = WildlifeHistory())
    }

    fun advance(
        state: EcologyState,
        tick: Long,
        recharge: Int,
        patches: MutableList<ForagePatch>,
        waypoints: Map<Long, GridCell>,
        random: SplitMix64,
    ): CreatureTurn {
        updateCover(patches)
        val events = mutableListOf<WildlifeEvent>()
        val animals = ArrayList<Creature>(state.creatures.size)
        val rabbits = state.creatures.filter { it.species == Species.Rabbit }
        val foxes = state.creatures.filter { it.species == Species.Fox }
        val deer = state.creatures.filter { it.species == Species.Deer }
        val herd = deer.firstOrNull { it.matureAt <= tick } ?: deer.firstOrNull()
        val hunt = foxes.associate { it.id to hunt(it, rabbits, tick) }
        for (animal in state.creatures) {
            val life = checkNotNull(animal.wildlife)
            if (tick - life.bornAt >= animal.lifespan()) {
                events.add(animal.event(tick, WildlifeMilestone.OldAge))
                continue
            }
            val next = act(animal, tick, recharge, patches, state.springs, waypoints[animal.id],
                herd, foxes, hunt, random)
            if (next.depletedTicks >= SurvivalTicks) {
                events.add(next.event(tick, WildlifeMilestone.Scarcity))
                continue
            }
            val movingRange = next.action == CreatureAction.Eating && next.species != Species.Fox &&
                spacing(life.home, next.cell) >= 12
            if (movingRange) {
                animals.add(next.copy(wildlife = checkNotNull(next.wildlife).copy(home = next.cell)))
                events.add(next.event(tick, WildlifeMilestone.NewRange))
            } else animals.add(next)
        }
        resolveHunts(animals, events, tick)
        var nextId = state.nextEntityId
        if (tick % 120L == 0L) {
            for (species in Species.entries) {
                if (nextId < Long.MAX_VALUE && breed(animals, patches, species, tick, nextId)) {
                    events.add(animals.last().event(tick, WildlifeMilestone.Birth))
                    nextId++
                }
            }
        }
        return CreatureTurn(animals, nextId, checkNotNull(state.wildlife).record(events))
    }

    private fun updateCover(patches: List<ForagePatch>) {
        val rooted = patches.filter { it.planted && it.biomass * 2 >= it.capacity }.map { it.cell }
        if (rooted == plantedCover) return
        plantedCover = rooted
        cover = naturalCover.toHashSet().apply {
            for (cell in rooted) {
                add(cell)
                addAll(island.navigation.adjacent(cell))
            }
        }
        shelters = cover.sortedBy(::cellId)
    }

    private data class Hunt(val life: WildlifeLife, val target: Creature?, val action: CreatureAction)

    private fun hunt(fox: Creature, rabbits: List<Creature>, tick: Long): Hunt {
        val life = checkNotNull(fox.wildlife)
        val prior = rabbits.firstOrNull { it.id == life.prey }
        val exhausted = fox.water <= 140 || fox.energy <= 100 || tick - life.bornAt >= fox.lifespan()
        val pursuing = life.prey != null
        if (pursuing && (prior == null || exhausted || tick >= life.huntUntil)) {
            return Hunt(life.copy(prey = null, huntUntil = later(tick, HuntRecovery)), null, CreatureAction.Idle)
        }
        if (prior != null) {
            val distance = spacing(fox.cell, prior.cell)
            val action = if (fox.action == CreatureAction.Chasing || distance <= 3 || life.huntUntil - tick <= 18)
                CreatureAction.Chasing else CreatureAction.Stalking
            return Hunt(life, prior, action)
        }
        if (tick < life.huntUntil || exhausted || fox.food > 780 || fox.matureAt > tick || fox.goal?.need == Need.Meet) {
            return Hunt(life, null, CreatureAction.Idle)
        }
        val seen = rabbits.filter { it.cell !in cover && spacing(it.cell, fox.cell) <= 11 }
        val distances = if (seen.isNotEmpty()) island.terrainDistance(fox.cell) else return Hunt(life, null, CreatureAction.Idle)
        val target = seen.sortedWith(compareBy<Creature> { distances[cellId(it.cell)] }.thenBy { it.id })
            .firstOrNull { distances[cellId(it.cell)] in 0..12 }
            ?: return Hunt(life, null, CreatureAction.Idle)
        return Hunt(life.copy(prey = target.id, huntUntil = later(tick, 34)), target, CreatureAction.Stalking)
    }

    private fun act(
        animal: Creature,
        tick: Long,
        recharge: Int,
        patches: MutableList<ForagePatch>,
        springs: List<GridCell>,
        waypoint: GridCell?,
        herd: Creature?,
        foxes: List<Creature>,
        hunts: Map<Long, Hunt>,
        random: SplitMix64,
    ): Creature {
        val pursuit = hunts[animal.id]
        val sprinting = animal.action == CreatureAction.Fleeing || animal.action == CreatureAction.Chasing
        val tired = animal.copy(
            food = (animal.food - if (sprinting) 2 else 1).coerceAtLeast(0),
            water = (animal.water - 1).coerceAtLeast(0),
            energy = (animal.energy - 1).coerceAtLeast(0),
            waypoint = (waypoint ?: animal.waypoint)?.takeUnless { it == animal.cell },
            wildlife = pursuit?.life ?: animal.wildlife,
        )
        val fox = if (tired.species == Species.Rabbit) foxes.firstOrNull {
            val hunt = hunts.getValue(it.id)
            hunt.target?.id == tired.id &&
                (hunt.action == CreatureAction.Chasing || spacing(tired.cell, it.cell) <= 5) ||
                hunt.target != null && spacing(tired.cell, it.cell) <= 3 &&
                island.terrainDistance(it.cell)[cellId(tired.cell)] in 0..3
        } else null
        val goal = when {
            pursuit?.target != null -> Goal(Need.Hunt, pursuit.target.cell)
            fox != null -> flee(tired, fox)
            else -> decide(tired, tick, recharge, patches, springs, herd, foxes, random)
        }
        val result = when (goal.need) {
            Need.Hunt -> travel(tired, goal, tick, checkNotNull(pursuit).action,
                if (pursuit.action == CreatureAction.Stalking) 3 else 1)
            Need.Flee -> if (tick % 6L == 0L) tired.copy(action = CreatureAction.Fleeing)
                else travel(tired, goal, tick, CreatureAction.Fleeing, 1)
            else -> when {
                goal.cell != tired.cell -> travel(tired, goal, tick, CreatureAction.Walking,
                    if (tired.species == Species.Rabbit) 2 else 3)
                goal.need == Need.Food -> eat(tired, tick, patches)
                goal.need == Need.Water -> tired.copy(water = minOf(MaxReserve, tired.water + 31), action = CreatureAction.Drinking)
                goal.need == Need.Rest -> tired.copy(energy = minOf(MaxReserve, tired.energy + 29), action = CreatureAction.Resting)
                else -> tired.copy(action = CreatureAction.Idle)
            }
        }
        return result.copy(
            goal = goal,
            depletedTicks = if (result.food == 0 || result.water == 0) result.depletedTicks + 1 else 0,
            waypoint = result.waypoint?.takeUnless { it == result.cell },
        )
    }

    private fun travel(animal: Creature, goal: Goal, tick: Long, action: CreatureAction, pace: Int): Creature {
        val watching = goal.need == Need.Hunt && goal.cell in cover && spacing(animal.cell, goal.cell) <= 1
        if (watching) return animal.copy(action = CreatureAction.Stalking)
        if (tick % pace != 0L || animal.cell == goal.cell) return animal.copy(action = action)
        val next = island.navigation.step(animal.cell, island.terrainDistance(goal.cell))
        val heading = when {
            next.x > animal.cell.x -> CreatureHeading.PositiveX
            next.x < animal.cell.x -> CreatureHeading.NegativeX
            next.z > animal.cell.z -> CreatureHeading.PositiveZ
            else -> CreatureHeading.NegativeZ
        }
        return animal.copy(cell = next, heading = heading, action = action,
            energy = (animal.energy - if (action == CreatureAction.Chasing) 4 else 1).coerceAtLeast(0))
    }

    private fun flee(rabbit: Creature, fox: Creature): Goal {
        if (rabbit.cell in cover) return Goal(Need.Flee, rabbit.cell)
        val fromFox = island.terrainDistance(fox.cell)
        val fromRabbit = island.terrainDistance(rabbit.cell)
        val previous = rabbit.goal
        if (previous?.need == Need.Flee && previous.cell in cover &&
            fromFox[cellId(previous.cell)] >= fromRabbit[cellId(previous.cell)]
        ) return previous
        val refuge = shelters.asSequence().filter { fromRabbit[cellId(it)] in 1..10 }
            .minWithOrNull(compareBy<GridCell> {
                val id = cellId(it)
                fromRabbit[id] * 3 - fromFox[id].coerceAtMost(12)
            }.thenBy(::cellId))
        val escape = refuge ?: island.navigation.adjacent(rabbit.cell)
            .maxByOrNull { fromFox[cellId(it)] } ?: rabbit.cell
        return Goal(Need.Flee, escape)
    }

    private fun decide(
        animal: Creature,
        tick: Long,
        recharge: Int,
        patches: List<ForagePatch>,
        springs: List<GridCell>,
        herd: Creature?,
        foxes: List<Creature>,
        random: SplitMix64,
    ): Goal {
        val prior = animal.goal
        fun wants(need: Need, reserve: Int, start: Int, stop: Int) =
            reserve <= start || (prior?.need == need && reserve < stop)
        fun water() = waterGoal(animal, springs, recharge)
        fun food() = foodGoal(animal, patches, recharge, herd)
        fun rest() = restGoal(animal)
        // Finish the urgent trip through a recovery reserve. A first bite or sip must not
        // send an animal straight back to the other resource across the island.
        if (prior?.need == Need.Water && animal.water < 490 && animal.food > 85) water()?.let { return it }
        if (prior?.need == Need.Food && animal.food < 490 && animal.water > 90) food()?.let { return it }
        val foodTicks = animal.food
        if (animal.water <= 185 && animal.water <= foodTicks) water()?.let { return it }
        if (foodTicks <= 185) food()?.let { return it }
        if (animal.water <= 185) water()?.let { return it }
        if (animal.energy < 130) rest()?.let { return it }
        animal.waypoint?.let { return Goal(Need.Waypoint, it) }
        if (wants(Need.Water, animal.water, 395, 900)) water()?.let { return it }
        if (wants(Need.Food, animal.food, 610, 940)) food()?.let { return it }
        if (wants(Need.Rest, animal.energy, if (tick % DayLength >= 660) 650 else 350, 920)) rest()?.let { return it }
        if (animal.species == Species.Fox && foxes.size in 2 until Species.Fox.populationLimit() &&
            seasonAt(tick) == Season.Wet && readyToBreed(animal, tick)
        ) {
            val den = checkNotNull(foxes.first().wildlife).home
            return Goal(Need.Meet, den)
        }
        if (prior?.need == Need.Roam && prior.cell != animal.cell) return prior
        if (prior?.need == Need.Roam && (tick + animal.id % 90) % 90L != 0L) return prior
        val home = checkNotNull(animal.wildlife).home
        val origin = when (animal.species) {
            Species.Deer -> herd?.cell ?: home
            else -> home
        }
        val habitat = when (animal.species) {
            Species.Fox -> edges
            else -> island.roamCells(animal.species)
        }
        val radius = if (animal.species == Species.Rabbit) 5 else 8
        val options = habitat.filter {
            spacing(it, origin) <= radius && spacing(it, animal.cell) in 1..10
        }
        return Goal(Need.Roam, if (options.isEmpty()) origin else options[random.nextBounded(options.size)])
    }

    private fun waterGoal(animal: Creature, springs: List<GridCell>, recharge: Int): Goal? {
        val prior = animal.goal
        if (prior?.need == Need.Water && prior.cell in springs) return prior
        return springs.minWithOrNull(compareBy<GridCell> {
            val distance = island.terrainDistance(animal.cell)[cellId(it)]
            distance * 5 - terrain.soilMoisture(it, recharge) / if (recharge < 64) 5 else 12
        }.thenBy(::cellId))?.let { Goal(Need.Water, it) }
    }

    private fun restGoal(animal: Creature): Goal? {
        val prior = animal.goal
        if (prior?.need == Need.Rest && (prior.cell in cover || shelters.isEmpty())) return prior
        if (animal.cell in cover || shelters.isEmpty()) return Goal(Need.Rest, animal.cell)
        val distances = island.terrainDistance(animal.cell)
        return island.nearest(distances, shelters)?.let { Goal(Need.Rest, it) }
    }

    private fun foodGoal(animal: Creature, patches: List<ForagePatch>, recharge: Int, herd: Creature?): Goal? {
        val prior = animal.goal
        if (prior?.need == Need.Food && patches.any { it.cell == prior.cell && edible(it, animal.species) }) return prior
        val distance = island.terrainDistance(animal.cell)
        val home = checkNotNull(animal.wildlife).home
        val patch = patches.asSequence().filter { edible(it, animal.species) }
            .minWithOrNull(compareBy<ForagePatch> {
                val steps = distance[it.id]
                val habitat = when (animal.species) {
                    Species.Rabbit -> {
                        val sheltered = it.cell in cover || island.navigation.adjacent(it.cell).any(cover::contains)
                        (if (sheltered) 16 else 0) + if (it.kind == ForageKind.Meadow) 23 else 0
                    }
                    Species.Deer -> (if (it.kind == ForageKind.Browse) 24 else 0) -
                        spacing(it.cell, herd?.cell ?: home).coerceAtMost(16) * 2
                    Species.Fox -> -spacing(it.cell, home).coerceAtMost(20) * 2
                }
                steps * 6 - habitat - it.biomass / 9 - terrain.soilMoisture(it.cell, recharge) / 5
            }.thenBy { it.id })
        return patch?.let { Goal(Need.Food, it.cell) }
    }

    private fun edible(patch: ForagePatch, species: Species) = patch.biomass >= when (species) {
        Species.Fox -> 14
        else -> 5
    } && when (species) {
        Species.Rabbit -> patch.kind == ForageKind.Meadow || patch.biomass >= 22
        Species.Fox -> patch.kind == ForageKind.Browse
        Species.Deer -> true
    }

    private fun eat(animal: Creature, tick: Long, patches: MutableList<ForagePatch>): Creature {
        val index = patches.indexOfFirst { it.cell == animal.cell && edible(it, animal.species) }
        if (index < 0) return animal.copy(action = CreatureAction.Idle)
        if (tick % (if (animal.species == Species.Fox) 5L else 4L) != 0L) return animal.copy(action = CreatureAction.Eating)
        val patch = patches[index]
        val eaten = minOf(patch.biomass, if (animal.species == Species.Fox) 5 else 10)
        val nutrition = when (animal.species) {
            Species.Rabbit -> if (patch.kind == ForageKind.Meadow) 4 else 3
            Species.Deer -> if (patch.kind == ForageKind.Browse) 5 else 3
            Species.Fox -> 4
        }
        patches[index] = patch.copy(biomass = patch.biomass - eaten)
        return animal.copy(food = minOf(MaxReserve, animal.food + eaten * nutrition), action = CreatureAction.Eating)
    }

    private fun resolveHunts(animals: MutableList<Creature>, events: MutableList<WildlifeEvent>, tick: Long) {
        val caught = mutableSetOf<Long>()
        for (index in animals.indices) {
            val fox = animals[index]
            val life = checkNotNull(fox.wildlife)
            if (fox.action != CreatureAction.Chasing || life.prey == null) continue
            val prey = animals.firstOrNull { it.id == life.prey } ?: continue
            if (prey.cell != fox.cell || prey.cell in cover || !caught.add(prey.id)) continue
            events.removeAll { it.id == prey.id }
            events.add(prey.event(tick, WildlifeMilestone.Caught))
            animals[index] = fox.copy(food = (fox.food + 410).coerceAtMost(MaxReserve), action = CreatureAction.Eating,
                goal = null, wildlife = life.copy(prey = null, huntUntil = later(tick, HuntRecovery)))
        }
        if (caught.isNotEmpty()) animals.removeAll { it.id in caught }
        val livingRabbits = animals.filter { it.species == Species.Rabbit }.mapTo(mutableSetOf()) { it.id }
        for ((index, fox) in animals.withIndex()) {
            val life = checkNotNull(fox.wildlife)
            if (life.prey != null && life.prey !in livingRabbits) {
                animals[index] = fox.copy(action = CreatureAction.Idle, goal = null,
                    wildlife = life.copy(prey = null, huntUntil = later(tick, HuntRecovery)))
            }
        }
    }

    private fun breed(
        animals: MutableList<Creature>,
        patches: List<ForagePatch>,
        species: Species,
        tick: Long,
        id: Long,
    ): Boolean {
        if (animals.count { it.species == species } >= species.populationLimit()) return false
        val adults = animals.indices.filter {
            val animal = animals[it]
            animal.species == species && readyToBreed(animal, tick)
        }
        if (adults.size < 2) return false
        val occupied = animals.mapTo(mutableSetOf()) { it.cell }
        for (firstIndex in adults) {
            val first = animals[firstIndex]
            val distances = island.terrainDistance(first.cell)
            val shelter = island.nearest(distances, shelters) ?: continue
            if (distances[cellId(shelter)] !in 0..10) continue
            val forageRadius = if (species == Species.Fox) 17 else 11
            val forage = patches.sumOf { if (distances[it.id] in 0..forageRadius && edible(it, species)) it.biomass else 0 }
            if (forage < 100) continue
            val pairRange = when (species) {
                Species.Rabbit -> 21
                Species.Deer -> 15
                Species.Fox -> 38
            }
            val secondIndex = adults.firstOrNull { it > firstIndex && distances[cellId(animals[it].cell)] in 0..pairRange }
                ?: continue
            val nursery = (listOf(shelter) + island.navigation.adjacent(shelter)).firstOrNull { it !in occupied }
                ?: continue
            for (index in listOf(firstIndex, secondIndex)) {
                val parent = animals[index]
                animals[index] = parent.copy(food = parent.food - 80, energy = parent.energy - 65,
                    breedAfter = later(tick, if (species == Species.Rabbit) 530 else 900))
            }
            animals.add(Creature(id, species, nursery, food = 790, water = 800, energy = 790,
                matureAt = later(tick, species.maturityTicks()), wildlife = WildlifeLife(tick, nursery)))
            return true
        }
        return false
    }

    private fun readyToBreed(animal: Creature, tick: Long): Boolean {
        val (food, water, energy) = when (animal.species) {
            Species.Rabbit -> ReserveThresholds(660, 490, 490)
            Species.Deer -> ReserveThresholds(740, 610, 610)
            Species.Fox -> ReserveThresholds(620, 420, 320)
        }
        return animal.matureAt <= tick && animal.breedAfter <= tick && animal.food >= food &&
            animal.water >= water && animal.energy >= energy &&
            animal.action != CreatureAction.Fleeing && animal.action != CreatureAction.Chasing
    }

    private data class ReserveThresholds(val food: Int, val water: Int, val energy: Int)

    private fun Creature.event(tick: Long, kind: WildlifeMilestone) = WildlifeEvent(tick, id, species, kind, cell)

}

internal const val SurvivalTicks = 100
internal const val HuntRecovery = 360L

private fun spacing(a: GridCell, b: GridCell) = abs(a.x - b.x) + abs(a.z - b.z)

internal fun later(tick: Long, interval: Long) = minOf(tick, Long.MAX_VALUE - interval) + interval
