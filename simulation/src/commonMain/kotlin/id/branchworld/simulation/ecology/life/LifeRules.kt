package id.branchworld.simulation.ecology.life

import id.branchworld.simulation.core.CreatureHeading
import id.branchworld.simulation.core.GridCell
import id.branchworld.simulation.ecology.Creature
import id.branchworld.simulation.ecology.CreatureAction
import id.branchworld.simulation.ecology.EcologyState
import id.branchworld.simulation.ecology.ForagePatch
import id.branchworld.simulation.ecology.Goal
import id.branchworld.simulation.ecology.IslandEcology
import id.branchworld.simulation.ecology.MaxReserve
import id.branchworld.simulation.ecology.Need
import id.branchworld.simulation.ecology.Species
import id.branchworld.simulation.ecology.cellId
import id.branchworld.simulation.ecology.chronicle.KeeperAct
import id.branchworld.simulation.ecology.chronicle.WorldChronicle
import id.branchworld.simulation.ecology.chronicle.WorldEventKind
import id.branchworld.simulation.ecology.fire.FireView
import id.branchworld.simulation.ecology.settlement.record
import id.branchworld.simulation.ecology.wildlife.SurvivalTicks
import id.branchworld.simulation.ecology.wildlife.WildlifeEvent
import id.branchworld.simulation.ecology.wildlife.WildlifeHistory
import id.branchworld.simulation.ecology.wildlife.WildlifeLife
import id.branchworld.simulation.ecology.wildlife.WildlifeMilestone
import id.branchworld.simulation.ecology.wildlife.later
import id.branchworld.simulation.ecology.wildlife.lifespan
import id.branchworld.simulation.ecology.wildlife.maturityTicks

/** Why the player cannot set something down at a cell. */
enum class PlacementCheck { Ok, Water, Unreachable, Unsuitable, Occupied, Burning, SpeciesFull, IslandFull, PlantsFull, NotLiving }

/** The rules-v12 creature step's result. The engine builds the ledger from the explicit counts. */
internal class LifeTurn(
    val animals: List<Creature>,
    val nextId: Long,
    val history: WildlifeHistory,
    val life: LifeState,
    val chronicle: WorldChronicle,
    val born: Long,
    val died: Long,
)

/** A player's placement waiting to be applied on this tick. */
internal data class Arrival(val species: Species, val cell: GridCell)

/**
 * Rules v12 creature behaviour. Every animal decides from what it can perceive on the previous tick, so the order in
 * which creatures are processed never changes a decision; random choices are counter-based [lifeRoll]s.
 */
internal class LifeRules(island: IslandEcology) {
    private val ground = LifeGround(island)
    val placement = LifePlacement(ground)
    private val prey = PreyBehaviour(ground)
    private val predators = PredatorBehaviour(ground)
    private val everyday = EverydayNeeds(ground)

    // ----- placement -----

    // ----- the step -----

    fun advance(
        previous: EcologyState,
        tick: Long,
        recharge: Int,
        patches: MutableList<ForagePatch>,
        fire: FireView?,
        removals: List<Long>,
        arrivals: List<Arrival>,
        chronicleIn: WorldChronicle,
    ): LifeTurn {
        var life = checkNotNull(previous.life)
        var chronicle = chronicleIn
        val events = mutableListOf<WildlifeEvent>()
        ground.begin(previous.terrain.key.seed, patches, fire?.bare ?: emptySet(), life.warrens)

        val removed = removals.toSet()
        val start = previous.creatures.filter { animal ->
            if (animal.id !in removed) true else {
                events.add(animal.event(tick, WildlifeMilestone.Removed))
                false
            }
        }
        val byId = start.associateBy { it.id }

        // Packs: drop the dead, dissolve lone survivors, then let lone wolves join or form packs.
        val packOf = HashMap<Long, Int>()
        for (animal in start) if (animal.species == Species.Wolf) packOf[animal.id] = checkNotNull(animal.life).packId
        var packs = life.packs.filter { pack -> packOf.values.count { it == pack.id } >= 2 }
        val kept = packs.mapTo(HashSet()) { it.id }
        for ((id, pack) in packOf) if (pack != 0 && pack !in kept) packOf[id] = 0
        for (wolf in start.filter { it.species == Species.Wolf && packOf[it.id] == 0 && it.matureAt <= tick }) {
            if (packOf[wolf.id] != 0) continue
            val joined = packs.firstOrNull { pack ->
                val members = packOf.filterValues { it == pack.id }.keys
                members.size < PackLimit && members.any { chebyshev(byId.getValue(it).cell, wolf.cell) <= PackReach }
            }
            if (joined != null) {
                packOf[wolf.id] = joined.id
                continue
            }
            if (packs.size >= MaxPacks) continue
            val partner = start.firstOrNull {
                it.id != wolf.id && it.species == Species.Wolf && packOf[it.id] == 0 && it.matureAt <= tick &&
                    chebyshev(it.cell, wolf.cell) <= PackReach
            } ?: continue
            val pack = Pack(life.nextPackId, tick, ground.denFor(wolf.cell, partner.cell, packs))
            packs = (packs + pack).sortedBy { it.id }
            life = life.copy(nextPackId = life.nextPackId + 1)
            packOf[wolf.id] = pack.id
            packOf[partner.id] = pack.id
            val placedWolves = chronicle.events.lastOrNull { it.kind == WorldEventKind.KeeperAct &&
                it.detail == KeeperAct.Place.code && it.subject == Species.Wolf.canonicalId }?.ref
            chronicle = chronicle.record(tick, WorldEventKind.PackFormed, pack.den, pack.id, 2, listOfNotNull(placedWolves)).first
        }
        fun membersOf(pack: Int) = start.filter { it.species == Species.Wolf && packOf[it.id] == pack }

        // Pack hunts are decided once per pack from what any member can see.
        packs = packs.map { pack ->
            val members = membersOf(pack.id)
            val leader = members.firstOrNull { it.matureAt <= tick } ?: members.first()
            var current = pack
            val target = byId[pack.target]
            if (pack.target != 0L) {
                val tiring = checkNotNull(leader.life).stamina < GiveUpStamina
                val safe = target != null && ground.refuge(target)
                if (target == null || tiring || safe || tick - pack.huntFrom > PackHuntTicks) {
                    if (target != null && tick - pack.huntFrom >= EscapeTicks) {
                        chronicle = predators.escaped(chronicle, tick, target, leader)
                    }
                    current = current.copy(target = 0, huntFrom = 0)
                }
            }
            val settled = members.all { tick >= checkNotNull(it.wildlife).huntUntil || it.wildlife.prey != null }
            if (current.target == 0L && settled && (tick + pack.id) % ScanEvery == 0L) {
                val hunger = members.sumOf { it.food } / members.size
                if (hunger < packHunger(tick)) {
                    val choice = start.asSequence()
                        .filter { it.species in Species.Wolf.prey() && (it.species != Species.Deer || members.size >= 2) && !ground.refuge(it) }
                        .filter { prey -> members.any { ground.sees(it.cell, prey.cell, WolfSight) } }
                        .minWithOrNull(compareBy<Creature> { prey ->
                            octile(leader.cell, prey.cell) - (if (prey.species == Species.Deer && members.size >= 3) 40 else 0) -
                                (if (prey.matureAt > tick) 20 else 0)
                        }.thenBy { it.id })
                    if (choice != null) current = current.copy(target = choice.id, huntFrom = tick)
                }
            }
            current
        }
        val packById = packs.associateBy { it.id }

        // Rabbits that saw danger themselves thump; neighbours who hear it stand ready to run.
        val thumpers = start.filter { it.species == Species.Rabbit && it.life?.let { l -> l.threat != 0L && !l.heard && l.alarmUntil > tick } == true }

        val context = LifeStep(tick, recharge, patches, previous.springs, fire, byId, packOf, packById, life.warrens, thumpers)
        val animals = ArrayList<Creature>(start.size + arrivals.size + 8)
        val diggings = mutableListOf<GridCell>()
        var died = 0L
        val deaths = mutableListOf<Creature>()
        for (animal in start) {
            val lived = checkNotNull(animal.wildlife)
            if (tick - lived.bornAt >= animal.lifespan()) {
                events.add(animal.event(tick, WildlifeMilestone.OldAge))
                died++
                deaths.add(animal)
                continue
            }
            val next = act(animal, context)
            if (next.depletedTicks >= SurvivalTicks) {
                events.add(next.event(tick, WildlifeMilestone.Scarcity))
                died++
                deaths.add(next)
                if (next.food == 0 && (next.species.predator || chronicle.events.none {
                        it.kind == WorldEventKind.Starved && it.detail == next.species.canonicalId && tick - it.tick < CensusInterval
                    })
                ) {
                    val hunger = chronicle.events.lastOrNull { event ->
                        (event.kind == WorldEventKind.Crash || event.kind == WorldEventKind.Vanished) &&
                            Species.entries.firstOrNull { it.canonicalId == event.detail } in next.species.prey()
                    }?.ref
                    chronicle = chronicle.record(tick, WorldEventKind.Starved, next.cell, next.id.toSubject(),
                        next.species.canonicalId, listOfNotNull(hunger)).first
                }
                continue
            }
            if (next.action == CreatureAction.Digging && checkNotNull(next.life).busyUntil == tick + 1) diggings.add(next.cell)
            animals.add(next)
        }

        chronicle = predators.drainEscapes(chronicle, tick)
        // Catches: a chasing hunter on or beside unsheltered prey.
        val caught = HashSet<Long>()
        val index = HashMap<Long, Int>()
        animals.forEachIndexed { i, animal -> index[animal.id] = i }
        for (i in animals.indices) {
            val hunter = animals[i]
            if (!hunter.species.predator || hunter.action != CreatureAction.Chasing) continue
            val hunterLife = checkNotNull(hunter.life)
            val hunterPack = packOf[hunter.id] ?: hunterLife.packId
            val packTarget = packById[hunterPack]?.target?.takeIf { it != 0L }
            val targetId = packTarget ?: hunter.wildlife?.prey ?: continue
            if (targetId in caught) continue
            val preyIndex = index[targetId] ?: continue
            val prey = animals[preyIndex]
            if (ground.refuge(prey)) continue
            val reach = chebyshev(hunter.cell, prey.cell)
            if (reach > 1) continue
            val sheltered = prey.species == Species.Rabbit && prey.cell in ground.cover
            val success = when {
                sheltered -> lifeRoll(ground.seed, tick, hunter.id, RollCatch, 100) < (if (reach == 0) 50 else if (hunter.species == Species.Wolf) 25 else 15)
                prey.species == Species.Deer -> animals.count {
                    it.species == Species.Wolf && (packOf[it.id] ?: checkNotNull(it.life).packId) == hunterPack && hunterPack != 0 &&
                        chebyshev(it.cell, prey.cell) <= 1
                } >= 2 && lifeRoll(ground.seed, tick, hunter.id, RollCatch, 100) < 60
                reach == 0 -> true
                else -> lifeRoll(ground.seed, tick, hunter.id, RollCatch, 100) < if (hunter.species == Species.Wolf) 55 else 40
            }
            if (!success) continue
            caught.add(prey.id)
            events.removeAll { it.id == prey.id }
            events.add(prey.event(tick, WildlifeMilestone.Caught))
            val packFormed = chronicle.events.lastOrNull { it.kind == WorldEventKind.PackFormed && it.subject == hunterPack }?.ref
            if (hunterLife.kills == 0 || prey.species == Species.Deer) {
                chronicle = chronicle.record(tick, WorldEventKind.Hunted, prey.cell, hunter.id.toSubject(), prey.species.canonicalId,
                    listOfNotNull(packFormed)).first
            }
            if (prey.species == Species.Deer || hunterPack != 0) {
                // A pack shares every kill among the members close enough to reach it.
                val meat = if (prey.species == Species.Deer) DeerMeat else PackRabbitMeat
                val feeders = animals.indices.filter {
                    animals[it].species == Species.Wolf && (packOf[animals[it].id] ?: checkNotNull(animals[it].life).packId) == hunterPack &&
                        chebyshev(animals[it].cell, prey.cell) <= 6
                }
                val share = meat / feeders.size.coerceAtLeast(1)
                for (f in feeders) animals[f] = feed(animals[f], share, tick, f == i)
            } else {
                animals[i] = feed(hunter, if (hunter.species == Species.Wolf) RabbitMeatWolf else RabbitMeatFox, tick, true)
            }
            if (hunterPack != 0) packs = packs.map {
                if (it.id == hunterPack) it.copy(target = 0, huntFrom = 0, lastKillAt = tick) else it
            }
        }
        if (caught.isNotEmpty()) {
            for (id in caught) deaths.add(animals[checkNotNull(index[id])])
            animals.removeAll { it.id in caught }
            died += caught.size
        }
        // Hunters whose quarry is gone stand down.
        val alive = animals.mapTo(HashSet()) { it.id }
        for (i in animals.indices) {
            val animal = animals[i]
            val prey = animal.wildlife?.prey ?: continue
            if (prey !in alive) animals[i] = animal.copy(goal = null,
                wildlife = checkNotNull(animal.wildlife).copy(prey = null, huntUntil = later(tick, SoloRecovery)))
        }
        packs = packs.map { if (it.target != 0L && it.target !in alive) it.copy(target = 0, huntFrom = 0) else it }

        // Warrens finished this tick.
        var warrens = life.warrens
        for (cell in diggings) {
            if (warrens.size < MaxWarrens && warrens.none { chebyshev(it.cell, cell) < WarrenSpacing }) {
                warrens = (warrens + Warren(cell, tick)).sortedBy { cellId(it.cell) }
            }
        }

        // Births.
        var nextId = previous.nextEntityId
        var born = 0L
        val births = mutableListOf<Creature>()
        for (i in animals.indices) {
            val mother = animals[i]
            val motherLife = checkNotNull(mother.life)
            if (motherLife.pregnantUntil == 0L || motherLife.pregnantUntil > tick) continue
            val room = minOf(mother.species.lifeLimit() - (animals.count { it.species == mother.species } +
                births.count { it.species == mother.species }), IslandCreatureLimit - animals.size - births.size)
            val size = minOf(room, litterSize(mother, tick))
            val nest = if (mother.species == Species.Rabbit) {
                warrens.filter { chebyshev(it.cell, mother.cell) <= 4 }.minByOrNull { octile(it.cell, mother.cell) }?.cell ?: mother.cell
            } else mother.cell
            repeat(size.coerceAtLeast(0)) {
                if (nextId == Long.MAX_VALUE) return@repeat
                val kit = Creature(nextId, mother.species, nest, food = 760, water = 800, energy = 800,
                    matureAt = later(tick, mother.species.maturityTicks()), breedAfter = later(tick, mother.species.maturityTicks()),
                    heading = mother.heading, wildlife = WildlifeLife(tick, nest),
                    life = CreatureLife(sexOf(nextId), Origin.Born, packId = motherLife.packId))
                births.add(kit)
                events.add(kit.event(tick, WildlifeMilestone.Birth))
                nextId++
                born++
            }
            if (size > 0 && motherLife.litters == 0 && motherLife.origin != Origin.Born) {
                val placedAct = chronicle.events.lastOrNull { it.kind == WorldEventKind.KeeperAct &&
                    it.detail == KeeperAct.Place.code && it.subject == mother.species.canonicalId }?.ref
                chronicle = chronicle.record(tick, WorldEventKind.FirstLitter, nest, mother.id.toSubject(),
                    mother.species.canonicalId, listOfNotNull(placedAct)).first
            }
            animals[i] = mother.copy(breedAfter = later(tick, mother.species.afterLitter()),
                life = motherLife.copy(pregnantUntil = 0, litters = motherLife.litters + if (size > 0) 1 else 0))
        }
        animals.addAll(births)

        // The player's arrivals join last, so they first act on the next tick.
        var placed = 0L
        for (arrival in arrivals) {
            if (nextId == Long.MAX_VALUE) break
            val animal = placement.creature(nextId, arrival.species, arrival.cell, tick, Origin.Placed)
            animals.add(animal)
            events.add(animal.event(tick, WildlifeMilestone.Placed))
            nextId++
            placed++
        }
        animals.sortBy { it.id }

        // A species whose last member died this tick has vanished from the ground.island.
        for (species in Species.entries) {
            if (deaths.none { it.species == species } || animals.any { it.species == species }) continue
            val last = deaths.last { it.species == species }
            chronicle = chronicle.record(tick, WorldEventKind.Vanished, last.cell, 0, species.canonicalId).first
        }
        var census = life.census
        if (tick % CensusInterval == 0L) {
            census = Species.entries.mapNotNull { species ->
                val count = animals.count { it.species == species }
                val prior = census.firstOrNull { it.species == species } ?: Census(species, count, count)
                if (count == 0 && prior.high == 0) return@mapNotNull null
                val where = animals.firstOrNull { it.species == species }?.cell ?: ground.navigation.spawn
                when {
                    count >= 2 * prior.low && count >= BoomFloor && count > prior.low -> {
                        chronicle = chronicle.record(tick, WorldEventKind.Boom, where, count, species.canonicalId).first
                        Census(species, count, count)
                    }
                    count * 2 <= prior.high && prior.high >= CrashFloor -> {
                        val cause = chronicle.events.lastOrNull { it.kind == WorldEventKind.Hunted && it.detail == species.canonicalId }?.ref
                        chronicle = chronicle.record(tick, WorldEventKind.Crash, where, count, species.canonicalId, listOfNotNull(cause)).first
                        Census(species, count, count)
                    }
                    else -> Census(species, minOf(prior.low, count), maxOf(prior.high, count))
                }
            }
        }

        // Pack ids of wolves follow the pack table; the dead leave their packs.
        val finalPacks = packs.filter { pack -> animals.count { it.species == Species.Wolf && packOf[it.id] == pack.id } >= 1 }
        val withPacks = animals.map { animal ->
            if (animal.species != Species.Wolf) animal else {
                val pack = packOf[animal.id] ?: checkNotNull(animal.life).packId
                val keep = if (finalPacks.any { it.id == pack }) pack else 0
                if (keep == checkNotNull(animal.life).packId) animal else animal.copy(life = animal.life.copy(packId = keep))
            }
        }
        life = life.copy(
            placed = life.placed + placed,
            removed = life.removed + (start.size.let { previous.creatures.size - it }),
            packs = finalPacks,
            warrens = warrens,
            census = census,
        )
        return LifeTurn(withPacks, nextId, checkNotNull(previous.wildlife).record(events), life, chronicle, born, died)
    }

    private fun feed(animal: Creature, meat: Int, tick: Long, catcher: Boolean): Creature {
        val life = checkNotNull(animal.life)
        return animal.copy(food = minOf(MaxReserve, animal.food + meat), action = CreatureAction.Eating, goal = null,
            depletedTicks = 0,
            wildlife = checkNotNull(animal.wildlife).copy(prey = null, huntUntil = later(tick, SoloRecovery)),
            life = life.copy(kills = life.kills + if (catcher) 1 else 0, chaseFrom = 0))
    }

    private fun act(animal: Creature, step: LifeStep): Creature {
        val tick = step.tick
        val life = checkNotNull(animal.life)
        val gait = animal.species.gait
        val wasSprinting = animal.action == CreatureAction.Fleeing || animal.action == CreatureAction.Chasing
        // Wolves gorge and then go long between kills; everything else eats a little all day.
        val base = if (animal.species == Species.Wolf) (if (tick % 2L == 0L) 1 else 0) else 1
        val hungerRate = base + if (wasSprinting) 1 else 0
        var self = animal.copy(
            food = (animal.food - hungerRate).coerceAtLeast(0),
            water = (animal.water - 1).coerceAtLeast(0),
            energy = (animal.energy - 1).coerceAtLeast(0),
            waypoint = null,
            life = if (life.alarmUntil <= tick && life.threat != 0L) life.copy(threat = 0, heard = false, alarmUntil = 0) else life,
        )
        val result = decideAndMove(self, step)
        self = result
        val after = checkNotNull(self.life)
        val sprinting = self.action == CreatureAction.Fleeing || self.action == CreatureAction.Chasing
        val stamina = if (sprinting && after.stamina > 0) (after.stamina - gait.sprintCost).coerceAtLeast(0)
            else (after.stamina + gait.recovery).coerceAtMost(MaxStamina)
        return self.copy(
            depletedTicks = if (self.food == 0 || self.water == 0) self.depletedTicks + 1 else 0,
            life = after.copy(stamina = stamina),
        )
    }

    private fun decideAndMove(animal: Creature, step: LifeStep): Creature {
        val tick = step.tick
        val life = checkNotNull(animal.life)
        // Fire overrides everything: run for the farthest safe bank.
        step.fire?.let { view ->
            if (view.distance(animal.cell, FireAlarm) <= FireAlarm) {
                val bank = step.springs.filter { view.distance(it, FireAlarm) > FireAlarm }
                    .minWithOrNull(compareBy<GridCell> { octile(animal.cell, it) }.thenBy(::cellId))
                val goal = bank ?: ground.navigation.adjacent8(animal.cell)
                    .maxWithOrNull(compareBy<GridCell> { view.distance(it, FireAlarm + 2) }.thenBy { -cellId(it) }) ?: animal.cell
                return ground.travel(animal, Goal(Need.Flee, goal), CreatureAction.Fleeing, ground.sprint(animal), moving = false)
            }
        }

        if (!animal.species.predator || animal.species == Species.Fox) {
            val threatened = prey.perceiveThreat(animal, step)
            // Hunger and thirst eventually outweigh fear: a desperate animal leaves ground.cover unless it is being run down.
            val desperate = animal.water < 150 || animal.food < 150
            val pressed = threatened?.life?.threat?.let(step.byId::get)?.let {
                it.action == CreatureAction.Chasing && chebyshev(it.cell, animal.cell) <= 4
            } == true
            if (threatened != null && (!desperate || pressed)) return prey.flee(threatened, step)
        }

        if (life.busyUntil > tick) {
            val action = when (animal.goal?.need) {
                Need.Den -> if (animal.species == Species.Wolf) CreatureAction.Howling else CreatureAction.Digging
                Need.Mate -> CreatureAction.Courting
                else -> animal.action
            }
            return animal.copy(action = action, life = life.copy(stride = 0))
        }

        if (animal.species.predator) predators.hunt(animal, step)?.let { return it }

        return everyday.needs(animal, step)
    }

    // ----- prey -----

    // ----- predators -----

    // ----- everyday needs -----

    // ----- movement -----

    // ----- perception -----

    // ----- validation -----

    fun validate(state: EcologyState) {
        val life = checkNotNull(state.life)
        require(state.settlements == null && state.habitat == null && state.fire != null && state.chronicle != null)
        require(state.creatures.size <= IslandCreatureLimit)
        require(life.placed >= 0 && life.removed >= 0 && life.planted >= 0 && life.cleared in 0..life.planted)
        require(life.packs.size <= MaxPacks && life.packs.zipWithNext().all { (a, b) -> a.id < b.id })
        require(life.packs.all { it.id in 1 until life.nextPackId && it.formedAt in 0..state.tick && it.den in ground.reachable })
        require(life.warrens.size <= MaxWarrens && life.warrens.zipWithNext().all { (a, b) -> cellId(a.cell) < cellId(b.cell) })
        require(life.warrens.all { it.cell in ground.reachable && it.dugAt in 0..state.tick })
        require(life.census.size <= Species.entries.size && life.census.all { it.low in 0..it.high })
        val byId = state.creatures.associateBy { it.id }
        val packIds = life.packs.mapTo(HashSet()) { it.id }
        require(life.packs.all { pack -> pack.target == 0L || byId[pack.target]?.species in Species.Wolf.prey() })
        require(state.creatures.all { animal ->
            val creature = animal.life ?: return@all false
            val wild = animal.wildlife ?: return@all false
            val matured = when (creature.origin) {
                Origin.Born -> wild.bornAt > 0 && animal.matureAt == later(wild.bornAt, animal.species.maturityTicks())
                Origin.Placed -> wild.bornAt > 0 && animal.matureAt == wild.bornAt
                Origin.Seeded -> wild.bornAt == 0L && animal.matureAt == 0L
            }
            matured && wild.bornAt <= state.tick && creature.sex == sexOf(animal.id) && wild.home in ground.reachable &&
                creature.stamina in 0..MaxStamina && creature.stride in 0..DiagonalStride &&
                (creature.packId == 0 || animal.species == Species.Wolf && creature.packId in packIds) &&
                (wild.prey == null || animal.species.predator && byId[wild.prey]?.species in animal.species.prey()) &&
                creature.kills >= 0 && creature.litters >= 0 &&
                (creature.pregnantUntil == 0L || creature.sex == Sex.Female && creature.pregnantUntil > state.tick)
        })
        require(Species.entries.all { species -> state.creatures.count { it.species == species } <= species.lifeLimit() })
    }

    private fun Creature.event(tick: Long, kind: WildlifeMilestone) = WildlifeEvent(tick, id, species, kind, cell)

    private fun litterSize(mother: Creature, tick: Long) = when (mother.species) {
        Species.Rabbit -> 2 + lifeRoll(ground.seed, tick, mother.id, RollLitter, 3)
        Species.Deer -> 1
        Species.Fox -> 1 + lifeRoll(ground.seed, tick, mother.id, RollLitter, 3)
        Species.Wolf -> 2 + lifeRoll(ground.seed, tick, mother.id, RollLitter, 2)
    }
}

internal fun Species.gestation(): Long = when (this) {
    Species.Rabbit -> 300L
    Species.Deer -> 900L
    Species.Fox -> 600L
    Species.Wolf -> 720L
}

internal fun Species.afterLitter(): Long = when (this) {
    Species.Rabbit -> 300L
    Species.Deer -> 1200L
    Species.Fox -> 900L
    Species.Wolf -> 2400L
}

internal fun mateReach(species: Species) = when (species) {
    Species.Rabbit -> 14
    Species.Deer -> 16
    Species.Fox -> 24
    Species.Wolf -> 20
}

internal fun headingOf(from: GridCell, to: GridCell): CreatureHeading {
    val dx = (to.x - from.x).coerceIn(-1, 1)
    val dz = (to.z - from.z).coerceIn(-1, 1)
    return when {
        dx > 0 && dz > 0 -> CreatureHeading.PositiveXPositiveZ
        dx < 0 && dz > 0 -> CreatureHeading.NegativeXPositiveZ
        dx > 0 && dz < 0 -> CreatureHeading.PositiveXNegativeZ
        dx < 0 && dz < 0 -> CreatureHeading.NegativeXNegativeZ
        dx > 0 -> CreatureHeading.PositiveX
        dx < 0 -> CreatureHeading.NegativeX
        dz > 0 -> CreatureHeading.PositiveZ
        else -> CreatureHeading.NegativeZ
    }
}

internal fun CreatureHeading.delta(): Pair<Int, Int> = when (this) {
    CreatureHeading.PositiveX -> 1 to 0
    CreatureHeading.NegativeX -> -1 to 0
    CreatureHeading.PositiveZ -> 0 to 1
    CreatureHeading.NegativeZ -> 0 to -1
    CreatureHeading.PositiveXPositiveZ -> 1 to 1
    CreatureHeading.NegativeXPositiveZ -> -1 to 1
    CreatureHeading.PositiveXNegativeZ -> 1 to -1
    CreatureHeading.NegativeXNegativeZ -> -1 to -1
}

internal fun Long.toSubject() = (this and Int.MAX_VALUE.toLong()).toInt()
