package id.branchworld.game.ecology

import id.branchworld.game.simulation.GridCell
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/** Rules v9: named households, construction, petitions to the Keeper and a truthful village chronicle. */
internal const val VillageRulesVersion = 9
internal const val VillageLots = 6
internal const val HomeCapacity9 = 3
internal const val UpgradedCapacity = 5
internal const val MaximumHouseholds = 8
internal const val MaximumMembers = 6
internal const val ChronicleLimit = 64
internal const val LeaveHardship = 3
internal const val PetitionTurns = 2

/** Family names are proper nouns, so they stay in shared data instead of localized resources. */
val HouseholdNames = listOf(
    "Alder", "Wren", "Thorn", "Hazel", "Rook", "Fenn", "Bramble", "Linden",
    "Sorrel", "Teal", "Birch", "Rowan", "Marsh", "Holloway", "Pell", "Quill",
)

@Serializable
enum class Trade(internal val code: Int) { Farmer(1), Forager(2), Woodcutter(3), Mason(4) }

@Serializable
enum class VillageTask(internal val code: Int) {
    Farming(1), Gathering(2), Woodcutting(3), Quarrying(4), Building(5), HaulingWater(6), Settling(7), Idle(8),
}

@Serializable
enum class Hardship(internal val code: Int) { Thirst(1), Hunger(2), Crowding(3), /** Rules v11: the home burned and no other roof was free. */ Fire(4) }

@Serializable
enum class StructureKind(internal val code: Int) { Home(1), Field(2), Well(3), Clearing(4) }

@Serializable
enum class PetitionKind(internal val code: Int) { Water(1), Growth(2), /** Rules v11: a dry storm gathers over the woods. */ Ember(3) }

@Serializable
enum class PlanOption(internal val code: Int, val petition: PetitionKind) {
    Well(1, PetitionKind.Water), RiverFlats(2, PetitionKind.Water),
    ClearWoods(3, PetitionKind.Growth), BuildWithin(4, PetitionKind.Growth),
    /** Rules v10: settle the herd's wet-season pasture. */
    Pasture(5, PetitionKind.Growth),
    /** Rules v11: fell the pines and clear the brush around the homes before the storm breaks. */
    FellAroundHomes(6, PetitionKind.Ember),
    /** Rules v11: keep the old woods standing. */
    KeepGrove(7, PetitionKind.Ember),
}

@Serializable
enum class VillageEventKind(internal val code: Int) {
    Founded(1), HouseholdArrived(2), HouseholdLeft(3), MemberBorn(4), WorkBegun(5), WorkCompleted(6),
    PetitionRaised(7), PlanChosen(8), HamletFounded(9), FieldGrazed(10), FieldFlooded(11), Thirst(12), Hunger(13),
    /** Rules v11: a home burned. [VillageEvent.subject] is the structure. */
    HomeBurned(14),
}

@Serializable
internal data class Household(
    val id: Int,
    val name: Int,
    val siteId: Int,
    val home: Int,
    val members: Int,
    val trade: Trade,
    val joinedAt: Long,
    val hardship: Int = 0,
    val task: VillageTask = VillageTask.Idle,
    val leftAt: Long? = null,
    val leftBecause: Hardship? = null,
) {
    val active: Boolean get() = leftAt == null
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
internal data class Structure(
    val id: Int,
    val siteId: Int,
    val kind: StructureKind,
    val cell: GridCell,
    val startedAt: Long,
    val required: Int,
    val progress: Int = 0,
    val level: Int = 1,
    val completedAt: Long? = null,
    /** Rules v11: the tick this home last burned. Omitted in earlier saves. */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val burnedAt: Long? = null,
) {
    val complete: Boolean get() = progress >= required
}

@Serializable
internal data class Petition(
    val id: Int,
    val kind: PetitionKind,
    val siteId: Int,
    val raisedAt: Long,
    val deadline: Long,
    val options: List<PlanOption>,
    val choice: PlanOption? = null,
    val decidedAt: Long? = null,
    val byKeeper: Boolean = false,
) {
    val open: Boolean get() = choice == null
}

/** One chronicle line. [subject] is a household, structure or petition id, [detail] a kind-specific value. */
@Serializable
internal data class VillageEvent(
    val tick: Long,
    val kind: VillageEventKind,
    val siteId: Int,
    val subject: Int = 0,
    val detail: Int = 0,
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
internal data class VillageState(
    val households: List<Household> = emptyList(),
    val structures: List<Structure> = emptyList(),
    val petitions: List<Petition> = emptyList(),
    val chronicle: List<VillageEvent> = emptyList(),
    /** Natural browse patches cleared for farmland. Their biomass went to the ledger as consumed timber. */
    val cleared: List<Int> = emptyList(),
    val nextId: Int = 1,
    val thirstTurns: Int = 0,
    /** Herbivore feeding ticks beside active fields since the last settlement turn. */
    val grazing: Int = 0,
    val lastGrazed: Long? = null,
    val lastFlood: Long? = null,
    /** Rules v10: growth turns a fed village could not house anyone. Omitted in earlier saves. */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val crowding: Int = 0,
) {
    fun active(siteId: Int) = households.filter { it.siteId == siteId && it.active }
    fun openPetition(): Petition? = petitions.firstOrNull { it.open }
    fun chosen(option: PlanOption) = petitions.any { it.choice == option }
    fun capacity(siteId: Int) = structures.filter { it.siteId == siteId && it.kind == StructureKind.Home && it.completedAt != null }
        .sumOf { if (it.level >= 2 && it.complete) UpgradedCapacity else HomeCapacity9 }
    fun fields(siteId: Int) = structures.filter {
        it.siteId == siteId && (it.kind == StructureKind.Field || it.kind == StructureKind.Clearing) && it.complete
    }
    fun underway(siteId: Int) = structures.firstOrNull { it.siteId == siteId && !it.complete }
}
