package id.branchworld.game.simulation

import id.branchworld.game.ecology.CreatureAction
import id.branchworld.game.ecology.DayLength
import id.branchworld.game.ecology.EcologyState
import id.branchworld.game.ecology.rooted
import id.branchworld.game.ecology.ForageKind
import id.branchworld.game.ecology.Need
import id.branchworld.game.ecology.Species
import id.branchworld.game.ecology.Season
import id.branchworld.game.ecology.Weather
import id.branchworld.game.ecology.WildlifeMilestone
import id.branchworld.game.ecology.ForagePatch
import id.branchworld.game.ecology.Settlement
import id.branchworld.game.ecology.StructureKind
import id.branchworld.game.ecology.VillageState
import id.branchworld.game.ecology.VillageTask
import id.branchworld.game.ecology.region
import id.branchworld.game.ecology.cropPerWorker
import id.branchworld.game.ecology.seasonAt
import id.branchworld.game.ecology.soilMoisture
import id.branchworld.game.ecology.terrainCell
import id.branchworld.game.ecology.ScarKind
import id.branchworld.game.ecology.Succession
import id.branchworld.game.ecology.WorldEventKind
import id.branchworld.game.ecology.successionAt
import id.branchworld.game.ecology.tileCell
import id.branchworld.game.terrain.Biome
import id.branchworld.game.terrain.LandmarkKind
import id.branchworld.game.terrain.TerrainMap
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlin.math.abs

class WorldRenderSource(
    val terrain: TerrainMap,
    val poses: StateFlow<WorldRenderProjection>,
    /** Rules v10: the herd's seasonal way, dry range first. Static for the island, so it is computed once. */
    val herdRoute: List<GroundPoint> = emptyList(),
)

/** Rules v10 range detail: declared refuges and which range the herd is heading for (1 wet, 2 dry). */
data class RangePose(val refuges: List<Int>, val herdBound: Int)

/** Presentation data for a completed tick. Terrain remains a separate static projection. */
data class WorldRenderProjection(
    val tick: Long,
    val creatures: List<CreaturePose>,
    val forage: List<ForagePose> = emptyList(),
    val springs: List<GroundPoint> = emptyList(),
    val ecology: EcologyReadout? = null,
    val climate: ClimatePose? = null,
    val branchId: Int? = null,
    val settlements: List<SettlementPose>? = null,
    val interpolateCreatures: Boolean = false,
    val wildlife: WildlifeReadout? = null,
    val shaping: ShapingPose? = null,
    val livingEcho: LivingEchoProjection? = null,
    val ranges: RangePose? = null,
    /** Rules v11 fire, scars and fallen pines. */
    val fire: FirePose? = null,
) {
    val leadRabbit: CreaturePose? get() = creatures.firstOrNull { it.species == Species.Rabbit }
}

data class GroundPoint(val x: Int, val z: Int, val elevationTwice: Int)

/** Read-only fire scene. Tiles are 2×2 cells; [FireTilePose.ground] is the tile's first cell. */
data class FirePose(
    val burning: List<FireTilePose>,
    val scars: List<ScarPose>,
    /** Pines (terrain prop index) that have fallen or are growing back, with their stage. */
    val stands: Map<Int, Succession>,
    /** Direction the wind carries fire, in eighths from east toward +z. */
    val wind: Int,
    /** Where the gathering storm will strike. */
    val storm: List<GroundPoint>,
    /** The most recent lightning and how many ticks ago it struck, while it is still visible. */
    val lightning: GroundPoint?,
    val lightningAge: Int,
)

data class FireTilePose(val tile: Int, val ground: GroundPoint, val intensity: Int)
data class ScarPose(val tile: Int, val ground: GroundPoint, val stage: Succession, val felled: Boolean)

data class SettlementPose(
    val id: Int, val homes: List<GroundPoint>, val field: GroundPoint, val resident: GroundPoint?,
    val waterBank: GroundPoint? = null,
    val haulingWater: Boolean = false,
    /** Rules v9 village detail; null for earlier histories. */
    val village: VillagePose? = null,
)

/** Read-only village scene: authoritative structures, stocks and each household's current work. */
data class VillagePose(
    val center: GroundPoint,
    val structures: List<StructurePose>,
    val villagers: List<VillagerPose>,
    /** Stock tiers 0..3 for the stores beside the centre. */
    val food: Int,
    val wood: Int,
    val stone: Int,
    /** Last harvest tier 0..3 shown as standing crops. */
    val harvest: Int,
)

data class StructurePose(
    val id: Int,
    val kind: StructureKind,
    val ground: GroundPoint,
    /** Construction progress in quarters, 4 when standing. */
    val stage: Int,
    val level: Int,
    val household: Int?,
    /** Rules v11: the home burned and has not yet been raised again. */
    val burned: Boolean = false,
)

data class VillagerPose(
    val id: Long,
    val household: Int,
    val body: Int,
    val task: VillageTask,
    val home: GroundPoint,
    val work: GroundPoint,
)

data class ShapingPose(
    val spent: Boolean,
    val warningAt: Long?,
    val shower: ShowerPose?,
    val retainedWater: List<WetRegionPose>,
    val rootedGroves: Int,
    val welfare: VillageWelfarePose?,
)

data class ShowerPose(val region: Int, val age: Int)
data class WetRegionPose(val region: Int, val retained: Int)

data class VillageWelfarePose(
    val water: Int,
    val unmet: Int,
    val harvest: Int,
    val firstShortage: Long?,
    val firstRecovery: Long?,
)

data class CreaturePose(
    val id: Long,
    val species: Species,
    val ground: GroundPoint,
    val heading: CreatureHeading,
    val action: CreatureAction,
    val juvenile: Boolean = false,
    val intent: WildlifeIntent? = null,
    val destination: GroundPoint? = null,
)

enum class WildlifeIntent { Forage, Browse, Berries, Water, Shelter, Range, Refuge, Prey, Meet, Roam }

data class WildlifeMoment(
    val tick: Long,
    val id: Long,
    val species: Species,
    val kind: WildlifeMilestone,
    val ground: GroundPoint,
)

data class WildlifeReadout(val foxes: Int, val catches: Long, val moments: List<WildlifeMoment>)

data class ForagePose(
    val id: Int,
    val kind: ForageKind,
    val ground: GroundPoint,
    val biomassTier: Int,
    val planted: Boolean = false,
)

data class ClimatePose(
    val day: Long,
    val isDaylight: Boolean,
    val weather: Weather,
    val daylight: Float,
    val dayPhase: Float = 0f,
    val watershed: WatershedPose? = null,
) {
    val isStorm: Boolean get() = weather == Weather.Rain && watershed?.season == Season.Wet
}

data class WatershedPose(
    val season: Season,
    val recharge: Int,
    val bankConditions: Map<LandmarkKind, BankCondition>,
)

enum class BankCondition { Parched, Holding, Recharged }

data class EcologyReadout(
    val rabbits: Int,
    val deer: Int,
    val forage: Int,
    val births: Long,
    val deaths: Long,
)

@Serializable
enum class CreatureHeading(internal val canonicalId: Int) {
    PositiveX(1), NegativeX(2), PositiveZ(3), NegativeZ(4),
}

internal fun WorldState.renderProjection(
    previous: WorldRenderProjection? = null,
    terrain: TerrainMap? = null,
): WorldRenderProjection {
    val rabbit = previous?.leadRabbit
    val heading = when {
        rabbit == null -> CreatureHeading.PositiveZ
        bunnyCell.x > rabbit.ground.x -> CreatureHeading.PositiveX
        bunnyCell.x < rabbit.ground.x -> CreatureHeading.NegativeX
        bunnyCell.z > rabbit.ground.z -> CreatureHeading.PositiveZ
        bunnyCell.z < rabbit.ground.z -> CreatureHeading.NegativeZ
        else -> rabbit.heading
    }
    val ground = bunnyCell.project(terrain)
    val moving = rabbit != null && rabbit.ground != ground
    val action = if (moving) CreatureAction.Walking else CreatureAction.Idle
    return WorldRenderProjection(tick, listOf(CreaturePose(bunnyId, Species.Rabbit, ground, heading, action)))
}

private const val LightningShown = 16L

internal fun EcologyState.renderProjection(terrain: TerrainMap) = WorldRenderProjection(
    tick = tick,
    creatures = creatures.map { animal ->
        CreaturePose(animal.id, animal.species, animal.cell.project(terrain), animal.heading, animal.action,
            juvenile = animal.wildlife != null && tick < animal.matureAt,
            intent = animal.wildlife?.let {
                when (animal.goal?.need) {
                    Need.Food -> when (animal.species) {
                        Species.Fox -> WildlifeIntent.Berries
                        Species.Deer, Species.Rabbit -> if (patches.any { patch -> patch.cell == animal.goal.cell && patch.kind == ForageKind.Browse })
                            WildlifeIntent.Browse else WildlifeIntent.Forage
                    }
                    Need.Water -> WildlifeIntent.Water
                    Need.Rest -> WildlifeIntent.Shelter
                    Need.Flee -> WildlifeIntent.Refuge
                    Need.Hunt -> WildlifeIntent.Prey
                    Need.Meet -> WildlifeIntent.Meet
                    Need.Roam -> WildlifeIntent.Range
                    else -> WildlifeIntent.Roam
                }
            },
            destination = animal.goal?.cell?.takeIf { animal.wildlife != null }?.project(terrain),
        )
    },
    forage = patches.map {
        ForagePose(it.id, it.kind, it.cell.project(terrain), (it.biomass * 4 + it.capacity - 1) / it.capacity, it.planted)
    },
    springs = springs.map { it.project(terrain) },
    ranges = habitat?.let { RangePose(it.refuges.map { refuge -> refuge.region }, it.herd.bound) },
    fire = fire?.let { burning ->
        val strike = chronicle?.events?.lastOrNull { it.kind == WorldEventKind.Lightning }?.takeIf { tick - it.tick < LightningShown }
        FirePose(
            burning.burning.map { FireTilePose(it.tile, tileCell(it.tile).project(terrain), (it.fuel / 60).coerceIn(1, 4)) },
            burning.scars.map { ScarPose(it.tile, tileCell(it.tile).project(terrain), successionAt(tick - it.at), it.kind == ScarKind.Felled) },
            burning.stands.associate { it.prop to successionAt(tick - it.at) },
            burning.wind,
            burning.strikes.map { tileCell(it.tile).project(terrain) },
            strike?.cell?.project(terrain),
            strike?.let { (tick - it.tick).toInt() } ?: 0,
        )
    },
    ecology = EcologyReadout(
        creatures.count { it.species == Species.Rabbit },
        creatures.count { it.species == Species.Deer },
        patches.sumOf { it.biomass },
        ledger.births,
        ledger.deaths,
    ),
    climate = environment?.let {
        climatePose(tick, it.weather).copy(watershed = it.recharge?.let { recharge ->
            val landmarks = checkNotNull(terrain.watershed).landmarks
            WatershedPose(seasonAt(tick), recharge, landmarks.associate { landmark ->
                var sum = 0
                var sampled = 0
                for (dz in -4..4) for (dx in -4..4) {
                    if (abs(dx) + abs(dz) > 4) continue
                    val cell = GridCell(landmark.x + dx, landmark.z + dz)
                    val ground = terrain.cellAt(cell.x, cell.z) ?: continue
                    if (!ground.walkable || ground.biome !in ForageBiomes) continue
                    sum += terrain.soilMoisture(cell, recharge)
                    sampled++
                }
                val moisture = if (sampled > 0) sum / sampled else recharge
                landmark.kind to when (cropPerWorker(moisture)) {
                    1 -> BankCondition.Parched
                    2 -> BankCondition.Holding
                    else -> BankCondition.Recharged
                }
            })
        })
    },
    settlements = settlements?.sites?.map { site ->
        val village = settlements.village
        if (village != null) return@map villagePose(site, village, terrain)
        SettlementPose(
            site.id,
            site.lots.take(site.homes).map { it.project(terrain) },
            site.field.project(terrain),
            site.center.takeIf { site.residents > 0 }?.project(terrain),
            site.welfare?.bank?.project(terrain),
            site.residents > 0 && (site.welfare?.unmet ?: 0) > 0,
        )
    },
    shaping = shaping?.let { shape ->
        ShapingPose(shape.spent, shape.warningAt, shape.shower?.let { ShowerPose(it.region, (tick - it.startedAt).toInt()) },
            shape.pools.map { WetRegionPose(it.region, it.retained) }, patches.count { it.rooted() },
            settlements?.sites?.firstOrNull()?.welfare?.let {
                VillageWelfarePose(it.water, it.unmet, it.cropYield, it.firstShortage, it.firstRecovery)
            },
        )
    },
    wildlife = wildlife?.let { history ->
        WildlifeReadout(creatures.count { it.species == Species.Fox }, history.catches, history.events.map {
            WildlifeMoment(it.tick, it.id, it.species, it.kind, it.cell.project(terrain))
        })
    },
)

private fun EcologyState.villagePose(site: Settlement, village: VillageState, terrain: TerrainMap): SettlementPose {
    val households = village.active(site.id)
    val structures = village.structures.filter { it.siteId == site.id }
    val occupied = households.mapNotNull { household -> structures.firstOrNull { it.id == household.home }?.let { it.id to household.id } }.toMap()
    val homes = structures.filter { it.kind == StructureKind.Home && it.completedAt != null && it.id in occupied }
    val underway = village.underway(site.id)
    val fields = village.fields(site.id).map { it.cell }.ifEmpty { listOf(site.field) }
    val local = patches.filter { it.cell.region() == site.center.region() }
    fun nearest(kind: ForageKind) = local.filter { it.kind == kind && !it.planted }
        .sortedWith(compareBy<ForagePatch> { abs(it.cell.x - site.center.x) + abs(it.cell.z - site.center.z) }.thenBy { it.id })
        .map { it.cell }
    val meadow = nearest(ForageKind.Meadow)
    val browse = nearest(ForageKind.Browse)
    val quarry = quarryCell(site.center, terrain)
    val villagers = buildList {
        for (household in households) {
            val home = structures.firstOrNull { it.id == household.home }?.cell ?: site.center
            // At most two figures per household keep the scene readable on phones.
            for (member in 0 until minOf(household.members, 2)) {
                val pick = household.id + member
                val work = when (household.task) {
                    VillageTask.Farming -> fields[pick % fields.size]
                    VillageTask.Gathering -> meadow.getOrNull(pick % maxOf(1, minOf(3, meadow.size))) ?: site.field
                    VillageTask.Woodcutting -> browse.getOrNull(pick % maxOf(1, minOf(3, browse.size))) ?: site.center
                    VillageTask.Quarrying -> quarry ?: site.center
                    VillageTask.Building, VillageTask.Settling -> underway?.cell ?: home
                    VillageTask.HaulingWater -> checkNotNull(site.welfare).bank
                    VillageTask.Idle -> home
                }
                add(VillagerPose(household.id * 8L + member, household.id, (household.name + member) % 3, household.task,
                    home.project(terrain), work.project(terrain)))
            }
        }
    }
    fun tier(amount: Int, full: Int) = when {
        amount <= 0 -> 0
        amount < full / 3 -> 1
        amount < full -> 2
        else -> 3
    }
    return SettlementPose(
        site.id,
        homes.map { it.cell.project(terrain) },
        site.field.project(terrain),
        null,
        site.welfare?.bank?.project(terrain),
        households.isNotEmpty() && (site.welfare?.unmet ?: 0) > 0,
        VillagePose(
            site.center.project(terrain),
            structures.map {
                StructurePose(it.id, it.kind, it.cell.project(terrain),
                    if (it.complete) 4 else (it.progress * 4 / it.required).coerceIn(0, 3), it.level, occupied[it.id],
                    burned = it.burnedAt != null && !it.complete)
            },
            villagers,
            tier(site.food, site.residents * 6),
            tier(site.wood, 24),
            tier(site.stone, 12),
            tier(site.welfare?.cropYield ?: 0, maxOf(1, site.residents * 2)),
        ),
    )
}

/** Nearest upland rock to a settlement, where its masons quarry. */
private fun quarryCell(center: GridCell, terrain: TerrainMap): GridCell? {
    var best: GridCell? = null
    var bestDistance = Int.MAX_VALUE
    for (dz in -12..12) for (dx in -12..12) {
        val distance = abs(dx) + abs(dz)
        if (distance > 12 || distance >= bestDistance) continue
        val ground = terrain.cellAt(center.x + dx, center.z + dz) ?: continue
        if (!ground.walkable || ground.biome != Biome.RockyUpland) continue
        best = GridCell(center.x + dx, center.z + dz)
        bestDistance = distance
    }
    return best
}

internal fun climatePose(tick: Long, weather: Weather): ClimatePose {
    val phase = tick % DayLength
    val daylight = minOf((phase + 60f) / 120f, (660f - phase) / 120f).coerceIn(0f, 1f)
    val dawn = ((phase - 1140f) / 120f).coerceIn(0f, 0.5f)
    return ClimatePose(tick / DayLength + 1, phase < DayLength / 2, weather,
        maxOf(daylight, dawn), phase.toFloat() / DayLength)
}

internal fun GridCell.project(terrain: TerrainMap?) =
    GroundPoint(x, z, terrain?.terrainCell(this)?.elevationTwice ?: 0)

private val ForageBiomes = setOf(Biome.Meadow, Biome.PineForest)
