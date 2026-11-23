package id.branchworld.game.ecology

import id.branchworld.game.simulation.CallRainRegion
import id.branchworld.game.simulation.ClearPlant
import id.branchworld.game.simulation.PlaceCreature
import id.branchworld.game.simulation.PlacePlant
import id.branchworld.game.simulation.RemoveCreature
import id.branchworld.game.simulation.living
import id.branchworld.game.simulation.CreatureHeading
import id.branchworld.game.simulation.CutFirebreak
import id.branchworld.game.simulation.EndorsePlan
import id.branchworld.game.simulation.ProtectRegion
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
    private val terrain: TerrainMap,
    checkpoint: EcologyState? = null,
    replayInputs: List<WorldCommand> = emptyList(),
    rulesVersion: Int = checkpoint?.rulesVersion ?: EcologyRulesVersion,
    recordHistory: Boolean = true,
    /** The crafted story a new rules-v10 history begins. Ignored when resuming a checkpoint. */
    newChapter: Chapter? = null,
    /** How a new rules-v12 living world begins. Ignored when resuming a checkpoint. */
    newStart: WorldStart = WorldStart.Empty,
    /** Static habitat for [terrain]. Callers validating many states of one island on one thread may share it. */
    private val island: IslandEcology = IslandEcology(terrain),
) {
    private val wildlife by lazy { Wildlife(island, rulesVersion) }
    private val regional by lazy { RegionalEcology(island) }
    private val economy by lazy { SettlementEconomy(island) }
    private val ranges by lazy { island.baseline.let { SeasonalRanges(island, it.springs, it.patches) } }
    private val villageRules by lazy { VillageRules(island, economy) { if (rulesVersion >= RangeRulesVersion) ranges else null } }
    private val fireRules by lazy { FireRules(island) }
    private val lifeRules by lazy { LifeRules(island) }
    val story: Chapter? = if (rulesVersion >= LifeRulesVersion) null else chapterOf(rulesVersion, terrain.key.seed,
        checkpoint?.habitat ?: if (rulesVersion >= RangeRulesVersion) HabitatState(newChapter?.code ?: 0) else null)
    private val chapter = story != null
    private val drought = story == Chapter.FadingGrove
    private val ember = story == Chapter.EmberSeason
    private val openingRain = when {
        drought -> OpeningDryEnd
        ember -> EmberOpeningRain
        else -> 300L
    }
    /** Ember Season opens after wet rains: the marsh is full, but the woods will cure through the long dry spell. */
    private val openingRecharge = if (ember) EmberOpeningRecharge else 64
    private val rainSoils by lazy { List(RegionCount) { rainGround(terrain, it) } }
    private val inputs = WorldCommands<WorldCommand>(recordHistory)

    var state: EcologyState = checkpoint ?: island.initialState().let {
        when (rulesVersion) {
            LifeRulesVersion -> {
                // A living world: terrain, wild plants, weather and fire, but no village, story or founders.
                val land = it.copy(creatures = emptyList(), nextEntityId = 1,
                    environment = Environment.start(terrain.key.seed, watershed = true, firstRain = openingRain, recharge = openingRecharge),
                    shaping = ShapingState(), fire = FireState.start(terrain.key.seed), chronicle = WorldChronicle(),
                    wildlife = WildlifeHistory(), life = LifeState(newStart))
                if (newStart == WorldStart.Wild) lifeRules.seedWild(land) else land
            }
            FireRulesVersion, RangeRulesVersion, VillageRulesVersion, ShapingRulesVersion, WildlifeRulesVersion, WatershedRulesVersion -> {
                val opening = it.copy(environment = Environment.start(terrain.key.seed, watershed = true, firstRain = openingRain, recharge = openingRecharge),
                    settlements = SettlementWorld(village = if (rulesVersion >= VillageRulesVersion) VillageState() else null),
                    shaping = if (rulesVersion >= ShapingRulesVersion) ShapingState() else null,
                    habitat = if (rulesVersion >= RangeRulesVersion) HabitatState(story?.code ?: 0) else null,
                    fire = if (rulesVersion >= FireRulesVersion) FireState.start(terrain.key.seed) else null,
                    chronicle = if (rulesVersion >= FireRulesVersion) WorldChronicle() else null)
                if (rulesVersion >= WildlifeRulesVersion) wildlife.populate(opening) else opening
            }
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
        require((rulesVersion in WatershedRulesVersion..LifeRulesVersion) == (terrain.watershed != null))
        require(story != Chapter.LastCrossing || terrain.key.seed == CrossingSeed)
        require(story != Chapter.EmberSeason || terrain.key.seed == EmberSeed && rulesVersion >= FireRulesVersion)
        require((state.fire == null) == (state.chronicle == null) && (state.fire == null || state.habitat != null || state.life != null))
        require(state.life == null || state.settlements == null && state.habitat == null && state.fire != null && state.shaping != null)
        require(state.life != null || state.creatures.none {
            it.life != null || it.species == Species.Wolf || it.heading.canonicalId > CreatureHeading.NegativeZ.canonicalId ||
                it.action.canonicalId > CreatureAction.Chasing.canonicalId || (it.goal?.need?.canonicalId ?: 0) > Need.Meet.canonicalId
        })
        require(state.environment?.recharge?.let { it in 0..255 && (state.settlements != null || state.life != null) } != false)
        require(state.settlements == null || state.environment != null)
        require(state.wildlife == null || state.environment?.recharge != null)
        require(state.shaping == null || state.wildlife != null && (state.settlements != null || state.life != null))
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
        require(Species.entries.all { species -> state.creatures.count { it.species == species } <= species.populationLimit(rulesVersion) })
        if (state.life != null) {
            lifeRules.validate(state)
        } else if (state.wildlife == null) {
            require(state.creatures.all { it.species in LegacySpecies && it.wildlife == null &&
                it.action.canonicalId <= CreatureAction.Resting.canonicalId &&
                (it.goal?.need?.canonicalId ?: 0) <= Need.Roam.canonicalId })
        } else {
            val rabbits = state.creatures.filter { it.species == Species.Rabbit }.mapTo(mutableSetOf()) { it.id }
            require(state.creatures.all { creature ->
                val life = checkNotNull(creature.wildlife)
                life.bornAt in 0..state.tick && island.navigation.contains(life.home) &&
                    (if (creature.id <= WildlifeFounderCount) life.bornAt == 0L && creature.matureAt == 0L
                        else life.bornAt > 0 && creature.matureAt == later(life.bornAt, creature.species.maturityTicks())) &&
                    (if (creature.species == Species.Fox) life.huntUntil in 0..later(state.tick, HuntRecovery) &&
                        (life.prey == null || life.prey in rabbits && life.huntUntil > state.tick)
                    else life.huntUntil == 0L && life.prey == null)
            })
        }
        require(state.environment?.let { it.nextChange > state.tick || state.tick == Long.MAX_VALUE } != false)
        for (command in replayInputs) {
            require(validTarget(command) && inputs.submit(state.tick, command))
        }
    }

    fun submit(command: WorldCommand): Boolean =
        validTarget(command) && (command !is EndorsePlan || state.settlements?.let {
            villageRules.endorse(it, command.tick, command.petitionId, command.option)
        } == true) &&
            (command !is SetDestination || state.life == null && state.creatures.any { it.id == command.entityId && it.species == Species.Rabbit }) &&
            inputs.submit(state.tick, command)

    private fun validTarget(command: WorldCommand) = if (command.living != (state.life != null)) false else when (command) {
        is PlaceCreature -> island.navigation.contains(command.cell)
        is RemoveCreature -> command.entityId > 0
        is PlacePlant -> island.navigation.contains(command.cell)
        is ClearPlant -> island.navigation.contains(command.cell)
        is SetDestination -> command.entityId > 0 && island.navigation.contains(command.cell)
        is ReforestRegion -> state.environment != null && command.region in 0 until RegionCount
        is SupplementForage -> state.environment != null && state.shaping == null && command.region in 0 until RegionCount
        is CallRainRegion -> state.shaping != null && command.region in 0 until RegionCount
        is EndorsePlan -> state.settlements?.village != null && command.petitionId > 0
        is ProtectRegion -> state.habitat != null && command.region in 0 until RegionCount
        is CutFirebreak -> state.fire != null && command.region in 0 until RegionCount
    }

    fun acceptedCommands(): List<WorldCommand> = inputs.accepted()
    fun pendingCommands(): List<WorldCommand> = inputs.pending()
    fun inspectRegions(): List<RegionReadout> = if (state.environment == null) emptyList() else regional.inspect(state)

    /** Persistence checks invariants that are stronger than synthetic simulation fixtures require. */
    fun validateSavedState() {
        if (state.life != null) return validateLivingState()
        val baseline = island.baseline
        val ledger = state.ledger
        require(state.springs == baseline.springs)
        val initialNextId = if (state.wildlife == null) baseline.nextEntityId else WildlifeFounderCount + 1L
        require(state.nextEntityId >= initialNextId)
        require(ledger.births == state.nextEntityId - initialNextId)
        require(ledger.deaths == state.nextEntityId - 1 - state.creatures.size && ledger.deaths >= 0)
        require(ledger.consumed >= 0 && ledger.regrown >= 0 && ledger.introduced >= 0)
        state.wildlife?.validate(state, island)
        require(state.creatures.all { it.matureAt >= 0 && it.breedAfter >= 0 && it.depletedTicks in 0 until SurvivalTicks })
        val natural = state.patches.filterNot { it.planted }
        val cleared = state.settlements?.village?.cleared.orEmpty().toSet()
        require(natural.none { it.id in cleared })
        require(natural.map { it.copy(biomass = it.capacity) } == baseline.patches.filterNot { it.id in cleared })
        val planted = state.patches.filter { it.planted }
        require(planted.all { regional.validSapling(it, state.springs) })
        require(ledger.introduced >= planted.size * RegionalEcology.SaplingBiomass)
        val supply = ledger.introduced.toULong() + ledger.regrown.toULong()
        val required = ledger.consumed.toULong() + state.patches.sumOf { it.biomass }.toULong()
        val initial = baseline.patches.sumOf { it.biomass }.toULong()
        require(required >= initial && required - initial == supply)
        val environment = requireNotNull(state.environment)
        state.shaping?.validate(state.tick, chapter)
        if (state.tick < openingRain) {
            val opening = Environment.start(state.terrain.key.seed, terrain.watershed != null, openingRain, openingRecharge)
            val recharge = opening.recharge?.let { (it - 2 * (state.tick / 10).toInt()).coerceAtLeast(0) }
            require(environment == opening.copy(recharge = recharge))
        } else require(environment.nextChange - state.tick in 0..360)
        state.habitat?.let { habitat ->
            require(habitat.chapter == (story?.code ?: 0))
            require(habitat.refuges.size <= MaximumRefuges && habitat.refuges.map { it.region }.distinct().size == habitat.refuges.size)
            require(habitat.refuges.all { it.region in 0 until RegionCount && it.declaredAt in 1..state.tick })
            require(habitat.refuges.zipWithNext().all { (a, b) -> a.declaredAt <= b.declaredAt })
            with(habitat.herd) {
                require(bound in 0..2 && departedAt in 0..state.tick && crossings in 0..4096 && forced in 0..crossings + 1)
                require(arrivedAt?.let { it in departedAt..state.tick } != false && calves in 0..4096)
            }
            require(habitat.log.size <= RangeLogLimit && habitat.log.zipWithNext().all { (a, b) -> a.tick <= b.tick })
            require(habitat.log.all { it.tick in 1..state.tick && island.terrain.cellAt(it.cell.x, it.cell.z) != null })
        }
        val fire = state.fire
        if (fire != null) {
            fireRules.validate(fire, state.chronicle, state.tick, state.settlements?.village)
            checkNotNull(state.chronicle).validate(state.tick, island)
        } else {
            val village = state.settlements?.village
            require(village == null || village.petitions.none { it.kind == PetitionKind.Ember } &&
                village.structures.none { it.burnedAt != null } && village.households.none { it.leftBecause == Hardship.Fire })
        }
        state.settlements?.let {
            if (it.village != null) villageRules.validate(it, state.tick, state.patches, state.springs, baseline.patches)
            else economy.validate(it, state.tick, state.patches, state.springs, state.shaping != null)
            require(it.gatheredFood.toULong() + it.gatheredWood.toULong() <= ledger.consumed.toULong())
        }
    }

    /** Rules v12 persistence checks: the player's hand is accounted for in the ledger and the biomass balance. */
    private fun validateLivingState() {
        val baseline = island.baseline
        val ledger = state.ledger
        val life = checkNotNull(state.life)
        require(state.springs == baseline.springs)
        require(state.nextEntityId >= 1 && life.placed <= state.nextEntityId - 1)
        require(ledger.births == state.nextEntityId - 1 - life.placed)
        require(ledger.deaths >= 0 && ledger.deaths + life.removed == state.nextEntityId - 1 - state.creatures.size)
        require(ledger.consumed >= 0 && ledger.regrown >= 0 && ledger.introduced >= 0)
        checkNotNull(state.wildlife).validate(state, island)
        require(state.creatures.all { it.matureAt >= 0 && it.breedAfter >= 0 && it.depletedTicks in 0 until SurvivalTicks })
        val natural = state.patches.filterNot { it.planted }
        require(natural.map { it.copy(biomass = it.capacity) } == baseline.patches)
        val planted = state.patches.filter { it.planted }
        require(planted.size == life.planted - life.cleared && planted.size <= MaxPlanted)
        require(planted.all { lifeRules.validPlanted(it, state.springs) })
        val supply = ledger.introduced.toULong() + ledger.regrown.toULong()
        val required = ledger.consumed.toULong() + state.patches.sumOf { it.biomass }.toULong()
        val initial = baseline.patches.sumOf { it.biomass }.toULong()
        require(required >= initial && required - initial == supply)
        val environment = requireNotNull(state.environment)
        checkNotNull(state.shaping).validate(state.tick, false)
        if (state.tick < openingRain) {
            val opening = Environment.start(state.terrain.key.seed, true, openingRain, openingRecharge)
            val recharge = opening.recharge?.let { (it - 2 * (state.tick / 10).toInt()).coerceAtLeast(0) }
            require(environment == opening.copy(recharge = recharge))
        } else require(environment.nextChange - state.tick in 0..360)
        fireRules.validate(checkNotNull(state.fire), state.chronicle, state.tick, null)
        checkNotNull(state.chronicle).validate(state.tick, island, living = true)
    }

    private fun shapingBlock(kind: Intervention, region: Int, shaping: ShapingState, weather: Weather, sites: List<GridCell>): ShapeBlock? = when {
        chapter && shaping.warningAt == null -> ShapeBlock.AwaitWarning
        kind == Intervention.Protect && state.habitat?.protects(region) == true -> ShapeBlock.AlreadyProtected
        shaping.spent -> ShapeBlock.Spent
        kind == Intervention.Rain && weather == Weather.Rain -> ShapeBlock.AlreadyRaining
        kind == Intervention.Rain && !rainSoils[region].available -> ShapeBlock.NoGround
        kind == Intervention.Reforest && sites.isEmpty() -> ShapeBlock.NoSites
        kind == Intervention.Firebreak && fireRules.firebreakTiles(region, checkNotNull(state.fire)).isEmpty() -> ShapeBlock.NoFuel
        else -> null
    }

    fun preview(kind: Intervention, region: Int): ShapePreview? {
        val shaping = state.shaping ?: return null
        if (region !in 0 until RegionCount || kind == Intervention.Supplement || kind == Intervention.Plan) return null
        if (kind == Intervention.Protect && state.habitat == null) return null
        if (kind == Intervention.Firebreak && state.fire == null) return null
        val village = state.settlements?.sites?.lastOrNull { it.residents > 0 }
        val sites = if (kind == Intervention.Reforest) regional.plantingSites(region, state.patches, state.springs,
            state.settlements?.reservedCells().orEmpty()) else emptyList()
        val field = village?.field?.takeIf { kind == Intervention.Rain && rainCovers(region, it) }
        val bank = village?.let { island.drinkingBank(it.center, state.springs) }
            ?.takeIf { kind == Intervention.Rain && rainCovers(region, it) }
        val blocker = shapingBlock(kind, region, shaping, checkNotNull(state.environment).weather, sites)
        val booked = pendingShape() != null
        val cut = state.fire?.takeIf { kind == Intervention.Firebreak }?.let { fire ->
            fireRules.firebreakTiles(region, fire).flatMap(fireRules::landIn)
        }.orEmpty()
        return ShapePreview(sites, field, bank, if (booked) ShapeBlock.Reserved else blocker, cut)
    }

    fun recommendedRainRegion(): Int {
        val village = state.settlements?.sites?.lastOrNull { it.residents > 0 }
        return village?.field?.region() ?: rainSoils.indexOfFirst { it.available }
    }

    fun recommendedGroveRegion(): Int =
        if (drought) 9 else inspectRegions().indexOfFirst { it.canPlant }.coerceAtLeast(0)

    /** The region holding the herd's wet-season pasture (rules v10). */
    fun pastureRegion(): Int? = if (state.habitat == null) null else ranges.wet.region()

    /** The herd's seasonal way, dry range first (rules v10). */
    fun herdRoute(): List<GridCell> = if (state.habitat == null) emptyList() else ranges.route

    /** The village's open petition and the plans it can carry out. */
    fun openPetition(): Petition? = state.settlements?.village?.openPetition()

    fun pendingEndorsement(): EndorsePlan? = inputs.pending().firstOrNull { it is EndorsePlan } as EndorsePlan?

    fun endorse(option: PlanOption): Boolean {
        val petition = openPetition() ?: return false
        if (option !in petition.options || pendingEndorsement() != null || state.tick >= petition.deadline) return false
        return inputs.submitNext(state.tick) { tick, sequence -> EndorsePlan(tick, sequence, petition.id, option) }
    }

    fun cancelEndorsement(): Boolean = pendingEndorsement()?.let(inputs::cancel) ?: false

    fun pendingShape(): WorldCommand? = if (state.shaping == null) null else
        inputs.pending().firstOrNull { it is CallRainRegion || it is ReforestRegion || it is ProtectRegion || it is CutFirebreak }

    fun cancelShape(): Boolean = pendingShape()?.let(inputs::cancel) ?: false

    fun intervene(kind: Intervention, region: Int): Boolean {
        if (kind == Intervention.Plan) return false
        if (state.shaping != null) {
            if (preview(kind, region)?.let { it.blocked == null } != true) return false
        } else {
            val availability = inspectRegions().getOrNull(region) ?: return false
            if (kind == Intervention.Rain || kind == Intervention.Reforest && !availability.canPlant ||
                kind == Intervention.Supplement && !availability.canSupplement
            ) return false
            if (kind == Intervention.Protect || kind == Intervention.Firebreak) return false
            val nextTick = state.tick + 1
            if (inputs.pending().any { command ->
                command.tick == nextTick && when (command) {
                    is ReforestRegion -> kind == Intervention.Reforest && command.region == region
                    is SupplementForage -> kind == Intervention.Supplement && command.region == region
                    is CallRainRegion, is SetDestination, is EndorsePlan, is ProtectRegion, is CutFirebreak,
                    is PlaceCreature, is RemoveCreature, is PlacePlant, is ClearPlant -> false
                }
            }) return false
        }
        return inputs.submitNext(state.tick) { tick, sequence ->
            when (kind) {
                Intervention.Reforest -> ReforestRegion(tick, sequence, region)
                Intervention.Supplement -> SupplementForage(tick, sequence, region)
                Intervention.Rain -> CallRainRegion(tick, sequence, region)
                Intervention.Protect -> ProtectRegion(tick, sequence, region)
                Intervention.Firebreak -> CutFirebreak(tick, sequence, region)
                Intervention.Plan -> error("Plans are endorsed through a petition")
            }
        }
    }

    // ----- rules v12: the player's hand in a living world -----

    private fun queuedArrivals(): List<PlaceCreature> = inputs.pending().filterIsInstance<PlaceCreature>()
    private fun queuedPlants(): List<PlacePlant> = inputs.pending().filterIsInstance<PlacePlant>()

    /** Whether [species] could be set down on [cell] now, counting placements already queued. */
    fun creatureCheck(species: Species, cell: GridCell): PlacementCheck =
        lifeRules.checkCreature(state, species, cell, queuedArrivals().map { it.species })

    fun plantCheck(kind: PlantKind, cell: GridCell): PlacementCheck =
        lifeRules.checkPlant(state, kind, cell, queuedPlants().map { it.cell })

    fun place(species: Species, cell: GridCell): Boolean = creatureCheck(species, cell) == PlacementCheck.Ok &&
        inputs.submitNext(state.tick) { tick, sequence -> PlaceCreature(tick, sequence, species, cell) }

    fun plant(kind: PlantKind, cell: GridCell): Boolean = plantCheck(kind, cell) == PlacementCheck.Ok &&
        inputs.submitNext(state.tick) { tick, sequence -> PlacePlant(tick, sequence, kind, cell) }

    fun remove(id: Long): Boolean = state.life != null && state.creatures.any { it.id == id } &&
        inputs.pending().none { it is RemoveCreature && it.entityId == id } &&
        inputs.submitNext(state.tick) { tick, sequence -> RemoveCreature(tick, sequence, id) }

    fun clearPlant(cell: GridCell): Boolean = state.life != null && state.patches.any { it.planted && it.cell == cell } &&
        inputs.pending().none { it is ClearPlant && it.cell == cell } &&
        inputs.submitNext(state.tick) { tick, sequence -> ClearPlant(tick, sequence, cell) }

    fun steeringTarget(left: Boolean): GridCell? = if (left) island.navigation.west else island.navigation.east

    fun steer(left: Boolean) {
        queueSteering(left)
    }

    fun queueSteering(left: Boolean): Boolean {
        if (state.life != null) return false
        val rabbit = state.creatures.firstOrNull { it.species == Species.Rabbit } ?: return false
        val target = steeringTarget(left) ?: return false
        return inputs.submitNext(state.tick) { tick, sequence -> SetDestination(tick, sequence, rabbit.id, target) }
    }

    fun advance(): EcologyState {
        val previous = state
        check(previous.tick < Long.MAX_VALUE) { "World tick limit reached" }
        val tick = previous.tick + 1
        val environment = previous.environment?.at(tick)
        var shaping = previous.shaping?.advance(tick, checkNotNull(environment), rainSoils)
        val reserved = previous.settlements?.reservedCells().orEmpty()
        val waypoints = mutableMapOf<Long, GridCell>()
        val random = SplitMix64(previous.randomState)
        val patches = previous.patches.toMutableList()
        appliedCommands = inputs.at(tick)
        var introduced = 0
        var endorsed = previous.settlements
        var habitat = previous.habitat
        var fire = previous.fire
        var chronicle = previous.chronicle
        var felledBiomass = 0
        // Rules v11 records each of the Keeper's acts so later events can name them as causes.
        fun keeperAct(act: KeeperAct, subject: Int, cell: GridCell): EventRef? = chronicle?.let { log ->
            log.record(tick, WorldEventKind.KeeperAct, cell, subject, act.code).also { chronicle = it.first }.second
        }
        // Rules v12: what the player set down, lifted out, planted or cleared on this tick.
        val removals = mutableListOf<Long>()
        val arrivals = mutableListOf<Arrival>()
        var lifeState = previous.life
        val acted = mutableSetOf<Pair<KeeperAct, Int>>()
        fun livingAct(act: KeeperAct, subject: Int, cell: GridCell) {
            if (acted.add(act to subject)) keeperAct(act, subject, cell)
        }
        val results = buildList {
            for (command in appliedCommands) {
                when (command) {
                    is PlaceCreature -> {
                        val queued = arrivals.map { it.species }
                        val gone = previous.copy(creatures = previous.creatures.filter { it.id !in removals })
                        if (lifeRules.checkCreature(gone, command.species, command.cell, queued) == PlacementCheck.Ok) {
                            arrivals.add(Arrival(command.species, command.cell))
                            livingAct(KeeperAct.Place, command.species.canonicalId, command.cell)
                        }
                    }
                    is RemoveCreature -> {
                        val animal = previous.creatures.firstOrNull { it.id == command.entityId }
                        if (animal != null && command.entityId !in removals) {
                            removals.add(command.entityId)
                            livingAct(KeeperAct.Remove, animal.species.canonicalId, animal.cell)
                        }
                    }
                    is PlacePlant -> {
                        val current = checkNotNull(lifeState)
                        val planted = previous.copy(patches = patches)
                        if (lifeRules.checkPlant(planted, command.kind, command.cell, emptyList()) == PlacementCheck.Ok) {
                            val patch = lifeRules.plantPatch(command.kind, command.cell)
                            patches.add(patch)
                            patches.sortBy(ForagePatch::id)
                            introduced += patch.biomass
                            lifeState = current.copy(planted = current.planted + 1)
                            livingAct(KeeperAct.Plant, command.kind.code, command.cell)
                        }
                    }
                    is ClearPlant -> {
                        val current = checkNotNull(lifeState)
                        val index = patches.indexOfFirst { it.planted && it.cell == command.cell }
                        if (index >= 0) {
                            felledBiomass += patches[index].biomass
                            val kind = if (patches[index].kind == ForageKind.Browse) PlantKind.Tree else PlantKind.Meadow
                            patches.removeAt(index)
                            lifeState = current.copy(cleared = current.cleared + 1)
                            livingAct(KeeperAct.Clear, kind.code, command.cell)
                        }
                    }
                    is EndorsePlan -> {
                        val before = endorsed
                        endorsed = endorsed?.let { villageRules.applyEndorsement(it, tick, command.petitionId, command.option) }
                        if (endorsed != before) keeperAct(KeeperAct.Endorse, command.option.code,
                            endorsed?.sites?.firstOrNull()?.center ?: regionCenter(0))
                    }
                    is SetDestination -> waypoints[command.entityId] = command.cell
                    is ReforestRegion -> {
                        val current = shaping
                        val count = if (current == null || shapingBlock(Intervention.Reforest, command.region, current,
                                checkNotNull(environment).weather,
                                regional.plantingSites(command.region, patches, previous.springs, reserved)) == null
                        ) regional.plant(command.region, patches, previous.springs, reserved) else 0
                        if (count > 0 && current != null) shaping = current.copy(spent = true)
                        if (count > 0) keeperAct(KeeperAct.Grove, command.region, regionCenter(command.region))
                        introduced += count * RegionalEcology.SaplingBiomass
                        add(InterventionResult(Intervention.Reforest, command.region, tick, count))
                    }
                    is CallRainRegion -> {
                        val current = checkNotNull(shaping)
                        val applied = shapingBlock(Intervention.Rain, command.region, current,
                            checkNotNull(environment).weather, emptyList()) == null
                        if (applied) {
                            shaping = current.copy(spent = true, shower = CalledShower(command.region, tick))
                            keeperAct(KeeperAct.Rain, command.region, regionCenter(command.region))
                        }
                        add(InterventionResult(Intervention.Rain, command.region, tick, if (applied) 1 else 0))
                    }
                    is ProtectRegion -> {
                        val current = checkNotNull(shaping)
                        val refuge = checkNotNull(habitat)
                        val applied = refuge.refuges.size < MaximumRefuges && shapingBlock(Intervention.Protect, command.region, current,
                            checkNotNull(environment).weather, emptyList()) == null
                        if (applied) {
                            shaping = current.copy(spent = true)
                            habitat = refuge.copy(refuges = refuge.refuges + Refuge(command.region, tick))
                                .record(RangeEvent(tick, RangeEventKind.RefugeDeclared, command.region, regionCenter(command.region)))
                            keeperAct(KeeperAct.Protect, command.region, regionCenter(command.region))
                        }
                        add(InterventionResult(Intervention.Protect, command.region, tick, if (applied) 1 else 0))
                    }
                    is CutFirebreak -> {
                        val current = checkNotNull(shaping)
                        val woods = checkNotNull(fire)
                        val tiles = if (shapingBlock(Intervention.Firebreak, command.region, current,
                                checkNotNull(environment).weather, emptyList()) == null) fireRules.firebreakTiles(command.region, woods)
                            else emptyList()
                        if (tiles.isNotEmpty()) {
                            val standing = patches.sumOf { it.biomass }
                            val cut = fireRules.cut(tiles, tick, woods, patches, fireRules.patchIndex(patches), null)
                            felledBiomass += standing - patches.sumOf { it.biomass }
                            shaping = current.copy(spent = true)
                            val act = checkNotNull(keeperAct(KeeperAct.Firebreak, command.region, tileCell(tiles[tiles.size / 2])))
                            chronicle = chronicle?.record(tick, WorldEventKind.TreesFelled, tileCell(tiles[tiles.size / 2]), cut.pines,
                                tiles.size, listOf(act))?.first
                            fire = cut.fire.copy(scars = cut.fire.scars.map { if (it.cause == -1) it.copy(cause = act.id) else it })
                            endorsed = endorsed?.giveWood(cut.pines * FelledWood)
                        }
                        add(InterventionResult(Intervention.Firebreak, command.region, tick, tiles.size))
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
        val conditions = shaping?.let { GrowingConditions(terrain, checkNotNull(environment?.recharge), it.pools, patches) }
        val before = patches.sumOf { it.biomass }
        if (tick % 10L == 0L) {
            val multiplier = if (environment?.weather == Weather.Rain) 2 else 1
            for ((index, patch) in patches.withIndex()) {
                if (patch.biomass < patch.capacity) {
                    val recharge = environment?.recharge
                    val growth = if (recharge == null) patch.regrowth * multiplier else {
                        val soil = conditions?.moisture(patch.cell) ?: terrain.soilMoisture(patch.cell, recharge)
                        val establishment = if (conditions != null && patch.planted && !patch.rooted() && soil >= 100) 2 else 0
                        val base = growthForSoil(soil, patch.regrowth) + establishment
                        // Rules v11: nothing grows in fresh ash; grass returns to burned ground in a rich flush.
                        fire?.let { base * fireRules.growthFactor(it, patch.cell, tick) } ?: base
                    }
                    patches[index] = patch.copy(biomass = minOf(patch.capacity, patch.biomass + growth))
                }
            }
        }
        val afterGrowth = patches.sumOf { it.biomass }
        if (fire != null && tick % FireStep == 0L) {
            val outcome = fireRules.step(tick, fire, checkNotNull(chronicle), checkNotNull(environment), checkNotNull(shaping), patches, endorsed)
            fire = outcome.fire
            chronicle = outcome.chronicle
            endorsed = outcome.settlements
        }
        if (lifeState != null) {
            val view = fire?.let { fireView(it, tick) }
            val turn = lifeRules.advance(previous.copy(life = lifeState), tick, checkNotNull(environment?.recharge), patches, view,
                removals, arrivals, checkNotNull(chronicle))
            val ledger = previous.ledger
            return previous.copy(
                tick = tick,
                patches = patches,
                creatures = turn.animals,
                nextEntityId = turn.nextId,
                environment = environment,
                wildlife = turn.history,
                shaping = shaping,
                fire = fire,
                chronicle = turn.chronicle,
                life = turn.life,
                ledger = EcologyLedger(
                    consumed = ledger.consumed + afterGrowth - patches.sumOf { it.biomass } + felledBiomass,
                    regrown = ledger.regrown + afterGrowth - before,
                    births = ledger.births + turn.born,
                    deaths = ledger.deaths + turn.died,
                    introduced = ledger.introduced + introduced,
                ),
            ).also { state = it }
        }
        val range = habitat?.let { RangeTurn(ranges, endorsed?.disturbedCells(island).orEmpty(), it) }
        val view = fire?.let { fireView(it, tick) }
        val (animals, nextId, history) = if (previous.wildlife == null) {
            advanceLegacy(previous, tick, patches, waypoints, random)
        } else {
            wildlife.advance(previous, tick, checkNotNull(environment?.recharge), patches, waypoints, random, conditions, range, view)
        }
        if (fire != null && wildlife.fledFromFire.isNotEmpty()) {
            val activeFires = fire.active.map { active ->
                val fled = wildlife.fledFromFire[active.number]
                if (active.fled || fled == null) return@map active
                val started = chronicle?.find(active.started)?.ref ?: EventRef(active.started, active.startedAt, WorldEventKind.FireStarted)
                chronicle = chronicle?.record(tick, WorldEventKind.AnimalsFled, fled.cell, (fled.id and Int.MAX_VALUE.toLong()).toInt(),
                    fled.species.canonicalId, listOf(started))?.first
                active.copy(fled = true)
            }
            fire = fire.copy(active = activeFires)
        }
        if (range != null) {
            // A herd pushing through a settlement tramples its fields as surely as grazing beside them.
            if (range.habitat.log.lastOrNull()?.let { it.tick == tick && it.kind == RangeEventKind.HerdPushedThrough } == true) {
                endorsed = endorsed?.let { world ->
                    world.village?.let { world.copy(village = it.copy(grazing = minOf(it.grazing + TrampledGrazing, 4096))) } ?: world
                }
            }
            habitat = range.habitat
        }
        val births = nextId - previous.nextEntityId
        val deaths = previous.creatures.size - animals.size + births
        val ledger = previous.ledger
        var settlements = endorsed?.let {
            if (it.village != null) {
                val grazed = villageRules.graze(it, animals)
                villageRules.advance(grazed, tick, checkNotNull(environment), patches, previous.springs, checkNotNull(conditions),
                    habitat, story)
            } else if (tick % SettlementTurn == 0L) economy.advance(
                it, tick, requireNotNull(environment).weather, patches, previous.springs, environment.recharge, conditions,
            )
            else it
        }
        val current = shaping
        // Ember Season: a dry storm gathers once the woods have cured, and the village asks what to do before it breaks.
        if (ember && current?.warningAt == null && tick % SettlementTurn == 0L && tick >= EmberEarliestWarning &&
            settlements?.village?.let { it.openPetition() == null && it.active(1).isNotEmpty() } == true &&
            fireRules.cure(tick, checkNotNull(fire), checkNotNull(environment)) >= 100
        ) {
            val log = checkNotNull(chronicle)
            val center = settlements.sites.first().center
            val planned = fireRules.planStorm(tick, checkNotNull(fire), patches, checkNotNull(settlements.village), center, log.nextId)
            val strike = planned.strikes.firstOrNull()
            if (strike != null) {
                chronicle = log.record(tick, WorldEventKind.DryStorm, tileCell(strike.tile), strike.tile, planned.wind).first
                fire = planned
                shaping = checkNotNull(current).copy(warningAt = tick)
                settlements = settlements.copy(village = checkNotNull(settlements.village).raiseEmberPetition(1, tick))
            }
        }
        if (drought && current?.warningAt == null && tick % SettlementTurn == 0L &&
            checkNotNull(environment?.recharge) <= 16 && settlements?.sites?.firstOrNull()?.residents?.let { it > 0 } == true
        ) shaping = checkNotNull(current).copy(warningAt = tick)
        // The Last Crossing: the warning is the village asking to settle the herd's pasture.
        if (story == Chapter.LastCrossing && current?.warningAt == null && tick % SettlementTurn == 0L &&
            settlements?.village?.openPetition()?.options?.contains(PlanOption.Pasture) == true
        ) shaping = checkNotNull(current).copy(warningAt = tick)
        if (chronicle != null) {
            val log = recordVillageTurns(chronicle!!, previous.settlements, settlements, tick)
            chronicle = log
        }
        return previous.copy(
            tick = tick,
            randomState = random.state,
            patches = patches,
            creatures = animals,
            nextEntityId = nextId,
            environment = environment,
            settlements = settlements,
            wildlife = history,
            shaping = shaping,
            habitat = habitat,
            fire = fire,
            chronicle = chronicle,
            ledger = EcologyLedger(
                consumed = ledger.consumed + afterGrowth - patches.sumOf { it.biomass } + felledBiomass,
                regrown = ledger.regrown + afterGrowth - before,
                births = ledger.births + births,
                deaths = ledger.deaths + deaths,
                introduced = ledger.introduced + introduced,
            ),
        ).also { state = it }
    }

    private var bareSource: List<Scar>? = null
    private var bareEpoch = -1L
    private var bare: Set<GridCell> = emptySet()
    private var burningSource: List<BurningTile>? = null
    private val fireTiles = IntArray(TileCount)
    private val emptyFireTiles = IntArray(0)

    /** What wildlife sees of the fire this tick. Bare ground is rebuilt only when scars or their stages change. */
    private fun fireView(fire: FireState, tick: Long): FireView {
        val epoch = tick / FireStep
        if (fire.scars !== bareSource || epoch != bareEpoch) {
            bare = fireRules.bareCells(fire, tick)
            bareSource = fire.scars
            bareEpoch = epoch
        }
        if (fire.burning !== burningSource) {
            fireTiles.fill(0)
            for (tile in fire.burning) fireTiles[tile.tile] = tile.fire
            burningSource = fire.burning
        }
        return FireView(if (fire.burning.isEmpty()) emptyFireTiles else fireTiles, bare)
    }

    /** Rules v11 chronicle entries for village changes the fire rules did not record themselves. */
    private fun recordVillageTurns(log: WorldChronicle, before: SettlementWorld?, after: SettlementWorld?, tick: Long): WorldChronicle {
        val village = after?.village ?: return log
        var chronicle = log
        val earlier = before?.village
        for (petition in village.petitions) {
            if (petition.kind != PetitionKind.Ember || petition.choice == null) continue
            if (earlier?.petitions?.firstOrNull { it.id == petition.id }?.choice != null) continue
            val storm = chronicle.events.lastOrNull { it.kind == WorldEventKind.DryStorm }?.ref
            val keeper = if (petition.byKeeper) chronicle.lastAct(KeeperAct.Endorse) else null
            chronicle = chronicle.record(tick, WorldEventKind.PlanChosen, after.sites.first().center, petition.id,
                checkNotNull(petition.choice).code, listOfNotNull(storm, keeper)).first
        }
        for (structure in village.structures) {
            if (structure.burnedAt == null || structure.completedAt != tick) continue
            val burned = chronicle.events.lastOrNull { it.kind == WorldEventKind.HomeBurned && it.subject == structure.id }?.ref
            chronicle = chronicle.record(tick, WorldEventKind.HomeRebuilt, structure.cell, structure.id, structure.siteId, listOfNotNull(burned)).first
        }
        return chronicle
    }

    private fun advanceLegacy(
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

internal data class CreatureTurn(
    val animals: List<Creature>,
    val nextId: Long,
    val history: WildlifeHistory? = null,
)
