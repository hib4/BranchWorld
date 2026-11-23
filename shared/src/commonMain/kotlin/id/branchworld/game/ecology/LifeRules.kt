package id.branchworld.game.ecology

import id.branchworld.game.simulation.CreatureHeading
import id.branchworld.game.simulation.GridCell
import id.branchworld.game.terrain.Biome
import id.branchworld.game.terrain.TerrainMap
import kotlin.math.abs
import kotlin.math.sign

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
internal class LifeRules(private val island: IslandEcology) {
    private val terrain = island.terrain
    private val navigation = island.navigation
    private val reachable = island.reachable.toHashSet()
    private val forest = island.reachable.filter { terrain.terrainCell(it).biome == Biome.PineForest }
    private val naturalCover = forest.toHashSet()
    private val meadow = island.roamCells(Species.Rabbit).toHashSet()
    private val edges = forest.filter { cell -> navigation.adjacent(cell).any { it !in naturalCover } }.ifEmpty { forest }
    private val edgeSet = edges.toHashSet()
    private val patchCells = PatchCellIndex()
    private var plantedCover: List<GridCell> = emptyList()
    private var bareCover: Set<GridCell> = emptySet()
    private var cover: Set<GridCell> = naturalCover
    private var warrenCells: Set<GridCell> = emptySet()
    private val fields = LinkedHashMap<GridCell, IntArray>()
    private var fieldBuilds = 0
    private var seed = terrain.key.seed

    // ----- placement -----

    fun checkCreature(state: EcologyState, species: Species, cell: GridCell, queued: List<Species>): PlacementCheck {
        if (state.life == null) return PlacementCheck.NotLiving
        groundCheck(cell)?.let { return it }
        if (burning(state, cell)) return PlacementCheck.Burning
        val total = state.creatures.size + queued.size
        if (total >= IslandCreatureLimit) return PlacementCheck.IslandFull
        if (state.creatures.count { it.species == species } + queued.count { it == species } >= species.lifeLimit()) {
            return PlacementCheck.SpeciesFull
        }
        return PlacementCheck.Ok
    }

    fun checkPlant(state: EcologyState, kind: PlantKind, cell: GridCell, queued: List<GridCell>): PlacementCheck {
        if (state.life == null) return PlacementCheck.NotLiving
        groundCheck(cell)?.let { return it }
        if (terrain.terrainCell(cell).biome !in PlantBiomes) return PlacementCheck.Unsuitable
        if (cell in state.springs || cell in queued || state.patches.any { it.cell == cell }) return PlacementCheck.Occupied
        if (burning(state, cell)) return PlacementCheck.Burning
        if (state.patches.count { it.planted } + queued.size >= MaxPlanted) return PlacementCheck.PlantsFull
        return PlacementCheck.Ok
    }

    private fun groundCheck(cell: GridCell): PlacementCheck? {
        val ground = terrain.cellAt(cell.x, cell.z) ?: return PlacementCheck.Unreachable
        if (ground.biome == Biome.Water) return PlacementCheck.Water
        if (!navigation.contains(cell)) return PlacementCheck.Unreachable
        return null
    }

    private fun burning(state: EcologyState, cell: GridCell) = state.fire?.burningAt(tileOf(cell)) != null

    fun plantPatch(kind: PlantKind, cell: GridCell): ForagePatch {
        val moisture = terrain.terrainCell(cell).moisture
        return ForagePatch(cellId(cell), cell, if (kind == PlantKind.Tree) ForageKind.Browse else ForageKind.Meadow,
            160 + moisture / 3, 1 + moisture / 96, if (kind == PlantKind.Tree) RegionalEcology.SaplingBiomass else MeadowSeed,
            planted = true)
    }

    fun validPlanted(patch: ForagePatch, springs: List<GridCell>): Boolean {
        val ground = terrain.terrainCell(patch.cell)
        return patch.planted && ground.biome in PlantBiomes && patch.cell !in springs &&
            patch.capacity == 160 + ground.moisture / 3 && patch.regrowth == 1 + ground.moisture / 96
    }

    fun creature(id: Long, species: Species, cell: GridCell, tick: Long, origin: Origin): Creature = Creature(
        id, species, cell, food = 720, water = 800, energy = 820, matureAt = tick,
        // Newcomers settle before they breed; a pack takes a season to raise its first pups.
        breedAfter = later(tick, if (origin == Origin.Seeded) 0 else if (species == Species.Wolf) WolfSettleTicks else SettleTicks),
        heading = Headings[lifeRoll(seed, tick, id, RollHeading, 4)],
        // A hunter set down takes in its new ground before it hunts.
        wildlife = WildlifeLife(tick, cell, huntUntil = if (species.predator && origin == Origin.Placed) later(tick, HunterSettleTicks) else 0),
        life = CreatureLife(sexOf(id), origin),
    )

    /** The Wild start: rabbits on the meadows, deer in the woods and a pair of foxes on the forest edge. */
    fun seedWild(state: EcologyState): EcologyState {
        seed = terrain.key.seed
        val animals = mutableListOf<Creature>()
        fun scatter(species: Species, count: Int, cells: List<GridCell>) {
            val near = navigation.octileDistancesTo(navigation.spawn)
            val ordered = cells.filter { near[cellId(it)] >= 0 }.sortedWith(compareBy<GridCell> {
                near[cellId(it)] + lifeRoll(seed, 0, cellId(it).toLong(), RollSeed + species.canonicalId, 90)
            }.thenBy(::cellId))
            var placed = 0
            for (cell in ordered) {
                if (placed == count) break
                if (animals.any { chebyshev(it.cell, cell) < 3 }) continue
                animals.add(creature(animals.size + 1L, species, cell, 0, Origin.Seeded))
                placed++
            }
        }
        scatter(Species.Rabbit, 8, meadow.sortedBy(::cellId))
        scatter(Species.Deer, 4, forest)
        scatter(Species.Fox, 2, edges)
        return state.copy(creatures = animals, nextEntityId = animals.size + 1L,
            life = checkNotNull(state.life).copy(placed = animals.size.toLong()))
    }

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
        seed = previous.terrain.key.seed
        fieldBuilds = 0
        patchCells.rebuild(patches)
        var life = checkNotNull(previous.life)
        var chronicle = chronicleIn
        val events = mutableListOf<WildlifeEvent>()
        updateCover(patches, fire?.bare ?: emptySet(), life.warrens)

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
            val pack = Pack(life.nextPackId, tick, denFor(wolf.cell, partner.cell, packs))
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
                val safe = target != null && refuge(target)
                if (target == null || tiring || safe || tick - pack.huntFrom > PackHuntTicks) {
                    if (target != null && tick - pack.huntFrom >= EscapeTicks) {
                        chronicle = escaped(chronicle, tick, target, leader)
                    }
                    current = current.copy(target = 0, huntFrom = 0)
                }
            }
            val settled = members.all { tick >= checkNotNull(it.wildlife).huntUntil || it.wildlife.prey != null }
            if (current.target == 0L && settled && (tick + pack.id) % ScanEvery == 0L) {
                val hunger = members.sumOf { it.food } / members.size
                if (hunger < packHunger(tick)) {
                    val choice = start.asSequence()
                        .filter { it.species in Species.Wolf.prey() && (it.species != Species.Deer || members.size >= 2) && !refuge(it) }
                        .filter { prey -> members.any { sees(it.cell, prey.cell, WolfSight) } }
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

        val context = Step(tick, recharge, patches, previous.springs, fire, byId, packOf, packById, life.warrens, thumpers)
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

        chronicle = drainEscapes(chronicle, tick)
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
            if (refuge(prey)) continue
            val reach = chebyshev(hunter.cell, prey.cell)
            if (reach > 1) continue
            val sheltered = prey.species == Species.Rabbit && prey.cell in cover
            val success = when {
                sheltered -> lifeRoll(seed, tick, hunter.id, RollCatch, 100) < (if (reach == 0) 50 else if (hunter.species == Species.Wolf) 25 else 15)
                prey.species == Species.Deer -> animals.count {
                    it.species == Species.Wolf && (packOf[it.id] ?: checkNotNull(it.life).packId) == hunterPack && hunterPack != 0 &&
                        chebyshev(it.cell, prey.cell) <= 1
                } >= 2 && lifeRoll(seed, tick, hunter.id, RollCatch, 100) < 60
                reach == 0 -> true
                else -> lifeRoll(seed, tick, hunter.id, RollCatch, 100) < if (hunter.species == Species.Wolf) 55 else 40
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
            val animal = creature(nextId, arrival.species, arrival.cell, tick, Origin.Placed)
            animals.add(animal)
            events.add(animal.event(tick, WildlifeMilestone.Placed))
            nextId++
            placed++
        }
        animals.sortBy { it.id }

        // A species whose last member died this tick has vanished from the island.
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
                val where = animals.firstOrNull { it.species == species }?.cell ?: navigation.spawn
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

    private fun escaped(chronicle: WorldChronicle, tick: Long, prey: Creature, hunter: Creature): WorldChronicle {
        if (chronicle.events.any { it.kind == WorldEventKind.Escaped && it.subject == prey.id.toSubject() && tick - it.tick < 300 }) {
            return chronicle
        }
        return chronicle.record(tick, WorldEventKind.Escaped, prey.cell, prey.id.toSubject(), hunter.id.toSubject()).first
    }

    private inner class Step(
        val tick: Long,
        val recharge: Int,
        val patches: MutableList<ForagePatch>,
        val springs: List<GridCell>,
        val fire: FireView?,
        val byId: Map<Long, Creature>,
        val packOf: Map<Long, Int>,
        val packs: Map<Int, Pack>,
        val warrens: List<Warren>,
        val thumpers: List<Creature>,
    ) {
        val phase = tick % DayLength
        val night = phase >= 660 || phase < 60
        val animals = byId.values.sortedBy { it.id }
    }

    private fun act(animal: Creature, step: Step): Creature {
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

    private fun decideAndMove(animal: Creature, step: Step): Creature {
        val tick = step.tick
        val life = checkNotNull(animal.life)
        // Fire overrides everything: run for the farthest safe bank.
        step.fire?.let { view ->
            if (view.distance(animal.cell, FireAlarm) <= FireAlarm) {
                val bank = step.springs.filter { view.distance(it, FireAlarm) > FireAlarm }
                    .minWithOrNull(compareBy<GridCell> { octile(animal.cell, it) }.thenBy(::cellId))
                val goal = bank ?: navigation.adjacent8(animal.cell)
                    .maxWithOrNull(compareBy<GridCell> { view.distance(it, FireAlarm + 2) }.thenBy { -cellId(it) }) ?: animal.cell
                return travel(animal, Goal(Need.Flee, goal), CreatureAction.Fleeing, sprint(animal), moving = false)
            }
        }

        if (!animal.species.predator || animal.species == Species.Fox) {
            val threatened = perceiveThreat(animal, step)
            // Hunger and thirst eventually outweigh fear: a desperate animal leaves cover unless it is being run down.
            val desperate = animal.water < 150 || animal.food < 150
            val pressed = threatened?.life?.threat?.let(step.byId::get)?.let {
                it.action == CreatureAction.Chasing && chebyshev(it.cell, animal.cell) <= 4
            } == true
            if (threatened != null && (!desperate || pressed)) return flee(threatened, step)
        }

        if (life.busyUntil > tick) {
            val action = when (animal.goal?.need) {
                Need.Den -> if (animal.species == Species.Wolf) CreatureAction.Howling else CreatureAction.Digging
                Need.Mate -> CreatureAction.Courting
                else -> animal.action
            }
            return animal.copy(action = action, life = life.copy(stride = 0))
        }

        if (animal.species.predator) hunt(animal, step)?.let { return it }

        return needs(animal, step)
    }

    // ----- prey -----

    private fun perceiveThreat(animal: Creature, step: Step): Creature? {
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
            val visible = (scanning || hunting || range <= 3) && sees(animal.cell, predator.cell, alarming)
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

    private fun flee(animal: Creature, step: Step): Creature {
        val tick = step.tick
        val life = checkNotNull(animal.life)
        val threat = step.byId[life.threat]
        if (animal.species == Species.Rabbit && shelter(animal)) {
            return animal.copy(goal = Goal(Need.Hide, animal.cell), action = CreatureAction.Alert, life = life.copy(stride = 0))
        }
        if (threat == null) {
            // A heard alarm for a predator nobody can place: stand up and look.
            return animal.copy(goal = Goal(Need.Hide, animal.cell), action = CreatureAction.Alert, life = life.copy(stride = 0))
        }
        val close = chebyshev(animal.cell, threat.cell)
        val refugeCell = if (animal.species == Species.Rabbit) bestRefuge(animal.cell, threat.cell, step) else null
        // Close pursuit: a rabbit jinks sideways and a hunter overshoots.
        if (animal.species == Species.Rabbit && close <= 2 && lifeRoll(seed, tick, animal.id, RollJink, 100) < 45) {
            val away = sign((animal.cell.x - threat.cell.x).toDouble()).toInt() to sign((animal.cell.z - threat.cell.z).toDouble()).toInt()
            val sides = listOf(GridCell(animal.cell.x - away.second, animal.cell.z + away.first),
                GridCell(animal.cell.x + away.second, animal.cell.z - away.first))
            val side = sides[lifeRoll(seed, tick, animal.id, RollSide, 2)].takeIf { navigation.canStep8(animal.cell, it) }
            if (side != null) return travel(animal, Goal(Need.Flee, side), CreatureAction.Fleeing, sprint(animal), moving = true)
        }
        if (life.heard && close > 6) {
            val goal = refugeCell ?: animal.cell
            return if (goal == animal.cell) animal.copy(goal = Goal(Need.Hide, goal), action = CreatureAction.Alert, life = life.copy(stride = 0))
            else travel(animal, Goal(Need.Flee, goal), CreatureAction.Walking, animal.species.gait.walk, moving = true)
        }
        if (refugeCell != null) return travel(animal, Goal(Need.Flee, refugeCell), CreatureAction.Fleeing, sprint(animal), moving = true)
        // Deer keep with the herd as they run; everything else simply puts distance between itself and the danger.
        val away = navigation.adjacent8(animal.cell).maxWithOrNull(compareBy<GridCell> { octile(it, threat.cell) }
            .thenBy { -cellId(it) }) ?: animal.cell
        return travel(animal, Goal(Need.Flee, away), CreatureAction.Fleeing, sprint(animal), moving = true)
    }

    /** The warren or cover a rabbit should run for: close to it and not toward the danger. */
    private fun bestRefuge(from: GridCell, threat: GridCell, step: Step): GridCell? {
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
            if (cell in cover && cell in reachable) consider(cell, 0)
        }
        return best
    }

    // ----- predators -----

    private fun hunt(animal: Creature, step: Step): Creature? {
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
            return travel(animal.copy(life = life.copy(chaseFrom = if (life.chaseFrom == 0L) tick else life.chaseFrom)),
                Goal(Need.Hunt, aim), if (chasing) CreatureAction.Chasing else CreatureAction.Stalking,
                if (chasing) sprint(animal) else animal.species.gait.stalk, moving = true)
        }
        if (pack != null) return null
        // Foxes and lone wolves hunt alone.
        val wild = checkNotNull(animal.wildlife)
        val prior = wild.prey?.let(step.byId::get)
        if (wild.prey != null) {
            val tooLong = tick - life.chaseFrom > SoloHuntTicks
            if (prior == null || refuge(prior) || tooLong || life.stamina < GiveUpStamina || chebyshev(prior.cell, animal.cell) > 14) {
                var given = animal.copy(goal = null, action = CreatureAction.Idle,
                    wildlife = wild.copy(prey = null, huntUntil = later(tick, SoloRecovery)), life = life.copy(chaseFrom = 0))
                if (prior != null && tick - life.chaseFrom >= EscapeTicks && animal.action == CreatureAction.Chasing) {
                    pendingEscapes.add(prior to animal)
                }
                return given
            }
            val range = chebyshev(animal.cell, prior.cell)
            val chasing = range <= ChaseRange || checkNotNull(prior.life).threat != 0L || animal.action == CreatureAction.Chasing
            return travel(animal, Goal(Need.Hunt, prior.cell), if (chasing) CreatureAction.Chasing else CreatureAction.Stalking,
                if (chasing) sprint(animal) else animal.species.gait.stalk, moving = true)
        }
        val hungry = animal.food < if (animal.species == Species.Wolf) 720 else 640
        if (!hungry || tick < wild.huntUntil || animal.matureAt > tick || (tick + animal.id) % ScanEvery != 0L ||
            life.stamina < 400 || animal.water < 150
        ) return null
        val sight = if (animal.species == Species.Wolf) WolfSight else FoxSight
        val target = step.animals.asSequence()
            .filter { it.species in animal.species.prey() && (it.species != Species.Deer) && !refuge(it) && sees(animal.cell, it.cell, sight) }
            .minWithOrNull(compareBy<Creature> { octile(animal.cell, it.cell) }.thenBy { it.id }) ?: return null
        return travel(animal.copy(wildlife = wild.copy(prey = target.id, huntUntil = later(tick, SoloHuntTicks.toLong())),
            life = life.copy(chaseFrom = tick)), Goal(Need.Hunt, target.cell), CreatureAction.Stalking, animal.species.gait.stalk, moving = true)
    }

    /** Escapes noticed while the solo hunters gave up this tick; recorded by the engine pass that owns the chronicle. */
    val pendingEscapes = mutableListOf<Pair<Creature, Creature>>()

    fun drainEscapes(chronicle: WorldChronicle, tick: Long): WorldChronicle {
        var log = chronicle
        for ((prey, hunter) in pendingEscapes) log = escaped(log, tick, prey, hunter)
        pendingEscapes.clear()
        return log
    }

    /** Where a flanking wolf should run to cut the prey off: a few cells ahead of it, or on its way to safety. */
    private fun intercept(target: GridCell, ahead: CreatureHeading): GridCell {
        val (dx, dz) = ahead.delta()
        for (lead in 3 downTo 1) {
            val cell = GridCell(target.x + dx * lead, target.z + dz * lead)
            if (cell in reachable) return cell
        }
        return target
    }

    private fun intercept(target: Creature, step: Step): GridCell {
        val goal = target.goal?.takeIf { it.need == Need.Flee || it.need == Need.Hide }?.cell
        if (goal != null && goal != target.cell && reachable.contains(goal)) {
            // Run for the point between the prey and the shelter it is making for.
            val mid = GridCell((target.cell.x + goal.x) / 2, (target.cell.z + goal.z) / 2)
            if (mid in reachable) return mid
        }
        return intercept(target.cell, target.heading)
    }

    // ----- everyday needs -----

    private fun needs(animal: Creature, step: Step): Creature {
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
            return travel(animal, prior, CreatureAction.Walking, animal.species.gait.walk, moving = true)
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
            Species.Rabbit -> { cell -> cell in meadow || cell in edgeSet }
            Species.Deer -> { cell -> cell in naturalCover || cell in meadow }
            Species.Fox -> { cell -> cell in edgeSet || cell in meadow }
            Species.Wolf -> { cell -> cell in reachable }
        }
        val radius = when (animal.species) {
            Species.Rabbit -> 6
            Species.Deer -> 9
            Species.Fox -> 10
            Species.Wolf -> 16
        }
        val target = roamCell(animal, home, radius, habitat, tick)
        return travel(animal, Goal(Need.Roam, target), CreatureAction.Walking, animal.species.gait.walk, moving = true)
    }

    private fun roamCell(animal: Creature, home: GridCell, radius: Int, habitat: (GridCell) -> Boolean, tick: Long): GridCell {
        val options = ArrayList<GridCell>()
        for (dz in -radius..radius) for (dx in -radius..radius) {
            val cell = GridCell(home.x + dx, home.z + dz)
            if (cell != animal.cell && cell in reachable && habitat(cell) && chebyshev(cell, animal.cell) <= 12) options.add(cell)
        }
        if (options.isEmpty()) return if (home in reachable) home else animal.cell
        return options[lifeRoll(seed, tick, animal.id, RollRoam, options.size)]
    }

    private fun water(animal: Creature, step: Step): Creature? {
        val prior = animal.goal
        val bank = if (prior?.need == Need.Water && prior.cell in step.springs) prior.cell else
            step.springs.minWithOrNull(compareBy<GridCell> { octile(animal.cell, it) }.thenBy(::cellId)) ?: return null
        if (bank == animal.cell) {
            return animal.copy(goal = Goal(Need.Water, bank), water = minOf(MaxReserve, animal.water + 31), action = CreatureAction.Drinking,
                life = checkNotNull(animal.life).copy(stride = 0))
        }
        return travel(animal, Goal(Need.Water, bank), CreatureAction.Walking, animal.species.gait.walk, moving = false)
    }

    private fun food(animal: Creature, step: Step): Creature? {
        val prior = animal.goal
        val patches = step.patches
        val current = prior?.takeIf { it.need == Need.Food }?.let { patches.getOrNull(patchCells[it.cell]) }
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
            return travel(animal, Goal(Need.Food, patch.cell), CreatureAction.Walking, animal.species.gait.walk, moving = false)
        }
        val life = checkNotNull(animal.life).copy(stride = 0)
        if ((step.tick + animal.id) % 4L != 0L) return animal.copy(goal = Goal(Need.Food, patch.cell), action = CreatureAction.Eating, life = life)
        val index = patchCells[patch.cell]
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

    private fun nearCover(cell: GridCell) = cell in cover || navigation.adjacent(cell).any(cover::contains)

    private fun rest(animal: Creature, step: Step): Creature? {
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
        return travel(animal, Goal(Need.Rest, shelter), CreatureAction.Walking, animal.species.gait.walk, moving = true)
    }

    private fun burrow(animal: Creature, step: Step): Creature? {
        val tick = step.tick
        if (animal.matureAt > tick || step.warrens.size >= MaxWarrens) return null
        if (step.warrens.any { chebyshev(it.cell, animal.cell) <= WarrenRange }) return null
        if (!diggable(animal.cell)) return null
        if (step.animals.any { it.id != animal.id && it.action == CreatureAction.Digging && chebyshev(it.cell, animal.cell) < WarrenSpacing }) return null
        return animal.copy(goal = Goal(Need.Den, animal.cell), action = CreatureAction.Digging,
            life = checkNotNull(animal.life).copy(busyUntil = tick + DigTicks, stride = 0))
    }

    private fun diggable(cell: GridCell) = cell in meadow && navigation.adjacent(cell).size >= 3 &&
        (nearCover(cell) || navigation.adjacent8(cell).any { it in edgeSet })

    private fun nearestCover(from: GridCell): GridCell? {
        if (from in cover) return from
        for (ring in 1..12) {
            var best: GridCell? = null
            for (dz in -ring..ring) for (dx in -ring..ring) {
                if (maxOf(abs(dx), abs(dz)) != ring) continue
                val cell = GridCell(from.x + dx, from.z + dz)
                if (cell in cover && cell in reachable && (best == null || cellId(cell) < cellId(best))) best = cell
            }
            if (best != null) return best
        }
        return null
    }

    private fun wolfDay(animal: Creature, step: Step): Creature? {
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
                return travel(animal, Goal(Need.Rally, other.cell), CreatureAction.Walking, animal.species.gait.walk, moving = true)
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
            return travel(animal, Goal(Need.Den, pack.den), CreatureAction.Walking, animal.species.gait.walk, moving = false)
        }
        if (phase in 560 until 600) {
            return travel(animal, Goal(Need.Den, pack.den), CreatureAction.Walking, animal.species.gait.walk, moving = false)
        }
        // Night patrol: the leader ranges the territory; the others keep with it.
        if (animal.id != leader.id) {
            val apart = chebyshev(animal.cell, leader.cell)
            if (apart > 8) return travel(animal, Goal(Need.Rally, leader.cell), CreatureAction.Walking, sprint(animal) * 3 / 4, moving = true)
            if (apart > 3) return travel(animal, Goal(Need.Rally, leader.cell), CreatureAction.Walking, animal.species.gait.walk, moving = true)
            return animal.copy(goal = Goal(Need.Rally, leader.cell), action = CreatureAction.Idle, life = life.copy(stride = 0))
        }
        val prior = animal.goal
        val hunger = members.sumOf { it.food } / members.size
        val range = if (hunger < 450) HungryTerritory else Territory
        val patrol = if (prior?.need == Need.Roam && prior.cell != animal.cell && (tick + animal.id) % 200L != 0L) prior.cell
            else roamCell(animal, pack.den, range, { true }, tick)
        return travel(animal, Goal(Need.Roam, patrol), CreatureAction.Walking, animal.species.gait.walk, moving = true)
    }

    private fun mate(animal: Creature, step: Step): Creature? {
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
        return travel(animal, Goal(Need.Mate, partner.cell), CreatureAction.Walking, animal.species.gait.walk, moving = true)
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

    private fun breedingPair(a: Creature, b: Creature, step: Step): Boolean {
        val pack = step.packOf[a.id] ?: 0
        if (pack == 0 || step.packOf[b.id] != pack) return false
        val members = step.animals.filter { it.species == Species.Wolf && step.packOf[it.id] == pack && it.matureAt <= step.tick }
        val female = members.firstOrNull { checkNotNull(it.life).sex == Sex.Female }?.id
        val male = members.firstOrNull { checkNotNull(it.life).sex == Sex.Male }?.id
        return setOf(a.id, b.id) == setOf(female, male)
    }

    // ----- movement -----

    private fun sprint(animal: Creature): Int {
        val gait = animal.species.gait
        return if (checkNotNull(animal.life).stamina > SprintFloor) gait.sprint else gait.walk
    }

    /**
     * Moves toward [goal] at [speed] centi-cells per tick: the stride fills until it covers the next step (141 on a
     * diagonal), so a sprinting rabbit crosses a cell every tick and a walking deer one about every third.
     * [moving] targets are steered toward directly; fixed ones follow a cached shortest-path field.
     */
    private fun travel(animal: Creature, goal: Goal, action: CreatureAction, speed: Int, moving: Boolean): Creature {
        val life = checkNotNull(animal.life)
        if (goal.cell == animal.cell) return animal.copy(goal = goal, action = action, life = life.copy(stride = 0))
        val next = if (moving) steer(animal.cell, goal.cell) else follow(animal.cell, goal.cell)
        if (next == animal.cell) return animal.copy(goal = goal, action = action, life = life.copy(stride = 0))
        val cost = if (next.x != animal.cell.x && next.z != animal.cell.z) DiagonalStride else OrthogonalStride
        val stride = minOf(life.stride + speed, DiagonalStride)
        if (stride < cost) return animal.copy(goal = goal, action = action, heading = headingOf(animal.cell, next),
            life = life.copy(stride = stride))
        return animal.copy(cell = next, goal = goal, action = action, heading = headingOf(animal.cell, next),
            energy = (animal.energy - if (action == CreatureAction.Chasing || action == CreatureAction.Fleeing) 2 else 0).coerceAtLeast(0),
            life = life.copy(stride = stride - cost))
    }

    private fun follow(from: GridCell, target: GridCell): GridCell {
        if (target !in reachable) return steer(from, target)
        val field = field(target) ?: return steer(from, target)
        return navigation.stepOctile(from, field)
    }

    /** One greedy step that closes on [target]; falls back to a path field when water or cliffs are in the way. */
    private fun steer(from: GridCell, target: GridCell): GridCell {
        val here = octile(from, target)
        var best = from
        var bestScore = here
        for (next in navigation.adjacent8(from)) {
            val score = octile(next, target)
            if (score < bestScore) {
                best = next
                bestScore = score
            }
        }
        if (best != from || target !in reachable) return best
        val field = field(target)
        // Out of fresh path fields this tick: the cheaper four-way field still finds a way round.
        if (field == null) return navigation.step(from, island.terrainDistance(target))
        return navigation.stepOctile(from, field)
    }

    private fun field(target: GridCell): IntArray? {
        fields[target]?.let {
            fields.remove(target)
            fields[target] = it
            return it
        }
        if (fieldBuilds >= FieldBudget) return null
        fieldBuilds++
        if (fields.size >= CachedFields) fields.remove(fields.keys.first())
        return navigation.octileDistancesTo(target).also { fields[target] = it }
    }

    // ----- perception -----

    private fun updateCover(patches: List<ForagePatch>, bare: Set<GridCell>, warrens: List<Warren>) {
        val rooted = patches.filter { it.rooted() && it.kind == ForageKind.Browse }.map { it.cell }
        warrenCells = warrens.mapTo(HashSet()) { it.cell }
        if (rooted == plantedCover && bare === bareCover) return
        plantedCover = rooted
        bareCover = bare
        cover = naturalCover.toHashSet().apply {
            removeAll(bare)
            for (cell in rooted) {
                add(cell)
                addAll(navigation.adjacent(cell))
            }
        }
    }

    /** A rabbit down its warren cannot be caught; cover only makes a catch harder. */
    private fun refuge(animal: Creature) = animal.species == Species.Rabbit && animal.cell in warrenCells

    /** Where a rabbit can stop running: its warren or a thicket. */
    private fun shelter(animal: Creature) = animal.species == Species.Rabbit && (animal.cell in warrenCells || animal.cell in cover)

    /** Whether something at [from] can see [to]: in range, not hidden deep in cover, and no thicket or ridge between. */
    private fun sees(from: GridCell, to: GridCell, sight: Int): Boolean {
        val range = chebyshev(from, to)
        if (range > sight) return false
        if (range <= 2) return true
        if (to in cover && range > 4) return false
        val ends = maxOf(terrain.terrainCell(from).elevationTwice, terrain.terrainCell(to).elevationTwice)
        var x = from.x
        var z = from.z
        val dx = abs(to.x - x)
        val dz = -abs(to.z - z)
        val sx = if (x < to.x) 1 else -1
        val sz = if (z < to.z) 1 else -1
        var error = dx + dz
        var thicket = 0
        while (true) {
            val doubled = 2 * error
            if (doubled >= dz) { error += dz; x += sx }
            if (doubled <= dx) { error += dx; z += sz }
            if (x == to.x && z == to.z) return true
            val cell = GridCell(x, z)
            if (cell in cover && ++thicket >= 2) return false
            val ground = terrain.cellAt(x, z) ?: return false
            if (ground.elevationTwice > ends + RidgeTwice) return false
        }
    }

    private fun denFor(a: GridCell, b: GridCell, packs: List<Pack>): GridCell {
        val mid = GridCell((a.x + b.x) / 2, (a.z + b.z) / 2)
        return edges.filter { cell -> packs.none { chebyshev(it.den, cell) < 14 } }
            .minWithOrNull(compareBy<GridCell> { octile(it, mid) }.thenBy(::cellId))
            ?: edges.minWithOrNull(compareBy<GridCell> { octile(it, mid) }.thenBy(::cellId)) ?: a
    }

    // ----- validation -----

    fun validate(state: EcologyState) {
        val life = checkNotNull(state.life)
        require(state.settlements == null && state.habitat == null && state.fire != null && state.chronicle != null)
        require(state.creatures.size <= IslandCreatureLimit)
        require(life.placed >= 0 && life.removed >= 0 && life.planted >= 0 && life.cleared in 0..life.planted)
        require(life.packs.size <= MaxPacks && life.packs.zipWithNext().all { (a, b) -> a.id < b.id })
        require(life.packs.all { it.id in 1 until life.nextPackId && it.formedAt in 0..state.tick && it.den in reachable })
        require(life.warrens.size <= MaxWarrens && life.warrens.zipWithNext().all { (a, b) -> cellId(a.cell) < cellId(b.cell) })
        require(life.warrens.all { it.cell in reachable && it.dugAt in 0..state.tick })
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
            matured && wild.bornAt <= state.tick && creature.sex == sexOf(animal.id) && wild.home in reachable &&
                creature.stamina in 0..MaxStamina && creature.stride in 0..DiagonalStride &&
                (creature.packId == 0 || animal.species == Species.Wolf && creature.packId in packIds) &&
                (wild.prey == null || animal.species.predator && byId[wild.prey]?.species in animal.species.prey()) &&
                creature.kills >= 0 && creature.litters >= 0 &&
                (creature.pregnantUntil == 0L || creature.sex == Sex.Female && creature.pregnantUntil > state.tick)
        })
        require(Species.entries.all { species -> state.creatures.count { it.species == species } <= species.lifeLimit() })
    }

    private fun Creature.event(tick: Long, kind: WildlifeMilestone) = WildlifeEvent(tick, id, species, kind, cell)

    private companion object {
        const val MeadowSeed = 60
        const val SettleTicks = 240L
        const val HunterSettleTicks = 400L
        const val WolfSettleTicks = 1800L
        const val RabbitSight = 9
        const val DeerSight = 11
        const val FoxSight = 9
        const val WolfSight = 11
        const val Hearing = 4
        const val ThumpReach = 6
        const val AlarmTicks = 30L
        const val HeardTicks = 20L
        const val RefugeReach = 10
        const val ChaseRange = 5
        const val GiveUpStamina = 150
        const val SprintFloor = 120
        const val PackHuntTicks = 90L
        const val SoloHuntTicks = 70L
        const val SoloRecovery = 200L
        const val EscapeTicks = 10L
        const val ScanEvery = 3L
        const val PackReach = 10
        const val PackLimit = 6
        const val LoneSearch = 22
        const val Territory = 14
        const val HungryTerritory = 30
        const val HowlTicks = 40L
        const val DigTicks = 40L
        const val CourtTicks = 12L
        const val MaleRest = 120L
        const val WarrenSpacing = 4
        const val WarrenRange = 10
        const val FreshGrass = 25
        const val RabbitMeatWolf = 380
        const val RabbitMeatFox = 420
        const val DeerMeat = 3000
        const val PackRabbitMeat = 900
        const val BoomFloor = 12
        const val CrashFloor = 10
        const val RidgeTwice = 4
        const val FireAlarm = 3
        const val FieldBudget = 10
        const val CachedFields = 128
        const val RollHeading = 1
        const val RollRoam = 2
        const val RollCatch = 3
        const val RollJink = 4
        const val RollSide = 5
        const val RollLitter = 6
        const val RollSeed = 16
        val PlantBiomes = setOf(Biome.Meadow, Biome.PineForest, Biome.Wetland)
        val Headings = listOf(CreatureHeading.PositiveX, CreatureHeading.NegativeX, CreatureHeading.PositiveZ, CreatureHeading.NegativeZ)

        fun packHunger(tick: Long): Int {
            val phase = tick % DayLength
            return if (phase >= 600 || phase < 60) 560 else 360
        }
    }

    private fun litterSize(mother: Creature, tick: Long) = when (mother.species) {
        Species.Rabbit -> 2 + lifeRoll(seed, tick, mother.id, RollLitter, 3)
        Species.Deer -> 1
        Species.Fox -> 1 + lifeRoll(seed, tick, mother.id, RollLitter, 3)
        Species.Wolf -> 2 + lifeRoll(seed, tick, mother.id, RollLitter, 2)
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

private fun mateReach(species: Species) = when (species) {
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

private fun Long.toSubject() = (this and Int.MAX_VALUE.toLong()).toInt()
