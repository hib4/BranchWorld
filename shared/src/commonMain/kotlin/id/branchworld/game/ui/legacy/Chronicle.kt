package id.branchworld.game.ui.legacy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import branchworld.shared.generated.resources.*
import id.branchworld.game.domain.session.IslandLoad
import id.branchworld.game.ui.components.Block
import id.branchworld.game.ui.components.BlockSize
import id.branchworld.game.ui.components.Board
import id.branchworld.game.ui.components.ScrollColumn
import id.branchworld.game.ui.theme.Wood
import id.branchworld.game.ui.world.echo.echoDay
import id.branchworld.simulation.ecology.chapters.Chapter
import id.branchworld.simulation.ecology.environment.DayLength
import id.branchworld.simulation.ecology.settlement.Hardship
import id.branchworld.simulation.ecology.settlement.Household
import id.branchworld.simulation.ecology.settlement.HouseholdNames
import id.branchworld.simulation.ecology.settlement.Petition
import id.branchworld.simulation.ecology.settlement.PetitionKind
import id.branchworld.simulation.ecology.settlement.PlanOption
import id.branchworld.simulation.ecology.settlement.SettlementWorld
import id.branchworld.simulation.ecology.settlement.StructureKind
import id.branchworld.simulation.ecology.settlement.Trade
import id.branchworld.simulation.ecology.settlement.VillageEvent
import id.branchworld.simulation.ecology.settlement.VillageEventKind
import id.branchworld.simulation.ecology.settlement.VillageState
import id.branchworld.simulation.ecology.settlement.VillageTask
import id.branchworld.simulation.projection.GroundPoint
import id.branchworld.simulation.projection.project
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Proper name of a settlement: Hollowmere and Reedford in the Fading Grove, Cairnholt and Westmead on Cairnwater. */
@Composable
internal fun settlementName(siteId: Int, chapter: Chapter?): String = stringResource(when (chapter) {
    Chapter.FadingGrove -> if (siteId == 2) Res.string.village_hamlet else Res.string.village_hollowmere
    Chapter.LastCrossing -> if (siteId == 2) Res.string.village_westmead else Res.string.village_cairnholt
    Chapter.EmberSeason -> if (siteId == 2) Res.string.village_fernlea else Res.string.village_sedgewick
    null -> if (siteId == 2) Res.string.village_new_hamlet else Res.string.village_generic
})

@Composable
internal fun familyName(household: Household?): String =
    household?.let { HouseholdNames[it.name] } ?: stringResource(Res.string.village_generic)

internal fun PlanOption.title(): StringResource = when (this) {
    PlanOption.Well -> Res.string.plan_well
    PlanOption.RiverFlats -> Res.string.plan_flats
    PlanOption.ClearWoods -> Res.string.plan_clear
    PlanOption.BuildWithin -> Res.string.plan_within
    PlanOption.Pasture -> Res.string.plan_pasture
    PlanOption.FellAroundHomes -> Res.string.plan_fell
    PlanOption.KeepGrove -> Res.string.plan_keep_grove
}

internal fun VillageTask.label(night: Boolean): StringResource = when {
    night && this != VillageTask.HaulingWater -> Res.string.task_resting
    else -> when (this) {
        VillageTask.Farming -> Res.string.task_farming
        VillageTask.Gathering -> Res.string.task_gathering
        VillageTask.Woodcutting -> Res.string.task_woodcutting
        VillageTask.Quarrying -> Res.string.task_quarrying
        VillageTask.Building -> Res.string.task_building
        VillageTask.HaulingWater -> Res.string.task_hauling
        VillageTask.Settling -> Res.string.task_settling
        VillageTask.Idle -> Res.string.task_idle
    }
}

private fun Trade.noun() = when (this) {
    Trade.Farmer -> Res.string.trade_farmer
    Trade.Forager -> Res.string.trade_forager
    Trade.Woodcutter -> Res.string.trade_woodcutter
    Trade.Mason -> Res.string.trade_mason
}

/** One truthful chronicle line, written from the recorded event and the village that recorded it. */
@Composable
internal fun VillageEvent.describe(village: VillageState, chapter: Chapter?): String {
    val place = settlementName(siteId, chapter)
    val household = village.households.firstOrNull { it.id == subject }
    val structure = village.structures.firstOrNull { it.id == subject }
    return when (kind) {
        VillageEventKind.Founded -> stringResource(Res.string.chronicle_founded,
            village.structures.count { it.siteId == siteId && it.kind == StructureKind.Home && it.startedAt == tick }, place)
        VillageEventKind.HouseholdArrived -> stringResource(Res.string.chronicle_arrived, familyName(household),
            stringResource(Trade.entries.first { it.code == detail }.noun()))
        VillageEventKind.HouseholdLeft -> stringResource(when (detail) {
            Hardship.Thirst.code -> Res.string.chronicle_left_thirst
            Hardship.Crowding.code -> Res.string.chronicle_left_crowding
            Hardship.Fire.code -> Res.string.chronicle_left_fire
            else -> Res.string.chronicle_left_hunger
        }, familyName(household), place)
        VillageEventKind.MemberBorn -> stringResource(Res.string.chronicle_grew, familyName(household), detail)
        VillageEventKind.WorkBegun -> when (structure?.kind) {
            StructureKind.Well -> stringResource(Res.string.chronicle_begun_well)
            StructureKind.Clearing -> stringResource(Res.string.chronicle_begun_clearing)
            else -> if (detail >= 2) stringResource(Res.string.chronicle_begun_enlarge,
                familyName(village.households.firstOrNull { it.home == subject }))
            else stringResource(Res.string.chronicle_begun_home)
        }
        VillageEventKind.WorkCompleted -> when (structure?.kind) {
            StructureKind.Well -> stringResource(Res.string.chronicle_done_well)
            StructureKind.Clearing -> stringResource(Res.string.chronicle_done_clearing)
            else -> stringResource(if (detail >= 2) Res.string.chronicle_done_enlarge else Res.string.chronicle_done_home)
        }
        VillageEventKind.PetitionRaised -> stringResource(when (detail) {
            PetitionKind.Water.code -> Res.string.chronicle_petition_water
            PetitionKind.Ember.code -> Res.string.chronicle_petition_ember
            else -> Res.string.chronicle_petition_growth
        }, place)
        VillageEventKind.PlanChosen -> {
            val plan = stringResource(PlanOption.entries.first { it.code == detail }.title())
            val keeper = village.petitions.firstOrNull { it.id == subject }?.byKeeper == true
            if (keeper) stringResource(Res.string.chronicle_keeper_chose, plan)
            else stringResource(Res.string.chronicle_village_chose, place, plan)
        }
        VillageEventKind.HamletFounded -> stringResource(when {
            chapter == Chapter.LastCrossing || village.chosen(PlanOption.Pasture) -> Res.string.chronicle_pasture
            village.chosen(PlanOption.RiverFlats) -> Res.string.chronicle_hamlet
            else -> Res.string.chronicle_new_hamlet
        }, familyName(household), place)
        VillageEventKind.FieldGrazed -> stringResource(Res.string.chronicle_grazed, detail)
        VillageEventKind.FieldFlooded -> stringResource(Res.string.chronicle_flooded, place)
        VillageEventKind.Thirst -> stringResource(Res.string.chronicle_thirst, place)
        VillageEventKind.Hunger -> stringResource(Res.string.chronicle_hunger, place)
        VillageEventKind.HomeBurned -> stringResource(Res.string.chronicle_home_burned, place)
    }
}

/** Where a chronicle event happened, for focusing the camera. */
internal fun VillageEvent.place(world: SettlementWorld, terrain: id.branchworld.simulation.terrain.TerrainMap): GroundPoint? {
    val village = world.village ?: return null
    val site = world.sites.firstOrNull { it.id == siteId } ?: return null
    val cell = when (kind) {
        VillageEventKind.WorkBegun, VillageEventKind.WorkCompleted, VillageEventKind.HomeBurned -> village.structures.firstOrNull { it.id == subject }?.cell
        VillageEventKind.HouseholdLeft, VillageEventKind.HouseholdArrived, VillageEventKind.MemberBorn, VillageEventKind.HamletFounded ->
            village.households.firstOrNull { it.id == subject }?.let { household ->
                village.structures.firstOrNull { it.id == household.home }?.cell
            }
        VillageEventKind.FieldGrazed, VillageEventKind.FieldFlooded -> site.field
        VillageEventKind.Thirst -> site.welfare?.bank
        else -> site.center
    } ?: site.center
    return cell.project(terrain)
}

@Composable
internal fun petitionTitle(petition: Petition, chapter: Chapter?): String = stringResource(
    when (petition.kind) {
        PetitionKind.Water -> Res.string.petition_water_title
        PetitionKind.Growth -> Res.string.petition_growth_title
        PetitionKind.Ember -> Res.string.petition_ember_title
    },
    settlementName(petition.siteId, chapter),
)

@Composable
private fun planDetail(option: PlanOption, village: VillageState): String = when (option) {
    PlanOption.Well -> stringResource(Res.string.plan_well_detail)
    PlanOption.RiverFlats -> stringResource(Res.string.plan_flats_detail, familyName(
        village.households.filter { it.active && it.siteId == 1 && it.trade == Trade.Farmer }
            .maxWithOrNull(compareBy<Household> { it.members }.thenByDescending { it.id }),
    ))
    PlanOption.ClearWoods -> stringResource(Res.string.plan_clear_detail)
    PlanOption.BuildWithin -> stringResource(Res.string.plan_within_detail)
    PlanOption.Pasture -> stringResource(Res.string.plan_pasture_detail)
    PlanOption.FellAroundHomes -> stringResource(Res.string.plan_fell_detail)
    PlanOption.KeepGrove -> stringResource(Res.string.plan_keep_grove_detail)
}

/** The village's question to the Keeper. Answering is a timeline command, so it can be rewound and compared. */
@Composable
internal fun PetitionCard(island: IslandLoad.Ready, modifier: Modifier = Modifier, compact: Boolean = false) {
    val view by island.timelineView.collectAsStateWithLifecycle()
    // Queued endorsements change the intervention feedback, not the timeline summary.
    val queued by island.interventions.collectAsStateWithLifecycle()
    val petition = remember(view, queued) { island.openPetition() } ?: return
    val village = island.clock.state.settlements?.village ?: return
    val pending = remember(view, queued) { island.pendingEndorsement() }
    val chapter = island.chapter
    var reasons by remember(petition.id) { mutableStateOf(!compact) }
    Board(modifier.widthIn(max = 404.dp).fillMaxWidth(), gap = 3.dp) {
        Text(petitionTitle(petition, chapter), Modifier.semantics {
            heading()
            liveRegion = LiveRegionMode.Polite
        }, style = MaterialTheme.typography.titleMedium)
        ScrollColumn(spacing = 6.dp) {
            if (pending != null) {
                Text(stringResource(Res.string.petition_endorsed, stringResource(pending.option.title())),
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
                Block(stringResource(Res.string.petition_change), island::cancelEndorsement, enabled = view.editsAllowed,
                    paint = Wood.BeechLight, size = BlockSize.Small)
            } else {
                if (!view.editsAllowed && !view.seeking) Text(stringResource(Res.string.petition_past),
                    style = MaterialTheme.typography.bodySmall, color = Wood.WalnutSoft)
                if (compact) Block(stringResource(if (reasons) Res.string.petition_hide_reasons else Res.string.petition_show_reasons),
                    { reasons = !reasons }, paint = Wood.BeechLight, size = BlockSize.Small)
                if (reasons) Text(stringResource(when (petition.kind) {
                    PetitionKind.Water -> Res.string.petition_water_body
                    PetitionKind.Growth -> Res.string.petition_growth_body
                    PetitionKind.Ember -> Res.string.petition_ember_body
                }),
                    style = MaterialTheme.typography.bodyMedium)
                for (option in petition.options) {
                    Block(stringResource(Res.string.petition_endorse) + " · " + stringResource(option.title()),
                        { island.endorse(option) }, Modifier.fillMaxWidth(), enabled = view.editsAllowed,
                        paint = Wood.BeechLight, size = BlockSize.Small)
                    if (reasons) Text(planDetail(option, village), style = MaterialTheme.typography.bodySmall, color = Wood.WalnutSoft)
                }
                val now = island.clock.state.tick
                val sameDay = petition.deadline / DayLength == now / DayLength
                val when_ = when {
                    sameDay && petition.deadline % DayLength < DayLength / 2 -> Res.string.petition_by_dusk
                    sameDay -> Res.string.petition_tonight
                    else -> Res.string.petition_tomorrow
                }
                Text(stringResource(Res.string.petition_deadline, stringResource(when_)), style = MaterialTheme.typography.labelSmall)

            }
        }
    }
}

/** Households, what they are doing, and the history the village actually recorded. */
@Composable
internal fun VillagePanel(island: IslandLoad.Ready, onFocus: (GroundPoint) -> Unit) {
    val view by island.timelineView.collectAsStateWithLifecycle()
    val pose by island.world.poses.collectAsStateWithLifecycle()
    val world = island.clock.state.settlements ?: return
    val village = world.village ?: return
    val chapter = island.chapter
    val night = pose.climate?.isDaylight == false
    if (world.sites.isEmpty()) {
        Text(stringResource(Res.string.settlement_searching))
        return
    }
    for (site in world.sites) {
        val active = village.active(site.id)
        Text(settlementName(site.id, chapter), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(Res.string.village_people, site.residents, site.homes), style = MaterialTheme.typography.bodySmall)
        when {
            (site.welfare?.unmet ?: 0) > 0 -> Text(stringResource(Res.string.village_water_short), style = MaterialTheme.typography.bodySmall)
            site.food < site.residents * 2 -> Text(stringResource(Res.string.village_stores_low), style = MaterialTheme.typography.bodySmall)
        }
        village.underway(site.id)?.let { work ->
            val what = when {
                work.kind == StructureKind.Well -> Res.string.structure_well
                work.kind == StructureKind.Clearing -> Res.string.structure_clearing
                work.level >= 2 -> Res.string.structure_enlarged
                else -> Res.string.structure_home
            }
            Text(stringResource(Res.string.village_building, stringResource(what)), style = MaterialTheme.typography.bodySmall)
        }
        for (household in active) {
            val home = village.structures.firstOrNull { it.id == household.home }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.village_family, familyName(household)), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(Res.string.village_family_line, household.members, stringResource(household.task.label(night))),
                        style = MaterialTheme.typography.bodySmall)
                    if (household.hardship > 0) Text(stringResource(
                        if (household.hardship >= 2) Res.string.village_struggling else Res.string.village_weary,
                    ), style = MaterialTheme.typography.labelSmall)
                }
                if (home != null) TextButton(onClick = { onFocus(home.cell.project(island.world.terrain)) }) {
                    Text(stringResource(Res.string.village_follow))
                }
            }
        }
        for (gone in village.households.filter { it.siteId == site.id && !it.active }) {
            Text(stringResource(Res.string.village_family, familyName(gone)) + " · " + stringResource(
                if (gone.leftBecause == Hardship.Thirst) Res.string.village_left_thirst else Res.string.village_left_hunger,
            ), style = MaterialTheme.typography.labelSmall)
        }
        HorizontalDivider()
    }
    VillageChronicle(island, view.branchId, chapter, onFocus)
}

@Composable
private fun VillageChronicle(island: IslandLoad.Ready, branchId: Int, chapter: Chapter?, onFocus: (GroundPoint) -> Unit) {
    val world = island.clock.state.settlements ?: return
    val village = world.village ?: return
    var limit by remember(branchId) { mutableIntStateOf(8) }
    val entries = remember(village.chronicle) { village.chronicle.asReversed() }
    Text(stringResource(Res.string.chronicle_title), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
    if (entries.isEmpty()) Text(stringResource(Res.string.chronicle_empty), style = MaterialTheme.typography.bodySmall)
    for (event in entries.take(limit)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 2.dp)) {
                Text(event.describe(village, chapter), style = MaterialTheme.typography.bodySmall)
                Text(echoDay(event.tick), style = MaterialTheme.typography.labelSmall)
            }
            event.place(world, island.world.terrain)?.let { ground ->
                Block(stringResource(Res.string.village_follow), { onFocus(ground) }, paint = Wood.BeechLight, size = BlockSize.Small)
            }
        }
    }
    if (entries.size > limit) TextButton(onClick = { limit += 8 }) { Text(stringResource(Res.string.echo_more_events)) }
}
