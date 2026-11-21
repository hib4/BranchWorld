package id.branchworld.game.ecology

import id.branchworld.game.simulation.GridCell
import id.branchworld.game.terrain.Biome
import id.branchworld.game.terrain.TerrainMap
import kotlin.math.abs

/** One active cohort. The second site is its last opportunity to relocate. */
internal class SettlementEconomy(private val island: IslandEcology) {
    private val terrain = island.terrain
    private val plots = island.reachable.toHashSet()
    private val uplands = island.reachable.filter { biome(it) == Biome.RockyUpland }

    fun advance(
        state: SettlementWorld,
        tick: Long,
        weather: Weather,
        patches: MutableList<ForagePatch>,
        springs: List<GridCell>,
        recharge: Int? = null,
        conditions: GrowingConditions? = null,
    ): SettlementWorld {
        if (tick % SettlementTurn != 0L) return state
        if (state.sites.isEmpty()) {
            val site = found(1, tick, 4, patches, springs, emptyList()) ?: return state
            val opening = site.withWater(springs, conditions)
            return state.copy(sites = listOf(opening), events = listOf(opening.event(tick, SettlementEventKind.Founded, 4)))
        }
        val previous = state.sites.last()
        if (previous.residents == 0) return state
        val local = patches.indices.filter { patches[it].cell.region() == previous.center.region() }
        val demand = previous.residents * 2
        val farmers = if (previous.food < demand) (previous.residents * 3 + 3) / 4 else (previous.residents + 1) / 2
        val lumberers = (previous.residents - farmers + 1) / 2
        val miners = previous.residents - farmers - lumberers
        val welfare = previous.welfare?.let { it.collect(tick, previous.residents, checkNotNull(conditions).stage(it.bank)) }
        val fieldWorkers = if ((welfare?.unmet ?: 0) > 0) (farmers - 1).coerceAtLeast(0) else farmers
        val labor = SettlementLabor(fieldWorkers, lumberers, miners)
        val harvest = harvest(patches, local, ForageKind.Meadow, farmers * 2, StockLimit - previous.food + demand)
        val woodland = local.count { patches[it].kind == ForageKind.Browse && patches[it].biomass > patches[it].capacity / 4 }
        val gatheredWood = harvest(patches, local, ForageKind.Browse, lumberers * (2 + minOf(6, woodland / 2)), StockLimit - previous.wood, protectRoots = conditions != null)
        val quarried = minOf(previous.stoneRemaining, miners * 4, StockLimit - previous.stone)
        val produced = if (conditions == null) cropYield(previous.field, fieldWorkers, weather, recharge)
            else fieldWorkers * cropPerWorker(conditions.moisture(previous.field))
        val availableFood = previous.food + produced + harvest
        val shortage = if (availableFood < demand) minOf(3, previous.shortage + 1) else 0
        val surplus = if (availableFood - demand >= demand * 2) minOf(3, previous.surplus + 1) else 0
        var updated = previous.copy(
            food = (availableFood - demand).coerceIn(0, StockLimit),
            wood = previous.wood + gatheredWood,
            stone = previous.stone + quarried,
            stoneRemaining = previous.stoneRemaining - quarried,
            shortage = shortage,
            surplus = surplus,
            labor = labor,
            foodBalance = produced + harvest - demand,
            welfare = welfare?.copy(cropYield = produced),
        )
        val events = mutableListOf<SettlementEvent>()
        if (shortage > 0 && state.events.none { it.siteId == previous.id && it.kind == SettlementEventKind.Shortage }) {
            events.add(previous.event(tick, SettlementEventKind.Shortage, demand - availableFood))
        }
        if (updated.residents == updated.homes * HomeCapacity && updated.homes < MaximumHomes &&
            updated.wood >= 24 && updated.stone >= 12 && updated.food >= demand * 2
        ) {
            updated = updated.copy(homes = updated.homes + 1, wood = updated.wood - 24, stone = updated.stone - 12)
            events.add(updated.event(tick, SettlementEventKind.Expanded, updated.homes))
        }
        if (surplus == 3 && updated.residents < updated.homes * HomeCapacity) {
            updated = updated.copy(residents = updated.residents + 1, surplus = 0)
        }
        val sites = state.sites.dropLast(1).toMutableList()
        if (shortage == 3) {
            val next = if (previous.id == 1) {
                found(2, tick, updated.residents, patches, springs, state.sites)
            } else null
            if (next != null) {
                sites.add(updated.copy(residents = 0, food = 0, wood = 0, stone = 0, labor = SettlementLabor()))
                sites.add(next.withWater(springs, conditions).copy(food = updated.food, wood = updated.wood, stone = updated.stone))
                events.add(previous.event(tick, SettlementEventKind.Relocated, next.id))
                events.add(previous.event(tick, SettlementEventKind.Abandoned))
                events.add(next.event(tick, SettlementEventKind.Founded, next.residents))
            } else {
                updated = updated.copy(residents = updated.residents - 1)
                if (updated.residents == 0) {
                    updated = updated.copy(labor = SettlementLabor())
                    events.add(updated.event(tick, SettlementEventKind.Abandoned))
                }
                sites.add(updated)
            }
        } else {
            sites.add(updated)
        }
        return SettlementWorld(
            sites, if (events.isEmpty()) state.events else state.events + events,
            state.gatheredFood + harvest, state.gatheredWood + gatheredWood,
        )
    }

    private fun Settlement.withWater(springs: List<GridCell>, conditions: GrowingConditions?): Settlement =
        if (conditions == null) this else copy(welfare = SettlementWelfare(
            checkNotNull(island.drinkingBank(center, springs)), residents * 2,
        ))

    internal fun cropYield(field: GridCell, workers: Int, weather: Weather, recharge: Int? = null): Int {
        if (recharge != null) return workers * cropPerWorker(terrain.soilMoisture(field, recharge))
        val soil = if (terrain.terrainCell(field).moisture >= 120) 2 else 1
        return workers * (soil + if (weather == Weather.Rain) 1 else 0)
    }

    internal fun harvest(
        patches: MutableList<ForagePatch>, local: List<Int>, kind: ForageKind, capacity: Int, space: Int,
        protectRoots: Boolean = false, retainDivisor: Int = 4,
    ): Int {
        val budget = minOf(capacity, space).coerceAtLeast(0)
        var remaining = budget
        for (index in local) {
            if (remaining == 0) break
            val patch = patches[index]
            if (patch.kind != kind) continue
            val available = if (protectRoots && patch.planted) patch.availableBrowse(true)
                else (patch.biomass - patch.capacity / retainDivisor).coerceAtLeast(0)
            val taken = minOf(remaining, available)
            if (taken == 0) continue
            patches[index] = patch.copy(biomass = patch.biomass - taken)
            remaining -= taken
        }
        return budget - remaining
    }

    internal fun found(
        id: Int, tick: Long, residents: Int, patches: List<ForagePatch>, springs: List<GridCell>, previous: List<Settlement>,
        lotCount: Int = MaximumHomes, blockedCells: Collection<GridCell> = emptyList(), lotSpacing: Int = 1,
    ): Settlement? {
        val localFood = patches.asSequence().filter { it.kind == ForageKind.Meadow }
            .groupBy { it.cell.region() }.mapValues { (_, forage) -> forage.sumOf { it.biomass } }
        val blocked = patches.mapTo(springs.toMutableSet()) { it.cell }
        for (site in previous) blocked.addAll(site.lots + site.center + site.field)
        blocked.addAll(blockedCells)
        val distances = springs.map(island::terrainDistance)
        val candidates = island.reachable.asSequence()
            .filter { it !in blocked && suitable(it) && localFood.getOrElse(it.region()) { 0 } >= 80 }
            .filter { cell -> previous.none { distance(cell, it.center) < 12 } }
            .map { it to (distances.minOfOrNull { field -> field[cellId(it)] } ?: Int.MAX_VALUE) }
            .filter { (_, spring) -> spring <= 24 }
            .sortedWith(compareBy<Pair<GridCell, Int>> { it.second }
                .thenByDescending { localFood[it.first.region()] }.thenBy { cellId(it.first) })
        for ((center, _) in candidates) {
            val clear = buildList {
                for (z in center.z - 4..center.z + 4) for (x in center.x - 4..center.x + 4) {
                    val cell = GridCell(x, z)
                    if (cell != center && cell in plots && cell !in blocked && suitable(cell)) add(cell)
                }
            }
            if (clear.size < lotCount + 1) continue
            val field = clear.maxWith(compareBy<GridCell> { terrain.terrainCell(it).moisture }.thenByDescending(::cellId))
            val ordered = clear.asSequence().filter { it != field }
                .sortedWith(compareBy<GridCell> { distance(it, center) }.thenBy(::cellId))
            val lots = if (lotSpacing <= 1) ordered.take(lotCount).toList() else buildList {
                // Spaced lots keep homes from overlapping at their rendered size.
                val taken = mutableListOf(center, field)
                for (cell in ordered) {
                    if (size == lotCount) break
                    if (taken.any { maxOf(abs(it.x - cell.x), abs(it.z - cell.z)) < lotSpacing }) continue
                    add(cell)
                    taken.add(cell)
                }
            }
            if (lots.size < lotCount) continue
            val quarry = uplands.count { cell ->
                distance(cell, center) <= 12 && previous.none { distance(cell, it.center) <= 12 }
            } * 8
            return Settlement(
                id, tick, center, field, lots, residents = residents,
                homes = (residents + HomeCapacity - 1) / HomeCapacity, stoneRemaining = minOf(StockLimit, quarry),
            )
        }
        return null
    }

    fun validate(state: SettlementWorld, tick: Long, patches: List<ForagePatch>, springs: List<GridCell>, shaping: Boolean = false) {
        require(state.sites.size <= 2 && state.events.size <= 14)
        require(state.gatheredFood >= 0 && state.gatheredWood >= 0)
        val reserved = state.reservedCells()
        require(reserved.size == reserved.distinct().size && reserved.all { it in plots && suitable(it) })
        require(reserved.none { it in springs } && patches.none { it.cell in reserved })
        for ((index, site) in state.sites.withIndex()) {
            require(site.id == index + 1 && site.foundedAt in SettlementTurn..tick && site.foundedAt % SettlementTurn == 0L)
            require(site.lots.size == MaximumHomes && site.homes in 1..MaximumHomes && site.residents in 0..site.homes * HomeCapacity)
            require(site.food in 0..StockLimit && site.wood in 0..StockLimit &&
                site.stone in 0..StockLimit && site.stoneRemaining in 0..StockLimit)
            require(site.shortage in 0..3 && site.surplus in 0..3)
            require(site.labor.farm in 0..16 && site.labor.wood in 0..16 && site.labor.stone in 0..16)
            require(site.foodBalance in -32..64)
            require((site.welfare != null) == shaping)
            site.welfare?.let {
                require(it.bank == island.drinkingBank(site.center, springs) && it.water in 0..64 &&
                    it.unmet in 0..16 && it.cropYield in 0..48)
                require(it.firstShortage?.let { first -> first in site.foundedAt..tick && first % SettlementTurn == 0L } != false)
                require(it.firstRecovery?.let { recovered ->
                    it.firstShortage != null && recovered > it.firstShortage && recovered <= tick && recovered % SettlementTurn == 0L
                } != false)
            }
            require(index == state.sites.lastIndex || site.residents == 0)
            require(state.events.count { it.siteId == site.id && it.kind == SettlementEventKind.Founded && it.tick == site.foundedAt } == 1)
            require(state.events.count { it.siteId == site.id && it.kind == SettlementEventKind.Abandoned } == if (site.residents == 0) 1 else 0)
        }
        require(state.events.zipWithNext().all { (a, b) -> a.tick <= b.tick })
        require(state.events.all { event ->
            val site = state.sites.getOrNull(event.siteId - 1) ?: return@all false
            event.tick in site.foundedAt..tick && event.tick % SettlementTurn == 0L && when (event.kind) {
                SettlementEventKind.Founded -> event.amount in 1..16
                SettlementEventKind.Shortage -> event.amount in 1..32
                SettlementEventKind.Expanded -> event.amount in 2..MaximumHomes
                SettlementEventKind.Relocated -> event.siteId == 1 && event.amount == 2 && state.sites.size == 2
                SettlementEventKind.Abandoned -> event.amount == 0
            }
        })
    }

    internal fun suitable(cell: GridCell): Boolean = cell in plots && biome(cell) in listOf(Biome.Meadow, Biome.PineForest)
    private fun biome(cell: GridCell) = terrain.terrainCell(cell).biome
    private fun distance(a: GridCell, b: GridCell) = abs(a.x - b.x) + abs(a.z - b.z)
    private fun Settlement.event(tick: Long, event: SettlementEventKind, amount: Int = 0) = SettlementEvent(tick, id, event, amount)
}
