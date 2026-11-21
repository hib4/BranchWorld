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
import id.branchworld.game.ecology.cropPerWorker
import id.branchworld.game.ecology.seasonAt
import id.branchworld.game.ecology.soilMoisture
import id.branchworld.game.ecology.terrainCell
import id.branchworld.game.terrain.Biome
import id.branchworld.game.terrain.LandmarkKind
import id.branchworld.game.terrain.TerrainMap
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlin.math.abs

class WorldRenderSource(val terrain: TerrainMap, val poses: StateFlow<WorldRenderProjection>)

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
) {
    val leadRabbit: CreaturePose? get() = creatures.firstOrNull { it.species == Species.Rabbit }
}

data class GroundPoint(val x: Int, val z: Int, val elevationTwice: Int)

data class SettlementPose(
    val id: Int, val homes: List<GroundPoint>, val field: GroundPoint, val resident: GroundPoint?,
    val waterBank: GroundPoint? = null,
    val haulingWater: Boolean = false,
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
