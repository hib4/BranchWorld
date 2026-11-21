package id.branchworld.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import branchworld.shared.generated.resources.*
import id.branchworld.game.ecology.DayLength
import id.branchworld.game.ecology.FadingGroveSeed
import id.branchworld.game.ecology.SettlementEvent
import id.branchworld.game.simulation.EchoKind
import id.branchworld.game.simulation.GroundPoint
import id.branchworld.game.simulation.WildlifeMoment
import id.branchworld.game.timeline.BranchSummary
import id.branchworld.game.timeline.ComparedBranch
import id.branchworld.game.timeline.HistoryMoment
import id.branchworld.game.timeline.OwnedIntervention
import id.branchworld.game.timeline.inTreeOrder
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.StringResource

@Composable
internal fun LivingEchoDock(
    island: IslandLoad.Ready,
    view: ComparisonView,
    timeline: TimelineView,
    details: Boolean,
    onDetails: (Boolean) -> Unit,
) {
    val target = view.target
    val event = view.event
    val operations = rememberCoroutineScope()
    LaunchedEffect(island, target) { target?.let { island.loadComparison(it) } }
    LaunchedEffect(island, event) { event?.let { island.loadComparisonEvent(it) } }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(Res.string.echo_title), Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = { onDetails(!details) }) {
            Text(stringResource(if (details) Res.string.echo_close_details else Res.string.echo_details))
        }
        TextButton(onClick = island::closeComparison) { Text(stringResource(Res.string.echo_close)) }
    }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(!view.showRight, { island.showComparisonSide(false) }, enabled = !timeline.seeking && view.pair != null,
            label = { Text(view.left.name, Modifier.widthIn(max = 185.dp)) })
        FilterChip(view.showRight, { island.showComparisonSide(true) }, enabled = !timeline.seeking && view.pair != null,
            label = { Text(view.right.name, Modifier.widthIn(max = 185.dp)) })
    }

    if (view.failed) OutlinedButton(onClick = island::retryComparison) { Text(stringResource(Res.string.echo_retry)) }
    if (timeline.failed && view.pair != null) {
        Text(stringResource(Res.string.echo_failed_continue), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
    val pair = view.pair
    val tick = view.tick
    val chapter = island.key.terrain.seed == FadingGroveSeed
    if (tick != null) {
        val named = view.moments.sortedBy { it.rank() }
        val shortcuts = named.asSequence().filter { it.kind != HistoryMoment.Kind.Intervention }
            .filter { details || it.kind != HistoryMoment.Kind.Recovery }
            .distinctBy { it.tick }.take(4).sortedBy { it.tick }.toList()
        TimelineScrubber(view.range, tick, enabled = !timeline.seeking,
            markers = named.map { it.tick }.distinct(), showTicks = false, snapToMarkers = true,
            previewLabel = { at ->
                val moment = named.firstOrNull { it.tick == at }
                if (moment == null) echoDay(at) else momentLabel(moment, chapter)
            },
            onSeek = island::compareAt)
        if (shortcuts.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (moment in shortcuts) {
                TextButton(onClick = { island.compareAt(moment.tick) }, enabled = tick != moment.tick && !timeline.seeking) {
                    Text(if (details) momentLabel(moment, chapter) else shortMomentLabel(moment, chapter))
                }
            }
        }
    }

    if (details) {
        val branches = remember(timeline.branches) { timeline.branches.inTreeOrder().map { it.branch } }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BranchPicker(stringResource(Res.string.comparison_left, view.left.name),
                branches, view.left.id, view.right.id, showTicks = false) { island.selectComparison(it, view.right.id) }
            BranchPicker(stringResource(Res.string.comparison_right, view.right.name),
                branches, view.right.id, view.left.id, showTicks = false) { island.selectComparison(view.left.id, it) }
        }
        if (view.sharedPast?.tick?.let { it !in view.range } == true) {
            TextButton(onClick = { operations.launch { island.revisitSharedPast() } }, enabled = !timeline.seeking) {
                Text(stringResource(Res.string.echo_revisit_shared))
            }
        }
        if (pair != null) {
            TextButton(onClick = { operations.launch { island.continueComparison() } }, enabled = !timeline.seeking) {
                Text(stringResource(Res.string.echo_continue))
            }
            EchoChronicle(island, view)
            HorizontalDivider()
            ComparisonTable(view, pair.left, pair.right)
        }
    }
}

@Composable
internal fun LivingEchoPlaces(
    island: IslandLoad.Ready,
    view: ComparisonView,
    modifier: Modifier,
    onFocus: (List<GroundPoint>) -> Unit,
) {
    var selection by remember(island) { mutableStateOf<EchoKind?>(null) }
    val pair = view.pair ?: return
    val pose = if (view.showRight) pair.right.projection else pair.left.projection
    val groups = pose.livingEcho?.sites.orEmpty().groupBy { it.kind }
    val a = pair.left.projection.shaping
    val b = pair.right.projection.shaping
    val context = when (selection?.takeIf { it in groups } ?: groups.keys.firstOrNull()) {
        EchoKind.Bank -> echoOutcome(Res.string.echo_water_outcome, a?.welfare?.water, b?.welfare?.water, view)
        EchoKind.Field -> echoOutcome(Res.string.echo_field_outcome, a?.welfare?.harvest, b?.welfare?.harvest, view)
        EchoKind.Grove -> echoOutcome(Res.string.echo_grove_outcome, a?.rootedGroves, b?.rootedGroves, view)
            ?: echoOutcome(Res.string.echo_sapling_outcome, pair.left.metrics.saplings.toInt(), pair.right.metrics.saplings.toInt(), view)
        else -> null
    }
    Surface(modifier, color = NightGreen.copy(alpha = 0.94f), contentColor = Parchment,
        shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            if (groups.isEmpty()) {
                Text(stringResource(if (pair.left.hash == pair.right.hash) Res.string.echo_no_difference
                    else Res.string.echo_no_marked_difference), style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 6.dp))
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((kind, sites) in groups) {
                        if (kind == EchoKind.WetGround && EchoKind.Bank in groups) continue
                        TextButton(onClick = {
                            selection = kind
                            onFocus(sites.map { it.ground })
                        },
                            colors = ButtonDefaults.textButtonColors(contentColor = Parchment)) {
                            Text(stringResource(kind.label()))
                        }
                    }
                    TextButton(onClick = island::toggleEcho,
                        colors = ButtonDefaults.textButtonColors(contentColor = Parchment)) {
                        Text(stringResource(if (view.echoEnabled) Res.string.echo_hide else Res.string.echo_show))
                    }
                }
                if (context != null) Text(context, style = MaterialTheme.typography.bodySmall)
                if (view.echoEnabled) Text(stringResource(Res.string.echo_legend),
                    style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 3.dp, bottom = 6.dp))
            }
        }
    }
}

@Composable
private fun echoOutcome(label: StringResource, left: Int?, right: Int?, view: ComparisonView): String? =
    if (left == null || right == null || left == right) null
    else stringResource(label, if (left > right) view.left.name else view.right.name)

@Composable
private fun shortMomentLabel(moment: HistoryMoment, chapter: Boolean): String {
    val label: StringResource = when (moment.kind) {
        HistoryMoment.Kind.Warning -> Res.string.echo_warning_short
        HistoryMoment.Kind.Fork -> Res.string.echo_fork_short
        HistoryMoment.Kind.Shortage -> if (chapter) Res.string.echo_harvest_short else Res.string.echo_scarcity
        HistoryMoment.Kind.AfterRain -> Res.string.echo_rains_short
        else -> return momentLabel(moment, chapter)
    }
    return stringResource(label)
}

@Composable
internal fun echoDay(tick: Long): String =
    stringResource(Res.string.echo_day, tick / DayLength + 1, (tick % DayLength * 100 / DayLength).toInt())

internal fun HistoryMoment.rank() = when (kind) {
    HistoryMoment.Kind.Warning -> 0
    HistoryMoment.Kind.Fork -> 1
    HistoryMoment.Kind.Shortage -> 2
    HistoryMoment.Kind.Recovery -> 3
    HistoryMoment.Kind.AfterRain -> 4
    HistoryMoment.Kind.Intervention -> 5
}

@Composable
internal fun momentLabel(moment: HistoryMoment, chapter: Boolean): String = when (moment.kind) {
    HistoryMoment.Kind.Warning -> stringResource(Res.string.echo_warning)
    HistoryMoment.Kind.Fork -> stringResource(Res.string.echo_common_moment)
    HistoryMoment.Kind.Shortage -> stringResource(if (chapter) Res.string.echo_shortage else Res.string.echo_scarcity)
    HistoryMoment.Kind.Recovery -> stringResource(Res.string.echo_recovery)
    HistoryMoment.Kind.AfterRain -> stringResource(Res.string.echo_after_rain)
    HistoryMoment.Kind.Intervention -> checkNotNull(moment.intervention).marker.label(showTicks = false)
}

private fun EchoKind.label() = when (this) {
    EchoKind.Bank -> Res.string.echo_bank
    EchoKind.Field -> Res.string.echo_field
    EchoKind.Grove -> Res.string.echo_grove
    EchoKind.Homes -> Res.string.echo_homes
    EchoKind.WetGround -> Res.string.echo_wet_ground
}

private sealed interface ChronicleEntry {
    val tick: Long
    data class Command(val event: OwnedIntervention) : ChronicleEntry { override val tick get() = event.marker.tick }
    data class Wildlife(val event: WildlifeMoment) : ChronicleEntry { override val tick get() = event.tick }
    data class Village(val event: SettlementEvent) : ChronicleEntry { override val tick get() = event.tick }
    data class Moment(val event: HistoryMoment) : ChronicleEntry { override val tick get() = event.tick }
}

private fun ComparedBranch.chronicle(commands: List<OwnedIntervention>): List<ChronicleEntry> = buildList {
    commands.mapTo(this, ChronicleEntry::Command)
    projection.wildlife?.moments.orEmpty().mapTo(this, ChronicleEntry::Wildlife)
    settlementEvents.mapTo(this, ChronicleEntry::Village)
    projection.shaping?.warningAt?.let { add(ChronicleEntry.Moment(HistoryMoment(it, HistoryMoment.Kind.Warning))) }
    projection.shaping?.welfare?.firstShortage?.let { add(ChronicleEntry.Moment(HistoryMoment(it, HistoryMoment.Kind.Shortage))) }
    projection.shaping?.welfare?.firstRecovery?.let { add(ChronicleEntry.Moment(HistoryMoment(it, HistoryMoment.Kind.Recovery))) }
}

@Composable
private fun EchoChronicle(island: IslandLoad.Ready, view: ComparisonView) {
    val pair = view.pair ?: return
    val past = view.sharedPast?.tick
    val trails = remember(pair, past) {
        val left = pair.left.chronicle(pair.leftTrail + pair.sharedTrail)
        val right = pair.right.chronicle(pair.rightTrail + pair.sharedTrail)
        val shared = left.intersect(right.toSet()).filter { past != null && it.tick <= past }.toSet()
        Triple(shared.toList(), left.filterNot { it in shared }, right.filterNot { it in shared })
    }
    Text(stringResource(Res.string.echo_chronicle), Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleMedium)
    if (trails.first.isNotEmpty()) ChronicleTrail(stringResource(Res.string.echo_shared_past), trails.first, island, view)
    ChronicleTrail(view.left.name, trails.second, island, view)
    ChronicleTrail(view.right.name, trails.third, island, view)
}

@Composable
private fun ChronicleTrail(name: String, entries: List<ChronicleEntry>, island: IslandLoad.Ready, view: ComparisonView) {
    var limit by remember(name, view.target) { mutableIntStateOf(8) }
    val ordered = remember(entries) { entries.sortedByDescending { it.tick } }
    val chapter = island.key.terrain.seed == FadingGroveSeed
    Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
    if (entries.isEmpty()) Text(stringResource(Res.string.comparison_no_events), style = MaterialTheme.typography.bodySmall)
    for (entry in ordered.take(limit)) {
        val label = when (entry) {
            is ChronicleEntry.Command -> entry.event.marker.label(showTicks = false)
            is ChronicleEntry.Moment -> when (entry.event.kind) {
                HistoryMoment.Kind.Shortage -> stringResource(Res.string.shape_shortage_event)
                HistoryMoment.Kind.Recovery -> stringResource(Res.string.shape_recovered_event)
                else -> momentLabel(entry.event, chapter)
            }
            is ChronicleEntry.Village -> entry.event.label(showTick = false)
            is ChronicleEntry.Wildlife -> entry.event.description()
        }
        if (entry is ChronicleEntry.Command) {
            val source = entry.event
            OutlinedButton(onClick = { island.inspectComparisonEvent(source.branchId, source.marker) }) { Text(label) }
            val selected = view.event
            if (selected?.branchId == source.branchId && selected.tick == source.marker.tick &&
                selected.sequence == source.marker.sequence) {
                val live = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                when {
                    view.outcomeFailed -> Text(stringResource(Res.string.timeline_seek_failed), live)
                    view.outcomes == null -> Text(stringResource(Res.string.comparison_event_loading), live)
                    else -> for (result in view.outcomes) Text(result.label(), live)
                }
            }
        } else {
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
        Text(echoDay(entry.tick), style = MaterialTheme.typography.labelSmall)
    }
    if (ordered.size > limit) TextButton(onClick = { limit += 8 }) {
        Text(stringResource(Res.string.echo_more_events))
    }
}
