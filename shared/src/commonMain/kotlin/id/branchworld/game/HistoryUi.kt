package id.branchworld.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import branchworld.shared.generated.resources.Res
import id.branchworld.game.ecology.KeeperAct
import id.branchworld.game.ecology.PlantKind
import id.branchworld.game.ecology.Species
import branchworld.shared.generated.resources.history_boom
import branchworld.shared.generated.resources.history_crash
import branchworld.shared.generated.resources.history_escaped
import branchworld.shared.generated.resources.history_first_litter
import branchworld.shared.generated.resources.history_hunted
import branchworld.shared.generated.resources.history_keeper_cleared
import branchworld.shared.generated.resources.history_keeper_placed
import branchworld.shared.generated.resources.history_keeper_planted
import branchworld.shared.generated.resources.history_keeper_removed
import branchworld.shared.generated.resources.history_pack_formed
import branchworld.shared.generated.resources.history_starved
import branchworld.shared.generated.resources.history_vanished
import branchworld.shared.generated.resources.history_wildlife_placed
import branchworld.shared.generated.resources.history_wildlife_removed
import branchworld.shared.generated.resources.plant_meadow
import branchworld.shared.generated.resources.plant_tree
import branchworld.shared.generated.resources.echo_branch_start
import branchworld.shared.generated.resources.echo_pasture_request
import branchworld.shared.generated.resources.echo_warning
import branchworld.shared.generated.resources.history_all
import branchworld.shared.generated.resources.history_calf
import branchworld.shared.generated.resources.history_cause_earlier
import branchworld.shared.generated.resources.history_dry_storm
import branchworld.shared.generated.resources.history_empty
import branchworld.shared.generated.resources.history_family_left
import branchworld.shared.generated.resources.history_family_sheltered
import branchworld.shared.generated.resources.history_felled
import branchworld.shared.generated.resources.history_fire_out
import branchworld.shared.generated.resources.history_fire_out_mark
import branchworld.shared.generated.resources.history_fire_rain
import branchworld.shared.generated.resources.history_fire_shower
import branchworld.shared.generated.resources.history_fire_started
import branchworld.shared.generated.resources.history_firebreak_held
import branchworld.shared.generated.resources.history_fizzled
import branchworld.shared.generated.resources.history_fled
import branchworld.shared.generated.resources.history_herd_arrived
import branchworld.shared.generated.resources.history_herd_departed
import branchworld.shared.generated.resources.history_herd_pushed
import branchworld.shared.generated.resources.history_home_burned
import branchworld.shared.generated.resources.history_home_rebuilt
import branchworld.shared.generated.resources.history_keeper
import branchworld.shared.generated.resources.history_your_hand
import branchworld.shared.generated.resources.history_keeper_acted
import branchworld.shared.generated.resources.history_land
import branchworld.shared.generated.resources.history_lightning
import branchworld.shared.generated.resources.history_marked
import branchworld.shared.generated.resources.history_meadow
import branchworld.shared.generated.resources.history_more
import branchworld.shared.generated.resources.history_pasture_settled
import branchworld.shared.generated.resources.history_plan_chosen
import branchworld.shared.generated.resources.history_plan_endorsed
import branchworld.shared.generated.resources.history_plan_named
import branchworld.shared.generated.resources.history_refuge
import branchworld.shared.generated.resources.history_settlement_abandoned
import branchworld.shared.generated.resources.history_settlement_expanded
import branchworld.shared.generated.resources.history_settlement_founded
import branchworld.shared.generated.resources.history_settlement_relocated
import branchworld.shared.generated.resources.history_settlement_shortage
import branchworld.shared.generated.resources.history_show
import branchworld.shared.generated.resources.history_village
import branchworld.shared.generated.resources.history_why
import branchworld.shared.generated.resources.history_wildlife
import branchworld.shared.generated.resources.history_wildlife_age
import branchworld.shared.generated.resources.history_wildlife_birth
import branchworld.shared.generated.resources.history_wildlife_caught
import branchworld.shared.generated.resources.history_wildlife_range
import branchworld.shared.generated.resources.history_wildlife_scarcity
import branchworld.shared.generated.resources.history_young_wood
import branchworld.shared.generated.resources.timeline_mark_go
import id.branchworld.game.ecology.Chapter
import id.branchworld.game.ecology.FireOutCause
import id.branchworld.game.ecology.PlanOption
import id.branchworld.game.ecology.RangeEventKind
import id.branchworld.game.ecology.SettlementEventKind
import id.branchworld.game.ecology.WildlifeMilestone
import id.branchworld.game.ecology.WorldEvent
import id.branchworld.game.ecology.WorldEventKind
import id.branchworld.game.save.Bookmark
import id.branchworld.game.simulation.GroundPoint
import id.branchworld.game.simulation.project
import id.branchworld.game.terrain.TerrainMap
import id.branchworld.game.timeline.HistoryCategory
import id.branchworld.game.timeline.HistoryEntry
import id.branchworld.game.timeline.HistoryMoment
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private enum class HistoryFilter(val category: HistoryCategory?) {
    All(null), Wildlife(HistoryCategory.Wildlife), Village(HistoryCategory.Village),
    Land(HistoryCategory.Land), Keeper(HistoryCategory.Keeper), Marked(null),
}

private const val HistoryPageSize = 12

@Composable
internal fun HistoryBoard(
    island: IslandLoad.Ready,
    bookmarks: List<Bookmark>,
    onVisit: (Bookmark) -> Unit,
    onForget: (Bookmark) -> Unit,
    onShow: (GroundPoint) -> Unit,
) {
    val view by island.timelineView.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(HistoryFilter.All) }
    var expanded by remember(island, view.branchId, filter) { mutableStateOf<HistoryEntry.World?>(null) }
    var visible by remember(island, view.branchId, filter) { mutableStateOf(HistoryPageSize) }
    val operations = rememberCoroutineScope()
    val browser = remember(island, view.branchId, view.active.head) { island.history(view.branchId) }
    val terrain = island.world.terrain

    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        // A living world has no villages, and the player's own acts read as their hand.
        val living = island.key.living
        for (entry in HistoryFilter.entries) {
            if (living && entry == HistoryFilter.Village) continue
            Block(stringResource(when (entry) {
                HistoryFilter.All -> Res.string.history_all
                HistoryFilter.Wildlife -> Res.string.history_wildlife
                HistoryFilter.Village -> Res.string.history_village
                HistoryFilter.Land -> Res.string.history_land
                HistoryFilter.Keeper -> if (living) Res.string.history_your_hand else Res.string.history_keeper
                HistoryFilter.Marked -> Res.string.history_marked
            }), { filter = entry }, selected = filter == entry,
                paint = if (filter == entry) Wood.Woad else Wood.BeechLight, size = BlockSize.Compact)
        }
    }

    if (filter == HistoryFilter.Marked) {
        if (bookmarks.isEmpty()) Text(stringResource(Res.string.history_empty))
        else MarkedMoments(bookmarks, island, onVisit, onForget)
        return
    }

    fun go(branch: Int, tick: Long, ground: GroundPoint? = null) {
        operations.launch {
            island.visit(branch, tick)
            val position = island.timelineView.value
            if (ground != null && position.branchId == branch && position.tick == tick && !position.failed) onShow(ground)
        }
    }

    val rows = remember(browser, filter) { browser.entries.filter { filter.category == null || it.category == filter.category } }
    if (rows.isEmpty()) Text(stringResource(Res.string.history_empty))
    for (row in rows.take(visible)) {
        val ground = row.place(terrain)
        Column(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(dayLabel(row.tick, null), style = MaterialTheme.typography.labelMedium, color = Wood.WalnutSoft)
            Text(row.describe(island.chapter), style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Block(stringResource(Res.string.timeline_mark_go), { go(row.branchId, row.tick) },
                    size = BlockSize.Compact, enabled = !view.seeking)
                if (ground != null) Block(stringResource(Res.string.history_show), { go(row.branchId, row.tick, ground) },
                    size = BlockSize.Compact, enabled = !view.seeking)
                if (row is HistoryEntry.World && row.event.causes.isNotEmpty()) {
                    Block(stringResource(Res.string.history_why), { expanded = if (expanded == row) null else row },
                        size = BlockSize.Compact, selected = expanded == row, paint = Wood.Woad)
                }
            }
            if (row is HistoryEntry.World && expanded == row) {
                Board(Modifier.fillMaxWidth(), padding = 8.dp) {
                    for (cause in browser.causesOf(row.event)) {
                        Column(Modifier.padding(start = (cause.depth.coerceAtMost(4) * 6).dp)) {
                            Text(worldEventLabel(cause.ref.kind, cause.event), style = MaterialTheme.typography.bodySmall)
                            val owner = cause.branchId
                            if (owner == null) {
                                Text(stringResource(Res.string.history_cause_earlier), style = MaterialTheme.typography.bodySmall)
                            } else {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Block(stringResource(Res.string.timeline_mark_go), { go(owner, cause.ref.tick) },
                                        size = BlockSize.Compact, enabled = !view.seeking)
                                    cause.event?.cell?.project(terrain)?.let { place ->
                                        Block(stringResource(Res.string.history_show), { go(owner, cause.ref.tick, place) },
                                            size = BlockSize.Compact, enabled = !view.seeking)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (rows.size > visible) Block(stringResource(Res.string.history_more), { visible += HistoryPageSize }, size = BlockSize.Small)
}

private fun HistoryEntry.place(terrain: TerrainMap): GroundPoint? = when (this) {
    is HistoryEntry.World -> event.cell.project(terrain)
    is HistoryEntry.Village -> event.place(world, terrain)
    is HistoryEntry.Settlement -> world.sites.firstOrNull { it.id == event.siteId }?.center?.project(terrain)
    is HistoryEntry.Wildlife -> event.cell.project(terrain)
    is HistoryEntry.Range -> event.cell.project(terrain)
    is HistoryEntry.Command, is HistoryEntry.Moment -> null
}

@Composable
private fun HistoryEntry.describe(chapter: Chapter?): String = when (this) {
    is HistoryEntry.World -> worldEventLabel(event.kind, event)
    is HistoryEntry.Village -> event.describe(checkNotNull(world.village), chapter)
    is HistoryEntry.Settlement -> stringResource(when (event.kind) {
        SettlementEventKind.Founded -> Res.string.history_settlement_founded
        SettlementEventKind.Shortage -> Res.string.history_settlement_shortage
        SettlementEventKind.Expanded -> Res.string.history_settlement_expanded
        SettlementEventKind.Relocated -> Res.string.history_settlement_relocated
        SettlementEventKind.Abandoned -> Res.string.history_settlement_abandoned
    }, settlementName(event.siteId, chapter))
    is HistoryEntry.Wildlife -> stringResource(when (event.kind) {
        WildlifeMilestone.Birth -> Res.string.history_wildlife_birth
        WildlifeMilestone.NewRange -> Res.string.history_wildlife_range
        WildlifeMilestone.Caught -> Res.string.history_wildlife_caught
        WildlifeMilestone.Scarcity -> Res.string.history_wildlife_scarcity
        WildlifeMilestone.OldAge -> Res.string.history_wildlife_age
        WildlifeMilestone.Placed -> Res.string.history_wildlife_placed
        WildlifeMilestone.Removed -> Res.string.history_wildlife_removed
    }, event.species.label().lowercase())
    is HistoryEntry.Range -> stringResource(when (event.kind) {
        RangeEventKind.HerdSetOut -> Res.string.history_herd_departed
        RangeEventKind.HerdCrossed -> Res.string.history_herd_arrived
        RangeEventKind.HerdPushedThrough -> Res.string.history_herd_pushed
        RangeEventKind.CalfBorn -> Res.string.history_calf
        RangeEventKind.RefugeDeclared -> Res.string.history_refuge
        RangeEventKind.PastureSettled -> Res.string.history_pasture_settled
    })
    is HistoryEntry.Command -> marker.label(false)
    is HistoryEntry.Moment -> stringResource(when (moment.kind) {
        HistoryMoment.Kind.Warning -> warningMoment(chapter)
        else -> Res.string.echo_branch_start
    })
}

@Composable
internal fun worldEventLabel(kind: WorldEventKind, event: WorldEvent?): String {
    if (event != null) livingEventLabel(kind, event)?.let { return it }
    if (kind == WorldEventKind.FireOut && event != null) return when (event.detail) {
        FireOutCause.Rain.code -> stringResource(Res.string.history_fire_rain)
        FireOutCause.CalledShower.code -> stringResource(Res.string.history_fire_shower)
        else -> stringResource(Res.string.history_fire_out, event.subject)
    }
    if (kind == WorldEventKind.PlanChosen && event != null) {
        PlanOption.entries.firstOrNull { it.code == event.detail }?.let {
            val label = if (event.causes.any { cause -> cause.kind == WorldEventKind.KeeperAct }) {
                Res.string.history_plan_endorsed
            } else Res.string.history_plan_named
            return stringResource(label, stringResource(it.title()))
        }
    }
    return stringResource(when (kind) {
        WorldEventKind.DryStorm -> Res.string.history_dry_storm
        WorldEventKind.Lightning -> Res.string.history_lightning
        WorldEventKind.StrikeFizzled -> Res.string.history_fizzled
        WorldEventKind.FireStarted -> Res.string.history_fire_started
        WorldEventKind.FireOut -> Res.string.history_fire_out_mark
        WorldEventKind.FirebreakHeld -> Res.string.history_firebreak_held
        WorldEventKind.HomeBurned -> Res.string.history_home_burned
        WorldEventKind.HouseholdLeft -> Res.string.history_family_left
        WorldEventKind.HouseholdSheltered -> Res.string.history_family_sheltered
        WorldEventKind.HomeRebuilt -> Res.string.history_home_rebuilt
        WorldEventKind.TreesFelled -> Res.string.history_felled
        WorldEventKind.KeeperAct -> Res.string.history_keeper_acted
        WorldEventKind.PlanChosen -> Res.string.history_plan_chosen
        WorldEventKind.MeadowReturned -> Res.string.history_meadow
        WorldEventKind.YoungWood -> Res.string.history_young_wood
        WorldEventKind.AnimalsFled -> Res.string.history_fled
        WorldEventKind.Hunted -> Res.string.history_hunted
        WorldEventKind.PackFormed -> Res.string.history_pack_formed
        WorldEventKind.Escaped -> Res.string.history_escaped
        WorldEventKind.Starved -> Res.string.history_starved
        WorldEventKind.Vanished -> Res.string.history_vanished
        WorldEventKind.Boom -> Res.string.history_boom
        WorldEventKind.Crash -> Res.string.history_crash
        WorldEventKind.FirstLitter -> Res.string.history_first_litter
    })
}

/** Rules v12 events, named with the animals involved. */
@Composable
private fun livingEventLabel(kind: WorldEventKind, event: WorldEvent): String? {
    fun species(code: Int) = Species.entries.firstOrNull { it.canonicalId == code }
    return when (kind) {
        WorldEventKind.Hunted -> stringResource(Res.string.history_hunted, wildlifeName(event.subject.toLong()),
            species(event.detail)?.label()?.lowercase().orEmpty())
        WorldEventKind.Escaped -> stringResource(Res.string.history_escaped, wildlifeName(event.subject.toLong()),
            wildlifeName(event.detail.toLong()))
        WorldEventKind.Starved -> stringResource(Res.string.history_starved, wildlifeName(event.subject.toLong()))
        WorldEventKind.Vanished -> stringResource(Res.string.history_vanished, species(event.detail)?.label()?.lowercase().orEmpty())
        WorldEventKind.Boom -> stringResource(Res.string.history_boom, species(event.detail)?.plural().orEmpty(), event.subject)
        WorldEventKind.Crash -> stringResource(Res.string.history_crash, species(event.detail)?.plural().orEmpty(), event.subject)
        WorldEventKind.FirstLitter -> stringResource(Res.string.history_first_litter, wildlifeName(event.subject.toLong()))
        WorldEventKind.KeeperAct -> when (event.detail) {
            KeeperAct.Place.code -> stringResource(Res.string.history_keeper_placed, species(event.subject)?.plural()?.lowercase().orEmpty())
            KeeperAct.Remove.code -> stringResource(Res.string.history_keeper_removed, species(event.subject)?.label()?.lowercase().orEmpty())
            KeeperAct.Plant.code -> stringResource(Res.string.history_keeper_planted, plantName(event.subject))
            KeeperAct.Clear.code -> stringResource(Res.string.history_keeper_cleared, plantName(event.subject))
            else -> null
        }
        else -> null
    }
}

@Composable
private fun plantName(code: Int) = stringResource(if (code == PlantKind.Tree.code) Res.string.plant_tree else Res.string.plant_meadow)
