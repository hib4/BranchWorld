package id.branchworld.game.simulation

import id.branchworld.game.ecology.CreatureAction
import id.branchworld.game.ecology.DayLength
import id.branchworld.game.ecology.EcologyState
import id.branchworld.game.ecology.ForageKind
import id.branchworld.game.ecology.Species
import id.branchworld.game.ecology.Weather
import id.branchworld.game.ecology.terrainCell
import id.branchworld.game.terrain.TerrainMap
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

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
) {
    val leadRabbit: CreaturePose? get() = creatures.firstOrNull { it.species == Species.Rabbit }
}

data class GroundPoint(val x: Int, val z: Int, val elevationTwice: Int)

data class SettlementPose(
    val id: Int, val homes: List<GroundPoint>, val field: GroundPoint, val resident: GroundPoint?,
)

data class CreaturePose(
    val id: Long,
    val species: Species,
    val ground: GroundPoint,
    val heading: CreatureHeading,
    val action: CreatureAction,
)

data class ForagePose(
    val id: Int,
    val kind: ForageKind,
    val ground: GroundPoint,
    val biomassTier: Int,
    val planted: Boolean = false,
)

data class ClimatePose(val day: Long, val isDaylight: Boolean, val weather: Weather, val daylight: Float)

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
    creatures = creatures.map { CreaturePose(it.id, it.species, it.cell.project(terrain), it.heading, it.action) },
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
        val phase = tick % DayLength
        val daylight = minOf((phase + 60f) / 120f, (660f - phase) / 120f).coerceIn(0f, 1f)
        val dawn = ((phase - 1140f) / 120f).coerceIn(0f, 0.5f)
        ClimatePose(tick / DayLength + 1, phase < DayLength / 2, it.weather, maxOf(daylight, dawn))
    },
    settlements = settlements?.sites?.map { site ->
        SettlementPose(
            site.id,
            site.lots.take(site.homes).map { it.project(terrain) },
            site.field.project(terrain),
            site.center.takeIf { site.residents > 0 }?.project(terrain),
        )
    },
)

private fun GridCell.project(terrain: TerrainMap?) =
    GroundPoint(x, z, terrain?.terrainCell(this)?.elevationTwice ?: 0)
