package id.branchworld.game.ecology

import id.branchworld.game.simulation.CreatureHeading
import id.branchworld.game.simulation.GridCell
import id.branchworld.game.simulation.ReforestRegion
import id.branchworld.game.simulation.SetDestination
import id.branchworld.game.simulation.SplitMix64
import id.branchworld.game.simulation.SupplementForage
import id.branchworld.game.simulation.WorldCommand
import id.branchworld.game.simulation.WorldCommands
import id.branchworld.game.terrain.TerrainMap

/** Single writer of ecology state. Distances are rebuilt from terrain. */
internal class EcologyEngine(
    terrain: TerrainMap,
    checkpoint: EcologyState? = null,
    replayInputs: List<WorldCommand> = emptyList(),
    rulesVersion: Int = checkpoint?.rulesVersion ?: EcologyRulesVersion,
    recordHistory: Boolean = true,
) {
    private val island = IslandEcology(terrain)
    private val regional by lazy { RegionalEcology(island) }
    private val economy by lazy { SettlementEconomy(island) }
    private val inputs = WorldCommands<WorldCommand>(recordHistory)

    var state: EcologyState = checkpoint ?: island.initialState().let {
        when (rulesVersion) {
            CivilizationRulesVersion -> it.copy(environment = Environment.start(terrain.key.seed), settlements = SettlementWorld())
            EnvironmentalRulesVersion -> it.copy(environment = Environment.start(terrain.key.seed))
            else -> it
        }
    }
        private set

    var interventionResults = emptyList<InterventionResult>()
        private set
    var appliedCommands = emptyList<WorldCommand>()
        private set

    init {
        require(state.rulesVersion == rulesVersion)
        require(state.settlements == null || state.environment != null)
        require(state.terrain == island.identity && state.tick >= 0)
        require(state.springs.all(island.navigation::contains))
        require(state.patches.zipWithNext().all { (first, next) -> first.id < next.id })
        require(state.patches.all {
            island.navigation.contains(it.cell) && it.id == cellId(it.cell) &&
                it.capacity > 0 && it.regrowth > 0 && it.biomass in 0..it.capacity
        })
        require(state.creatures.zipWithNext().all { (first, next) -> first.id < next.id })
        require(state.creatures.all {
            it.id in 1 until state.nextEntityId && island.navigation.contains(it.cell) &&
                it.food in 0..MaxReserve && it.water in 0..MaxReserve && it.energy in 0..MaxReserve &&
                (it.goal == null || island.navigation.contains(it.goal.cell)) &&
                (it.waypoint == null || island.navigation.contains(it.waypoint))
        })
        require(Species.entries.all { species -> state.creatures.count { it.species == species } <= MaxPerSpecies })
        require(state.environment?.let { it.nextChange > state.tick || state.tick == Long.MAX_VALUE } != false)
        for (command in replayInputs) {
            require(validTarget(command) && inputs.submit(state.tick, command))
        }
    }

    fun submit(command: WorldCommand): Boolean =
        validTarget(command) &&
            (command !is SetDestination || state.creatures.any { it.id == command.entityId && it.species == Species.Rabbit }) &&
            inputs.submit(state.tick, command)

    private fun validTarget(command: WorldCommand) = when (command) {
        is SetDestination -> command.entityId > 0 && island.navigation.contains(command.cell)
        is ReforestRegion -> state.environment != null && command.region in 0 until RegionCount
        is SupplementForage -> state.environment != null && command.region in 0 until RegionCount
    }

    fun acceptedCommands(): List<WorldCommand> = inputs.accepted()
    fun pendingCommands(): List<WorldCommand> = inputs.pending()
    fun inspectRegions(): List<RegionReadout> = if (state.environment == null) emptyList() else regional.inspect(state)

    /** Persistence checks invariants that are stronger than synthetic simulation fixtures require. */
    fun validateSavedState() {
        val baseline = island.initialState()
        val ledger = state.ledger
        require(state.springs == baseline.springs)
        require(state.nextEntityId >= baseline.nextEntityId)
        require(ledger.births == state.nextEntityId - baseline.nextEntityId)
        require(ledger.deaths == state.nextEntityId - 1 - state.creatures.size && ledger.deaths >= 0)
        require(ledger.consumed >= 0 && ledger.regrown >= 0 && ledger.introduced >= 0)
        require(state.creatures.all { it.matureAt >= 0 && it.breedAfter >= 0 && it.depletedTicks in 0 until SurvivalTicks })
        val natural = state.patches.filterNot { it.planted }
        require(natural.map { it.copy(biomass = it.capacity) } == baseline.patches)
        val planted = state.patches.filter { it.planted }
        require(planted.all { regional.validSapling(it, state.springs) })
        require(ledger.introduced >= planted.size * RegionalEcology.SaplingBiomass)
        val supply = ledger.introduced.toULong() + ledger.regrown.toULong()
        val required = ledger.consumed.toULong() + state.patches.sumOf { it.biomass }.toULong()
        val initial = baseline.patches.sumOf { it.biomass }.toULong()
        require(required >= initial && required - initial == supply)
        val environment = requireNotNull(state.environment)
        if (state.tick < 300) require(environment == Environment.start(state.terrain.key.seed))
        else require(environment.nextChange - state.tick in 0..360)
        state.settlements?.let {
            economy.validate(it, state.tick, state.patches, state.springs)
            require(it.gatheredFood.toULong() + it.gatheredWood.toULong() <= ledger.consumed.toULong())
        }
    }

    fun intervene(kind: Intervention, region: Int): Boolean {
        val availability = inspectRegions().getOrNull(region) ?: return false
        if (kind == Intervention.Reforest && !availability.canPlant ||
            kind == Intervention.Supplement && !availability.canSupplement
        ) return false
        val nextTick = state.tick + 1
        if (inputs.pending().any { command ->
            command.tick == nextTick && when (command) {
                is ReforestRegion -> kind == Intervention.Reforest && command.region == region
                is SupplementForage -> kind == Intervention.Supplement && command.region == region
                is SetDestination -> false
            }
        }) return false
        return inputs.submitNext(state.tick) { tick, sequence ->
            when (kind) {
                Intervention.Reforest -> ReforestRegion(tick, sequence, region)
                Intervention.Supplement -> SupplementForage(tick, sequence, region)
            }
        }
    }

    fun steeringTarget(left: Boolean): GridCell? = if (left) island.navigation.west else island.navigation.east

    fun steer(left: Boolean) {
        queueSteering(left)
    }

    fun queueSteering(left: Boolean): Boolean {
        val rabbit = state.creatures.firstOrNull { it.species == Species.Rabbit } ?: return false
        val target = steeringTarget(left) ?: return false
        return inputs.submitNext(state.tick) { tick, sequence -> SetDestination(tick, sequence, rabbit.id, target) }
    }

    fun advance(): EcologyState {
        val previous = state
        check(previous.tick < Long.MAX_VALUE) { "World tick limit reached" }
        val tick = previous.tick + 1
        val environment = previous.environment?.at(tick)
        val waypoints = mutableMapOf<Long, GridCell>()
        val random = SplitMix64(previous.randomState)
        val patches = previous.patches.toMutableList()
        appliedCommands = inputs.at(tick)
        var introduced = 0
        val results = buildList {
            for (command in appliedCommands) {
                when (command) {
                    is SetDestination -> waypoints[command.entityId] = command.cell
                    is ReforestRegion -> {
                        val count = regional.plant(
                            command.region, patches, previous.springs, previous.settlements?.reservedCells().orEmpty(),
                        )
                        introduced += count * RegionalEcology.SaplingBiomass
                        add(InterventionResult(Intervention.Reforest, command.region, tick, count))
                    }
                    is SupplementForage -> {
                        val amount = regional.supplement(command.region, patches)
                        introduced += amount
                        add(InterventionResult(Intervention.Supplement, command.region, tick, amount))
                    }
                }
            }
        }
        interventionResults = results
        val before = patches.sumOf { it.biomass }
        if (tick % 10L == 0L) {
            val multiplier = if (environment?.weather == Weather.Rain) 2 else 1
            for ((index, patch) in patches.withIndex()) {
                if (patch.biomass < patch.capacity) {
                    patches[index] = patch.copy(biomass = minOf(patch.capacity, patch.biomass + patch.regrowth * multiplier))
                }
            }
        }
        val afterGrowth = patches.sumOf { it.biomass }
        val animals = ArrayList<Creature>(previous.creatures.size)
        for (creature in previous.creatures) {
            val waypoint = waypoints[creature.id]
            val commanded = if (waypoint != null && creature.species == Species.Rabbit) {
                creature.copy(waypoint = waypoint)
            } else {
                creature
            }
            val animal = act(commanded, tick, patches, previous.springs, random)
            if (animal.depletedTicks < SurvivalTicks) animals.add(animal)
        }
        val deaths = previous.creatures.size - animals.size
        var nextId = previous.nextEntityId
        if (tick % 120L == 0L) {
            for (species in Species.entries) {
                if (nextId < Long.MAX_VALUE && breed(animals, species, tick, nextId)) nextId++
            }
        }
        val ledger = previous.ledger
        val settlements = previous.settlements?.let {
            if (tick % SettlementTurn == 0L) economy.advance(it, tick, requireNotNull(environment).weather, patches, previous.springs)
            else it
        }
        return previous.copy(
            tick = tick,
            randomState = random.state,
            patches = patches,
            creatures = animals,
            nextEntityId = nextId,
            environment = environment,
            settlements = settlements,
            ledger = EcologyLedger(
                consumed = ledger.consumed + afterGrowth - patches.sumOf { it.biomass },
                regrown = ledger.regrown + afterGrowth - before,
                births = ledger.births + (nextId - previous.nextEntityId),
                deaths = ledger.deaths + deaths,
                introduced = ledger.introduced + introduced,
            ),
        ).also { state = it }
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

    private fun later(tick: Long, interval: Long) = minOf(tick, Long.MAX_VALUE - interval) + interval

    private fun heading(from: GridCell, to: GridCell) = when {
        to.x > from.x -> CreatureHeading.PositiveX
        to.x < from.x -> CreatureHeading.NegativeX
        to.z > from.z -> CreatureHeading.PositiveZ
        else -> CreatureHeading.NegativeZ
    }

    private companion object {
        const val Bite = 10
        const val SurvivalTicks = 100
    }
}
