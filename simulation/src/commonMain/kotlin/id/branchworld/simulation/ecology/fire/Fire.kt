package id.branchworld.simulation.ecology.fire

import id.branchworld.simulation.core.GridCell
import id.branchworld.simulation.core.SplitMix64
import id.branchworld.simulation.core.mix64
import id.branchworld.simulation.ecology.ForageKind
import id.branchworld.simulation.ecology.ForagePatch
import id.branchworld.simulation.ecology.IslandEcology
import id.branchworld.simulation.ecology.PatchCellIndex
import id.branchworld.simulation.ecology.cellId
import id.branchworld.simulation.ecology.chronicle.EventRef
import id.branchworld.simulation.ecology.chronicle.FireOutCause
import id.branchworld.simulation.ecology.chronicle.KeeperAct
import id.branchworld.simulation.ecology.chronicle.WorldChronicle
import id.branchworld.simulation.ecology.chronicle.WorldEventKind
import id.branchworld.simulation.ecology.chronicle.lastAct
import id.branchworld.simulation.ecology.environment.CalledShower
import id.branchworld.simulation.ecology.environment.Environment
import id.branchworld.simulation.ecology.environment.RefillRecharge
import id.branchworld.simulation.ecology.environment.RegionalWater
import id.branchworld.simulation.ecology.environment.Season
import id.branchworld.simulation.ecology.environment.ShapingState
import id.branchworld.simulation.ecology.environment.Weather
import id.branchworld.simulation.ecology.environment.rainCovers
import id.branchworld.simulation.ecology.environment.seasonAt
import id.branchworld.simulation.ecology.environment.soilMoisture
import id.branchworld.simulation.ecology.region
import id.branchworld.simulation.ecology.regionCenter
import id.branchworld.simulation.ecology.settlement.Hardship
import id.branchworld.simulation.ecology.settlement.PlanOption
import id.branchworld.simulation.ecology.settlement.SettlementWorld
import id.branchworld.simulation.ecology.settlement.Structure
import id.branchworld.simulation.ecology.settlement.StructureKind
import id.branchworld.simulation.ecology.settlement.VillageEvent
import id.branchworld.simulation.ecology.settlement.VillageEventKind
import id.branchworld.simulation.ecology.settlement.VillageRules
import id.branchworld.simulation.ecology.settlement.VillageState
import id.branchworld.simulation.ecology.settlement.VillageTask
import id.branchworld.simulation.ecology.settlement.giveWood
import id.branchworld.simulation.ecology.settlement.record
import id.branchworld.simulation.terrain.Biome
import id.branchworld.simulation.terrain.PropKind
import id.branchworld.simulation.terrain.TerrainMap
import kotlinx.serialization.Serializable
import kotlin.math.abs

internal const val FireStep = 10L
internal const val TileGrid = TerrainMap.Size / 2
internal const val TileCount = TileGrid * TileGrid
internal const val AshAge = 300L
internal const val MeadowAge = 2_400L
internal const val ScrubAge = 6_000L
internal const val FirebreakHold = 1_800L
internal const val EmberOpeningRain = 720L
internal const val EmberOpeningRecharge = 112
/** The pine hollow where Sedgewick is founded. */
internal val EmberHollow = GridCell(0, -18)
internal const val EmberEarliestWarning = 360L
internal const val StrikeDelay = 180L
/** The storm crosses the ridge: a second strike falls this long after the first. */
internal const val SecondStrike = 60L
private const val FireSalt = 0x2d358dccaa6c78a5uL
private const val CureTicks = 300
private const val WetSeasonCure = 35
private const val LightningCure = 80
private const val LightningChance = 14
private const val WetCeiling = 150
private const val SpreadScale = 18
private const val IgniteFloor = 200
private const val BurnRate = 50
private const val CrownFuel = 120
private const val PineFuel = 40
private const val HomeFuel = 60
private const val GrassLitter = 12
internal const val FelledWood = 3
private const val MaxRecoveries = 8
private const val FirebreakReach = 9

/** How far burned or felled ground has come back. Derived from age, never stored. */
enum class Succession { Ash, Meadow, Scrub, Grove }

internal fun successionAt(age: Long): Succession = when {
    age < AshAge -> Succession.Ash
    age < MeadowAge -> Succession.Meadow
    age < ScrubAge -> Succession.Scrub
    else -> Succession.Grove
}

@Serializable
enum class ScarKind(internal val code: Int) { Burned(1), Felled(2) }

/** A tile on fire. [fire] is the fire's number; [fuel] is what is left to burn. */
@Serializable
data class BurningTile(val tile: Int, val fuel: Int, val fire: Int)

/** Ground that burned or was cleared at [at]. [cause] is the chronicle event that did it. Removed once it is a grove again. */
@Serializable
data class Scar(val tile: Int, val at: Long, val kind: ScarKind, val cause: Int)

/** A pine (terrain prop index) that burned or was felled. Removed once it has grown back. */
@Serializable
data class StandChange(val prop: Int, val at: Long, val kind: ScarKind)

/** The Ember Season strike the dry storm brings. [cause] is the dry-storm event. */
@Serializable
data class PlannedStrike(val tile: Int, val at: Long, val cause: Int)

/** A fire still burning: its starting event and what it has taken so far. */
@Serializable
data class ActiveFire(
    val number: Int,
    val started: Int,
    val startedAt: Long,
    val tile: Int,
    val tiles: Int = 0,
    val fled: Boolean = false,
    /** The chronicle event that cut the firebreak this fire met, once one turned it back. */
    val heldBy: Int? = null,
    val heldAt: Int? = null,
)

/** A fire that went out and the regrowth milestones it has reached (0 none, 1 grass, 2 young wood). */
@Serializable
data class Recovery(val number: Int, val out: Int, val outAt: Long, val tile: Int, val reached: Int = 0)

@Serializable
data class FireState(
    val randomState: ULong,
    /** Direction (0 east, counter-clockwise in eighths toward +z) the wind carries fire this dry spell. */
    val wind: Int = 0,
    /** The last fire step it rained; dryness cures from here. */
    val dryFrom: Long = 0,
    /** The dry spell (by its end tick) already tested for lightning. */
    val testedSpell: Long = -1,
    val burning: List<BurningTile> = emptyList(),
    val scars: List<Scar> = emptyList(),
    val stands: List<StandChange> = emptyList(),
    /** Strikes the Ember Season storm will bring, earliest first. */
    val strikes: List<PlannedStrike> = emptyList(),
    val active: List<ActiveFire> = emptyList(),
    val recoveries: List<Recovery> = emptyList(),
    val fires: Int = 0,
    val tilesBurned: Int = 0,
    val homesBurned: Int = 0,
    val lastFireOut: Long? = null,
    /** Ember Season: the village has felled the trees around its homes. */
    val felled: Boolean = false,
) {
    fun scarAt(tile: Int): Scar? {
        var low = 0
        var high = scars.lastIndex
        while (low <= high) {
            val middle = (low + high) ushr 1
            val scar = scars[middle]
            when {
                scar.tile < tile -> low = middle + 1
                scar.tile > tile -> high = middle - 1
                else -> return scar
            }
        }
        return null
    }

    fun burningAt(tile: Int): BurningTile? = burning.firstOrNull { it.tile == tile }

    companion object {
        fun start(seed: Long) = FireState(mix64(seed.toULong() xor FireSalt))
    }
}

internal fun tileOf(cell: GridCell) = tileOf(cell.x, cell.z)
internal fun tileOf(x: Int, z: Int) = (z - TerrainMap.MinCell) / 2 * TileGrid + (x - TerrainMap.MinCell) / 2
fun tileCell(tile: Int) = GridCell(TerrainMap.MinCell + tile % TileGrid * 2, TerrainMap.MinCell + tile / TileGrid * 2)
private val StepX = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1)
private val StepZ = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)
private val WindWeight = intArrayOf(4, 3, 1, 0, 0)

internal fun neighbourTile(tile: Int, direction: Int, distance: Int = 1): Int? {
    val x = tile % TileGrid + StepX[direction] * distance
    val z = tile / TileGrid + StepZ[direction] * distance
    return if (x in 0 until TileGrid && z in 0 until TileGrid) z * TileGrid + x else null
}

/** The eighth of the compass pointing from [from] toward [to]. */
internal fun octant(from: GridCell, to: GridCell): Int {
    val dx = to.x - from.x
    val dz = to.z - from.z
    return when {
        abs(dx) > 2 * abs(dz) -> if (dx >= 0) 0 else 4
        abs(dz) > 2 * abs(dx) -> if (dz >= 0) 2 else 6
        dx >= 0 && dz >= 0 -> 1
        dx < 0 && dz >= 0 -> 3
        dx < 0 -> 5
        else -> 7
    }
}

internal data class FireOutcome(val fire: FireState, val chronicle: WorldChronicle, val settlements: SettlementWorld?)

/** Static fuel, wetness and pines per tile for one island, and the fire step. */
internal class FireRules(private val island: IslandEcology) {
    private val terrain = island.terrain
    private val cellLitter = IntArray(TerrainMap.Size * TerrainMap.Size)
    private val litter = IntArray(TileCount)
    private val grass = IntArray(TileCount)
    private val elevation = IntArray(TileCount)
    private val landCells = Array(TileCount) { mutableListOf<GridCell>() }
    private val pines = Array(TileCount) { mutableListOf<Int>() }
    private val patchCells = PatchCellIndex()

    init {
        for (z in TerrainMap.MinCell until TerrainMap.MinCell + TerrainMap.Size) {
            for (x in TerrainMap.MinCell until TerrainMap.MinCell + TerrainMap.Size) {
                val cell = checkNotNull(terrain.cellAt(x, z))
                val value = when {
                    !cell.land -> 0
                    cell.biome == Biome.PineForest -> 36
                    cell.biome == Biome.Meadow -> GrassLitter
                    cell.biome == Biome.Wetland -> 3
                    cell.biome == Biome.RockyUpland -> 2
                    cell.biome == Biome.Beach -> 1
                    else -> 0
                }
                val tile = tileOf(x, z)
                cellLitter[TerrainMap.cellIndex(x, z)] = value
                litter[tile] += value
                if (cell.land) {
                    landCells[tile].add(GridCell(x, z))
                    elevation[tile] += cell.elevationTwice
                    if (value >= GrassLitter) grass[tile] += GrassLitter else grass[tile] += value
                }
            }
        }
        for (index in 0 until terrain.propCount) {
            val prop = terrain.prop(index)
            if (prop.kind == PropKind.Pine) pines[tileOf(prop.x, prop.z)].add(index)
        }
    }

    fun pinesIn(tile: Int): List<Int> = pines[tile]
    fun landIn(tile: Int): List<GridCell> = landCells[tile]
    fun burnable(tile: Int) = litter[tile] > landCells[tile].size * 3

    /** How cured the island's fuel is (0–100) on this tick. */
    fun cure(tick: Long, fire: FireState, environment: Environment): Int {
        if (environment.weather == Weather.Rain) return 0
        val ramp = ((tick - fire.dryFrom).coerceIn(0, CureTicks.toLong()) * 100 / CureTicks).toInt()
        return if (seasonAt(tick) == Season.Dry) ramp else ramp * WetSeasonCure / 100
    }

    /** Average soil wetness over the tile's land, including water the Keeper's shower left behind. */
    fun wetness(tile: Int, pools: List<RegionalWater>): Int {
        val cells = landCells[tile]
        if (cells.isEmpty()) return 255
        var total = 0
        for (cell in cells) {
            var held = 0
            for (pool in pools) if (rainCovers(pool.region, cell)) held = maxOf(held, pool.retained)
            total += terrain.soilMoisture(cell, RefillRecharge + held)
        }
        return total / cells.size
    }

    /** What would burn on [tile] now. */
    fun fuel(tile: Int, tick: Long, fire: FireState, patchAt: IntArray, patches: List<ForagePatch>, village: VillageState?): Int {
        val cells = landCells[tile]
        if (cells.isEmpty()) return 0
        val scar = fire.scarAt(tile)
        var ground = litter[tile]
        var homes = 0
        if (village != null) {
            for (structure in village.structures) {
                if (tileOf(structure.cell) != tile) continue
                ground -= cellLitter[cellId(structure.cell)]
                if (structure.kind == StructureKind.Home && structure.completedAt != null) homes++
            }
        }
        ground = ground.coerceAtLeast(0)
        val litterPart = if (scar == null) ground else {
            val age = tick - scar.at
            when {
                scar.kind == ScarKind.Felled && age < FirebreakHold -> 0
                else -> when (successionAt(age)) {
                    Succession.Ash -> 0
                    Succession.Meadow -> minOf(grass[tile], ground)
                    Succession.Scrub -> (minOf(grass[tile], ground) + ground) / 2
                    Succession.Grove -> ground
                }
            }
        }
        val felledHold = scar?.kind == ScarKind.Felled && tick - scar.at < FirebreakHold
        var pineFuel = 0
        if (!felledHold) for (prop in pines[tile]) {
            val change = fire.stands.firstOrNull { it.prop == prop }
            pineFuel += when (change?.let { successionAt(tick - it.at) }) {
                null, Succession.Grove -> PineFuel
                Succession.Scrub -> PineFuel / 3
                else -> 0
            }
        }
        var patchFuel = 0
        if (!felledHold) for (cell in cells) {
            val index = patchAt[cellId(cell)]
            if (index < 0) continue
            val patch = patches[index]
            patchFuel += if (patch.kind == ForageKind.Browse) patch.biomass / 4 else patch.biomass / 8
        }
        return litterPart + pineFuel + patchFuel + homes * HomeFuel
    }

    private fun combustibility(fuel: Int, cure: Int, wetness: Int): Int =
        fuel * cure / 100 * (WetCeiling - wetness).coerceAtLeast(0) / 10

    fun patchIndex(patches: List<ForagePatch>): IntArray = patchCells.rebuild(patches)

    /** One fire step. Called every [FireStep] ticks after regrowth and before wildlife. */
    fun step(
        tick: Long,
        previous: FireState,
        chronicle: WorldChronicle,
        environment: Environment,
        shaping: ShapingState,
        patches: MutableList<ForagePatch>,
        settlements: SettlementWorld?,
    ): FireOutcome {
        val random = SplitMix64(previous.randomState)
        var fire = previous
        var log = chronicle
        var world = settlements
        val raining = environment.weather == Weather.Rain
        // A new dry spell brings its own wind.
        if (!raining && fire.dryFrom == tick - FireStep && fire.strikes.isEmpty()) fire = fire.copy(wind = random.nextBounded(8))
        if (raining) fire = fire.copy(dryFrom = tick)
        fire = prune(fire, tick)
        val cure = cure(tick, fire, environment)
        val patchAt = patchIndex(patches)

        // Ember Season: the village fells the trees around its homes once it has chosen to.
        val village = world?.village
        if (!fire.felled && village?.chosen(PlanOption.FellAroundHomes) == true) {
            val decided = log.events.lastOrNull { it.kind == WorldEventKind.PlanChosen }?.ref
            val homes = village.structures.filter { it.kind == StructureKind.Home && it.completedAt != null }
            val tiles = homes.flatMap { home ->
                val center = tileOf(home.cell)
                buildList { for (dz in -2..2) for (dx in -2..2) {
                    val x = center % TileGrid + dx
                    val z = center / TileGrid + dz
                    if (x in 0 until TileGrid && z in 0 until TileGrid) add(z * TileGrid + x)
                } }
            }.distinct().sorted().filter(::burnable)
            val cut = cut(tiles, tick, fire, patches, patchAt, null)
            fire = cut.fire.copy(felled = true)
            val (next, felledRef) = log.record(tick, WorldEventKind.TreesFelled, homes.firstOrNull()?.cell ?: tileCell(tiles.first()),
                cut.pines, tiles.size, listOfNotNull(decided))
            log = next
            fire = fire.copy(scars = fire.scars.map { if (it.cause == -1) it.copy(cause = felledRef.id) else it })
            world = world.giveWood(cut.pines * FelledWood)
        }

        // The Keeper's called shower douses what it covers and its pools keep the ground wet.
        val shower = shaping.shower
        var burning = fire.burning
        val outs = mutableMapOf<Int, FireOutCause>()
        if (raining && burning.isNotEmpty()) {
            for (tile in burning) outs[tile.fire] = FireOutCause.Rain
            burning = emptyList()
        } else if (shower != null) {
            val doused = burning.filter { rainCovers(shower.region, tileCell(it.tile)) }
            for (tile in doused) outs[tile.fire] = FireOutCause.CalledShower
            burning = burning - doused.toSet()
        }

        // Lightning: the Ember Season strike at its hour, otherwise a dry storm late in a cured dry spell.
        val due = mutableListOf<Pair<Int, EventRef?>>()
        val planned = fire.strikes
        if (planned.isNotEmpty()) {
            for (strike in planned.filter { tick >= it.at }) {
                due.add(strike.tile to (log.find(strike.cause)?.ref ?: EventRef(strike.cause, strike.at, WorldEventKind.DryStorm)))
            }
            fire = fire.copy(strikes = planned.filter { tick < it.at })
        } else if (!raining && seasonAt(tick) == Season.Dry && cure >= LightningCure && fire.testedSpell != environment.nextChange) {
            fire = fire.copy(testedSpell = environment.nextChange)
            if (random.nextBounded(100) < LightningChance) pickStrike(tick, fire, patches, patchAt, world?.village, random)?.let { due.add(it to null) }
        }
        for ((strikeTile, strikeCause) in due) {
            val (afterStrike, lightning) = log.record(tick, WorldEventKind.Lightning, tileCell(strikeTile), 0, strikeTile,
                listOfNotNull(strikeCause))
            log = afterStrike
            val wet = wetness(strikeTile, shaping.pools)
            val fuel = fuel(strikeTile, tick, fire, patchAt, patches, world?.village)
            val dousing = shower?.takeIf { rainCovers(it.region, tileCell(strikeTile)) } != null ||
                shaping.pools.any { rainCovers(it.region, tileCell(strikeTile)) }
            if (combustibility(fuel, cure, wet) >= IgniteFloor && !dousing && burning.none { it.tile == strikeTile }) {
                val number = fire.fires + 1
                val (started, ref) = log.record(tick, WorldEventKind.FireStarted, tileCell(strikeTile), number, strikeTile, listOf(lightning))
                log = started
                fire = fire.copy(fires = number, active = fire.active + ActiveFire(number, ref.id, tick, strikeTile))
                burning = burning + BurningTile(strikeTile, fuel, number)
                val ignited = ignite(strikeTile, tick, fire, log, patches, patchAt, world, number)
                fire = ignited.fire; log = ignited.chronicle; world = ignited.settlements
            } else {
                val rain = log.lastAct(KeeperAct.Rain)?.takeIf { dousing }
                log = log.record(tick, WorldEventKind.StrikeFizzled, tileCell(strikeTile), 0, 0, listOfNotNull(lightning, rain)).first
            }
        }

        // Spread from the tiles burning at the start of this step, in tile order, then burn them down.
        if (burning.isNotEmpty() && !raining) {
            val snapshot = burning.sortedBy { it.tile }
            val burningSet = snapshot.mapTo(HashSet()) { it.tile }
            val caught = mutableMapOf<Int, Int>()
            val village = world?.village
            for (source in snapshot) {
                for (direction in 0 until 8) {
                    val weight = windWeight(direction, fire.wind)
                    if (weight == 0) continue
                    val target = neighbourTile(source.tile, direction) ?: continue
                    if (target in burningSet || target in caught) continue
                    when (val result = trySpread(target, weight, tick, cure, fire, shaping, patches, patchAt, village, random)) {
                        Spread.Caught -> caught[target] = source.fire
                        is Spread.Held -> fire = noteHeld(fire, source.fire, result.scar)
                        null -> Unit
                    }
                }
                // Crown fires throw embers two tiles downwind.
                if (source.fuel >= CrownFuel) {
                    val target = neighbourTile(source.tile, fire.wind, 2)
                    if (target != null && target !in burningSet && target !in caught) {
                        if (trySpread(target, 1, tick, cure, fire, shaping, patches, patchAt, village, random) == Spread.Caught) {
                            caught[target] = source.fire
                        }
                    }
                }
            }
            burning = snapshot.mapNotNull { tile ->
                val left = tile.fuel - maxOf(BurnRate, tile.fuel / 3)
                if (left > 0) tile.copy(fuel = left) else null
            }
            for ((target, number) in caught.entries.sortedBy { it.key }) {
                val fuel = fuel(target, tick, fire, patchAt, patches, world?.village)
                burning = burning + BurningTile(target, fuel, number)
                val ignited = ignite(target, tick, fire, log, patches, patchAt, world, number)
                fire = ignited.fire; log = ignited.chronicle; world = ignited.settlements
            }
            burning = burning.sortedBy { it.tile }
        }

        // Firebreaks that held, and fires that are out.
        for (active in fire.active) {
            val heldBy = active.heldBy ?: continue
            if (log.events.any { it.kind == WorldEventKind.FirebreakHeld && it.subject == active.number }) continue
            val breakRef = log.find(heldBy)?.ref
            val started = log.find(active.started)?.ref
            log = log.record(tick, WorldEventKind.FirebreakHeld, tileCell(active.heldAt ?: active.tile), active.number, 0,
                listOfNotNull(breakRef, started)).first
        }
        val stillBurning = burning.mapTo(HashSet()) { it.fire }
        val ended = fire.active.filter { it.number !in stillBurning }
        var recoveries = fire.recoveries
        for (done in ended) {
            val cause = outs[done.number] ?: FireOutCause.BurnedOut
            val started = log.find(done.started)?.ref ?: EventRef(done.started, done.startedAt, WorldEventKind.FireStarted)
            val extinguisher = if (cause == FireOutCause.CalledShower) log.lastAct(KeeperAct.Rain) else null
            val (next, out) = log.record(tick, WorldEventKind.FireOut, tileCell(done.tile), done.tiles, cause.code,
                listOfNotNull(started, extinguisher))
            log = next
            recoveries = (recoveries + Recovery(done.number, out.id, tick, done.tile)).takeLast(MaxRecoveries)
        }
        fire = fire.copy(
            burning = burning,
            active = fire.active.filter { it.number in stillBurning },
            recoveries = recoveries,
            lastFireOut = if (ended.isNotEmpty()) tick else fire.lastFireOut,
            randomState = random.state,
        )

        // Regrowth milestones on burned ground.
        val grown = fire.recoveries.map { recovery ->
            val age = tick - recovery.outAt
            val reached = when {
                age >= MeadowAge -> 2
                age >= AshAge -> 1
                else -> 0
            }
            if (reached <= recovery.reached) return@map recovery
            val outRef = log.find(recovery.out)?.ref ?: EventRef(recovery.out, recovery.outAt, WorldEventKind.FireOut)
            val kind = if (reached == 1) WorldEventKind.MeadowReturned else WorldEventKind.YoungWood
            log = log.record(tick, kind, tileCell(recovery.tile), recovery.number, 0, listOf(outRef)).first
            recovery.copy(reached = reached)
        }.filter { it.reached < 2 }
        fire = fire.copy(recoveries = grown)
        return FireOutcome(fire, log, world)
    }

    private fun noteHeld(fire: FireState, number: Int, scar: Scar): FireState = fire.copy(active = fire.active.map {
        if (it.number == number && it.heldBy == null) it.copy(heldBy = scar.cause, heldAt = scar.tile) else it
    })

    private sealed interface Spread {
        data object Caught : Spread
        data class Held(val scar: Scar) : Spread
    }

    /** A spread attempt. A young firebreak turns the fire back without a draw; dry fuel catches by chance. */
    private fun trySpread(
        target: Int, weight: Int, tick: Long, cure: Int, fire: FireState, shaping: ShapingState,
        patches: List<ForagePatch>, patchAt: IntArray, village: VillageState?, random: SplitMix64,
    ): Spread? {
        val scar = fire.scarAt(target)
        if (scar != null && scar.kind == ScarKind.Felled && tick - scar.at < FirebreakHold) return if (burnable(target)) Spread.Held(scar) else null
        val fuel = fuel(target, tick, fire, patchAt, patches, village)
        if (fuel <= 0) return null
        val chance = (combustibility(fuel, cure, wetness(target, shaping.pools)) * weight * SpreadScale / 100).coerceAtMost(950)
        if (chance <= 0) return null
        return if (random.nextBounded(1000) < chance) Spread.Caught else null
    }

    private fun windWeight(direction: Int, wind: Int): Int {
        val apart = abs(direction - wind)
        return WindWeight[minOf(apart, 8 - apart)]
    }

    /** Sets a tile alight: its forage burns to the ground, its pines fall and its homes are lost. */
    private fun ignite(
        tile: Int, tick: Long, previous: FireState, chronicle: WorldChronicle, patches: MutableList<ForagePatch>,
        patchAt: IntArray, settlements: SettlementWorld?, number: Int,
    ): FireOutcome {
        var fire = previous
        var log = chronicle
        var world = settlements
        for (cell in landCells[tile]) {
            val index = patchAt[cellId(cell)]
            if (index >= 0 && patches[index].biomass > 0) patches[index] = patches[index].copy(biomass = 0)
        }
        val fallen = pines[tile].filter { prop ->
            fire.stands.none { it.prop == prop && successionAt(tick - it.at) != Succession.Grove }
        }
        val stands = (fire.stands.filterNot { it.prop in fallen } + fallen.map { StandChange(it, tick, ScarKind.Burned) }).sortedBy { it.prop }
        val started = fire.active.firstOrNull { it.number == number }
        val scars = (fire.scars.filterNot { it.tile == tile } + Scar(tile, tick, ScarKind.Burned, started?.started ?: 0)).sortedBy { it.tile }
        fire = fire.copy(stands = stands, scars = scars, tilesBurned = fire.tilesBurned + 1,
            active = fire.active.map { if (it.number == number) it.copy(tiles = it.tiles + 1) else it })
        val village = world?.village
        if (world != null && village != null) {
            val homes = village.structures.filter { it.kind == StructureKind.Home && it.completedAt != null && tileOf(it.cell) == tile }
            for (home in homes) {
                val startedRef = started?.let { log.find(it.started)?.ref }
                val burned = burnHome(checkNotNull(world), home, tick)
                world = burned.first
                fire = fire.copy(homesBurned = fire.homesBurned + 1)
                val (afterHome, homeRef) = log.record(tick, WorldEventKind.HomeBurned, home.cell, home.id, home.siteId, listOfNotNull(startedRef))
                log = afterHome
                for ((household, left) in burned.second) {
                    log = log.record(tick, if (left) WorldEventKind.HouseholdLeft else WorldEventKind.HouseholdSheltered, home.cell,
                        household, if (left) Hardship.Fire.code else 0, listOf(homeRef)).first
                }
            }
        }
        return FireOutcome(fire, log, world)
    }

    /** The home burns; its family moves into an empty home on the same site, or leaves. */
    private fun burnHome(world: SettlementWorld, home: Structure, tick: Long): Pair<SettlementWorld, List<Pair<Int, Boolean>>> {
        var village = checkNotNull(world.village)
        val ruined = home.copy(progress = 0, required = VillageRules.HomeSteps, level = 1, completedAt = null, startedAt = tick, burnedAt = tick)
        village = village.copy(structures = village.structures.map { if (it.id == home.id) ruined else it })
            .record(VillageEvent(tick, VillageEventKind.HomeBurned, home.siteId, home.id))
        val moves = mutableListOf<Pair<Int, Boolean>>()
        for (household in village.households.filter { it.active && it.home == home.id }) {
            val occupied = village.households.filter { it.active }.map { it.home }.toSet()
            val shelter = village.structures.firstOrNull {
                it.siteId == home.siteId && it.kind == StructureKind.Home && it.completedAt != null && it.id !in occupied
            }
            village = if (shelter != null) {
                moves.add(household.id to false)
                village.copy(households = village.households.map { if (it.id == household.id) it.copy(home = shelter.id) else it })
            } else {
                moves.add(household.id to true)
                village.copy(households = village.households.map {
                    if (it.id == household.id) it.copy(leftAt = tick, leftBecause = Hardship.Fire, task = VillageTask.Idle) else it
                }).record(VillageEvent(tick, VillageEventKind.HouseholdLeft, home.siteId, household.id, Hardship.Fire.code))
            }
        }
        val sites = world.sites.map { site ->
            site.copy(
                residents = village.active(site.id).sumOf { it.members },
                homes = village.structures.count { it.siteId == site.id && it.kind == StructureKind.Home && it.completedAt != null },
            )
        }
        return world.copy(sites = sites, village = village) to moves
    }

    internal class Cut(val fire: FireState, val pines: Int)

    /** Clears fuel from [tiles]: forage is cut to the ground and pines are felled. [cause] -1 is filled in by the caller. */
    fun cut(tiles: List<Int>, tick: Long, previous: FireState, patches: MutableList<ForagePatch>, patchAt: IntArray, cause: Int?): Cut {
        var felled = 0
        val stands = previous.stands.toMutableList()
        val scars = previous.scars.associateBy { it.tile }.toMutableMap()
        for (tile in tiles) {
            for (cell in landCells[tile]) {
                val index = patchAt[cellId(cell)]
                if (index >= 0 && patches[index].biomass > 0) patches[index] = patches[index].copy(biomass = 0)
            }
            for (prop in pines[tile]) {
                if (stands.any { it.prop == prop && successionAt(tick - it.at) != Succession.Grove }) continue
                stands.removeAll { it.prop == prop }
                stands.add(StandChange(prop, tick, ScarKind.Felled))
                felled++
            }
            scars[tile] = Scar(tile, tick, ScarKind.Felled, cause ?: -1)
        }
        return Cut(previous.copy(stands = stands.sortedBy { it.prop }, scars = scars.values.sortedBy { it.tile }), felled)
    }

    /** The firebreak a Keeper cuts in [region]: across the wind through its centre, from one natural break to the next. */
    fun firebreakTiles(region: Int, fire: FireState): List<Int> {
        val middle = tileOf(regionCenter(region))
        val center = (0 until TileCount).filter { tileCell(it).region() == region && burnable(it) }
            .minWithOrNull(compareBy<Int> { abs(it % TileGrid - middle % TileGrid) + abs(it / TileGrid - middle / TileGrid) }.thenBy { it })
            ?: return emptyList()
        val along = (fire.wind + 2) % 8
        val tiles = mutableListOf(center)
        for (sign in listOf(0, 4)) {
            val direction = (along + sign) % 8
            for (step in 1..FirebreakReach) {
                val tile = neighbourTile(center, direction, step) ?: break
                if (!burnable(tile)) break
                tiles.add(tile)
            }
        }
        // Diagonal lines are doubled so fire cannot slip between corners.
        if (along % 2 == 1) tiles.addAll(tiles.mapNotNull { neighbourTile(it, (along + 1) % 8) }.filter(::burnable))
        return tiles.distinct().sorted()
    }

    private fun pickStrike(
        tick: Long, fire: FireState, patches: List<ForagePatch>, patchAt: IntArray, village: VillageState?, random: SplitMix64,
    ): Int? {
        val weights = IntArray(TileCount)
        var total = 0
        for (tile in 0 until TileCount) {
            if (!burnable(tile) || fire.scarAt(tile) != null) continue
            val height = (elevation[tile] / landCells[tile].size.coerceAtLeast(1)).coerceAtLeast(0)
            weights[tile] = fuel(tile, tick, fire, patchAt, patches, village) * (height + 8) / 8
            total += weights[tile]
        }
        if (total <= 0) return null
        var draw = random.nextBounded(total)
        for (tile in 0 until TileCount) {
            draw -= weights[tile]
            if (draw < 0) return tile
        }
        return null
    }

    /**
     * Ember Season: the dry storm gathers over the richest high woodland upwind of the village, and the wind it brings
     * blows from there toward the homes.
     */
    fun planStorm(tick: Long, fire: FireState, patches: List<ForagePatch>, village: VillageState, center: GridCell, cause: Int): FireState {
        val home = tileOf(center)
        val patchAt = patchIndex(patches)
        val ranked = (0 until TileCount).filter { tile ->
            val dx = abs(tile % TileGrid - home % TileGrid)
            val dz = abs(tile / TileGrid - home / TileGrid)
            maxOf(dx, dz) in 4..7 && burnable(tile) && village.structures.none { tileOf(it.cell) == tile }
        }.sortedWith(compareByDescending<Int> { tile ->
            val height = elevation[tile] / landCells[tile].size.coerceAtLeast(1)
            fuel(tile, tick, fire, patchAt, patches, village) * (height + 8)
        }.thenBy { it })
        val first = ranked.firstOrNull() ?: return fire
        val wind = octant(tileCell(first), center)
        // The storm crosses the ridge and strikes again over another stretch of woods.
        val second = ranked.firstOrNull { tile ->
            tileCell(tile).region() != tileCell(first).region() &&
                maxOf(abs(tile % TileGrid - first % TileGrid), abs(tile / TileGrid - first / TileGrid)) >= 4
        }
        val strikes = listOfNotNull(PlannedStrike(first, tick + StrikeDelay, cause),
            second?.let { PlannedStrike(it, tick + StrikeDelay + SecondStrike, cause) })
        return fire.copy(strikes = strikes, wind = wind)
    }

    /** Cells whose cover burned or was cut and has not grown back. */
    fun bareCells(fire: FireState, tick: Long): Set<GridCell> = buildSet {
        for (scar in fire.scars) {
            val stage = successionAt(tick - scar.at)
            if (stage == Succession.Ash || stage == Succession.Meadow) addAll(landCells[scar.tile])
        }
    }

    fun burningCells(fire: FireState): List<GridCell> = fire.burning.flatMap { landCells[it.tile] }

    /** Regrowth on burned ground: nothing in the ash, a rich flush once grass returns. */
    fun growthFactor(fire: FireState, cell: GridCell, tick: Long): Int {
        val scar = fire.scarAt(tileOf(cell)) ?: return 1
        if (scar.kind != ScarKind.Burned) return 1
        return when (successionAt(tick - scar.at)) {
            Succession.Ash -> 0
            Succession.Meadow -> 2
            else -> 1
        }
    }

    private fun prune(fire: FireState, tick: Long): FireState {
        val scars = fire.scars.filter { tick - it.at < ScrubAge || fire.burning.any { burning -> burning.tile == it.tile } }
        val stands = fire.stands.filter { tick - it.at < ScrubAge }
        return if (scars.size == fire.scars.size && stands.size == fire.stands.size) fire else fire.copy(scars = scars, stands = stands)
    }

    fun validate(fire: FireState, chronicle: WorldChronicle?, tick: Long, village: VillageState?) {
        require(fire.wind in 0..7 && fire.dryFrom in 0..tick)
        require(fire.burning.zipWithNext().all { (a, b) -> a.tile < b.tile } && fire.burning.all { it.tile in 0 until TileCount && it.fuel > 0 })
        require(fire.scars.zipWithNext().all { (a, b) -> a.tile < b.tile })
        require(fire.scars.all { it.tile in 0 until TileCount && it.at in 1..tick && (tick - it.at < ScrubAge + FireStep || fire.burningAt(it.tile) != null) })
        require(fire.burning.all { fire.scarAt(it.tile)?.kind == ScarKind.Burned && it.fire in 1..fire.fires })
        require(fire.stands.zipWithNext().all { (a, b) -> a.prop < b.prop })
        require(fire.stands.all { it.prop in 0 until terrain.propCount && terrain.prop(it.prop).kind == PropKind.Pine && it.at in 1..tick && tick - it.at < ScrubAge + FireStep })
        require(fire.active.map { it.number } == fire.burning.map { it.fire }.distinct().sorted())
        require(fire.active.all { it.number in 1..fire.fires && it.startedAt in 1..tick && it.tiles in 1..TileCount && it.tile in 0 until TileCount })
        require(fire.recoveries.size <= MaxRecoveries && fire.recoveries.all {
            it.number in 1..fire.fires && it.outAt in 1..tick && it.reached in 0..1 && it.tile in 0 until TileCount
        })
        require(fire.strikes.size <= 2 && fire.strikes.all { it.tile in 0 until TileCount && it.at > tick } && fire.strikes.zipWithNext().all { (a, b) -> a.at < b.at })
        require(fire.tilesBurned >= 0 && fire.homesBurned in 0..fire.tilesBurned && fire.fires >= fire.active.size)
        require(fire.lastFireOut?.let { it in 1..tick } != false)
        require(chronicle != null)
        require(!fire.felled || village?.chosen(PlanOption.FellAroundHomes) == true)
    }
}

/** What wildlife sees of fire on one tick: which tiles burn and which cells have lost their cover. */
internal class FireView(private val burning: IntArray, val bare: Set<GridCell>) {
    val active get() = burning.isNotEmpty()

    /** Tile distance (Chebyshev) from [cell] to the nearest burning tile, or [Int.MAX_VALUE] beyond [reach]. */
    fun distance(cell: GridCell, reach: Int = 6): Int = firstBurning(cell, reach) { _, ring -> ring }

    fun nearestFire(cell: GridCell, reach: Int): Int? =
        firstBurning(cell, reach) { number, _ -> number }.takeUnless { it == Int.MAX_VALUE }

    private inline fun firstBurning(cell: GridCell, reach: Int, hit: (number: Int, distance: Int) -> Int): Int {
        if (!active) return Int.MAX_VALUE
        val tile = tileOf(cell)
        val x = tile % TileGrid
        val z = tile / TileGrid
        for (ring in 0..reach) {
            for (dz in -ring..ring) for (dx in -ring..ring) {
                if (maxOf(abs(dx), abs(dz)) != ring) continue
                val tx = x + dx
                val tz = z + dz
                if (tx !in 0 until TileGrid || tz !in 0 until TileGrid) continue
                val number = burning[tz * TileGrid + tx]
                if (number != 0) return hit(number, ring)
            }
        }
        return Int.MAX_VALUE
    }
}
