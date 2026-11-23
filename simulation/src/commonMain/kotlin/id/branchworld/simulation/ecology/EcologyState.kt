package id.branchworld.simulation.ecology

import id.branchworld.simulation.core.CivilizationRulesVersion
import id.branchworld.simulation.core.CreatureHeading
import id.branchworld.simulation.core.EcologyRulesVersion
import id.branchworld.simulation.core.EnvironmentalRulesVersion
import id.branchworld.simulation.core.FireRulesVersion
import id.branchworld.simulation.core.GridCell
import id.branchworld.simulation.core.LifeRulesVersion
import id.branchworld.simulation.core.RangeRulesVersion
import id.branchworld.simulation.core.ShapingRulesVersion
import id.branchworld.simulation.core.VillageRulesVersion
import id.branchworld.simulation.core.WatershedRulesVersion
import id.branchworld.simulation.core.WildlifeRulesVersion
import id.branchworld.simulation.ecology.chronicle.WorldChronicle
import id.branchworld.simulation.ecology.environment.Environment
import id.branchworld.simulation.ecology.environment.ShapingState
import id.branchworld.simulation.ecology.fire.FireState
import id.branchworld.simulation.ecology.life.CreatureLife
import id.branchworld.simulation.ecology.life.LifeState
import id.branchworld.simulation.ecology.range.HabitatState
import id.branchworld.simulation.ecology.settlement.SettlementWorld
import id.branchworld.simulation.ecology.wildlife.WildlifeHistory
import id.branchworld.simulation.ecology.wildlife.WildlifeLife
import id.branchworld.simulation.terrain.TerrainIdentity
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
enum class Species(val canonicalId: Int) { Rabbit(1), Deer(2), Fox(3), /** Rules v12. */ Wolf(4) }
@Serializable
enum class ForageKind(internal val canonicalId: Int) { Meadow(1), Browse(2) }
@Serializable
enum class CreatureAction(internal val canonicalId: Int) {
    Idle(0), Walking(1), Eating(2), Drinking(3), Resting(4),
    Fleeing(5), Stalking(6), Chasing(7),
    /** Rules v12. */
    Alert(8), Howling(9), Sleeping(10), Digging(11), Courting(12),
}

@Serializable
enum class Need(internal val canonicalId: Int) {
    Food(1), Water(2), Rest(3), Waypoint(4), Roam(5),
    Flee(6), Hunt(7), Meet(8),
    /** Rules v12. */
    Mate(9), Den(10), Rally(11), Hide(12),
}

internal const val MaxReserve = 1000
internal const val MaxPerSpecies = 8
internal const val MaturityTicks = 240L
internal val LegacySpecies = listOf(Species.Rabbit, Species.Deer)

@Serializable
data class ForagePatch(
    val id: Int,
    val cell: GridCell,
    val kind: ForageKind,
    val capacity: Int,
    val regrowth: Int,
    val biomass: Int = capacity,
    val planted: Boolean = false,
)

@Serializable
data class Goal(val need: Need, val cell: GridCell)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class Creature(
    val id: Long,
    val species: Species,
    val cell: GridCell,
    val food: Int = 600,
    val water: Int = 780,
    val energy: Int = 780,
    val matureAt: Long = 0,
    val breedAfter: Long = 0,
    val depletedTicks: Int = 0,
    val heading: CreatureHeading = CreatureHeading.PositiveZ,
    val action: CreatureAction = CreatureAction.Idle,
    val goal: Goal? = null,
    val waypoint: GridCell? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val wildlife: WildlifeLife? = null,
    /** Rules v12 sex, stamina, alarm and pack. */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val life: CreatureLife? = null,
)

@Serializable
data class EcologyLedger(
    val consumed: Long = 0,
    val regrown: Long = 0,
    val births: Long = 0,
    val deaths: Long = 0,
    val introduced: Long = 0,
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class EcologyState(
    val terrain: TerrainIdentity,
    val tick: Long,
    val randomState: ULong,
    val patches: List<ForagePatch>,
    val springs: List<GridCell>,
    val creatures: List<Creature>,
    val nextEntityId: Long,
    val ledger: EcologyLedger = EcologyLedger(),
    val environment: Environment? = null,
    // Omit the new field in old rules so checksum re-encoding stays byte-for-byte compatible.
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val settlements: SettlementWorld? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val wildlife: WildlifeHistory? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val shaping: ShapingState? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val habitat: HabitatState? = null,
    /** Rules v11 wildfire, regrowth and felled woods. */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val fire: FireState? = null,
    /** Rules v11 world events with their recorded causes. */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val chronicle: WorldChronicle? = null,
    /** Rules v12 living sandbox: placed life, packs, warrens and census. */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val life: LifeState? = null,
) {
    val rulesVersion: Int get() = when {
        life != null -> LifeRulesVersion
        fire != null -> FireRulesVersion
        habitat != null -> RangeRulesVersion
        settlements?.village != null -> VillageRulesVersion
        shaping != null -> ShapingRulesVersion
        wildlife != null -> WildlifeRulesVersion
        environment?.recharge != null -> WatershedRulesVersion
        settlements != null -> CivilizationRulesVersion
        environment != null -> EnvironmentalRulesVersion
        else -> EcologyRulesVersion
    }
}
