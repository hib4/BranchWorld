package id.branchworld.simulation.command

import id.branchworld.simulation.core.GridCell
import id.branchworld.simulation.ecology.Species
import id.branchworld.simulation.ecology.life.PlantKind
import id.branchworld.simulation.ecology.settlement.PlanOption
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface WorldCommand {
    val tick: Long
    val sequence: Long
}

@Serializable
@SerialName("destination")
internal data class SetDestination(
    override val tick: Long,
    override val sequence: Long,
    val entityId: Long,
    val cell: GridCell,
) : WorldCommand

@Serializable
@SerialName("reforest")
data class ReforestRegion(
    override val tick: Long,
    override val sequence: Long,
    val region: Int,
) : WorldCommand

@Serializable
@SerialName("supplement")
data class SupplementForage(
    override val tick: Long,
    override val sequence: Long,
    val region: Int,
) : WorldCommand

@Serializable
@SerialName("call_rain")
internal data class CallRainRegion(
    override val tick: Long,
    override val sequence: Long,
    val region: Int,
) : WorldCommand

/** The Keeper endorses one of the plans in the village's open petition (rules v9). */
@Serializable
@SerialName("endorse_plan")
data class EndorsePlan(
    override val tick: Long,
    override val sequence: Long,
    val petitionId: Int,
    val option: PlanOption,
) : WorldCommand

/** The Keeper declares a region a refuge where nothing new is built (rules v10). */
@Serializable
@SerialName("protect_region")
internal data class ProtectRegion(
    override val tick: Long,
    override val sequence: Long,
    val region: Int,
) : WorldCommand

/** The Keeper cuts a firebreak across the wind through a region, from one natural break to the next (rules v11). */
@Serializable
@SerialName("cut_firebreak")
internal data class CutFirebreak(
    override val tick: Long,
    override val sequence: Long,
    val region: Int,
) : WorldCommand

/** The player sets an animal down on a cell (rules v12). */
@Serializable
@SerialName("place_creature")
internal data class PlaceCreature(
    override val tick: Long,
    override val sequence: Long,
    val species: Species,
    val cell: GridCell,
) : WorldCommand

/** The player lifts an animal out of the world (rules v12). */
@Serializable
@SerialName("remove_creature")
internal data class RemoveCreature(
    override val tick: Long,
    override val sequence: Long,
    val entityId: Long,
) : WorldCommand

/** The player plants a young tree or a patch of grass on a cell (rules v12). */
@Serializable
@SerialName("place_plant")
internal data class PlacePlant(
    override val tick: Long,
    override val sequence: Long,
    val kind: PlantKind,
    val cell: GridCell,
) : WorldCommand

/** The player clears a planted tree or grass patch (rules v12). */
@Serializable
@SerialName("clear_plant")
internal data class ClearPlant(
    override val tick: Long,
    override val sequence: Long,
    val cell: GridCell,
) : WorldCommand

/** Commands only a rules-v12 living world accepts. */
internal val WorldCommand.living get() = this is PlaceCreature || this is RemoveCreature || this is PlacePlant || this is ClearPlant
