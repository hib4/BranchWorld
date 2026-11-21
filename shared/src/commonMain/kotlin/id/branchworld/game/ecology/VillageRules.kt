package id.branchworld.game.ecology

import id.branchworld.game.simulation.GridCell
import id.branchworld.game.simulation.SplitMix64
import id.branchworld.game.simulation.mix64
import id.branchworld.game.terrain.WaterKind
import kotlin.math.abs

private const val NameSalt = 0x3c79ac492ba7b653uL
private const val HaulWater = 1
private const val MaximumHaulers = 1
private const val FoundingFood = 24
private const val ArrivalCooldown = 1_200L
private const val GrowthMargin = 2
private const val LotSpacing = 2
private const val FieldHands = 4
private const val WoodReserve = 12
private const val StoneReserve = 12
private const val GrazingRadius = 4
private const val GrazingPerCrop = 4
private const val ThirstPetitionTurns = 2
private const val ClearedPatches = 2

/**
 * Rules v9 settlement turns. Every effect comes from authoritative ecology inputs: bank stage, soil moisture,
 * forage biomass, herbivores feeding beside the fields, and the Keeper's recorded endorsements.
 */
internal class VillageRules(private val island: IslandEcology, private val economy: SettlementEconomy) {
    private val terrain = island.terrain
    private val names: List<Int> = run {
        val random = SplitMix64(mix64(terrain.key.seed.toULong() xor NameSalt))
        val order = HouseholdNames.indices.toMutableList()
        for (index in order.lastIndex downTo 1) {
            val swap = random.nextBounded(index + 1)
            val held = order[index]
            order[index] = order[swap]
            order[swap] = held
        }
        order
    }

    /** Herbivores feeding next to an active field during this tick. Evaluated every tick between turns. */
    fun graze(world: SettlementWorld, creatures: List<Creature>): SettlementWorld {
        val village = world.village ?: return world
        val fields = world.sites.flatMap { site -> if (village.active(site.id).isEmpty()) emptyList() else village.fields(site.id) }
        if (fields.isEmpty()) return world
        val feeding = creatures.count { animal ->
            animal.species != Species.Fox && animal.action == CreatureAction.Eating &&
                fields.any { distance(it.cell, animal.cell) <= GrazingRadius }
        }
        if (feeding == 0) return world
        return world.copy(village = village.copy(grazing = minOf(village.grazing + feeding, 4096)))
    }

    fun endorse(world: SettlementWorld, tick: Long, petitionId: Int, option: PlanOption): Boolean {
        val petition = world.village?.openPetition() ?: return false
        return petition.id == petitionId && option in petition.options && tick <= petition.deadline
    }

    fun applyEndorsement(world: SettlementWorld, tick: Long, petitionId: Int, option: PlanOption): SettlementWorld {
        if (!endorse(world, tick, petitionId, option)) return world
        val village = checkNotNull(world.village)
        val petitions = village.petitions.map {
            if (it.id == petitionId) it.copy(choice = option, decidedAt = tick, byKeeper = true) else it
        }
        val petition = petitions.first { it.id == petitionId }
        return world.copy(village = village.copy(petitions = petitions).record(
            VillageEvent(tick, VillageEventKind.PlanChosen, petition.siteId, petitionId, option.code),
        ))
    }

    fun advance(
        world: SettlementWorld,
        tick: Long,
        environment: Environment,
        patches: MutableList<ForagePatch>,
        springs: List<GridCell>,
        conditions: GrowingConditions,
    ): SettlementWorld {
        if (tick % SettlementTurn != 0L) return world
        val village = world.village ?: return world
        if (world.sites.isEmpty()) return foundHollowmere(world, village, tick, patches, springs)

        var state = world
        state = decideOverdue(state, tick)
        state = applyPlans(state, tick, patches, springs)
        val sites = state.sites.toMutableList()
        var current = checkNotNull(state.village)
        var gatheredFood = state.gatheredFood
        var gatheredWood = state.gatheredWood
        val grazingLoss = current.grazing / GrazingPerCrop
        var grazingLeft = grazingLoss
        for ((index, site) in sites.withIndex()) {
            val households = current.active(site.id)
            if (households.isEmpty()) continue
            val turn = siteTurn(site, households, current, tick, environment, patches, springs, conditions, grazingLeft)
            grazingLeft -= turn.grazed
            sites[index] = turn.site
            current = turn.village
            gatheredFood += turn.gatheredFood
            gatheredWood += turn.gatheredWood
        }
        current = current.copy(grazing = 0)
        if (grazingLoss - grazingLeft > 0 && (current.lastGrazed == null || tick - checkNotNull(current.lastGrazed) >= 600)) {
            current = current.copy(lastGrazed = tick).record(
                VillageEvent(tick, VillageEventKind.FieldGrazed, sites.first().id, 0, grazingLoss - grazingLeft),
            )
        }
        val thirsty = severeThirst(sites.first().welfare?.unmet ?: 0, sites.first().residents) && current.active(sites.first().id).isNotEmpty()
        current = current.copy(thirstTurns = if (thirsty) minOf(current.thirstTurns + 1, 16) else 0)
        current = raisePetitions(sites, current, tick, patches, springs)
        return SettlementWorld(sites, state.events, gatheredFood, gatheredWood, current)
    }

    private fun foundHollowmere(
        world: SettlementWorld, village: VillageState, tick: Long, patches: List<ForagePatch>, springs: List<GridCell>,
    ): SettlementWorld {
        val site = economy.found(1, tick, 4, patches, springs, emptyList(), VillageLots, lotSpacing = LotSpacing) ?: return world
        val bank = checkNotNull(island.drinkingBank(site.center, springs))
        var next = village.nextId
        val structures = buildList {
            for (lot in site.lots.take(3)) add(Structure(next++, site.id, StructureKind.Home, lot, tick, HomeSteps, HomeSteps, completedAt = tick))
            add(Structure(next++, site.id, StructureKind.Field, site.field, tick, 1, 1, completedAt = tick))
        }
        val founders = listOf(Trade.Farmer to 2, Trade.Woodcutter to 1, Trade.Mason to 1)
        val households = founders.mapIndexed { index, (trade, members) ->
            Household(next++, names[index], site.id, structures[index].id, members, trade, tick)
        }
        val residents = households.sumOf { it.members }
        val opening = site.copy(residents = residents, homes = 3, food = FoundingFood, welfare = SettlementWelfare(bank, residents * 2))
        val founded = VillageState(households, structures, nextId = next)
            .record(VillageEvent(tick, VillageEventKind.Founded, site.id, 0, residents))
        return world.copy(sites = listOf(opening), village = founded)
    }

    private class SiteTurn(
        val site: Settlement, val village: VillageState, val gatheredFood: Int, val gatheredWood: Int, val grazed: Int,
    )

    private fun siteTurn(
        previous: Settlement, households: List<Household>, village: VillageState, tick: Long, environment: Environment,
        patches: MutableList<ForagePatch>, springs: List<GridCell>, conditions: GrowingConditions, grazing: Int,
    ): SiteTurn {
        val residents = households.sumOf { it.members }
        val demand = residents * 2
        val recharge = checkNotNull(environment.recharge)
        val well = village.structures.any { it.siteId == previous.id && it.kind == StructureKind.Well && it.complete }
        val wellSupply = if (well) residents * if (recharge >= 24) 2 else 1 else 0
        val bankStage = conditions.stage(checkNotNull(previous.welfare).bank)
        val dry = previous.welfare.collect(tick, residents, bankStage, wellSupply)
        val underway = village.underway(previous.id)

        // Labor follows needs: water first, then construction, then stocks, then the household's own trade.
        val tasks = households.associate { it.id to taskFor(it) }.toMutableMap()
        val crew = households.associate { it.id to it.members }.toMutableMap()
        var haulers = 0
        if (dry.unmet > 0) {
            val order = listOf(Trade.Forager, Trade.Woodcutter, Trade.Mason, Trade.Farmer)
            for (household in households.sortedWith(compareBy<Household> { order.indexOf(it.trade) }.thenBy { it.id })) {
                if (haulers * HaulWater >= dry.unmet || haulers >= MaximumHaulers) break
                val spare = minOf(crew.getValue(household.id), MaximumHaulers - haulers,
                    (dry.unmet - haulers * HaulWater + HaulWater - 1) / HaulWater)
                if (spare <= 0) continue
                haulers += spare
                crew[household.id] = crew.getValue(household.id) - spare
                if (crew.getValue(household.id) == 0) tasks[household.id] = VillageTask.HaulingWater
            }
        }
        val welfare = if (haulers == 0) dry else previous.welfare.collect(tick, residents, bankStage, wellSupply + haulers * HaulWater)
        var builders = 0
        val (woodCost, stoneCost) = underway?.let { cost(it, previous) } ?: (0 to 0)
        val affordable = underway != null && previous.wood >= woodCost && previous.stone >= stoneCost
        val starving = previous.food < demand
        if (underway != null && affordable && !starving) {
            val settling = households.firstOrNull { it.home == underway.id }
            val crewing = when {
                settling != null -> listOf(settling)
                underway.kind == StructureKind.Home -> households.filter { it.trade == Trade.Mason }.ifEmpty {
                    households.filter { it.trade == Trade.Woodcutter }.take(1)
                }
                else -> households.filter { it.trade == Trade.Mason || it.trade == Trade.Woodcutter }
            }
            for (household in crewing) {
                // Settlers keep one pair of hands on their new field while they raise the home.
                val available = if (settling != null) minOf(1, crew.getValue(household.id)) else crew.getValue(household.id)
                if (available <= 0) continue
                builders += available
                crew[household.id] = crew.getValue(household.id) - available
                tasks[household.id] = if (settling != null && underway.level == 1) VillageTask.Settling else VillageTask.Building
            }
        }
        val hungry = previous.food < demand * 2
        val needWood = !hungry && previous.wood < WoodReserve + woodCost * HomeSteps
        val needStone = !hungry && previous.stoneRemaining > 0 && previous.stone < StoneReserve + stoneCost * HomeSteps
        // Stalled work sends its own crew for the missing material, even outside their trade.
        val stalledCrew = if (underway == null || affordable) emptySet() else households.filter {
            it.home == underway.id || it.trade == Trade.Mason || it.trade == Trade.Woodcutter
        }.map { it.id }.toSet()
        val stalledTask = when {
            underway == null || affordable || hungry -> null
            previous.wood < woodCost -> VillageTask.Woodcutting
            previous.stoneRemaining > 0 -> VillageTask.Quarrying
            else -> null
        }
        val fieldRoom = village.fields(previous.id).size * FieldHands
        var farmers = 0
        var foragers = 0
        var woodcutters = 0
        var masons = 0
        for (household in households) {
            var hands = crew.getValue(household.id)
            if (hands <= 0) continue
            if (stalledTask != null && household.id in stalledCrew) {
                // One pair of hands fetches materials; a settler family keeps the rest on its field.
                val fetching = if (household.home == underway?.id) 1 else hands
                tasks[household.id] = stalledTask
                if (stalledTask == VillageTask.Woodcutting) woodcutters += fetching else masons += fetching
                hands -= fetching
                if (hands <= 0) continue
            }
            val task = when (household.trade) {
                Trade.Farmer -> VillageTask.Farming
                Trade.Forager -> VillageTask.Gathering
                Trade.Woodcutter -> if (needWood) VillageTask.Woodcutting else VillageTask.Farming
                Trade.Mason -> if (needStone) VillageTask.Quarrying else VillageTask.Farming
            }
            when (task) {
                VillageTask.Farming -> {
                    // Fields hold a limited number of hands; the rest of the family gathers.
                    val tilling = minOf(hands, (fieldRoom - farmers).coerceAtLeast(0))
                    farmers += tilling
                    foragers += hands - tilling
                    val shown = if (tilling > 0) VillageTask.Farming else VillageTask.Gathering
                    if (tasks[household.id] != stalledTask || household.id !in stalledCrew) tasks[household.id] = shown
                }
                VillageTask.Woodcutting -> woodcutters += hands
                VillageTask.Quarrying -> masons += hands
                else -> foragers += hands
            }
            if (task != VillageTask.Farming && (tasks[household.id] != stalledTask || household.id !in stalledCrew)) tasks[household.id] = task
        }

        // Fields: farmers spread over completed fields in id order. Storms flood low fields beside the water.
        val fields = village.fields(previous.id)
        var crops = 0
        var flooded = false
        for ((index, field) in fields.withIndex()) {
            val workers = farmers / fields.size + if (index < farmers % fields.size) 1 else 0
            val moisture = conditions.moisture(field.cell)
            var yield = workers * cropPerWorker(moisture)
            if (workers > 0 && environment.weather == Weather.Rain && seasonAt(tick) == Season.Wet && floodplain(field.cell)) {
                yield /= 2
                flooded = true
            }
            crops += yield
        }
        val grazed = minOf(grazing, crops / 2)
        crops -= grazed

        val local = patches.indices.filter { patches[it].cell.region() == previous.center.region() }
        val gathered = economy.harvest(patches, local, ForageKind.Meadow, foragers * 4 + farmers, StockLimit - previous.food + demand)
        val woodland = local.count { patches[it].kind == ForageKind.Browse && patches[it].biomass > patches[it].capacity / 4 }
        // Woodcutters coppice: half of every shrub stays for the deer and foxes that browse it.
        val wood = economy.harvest(patches, local, ForageKind.Browse, woodcutters * (2 + minOf(6, woodland / 2)),
            StockLimit - previous.wood, protectRoots = true, retainDivisor = 2)
        val quarried = minOf(previous.stoneRemaining, masons * 4, StockLimit - previous.stone)

        val availableFood = previous.food + crops + gathered
        val shortage = if (availableFood < demand) minOf(3, previous.shortage + 1) else 0
        // Surplus only counts while the harvest itself outpaces need, not while the village eats its stores.
        val surplus = if (availableFood - demand >= demand * 2 && crops + gathered - demand >= GrowthMargin) {
            minOf(3, previous.surplus + 1)
        } else 0
        var site = previous.copy(
            food = (availableFood - demand).coerceIn(0, StockLimit),
            wood = previous.wood + wood,
            stone = previous.stone + quarried,
            stoneRemaining = previous.stoneRemaining - quarried,
            shortage = shortage,
            surplus = surplus,
            labor = SettlementLabor(farmers, woodcutters, masons + builders),
            foodBalance = (crops + gathered - demand).coerceIn(-32, 64),
            welfare = welfare.copy(cropYield = crops.coerceAtMost(48)),
        )
        var next = village
        if (flooded && (next.lastFlood == null || tick - checkNotNull(next.lastFlood) >= DayLength)) {
            next = next.copy(lastFlood = tick).record(VillageEvent(tick, VillageEventKind.FieldFlooded, site.id))
        }

        // Construction consumes timber, stone and the builders' days.
        if (underway != null && builders > 0) {
            var steps = minOf(builders, underway.required - underway.progress)
            if (woodCost > 0) steps = minOf(steps, site.wood / woodCost)
            if (stoneCost > 0) steps = minOf(steps, site.stone / stoneCost)
            if (steps > 0) {
                site = site.copy(wood = site.wood - steps * woodCost, stone = site.stone - steps * stoneCost)
                val built = underway.copy(progress = underway.progress + steps)
                val finished = if (built.complete) built.copy(completedAt = tick) else built
                next = next.copy(structures = next.structures.map { if (it.id == underway.id) finished else it })
                if (finished.complete) {
                    next = next.record(VillageEvent(tick, VillageEventKind.WorkCompleted, site.id, finished.id, finished.level))
                    if (finished.kind == StructureKind.Clearing) next = clear(next, finished, patches, site).also { (_, timber) ->
                        site = site.copy(wood = minOf(StockLimit, site.wood + timber))
                    }.first
                }
            }
        }

        // Hardship: shared thirst or hunger weighs on every household; the most strained leaves first.
        val cause = when {
            severeThirst(welfare.unmet, residents) -> Hardship.Thirst
            availableFood < demand -> Hardship.Hunger
            else -> null
        }
        if (cause == Hardship.Thirst && !severeThirst(previous.welfare.unmet, previous.residents)) {
            next = next.record(VillageEvent(tick, VillageEventKind.Thirst, site.id, 0, welfare.unmet))
        }
        if (cause == Hardship.Hunger && previous.shortage == 0) {
            next = next.record(VillageEvent(tick, VillageEventKind.Hunger, site.id, 0, demand - availableFood))
        }
        var updated = next.households.map { household ->
            if (household.siteId != site.id || !household.active) household else household.copy(
                hardship = if (cause != null) minOf(LeaveHardship, household.hardship + 1) else maxOf(0, household.hardship - 1),
                task = tasks.getValue(household.id),
            )
        }
        val leaving = updated.filter { it.siteId == site.id && it.active && it.hardship >= LeaveHardship }
            .maxWithOrNull(compareBy<Household> { it.joinedAt }.thenBy { it.id })
        if (leaving != null && cause != null) {
            updated = updated.map { if (it.id == leaving.id) it.copy(leftAt = tick, leftBecause = cause, task = VillageTask.Idle) else it }
            next = next.copy(households = updated).record(
                VillageEvent(tick, VillageEventKind.HouseholdLeft, site.id, leaving.id, cause.code),
            )
        } else {
            next = next.copy(households = updated)
        }

        // Growth needs sustained surplus, a harvest that outpaces the village's needs, and room under a roof.
        if (site.surplus == 3 && cause == null) {
            site = site.copy(surplus = 0)
            next = grow(next, site, tick)
        }
        val active = next.active(site.id)
        site = site.copy(
            residents = active.sumOf { it.members },
            homes = next.structures.count { it.siteId == site.id && it.kind == StructureKind.Home && it.completedAt != null },
        )
        if (active.isEmpty()) site = site.copy(labor = SettlementLabor())
        return SiteTurn(site, next, gathered + crops, wood, grazed)
    }

    /** Fields beside standing water (lakes and marshes) flood when wet-season storms raise it. */
    private fun floodplain(field: GridCell): Boolean {
        val watershed = checkNotNull(terrain.watershed)
        for (dz in -2..2) for (dx in -2..2) {
            if (abs(dx) + abs(dz) > 2) continue
            val water = watershed.waterAt(field.x + dx, field.z + dz) ?: continue
            if (water.kind != WaterKind.Stream) return true
        }
        return false
    }

    private fun taskFor(household: Household) = when (household.trade) {
        Trade.Farmer -> VillageTask.Farming
        Trade.Forager -> VillageTask.Gathering
        Trade.Woodcutter -> VillageTask.Woodcutting
        Trade.Mason -> VillageTask.Quarrying
    }

    /** Timber and stone per construction step. Sites without a quarry raise timber homes. */
    private fun cost(structure: Structure, site: Settlement): Pair<Int, Int> = when (structure.kind) {
        StructureKind.Home -> if (site.stoneRemaining == 0 && site.stone < 3) 4 to 0 else 6 to 3
        StructureKind.Well -> 3 to 6
        StructureKind.Clearing, StructureKind.Field -> 0 to 0
    }

    private fun clear(
        village: VillageState, clearing: Structure, patches: MutableList<ForagePatch>, site: Settlement,
    ): Pair<VillageState, Int> {
        val targets = clearingTargets(site, patches, village, clearing.cell)
        var timber = 0
        for (patch in targets) {
            timber += patch.biomass / 8
            patches.removeAll { it.id == patch.id }
        }
        val structures = village.structures.map {
            if (it.id == clearing.id) it.copy(cell = targets.firstOrNull()?.cell ?: it.cell) else it
        }
        return village.copy(structures = structures, cleared = (village.cleared + targets.map { it.id }).sorted()) to timber
    }

    /** The natural browse nearest Hollowmere, excluding the Keeper's planted groves. */
    fun clearingTargets(
        site: Settlement, patches: List<ForagePatch>, village: VillageState, underway: GridCell? = null,
    ): List<ForagePatch> {
        val reserved = village.structures.map { it.cell }.filter { it != underway }.toSet() + site.lots + site.center
        return patches.asSequence()
            .filter { it.kind == ForageKind.Browse && !it.planted && it.cell !in reserved && economy.suitable(it.cell) }
            .filter { distance(it.cell, site.center) <= 8 }
            .sortedWith(compareBy<ForagePatch> { if (it.cell == underway) -1 else distance(it.cell, site.center) }.thenBy { it.id })
            .take(ClearedPatches).toList()
    }

    private fun grow(village: VillageState, site: Settlement, tick: Long): VillageState {
        val active = village.active(site.id)
        val residents = active.sumOf { it.members }
        val capacity = village.capacity(site.id)
        val homesInUse = active.map { it.home }.toSet()
        val freeHome = village.structures.firstOrNull {
            it.siteId == site.id && it.kind == StructureKind.Home && it.completedAt != null && it.id !in homesInUse
        }
        // Word of hardship travels: nobody moves into a village that a family recently left.
        val recentlyLeft = village.households.any { it.siteId == site.id && it.leftAt?.let { left -> tick - left < ArrivalCooldown } == true }
        if (freeHome != null && !recentlyLeft && village.households.size < MaximumHouseholds * 2) {
            val trade = neededTrade(active)
            val household = Household(village.nextId, names[village.households.size % names.size], site.id, freeHome.id, 2, trade, tick)
            return village.copy(households = village.households + household, nextId = village.nextId + 1)
                .record(VillageEvent(tick, VillageEventKind.HouseholdArrived, site.id, household.id, trade.code))
        }
        if (residents < capacity) {
            val roomy = active.filter { it.members < MaximumMembers && it.members < homeCapacity(village, it.home) }
                .minWithOrNull(compareBy<Household> { it.members }.thenBy { it.id })
            if (roomy != null) {
                return village.copy(households = village.households.map { if (it.id == roomy.id) it.copy(members = it.members + 1) else it })
                    .record(VillageEvent(tick, VillageEventKind.MemberBorn, site.id, roomy.id, roomy.members + 1))
            }
        }
        if (village.underway(site.id) != null) return village
        // Expansion follows the plan Hollowmere chose for its growth.
        return when {
            village.chosen(PlanOption.ClearWoods) && village.fields(site.id).size > 1 -> startHome(village, site, tick)
            village.chosen(PlanOption.BuildWithin) -> village.structures.firstOrNull {
                it.siteId == site.id && it.kind == StructureKind.Home && it.level == 1 && it.completedAt != null
            }?.let { home ->
                village.copy(structures = village.structures.map {
                    if (it.id == home.id) it.copy(level = 2, required = it.required + HomeSteps) else it
                }).record(VillageEvent(tick, VillageEventKind.WorkBegun, site.id, home.id, 2))
            } ?: village
            site.id != 1 -> startHome(village, site, tick)
            else -> village
        }
    }

    private fun homeCapacity(village: VillageState, homeId: Int): Int {
        val home = village.structures.firstOrNull { it.id == homeId } ?: return 0
        return if (home.level >= 2 && home.complete) UpgradedCapacity else HomeCapacity9
    }

    private fun startHome(village: VillageState, site: Settlement, tick: Long): VillageState {
        val used = village.structures.map { it.cell }.toSet()
        val lot = site.lots.firstOrNull { it !in used } ?: return village
        val home = Structure(village.nextId, site.id, StructureKind.Home, lot, tick, HomeSteps)
        return village.copy(structures = village.structures + home, nextId = village.nextId + 1)
            .record(VillageEvent(tick, VillageEventKind.WorkBegun, site.id, home.id, 1))
    }

    private fun neededTrade(active: List<Household>): Trade {
        val weights = mapOf(Trade.Farmer to 3, Trade.Forager to 1, Trade.Woodcutter to 1, Trade.Mason to 1)
        return Trade.entries.minWith(compareBy<Trade> { trade ->
            active.filter { it.trade == trade }.sumOf { it.members } * 12 / weights.getValue(trade)
        }.thenBy { it.code })
    }

    /** The village settles an unanswered petition from its own circumstances. */
    private fun decideOverdue(world: SettlementWorld, tick: Long): SettlementWorld {
        val village = checkNotNull(world.village)
        val petition = village.openPetition() ?: return world
        if (tick < petition.deadline) return world
        val site = world.sites.first { it.id == petition.siteId }
        val options = petition.options
        val choice = when (petition.kind) {
            PetitionKind.Water -> if (site.stone >= 12 || PlanOption.RiverFlats !in options) PlanOption.Well else PlanOption.RiverFlats
            // A hungry village wants farmland; a fed one prefers to keep its woods and build up.
            PetitionKind.Growth -> if (PlanOption.ClearWoods in options && (site.foodBalance < 0 || site.shortage > 0)) {
                PlanOption.ClearWoods
            } else PlanOption.BuildWithin
        }
        val petitions = village.petitions.map { if (it.id == petition.id) it.copy(choice = choice, decidedAt = tick) else it }
        return world.copy(village = village.copy(petitions = petitions).record(
            VillageEvent(tick, VillageEventKind.PlanChosen, petition.siteId, petition.id, choice.code),
        ))
    }

    /** Starts the work a decided petition calls for, once. */
    private fun applyPlans(
        world: SettlementWorld, tick: Long, patches: List<ForagePatch>, springs: List<GridCell>,
    ): SettlementWorld {
        var village = checkNotNull(world.village)
        var sites = world.sites
        for (petition in village.petitions) {
            val choice = petition.choice ?: continue
            val home = sites.first { it.id == petition.siteId }
            when (choice) {
                PlanOption.Well -> if (village.structures.none { it.kind == StructureKind.Well && it.siteId == home.id }) {
                    val cell = freeCellNear(home, village, patches, springs) ?: continue
                    val well = Structure(village.nextId, home.id, StructureKind.Well, cell, tick, WellSteps)
                    village = village.copy(structures = village.structures + well, nextId = village.nextId + 1)
                        .record(VillageEvent(tick, VillageEventKind.WorkBegun, home.id, well.id, 1))
                }
                PlanOption.ClearWoods -> if (village.structures.none { it.kind == StructureKind.Clearing && it.siteId == home.id }) {
                    val target = clearingTargets(home, patches, village).firstOrNull() ?: continue
                    val clearing = Structure(village.nextId, home.id, StructureKind.Clearing, target.cell, tick, ClearingSteps)
                    village = village.copy(structures = village.structures + clearing, nextId = village.nextId + 1)
                        .record(VillageEvent(tick, VillageEventKind.WorkBegun, home.id, clearing.id, 1))
                }
                PlanOption.RiverFlats -> if (sites.size == 1) {
                    val settlers = village.active(home.id).filter { it.trade == Trade.Farmer }
                        .maxWithOrNull(compareBy<Household> { it.members }.thenByDescending { it.id }) ?: continue
                    val blocked = village.structures.map { it.cell }
                    val hamlet = economy.found(2, tick, settlers.members, patches, springs, sites, VillageLots, blocked, LotSpacing) ?: continue
                    val bank = checkNotNull(island.drinkingBank(hamlet.center, springs))
                    val share = settlers.members
                    val total = home.residents.coerceAtLeast(1)
                    val food = home.food * share / total
                    val wood = home.wood * share / total
                    val stone = home.stone * share / total
                    val newHome = Structure(village.nextId, 2, StructureKind.Home, hamlet.lots.first(), tick, HomeSteps)
                    val field = Structure(village.nextId + 1, 2, StructureKind.Field, hamlet.field, tick, 1, 1, completedAt = tick)
                    val moved = village.households.map {
                        if (it.id == settlers.id) it.copy(siteId = 2, home = newHome.id, task = VillageTask.Settling, hardship = 0) else it
                    }
                    village = village.copy(households = moved, structures = village.structures + newHome + field, nextId = village.nextId + 2)
                        .record(VillageEvent(tick, VillageEventKind.HamletFounded, 2, settlers.id, settlers.members))
                    val remaining = moved.filter { it.siteId == home.id && it.active }
                    sites = listOf(
                        home.copy(food = home.food - food, wood = home.wood - wood, stone = home.stone - stone,
                            residents = remaining.sumOf { it.members }),
                        hamlet.copy(residents = settlers.members, homes = 0, food = food, wood = wood, stone = stone,
                            welfare = SettlementWelfare(bank, settlers.members * 2)),
                    )
                }
                PlanOption.BuildWithin -> Unit
            }
        }
        return world.copy(sites = sites, village = village)
    }

    private fun freeCellNear(site: Settlement, village: VillageState, patches: List<ForagePatch>, springs: List<GridCell>): GridCell? {
        val used = village.structures.map { it.cell }.toSet() + site.lots + site.center + springs + patches.map { it.cell }
        val candidates = buildList {
            for (dz in -3..3) for (dx in -3..3) {
                val cell = GridCell(site.center.x + dx, site.center.z + dz)
                if (cell !in used && economy.suitable(cell)) add(cell)
            }
        }
        return candidates.minWithOrNull(compareBy<GridCell> { distance(it, site.center) }.thenBy(::cellId))
    }

    /** Which plans the village can actually carry out right now. */
    fun petitionOptions(
        kind: PetitionKind, world: SettlementWorld, patches: List<ForagePatch>, springs: List<GridCell>,
    ): List<PlanOption> {
        val village = world.village ?: return emptyList()
        val site = world.sites.firstOrNull() ?: return emptyList()
        return when (kind) {
            PetitionKind.Water -> buildList {
                add(PlanOption.Well)
                val settlers = village.active(site.id).filter { it.trade == Trade.Farmer }
                if (world.sites.size == 1 && settlers.isNotEmpty() && village.active(site.id).size > 1 &&
                    economy.found(2, 0, 1, patches, springs, world.sites, VillageLots, village.structures.map { it.cell }, LotSpacing) != null
                ) add(PlanOption.RiverFlats)
            }
            PetitionKind.Growth -> buildList {
                if (clearingTargets(site, patches, village).isNotEmpty()) add(PlanOption.ClearWoods)
                add(PlanOption.BuildWithin)
            }
        }
    }

    private fun raisePetitions(
        sites: List<Settlement>, village: VillageState, tick: Long, patches: List<ForagePatch>, springs: List<GridCell>,
    ): VillageState {
        if (village.openPetition() != null) return village
        val site = sites.first()
        val active = village.active(site.id)
        if (active.isEmpty()) return village
        val raised = village.petitions.map { it.kind }.toSet()
        val world = SettlementWorld(sites, village = village)
        val kind = when {
            PetitionKind.Water !in raised && village.thirstTurns >= ThirstPetitionTurns -> PetitionKind.Water
            PetitionKind.Growth !in raised && active.sumOf { it.members } >= village.capacity(site.id) &&
                (site.surplus >= 2 || site.foodBalance < 0) &&
                village.structures.none { it.siteId == site.id && !it.complete } -> PetitionKind.Growth
            else -> return village
        }
        val options = petitionOptions(kind, world, patches, springs)
        if (options.size < 2) return village
        val petition = Petition(village.nextId, kind, site.id, tick, tick + PetitionTurns * SettlementTurn, options)
        return village.copy(petitions = village.petitions + petition, nextId = village.nextId + 1)
            .record(VillageEvent(tick, VillageEventKind.PetitionRaised, site.id, petition.id, kind.code))
    }

    /** Saved-state invariants. Stronger than the simulation needs, so corrupt or edited saves are rejected. */
    fun validate(world: SettlementWorld, tick: Long, patches: List<ForagePatch>, springs: List<GridCell>, baseline: List<ForagePatch>) {
        val village = checkNotNull(world.village)
        require(world.events.isEmpty() && world.gatheredFood >= 0 && world.gatheredWood >= 0)
        require(world.sites.size in 0..2 && world.sites.withIndex().all { (index, site) -> site.id == index + 1 })
        if (world.sites.isEmpty()) {
            require(village == VillageState())
            return
        }
        val ids = village.households.map { it.id } + village.structures.map { it.id } + village.petitions.map { it.id }
        require(ids.all { it in 1 until village.nextId } && ids.size == ids.distinct().size)
        require(village.households.zipWithNext().all { (a, b) -> a.id < b.id })
        require(village.structures.zipWithNext().all { (a, b) -> a.id < b.id })
        require(village.petitions.zipWithNext().all { (a, b) -> a.id < b.id && a.raisedAt <= b.raisedAt })
        require(village.households.size <= MaximumHouseholds * 2 && village.structures.size <= 24 && village.petitions.size <= 2)
        require(village.thirstTurns in 0..16 && village.grazing in 0..4096 && village.chronicle.size <= ChronicleLimit)
        require(village.lastGrazed?.let { it <= tick } != false && village.lastFlood?.let { it <= tick } != false)
        val cells = village.structures.map { it.cell }
        require(cells.size == cells.distinct().size && cells.all(economy::suitable) && cells.none { it in springs })
        val patchCells = patches.map { it.cell }.toSet()
        val baselineIds = baseline.map { it.id }.toSet()
        require(village.cleared.zipWithNext().all { (a, b) -> a < b } && village.cleared.all { it in baselineIds })
        require(village.cleared.size <= ClearedPatches)
        for (structure in village.structures) {
            require(world.sites.any { it.id == structure.siteId } && structure.startedAt in SettlementTurn..tick)
            require(structure.required in 1..HomeSteps * 2 && structure.progress in 0..structure.required && structure.level in 1..2)
            require(structure.completedAt?.let { it in structure.startedAt..tick } != false)
            require((structure.completedAt != null) == (structure.complete || structure.level == 2))
            require(structure.level == 1 || structure.kind == StructureKind.Home)
            val underway = structure.kind == StructureKind.Clearing && !structure.complete
            require(underway || structure.cell !in patchCells)
        }
        require(world.sites.all { site -> village.structures.count { !it.complete && it.siteId == site.id } <= 1 })
        for (household in village.households) {
            require(household.name in HouseholdNames.indices && household.members in 1..MaximumMembers)
            require(household.hardship in 0..LeaveHardship && household.joinedAt in SettlementTurn..tick)
            require((household.leftAt == null) == (household.leftBecause == null))
            require(household.leftAt?.let { it in household.joinedAt..tick } != false)
            val home = village.structures.firstOrNull { it.id == household.home }
            require(home != null && home.kind == StructureKind.Home && (!household.active || home.siteId == household.siteId))
        }
        val occupied = village.households.filter { it.active }.map { it.home }
        require(occupied.size == occupied.distinct().size)
        for (petition in village.petitions) {
            require(petition.deadline == petition.raisedAt + PetitionTurns * SettlementTurn && petition.raisedAt <= tick)
            require(petition.options.size == 2 && petition.options.all { it.petition == petition.kind })
            require(petition.choice == null || petition.choice in petition.options)
            require((petition.choice == null) == (petition.decidedAt == null))
            require(petition.decidedAt?.let { it in petition.raisedAt..minOf(tick, petition.deadline) } != false)
            require(!petition.byKeeper || petition.choice != null)
        }
        require(village.petitions.count { it.open } <= 1 && village.petitions.map { it.kind }.distinct().size == village.petitions.size)
        require(village.chronicle.zipWithNext().all { (a, b) -> a.tick <= b.tick } && village.chronicle.all { it.tick in SettlementTurn..tick })
        require(village.chronicle.all { event -> world.sites.any { it.id == event.siteId } })
        for (site in world.sites) {
            val active = village.active(site.id)
            require(site.residents == active.sumOf { it.members })
            require(site.homes == village.structures.count { it.siteId == site.id && it.kind == StructureKind.Home && it.completedAt != null })
            require(site.lots.size == VillageLots && site.foundedAt in SettlementTurn..tick && site.foundedAt % SettlementTurn == 0L)
            require(site.food in 0..StockLimit && site.wood in 0..StockLimit && site.stone in 0..StockLimit && site.stoneRemaining in 0..StockLimit)
            require(site.shortage in 0..3 && site.surplus in 0..3 && site.foodBalance in -32..64)
            val welfare = checkNotNull(site.welfare)
            require(welfare.bank == island.drinkingBank(site.center, springs) && welfare.water in 0..StockLimit &&
                welfare.unmet in 0..StockLimit && welfare.cropYield in 0..48)
        }
    }

    private fun distance(a: GridCell, b: GridCell) = abs(a.x - b.x) + abs(a.z - b.z)

    companion object {
        const val HomeSteps = 4
        const val WellSteps = 3
        const val ClearingSteps = 3
    }
}

/** Thirst that strains a household: more than two thirds of the village's daily need is missing. */
internal fun severeThirst(unmet: Int, residents: Int) = unmet * 3 > residents * 2

/** Keeps the founding and the most recent history when the chronicle is full. */
internal fun VillageState.record(event: VillageEvent): VillageState {
    if (chronicle.size < ChronicleLimit) return copy(chronicle = chronicle + event)
    val oldest = chronicle.indexOfFirst { it.kind != VillageEventKind.Founded }
    return copy(chronicle = chronicle.filterIndexed { index, _ -> index != oldest } + event)
}
