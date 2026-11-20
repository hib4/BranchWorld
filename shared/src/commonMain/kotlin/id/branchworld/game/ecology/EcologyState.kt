package id.branchworld.game.ecology

import id.branchworld.game.simulation.CreatureHeading
import id.branchworld.game.simulation.GridCell
import id.branchworld.game.simulation.TerrainIdentity

enum class Species(internal val canonicalId: Int) { Rabbit(1), Deer(2) }
enum class ForageKind(internal val canonicalId: Int) { Meadow(1), Browse(2) }
enum class CreatureAction(internal val canonicalId: Int) {
    Idle(0), Walking(1), Eating(2), Drinking(3), Resting(4),
}

internal enum class Need(internal val canonicalId: Int) {
    Food(1), Water(2), Rest(3), Waypoint(4), Roam(5),
}

internal const val EcologyRulesVersion = 3
internal const val MaxReserve = 1000
internal const val MaxPerSpecies = 8
internal const val MaturityTicks = 240L

internal data class ForagePatch(
    val id: Int,
    val cell: GridCell,
    val kind: ForageKind,
    val capacity: Int,
    val regrowth: Int,
    val biomass: Int = capacity,
)

internal data class Goal(val need: Need, val cell: GridCell)

internal data class Creature(
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
)

internal data class EcologyLedger(
    val consumed: Long = 0,
    val regrown: Long = 0,
    val births: Long = 0,
    val deaths: Long = 0,
)

internal data class EcologyState(
    val terrain: TerrainIdentity,
    val tick: Long,
    val randomState: ULong,
    val patches: List<ForagePatch>,
    val springs: List<GridCell>,
    val creatures: List<Creature>,
    val nextEntityId: Long,
    val ledger: EcologyLedger = EcologyLedger(),
)
