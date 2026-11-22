package id.branchworld.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.layout.height
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import branchworld.shared.generated.resources.Res
import branchworld.shared.generated.resources.echo_branch_start
import branchworld.shared.generated.resources.plan_marker
import branchworld.shared.generated.resources.echo_past_fork
import branchworld.shared.generated.resources.echo_warning
import branchworld.shared.generated.resources.grove_applied
import branchworld.shared.generated.resources.shape_timeline_position_percent
import branchworld.shared.generated.resources.shape_return_warning
import branchworld.shared.generated.resources.return_turning_point
import branchworld.shared.generated.resources.echo_pasture_request
import branchworld.shared.generated.resources.shape_rain_marker
import branchworld.shared.generated.resources.shape_refuge_marker
import branchworld.shared.generated.resources.shape_refuge_applied
import branchworld.shared.generated.resources.shape_grove_marker
import branchworld.shared.generated.resources.shape_rain_arrived
import branchworld.shared.generated.resources.shape_did_not_apply
import branchworld.shared.generated.resources.shape_watch_warning
import branchworld.shared.generated.resources.shape_watch_recovery
import branchworld.shared.generated.resources.shape_fork_here
import branchworld.shared.generated.resources.shape_next_harvest
import id.branchworld.game.ecology.SettlementTurn
import id.branchworld.game.ecology.ShapingRulesVersion
import id.branchworld.game.ecology.Chapter
import branchworld.shared.generated.resources.comparison_open
import branchworld.shared.generated.resources.comparison_needs_fork
import branchworld.shared.generated.resources.save_cancel
import branchworld.shared.generated.resources.timeline_branch
import branchworld.shared.generated.resources.timeline_branch_limit
import branchworld.shared.generated.resources.timeline_command_limit
import branchworld.shared.generated.resources.timeline_create
import branchworld.shared.generated.resources.timeline_default_name
import branchworld.shared.generated.resources.timeline_fork
import branchworld.shared.generated.resources.timeline_fork_at
import branchworld.shared.generated.resources.timeline_name
import branchworld.shared.generated.resources.timeline_past
import branchworld.shared.generated.resources.timeline_play_to
import branchworld.shared.generated.resources.timeline_range
import branchworld.shared.generated.resources.timeline_seek
import branchworld.shared.generated.resources.timeline_seek_failed
import branchworld.shared.generated.resources.timeline_seeking
import branchworld.shared.generated.resources.timeline_tick_entry
import branchworld.shared.generated.resources.timeline_forked_at
import branchworld.shared.generated.resources.timeline_rename
import branchworld.shared.generated.resources.timeline_rename_title
import branchworld.shared.generated.resources.timeline_scrubber
import branchworld.shared.generated.resources.timeline_reforest_event
import branchworld.shared.generated.resources.timeline_supplement_event
import branchworld.shared.generated.resources.timeline_queued_event
import branchworld.shared.generated.resources.timeline_earlier_events
import branchworld.shared.generated.resources.world_tick
import branchworld.shared.generated.resources.plants_applied
import branchworld.shared.generated.resources.supplement_applied
import branchworld.shared.generated.resources.settlement_inspector
import androidx.compose.ui.semantics.contentDescription
import id.branchworld.game.ecology.Intervention
import id.branchworld.game.ecology.InterventionResult
import id.branchworld.game.timeline.FadingGroveRecovery
import id.branchworld.game.timeline.InterventionMarker
import id.branchworld.game.timeline.MaxBranchNameLength
import id.branchworld.game.timeline.RecentInterventions
import id.branchworld.game.timeline.fractionAt
import id.branchworld.game.timeline.inTreeOrder
import id.branchworld.game.timeline.tickAt
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TimelineControls(island: IslandLoad.Ready, diagnostics: Boolean = false, onForked: () -> Unit) {
    val view by island.timelineView.collectAsStateWithLifecycle()
    TimelinePanel(island, view, diagnostics || island.key.rules < ShapingRulesVersion, onForked)
}

@Composable
private fun TimelinePanel(island: IslandLoad.Ready, view: TimelineView, showTicks: Boolean, onForked: () -> Unit) {
    val operations = rememberCoroutineScope()
    var input by rememberSaveable(view.branchId) { mutableStateOf("") }
    var forkDialog by remember { mutableStateOf(false) }
    var renameDialog by remember { mutableStateOf(false) }
    val target = input.toLongOrNull()
    val branch = view.active
    val focus = LocalFocusManager.current
    val canSeek = !view.seeking && target != null && target in branch.start..branch.head && target != view.tick
    val canPlay = !view.seeking && target != null && target > view.tick
    fun seek() {
        focus.clearFocus()
        target?.let { operations.launch { island.seek(it) } }
    }
    fun playTo() {
        focus.clearFocus()
        target?.let(island::playTo)
    }
    val submitTick = {
        when {
            canSeek -> {
                seek()
                true
            }
            canPlay -> {
                playTo()
                true
            }
            else -> false
        }
    }
    val feedback by island.interventions.collectAsStateWithLifecycle()
    val markers = remember(island, branch.id, branch.interventionCount, feedback.queued) { island.markers(branch.id) }
    val executed = remember(markers) { markers.filterNot { it.queued } }
    val milestones = remember(island, branch.id, branch.milestoneCount) { island.settlementEvents(branch.id) }
    val warning = island.warningTick
    val markedTicks = remember(executed, milestones, warning) {
        (executed.map { it.tick } + milestones.map { it.tick } + listOfNotNull(warning)).distinct()
    }
    val tree = remember(view.branches) { view.branches.inTreeOrder() }

    Text(stringResource(Res.string.timeline_branch, branch.name), style = MaterialTheme.typography.titleMedium)
    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        for ((entry, depth) in tree) {
            val active = entry.id == branch.id
            val detail = if (!showTicks) null else stringResource(
                if (entry.parentId == null) Res.string.timeline_range else Res.string.timeline_forked_at,
                entry.start.toString(), entry.head.toString(),
            )
            Block(entry.name, { if (!active) operations.launch { island.switchBranch(entry.id) } },
                Modifier.fillMaxWidth().padding(start = (depth.coerceAtMost(5) * 9).dp),
                selected = active, enabled = !view.seeking, detail = detail,
                paint = if (active) Wood.Woad else Wood.BeechLight, size = BlockSize.Compact)
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { renameDialog = true }, enabled = !view.seeking) {
            Text(stringResource(Res.string.timeline_rename))
        }
        Block(stringResource(Res.string.comparison_open), island::openComparison, enabled = !view.seeking && tree.size > 1,
            paint = Wood.Woad, size = BlockSize.Small)
    }
    if (tree.size == 1) Text(stringResource(Res.string.comparison_needs_fork))
    if (showTicks) Text(stringResource(Res.string.timeline_range, branch.start.toString(), branch.head.toString()))
    if (warning != null) {
        OutlinedButton(onClick = { operations.launch { island.returnToWarning() } },
            enabled = island.canReturnToWarning) {
                Text(stringResource(if (island.chapter == Chapter.LastCrossing) Res.string.return_turning_point else Res.string.shape_return_warning))
            }
    }
    TimelineScrubber(
        branch.start..branch.head, view.tick, !view.seeking,
        markers = markedTicks, onStart = island.clock::pause, showTicks = showTicks, snapToMarkers = !showTicks,
        previewLabel = if (showTicks) null else { tick ->
            when (tick) {
                warning -> stringResource(if (island.chapter == Chapter.LastCrossing) Res.string.echo_pasture_request else Res.string.echo_warning)
                branch.start -> stringResource(Res.string.echo_branch_start)
                else -> executed.lastOrNull { it.tick == tick }?.label(false) ?: echoDay(tick)
            }
        },
    ) { requested ->
        if (requested != view.tick) operations.launch { island.seek(requested) }
    }
    if (showTicks) OutlinedTextField(
        value = input,
        onValueChange = { if (it.length <= 19 && it.all { char -> char in '0'..'9' }) input = it },
        modifier = Modifier.widthIn(max = 220.dp).submitTickOnEnter(submitTick),
        label = { Text(stringResource(Res.string.timeline_tick_entry)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { submitTick() }),
        enabled = !view.seeking,
        singleLine = true,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showTicks) {
            OutlinedButton(onClick = ::seek, enabled = canSeek) { Text(stringResource(Res.string.timeline_seek)) }
            OutlinedButton(onClick = ::playTo, enabled = canPlay) { Text(stringResource(Res.string.timeline_play_to)) }
        } else if (view.tick < Long.MAX_VALUE - SettlementTurn) {
            val chapter = island.chapter == Chapter.FadingGrove
            val watchWarning = chapter && view.tick < 240
            val nextHarvest = (view.tick / SettlementTurn + 1) * SettlementTurn
            OutlinedButton(onClick = { island.playTo(if (watchWarning) 240L else nextHarvest) }, enabled = !view.seeking) {
                Text(stringResource(if (watchWarning) Res.string.shape_watch_warning else Res.string.shape_next_harvest))
            }
            if (chapter && warning != null && view.tick < FadingGroveRecovery) {
                OutlinedButton(onClick = { island.playTo(FadingGroveRecovery) }, enabled = !view.seeking) {
                    Text(stringResource(Res.string.shape_watch_recovery))
                }
            }
        }
        OutlinedButton(
            onClick = {
                island.clock.pause()
                forkDialog = true
            },
            enabled = !view.seeking && view.canFork,
        ) { Text(stringResource(Res.string.timeline_fork)) }
    }
    val live = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    if (view.seeking) Text(stringResource(Res.string.timeline_seeking), live)
    if (view.failed) Text(stringResource(Res.string.timeline_seek_failed), live)
    if (view.tick < branch.head) Text(stringResource(if (showTicks) Res.string.timeline_past else Res.string.echo_past_fork))
    if (!view.canFork) Text(stringResource(Res.string.timeline_branch_limit))
    if (view.commandLimitReached) Text(stringResource(Res.string.timeline_command_limit))
    val previousEvents = executed.indexOfLast { it.tick <= view.tick } + 1
    val nearby = executed.subList((previousEvents - RecentInterventions).coerceAtLeast(0), previousEvents).asReversed() +
        listOfNotNull(executed.getOrNull(previousEvents))
    if (nearby.isNotEmpty() || markers.any { it.queued }) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (marker in nearby) {
                OutlinedButton(
                    onClick = { operations.launch { island.seekEvent(marker.tick) } },
                    enabled = !view.seeking,
                ) { Text(marker.label(showTicks)) }
            }
        }
        for (marker in markers.asSequence().filter { it.queued }.take(RecentInterventions)) {
            Text(stringResource(Res.string.timeline_queued_event, marker.label(showTicks)))
        }
        if (previousEvents > RecentInterventions) {
            Text(stringResource(Res.string.timeline_earlier_events))
        }
    }
    for (result in feedback.applied) Text(result.label(showTicks), live)
    if (milestones.isNotEmpty()) {
        Text(stringResource(Res.string.settlement_inspector), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (milestone in milestones) {
                OutlinedButton(
                    onClick = { operations.launch { island.seek(milestone.tick) } },
                    enabled = !view.seeking && milestone.tick != view.tick,
                ) { Text(milestone.label(showTicks)) }
            }
        }
    }
    if (forkDialog) {
        BranchNameDialog(
            title = if (showTicks) stringResource(Res.string.timeline_fork_at, view.tick.toString()) else stringResource(Res.string.shape_fork_here),
            action = stringResource(Res.string.timeline_create),
            suggestedName = stringResource(Res.string.timeline_default_name, view.branches.size + 1),
            onDismiss = { forkDialog = false },
            onFork = {
                if (island.fork(it)) {
                    forkDialog = false
                    onForked()
                }
            },
        )
    }
    if (renameDialog) {
        BranchNameDialog(
            title = stringResource(Res.string.timeline_rename_title, branch.name),
            action = stringResource(Res.string.timeline_rename),
            suggestedName = branch.name,
            onDismiss = { renameDialog = false },
            onFork = { if (island.renameActive(it)) renameDialog = false },
        )
    }
}

@Composable
private fun BranchNameDialog(title: String, action: String, suggestedName: String, onDismiss: () -> Unit, onFork: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(suggestedName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= MaxBranchNameLength) name = it },
                label = { Text(stringResource(Res.string.timeline_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onFork(name) }, enabled = name.isNotBlank()) {
                Text(action)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.save_cancel)) }
        },
    )
}

@Composable
internal fun TimelineScrubber(
    range: LongRange,
    tick: Long,
    enabled: Boolean,
    markers: List<Long> = emptyList(),
    onStart: () -> Unit = {},
    showTicks: Boolean = true,
    snapToMarkers: Boolean = false,
    previewLabel: (@Composable (Long) -> String)? = null,
    onSeek: (Long) -> Unit,
) {
    var drag by remember(range.first, range.last) { mutableStateOf<Float?>(null) }
    val value = drag ?: range.fractionAt(tick)
    val label = stringResource(Res.string.timeline_scrubber)
    fun target(fraction: Float): Long {
        val unsnapped = range.tickAt(fraction)
        if (!snapToMarkers || fraction <= 0f || fraction >= 1f) return unsnapped
        val closest = markers.filter { it in range }.minByOrNull { kotlin.math.abs(range.fractionAt(it) - fraction) }
        return closest?.takeIf { kotlin.math.abs(range.fractionAt(it) - fraction) <= 0.022f } ?: unsnapped
    }
    val position = if (showTicks) stringResource(Res.string.world_tick, range.tickAt(value).toString())
        else stringResource(Res.string.shape_timeline_position_percent, (value * 100).toInt())
    val preview = previewLabel?.invoke(drag?.let(::target) ?: tick)
    if (preview != null) Text(preview, style = MaterialTheme.typography.labelLarge)
    Slider(
        value = value,
        onValueChange = {
            if (drag == null) onStart()
            drag = it
        },
        onValueChangeFinished = {
            val released = drag
            drag = null
            if (released != null) onSeek(target(released))
        },
        modifier = Modifier.fillMaxWidth().keepFromSystemGestures().semantics {
            contentDescription = label
            stateDescription = preview ?: position
        },
        enabled = enabled && range.first < range.last,
    )
    if (markers.isNotEmpty()) {
        val ink = MaterialTheme.colorScheme.tertiary
        Canvas(Modifier.fillMaxWidth().height(7.dp).padding(horizontal = 10.dp)) {
            for (tick in markers) {
                if (tick !in range) continue
                drawCircle(ink, 2.5.dp.toPx(), Offset(range.fractionAt(tick) * size.width, size.height / 2))
            }
        }
    }
}

@Composable
internal fun InterventionMarker.label(showTicks: Boolean = true): String {
    plan?.let { return stringResource(Res.string.plan_marker, stringResource(it.title())) }
    if (kind == Intervention.Rain) return stringResource(Res.string.shape_rain_marker, regionName(region))
    if (kind == Intervention.Protect) return stringResource(Res.string.shape_refuge_marker, regionName(region))
    if (!showTicks && kind == Intervention.Reforest) return stringResource(Res.string.shape_grove_marker, regionName(region))
    val resource = if (kind == Intervention.Reforest) Res.string.timeline_reforest_event else Res.string.timeline_supplement_event
    return stringResource(resource, regionName(region), tick.toString())
}

@Composable
internal fun InterventionResult.label(showTicks: Boolean = false): String {
    if (amount == 0 && (!showTicks || kind == Intervention.Rain)) return stringResource(Res.string.shape_did_not_apply)
    if (kind == Intervention.Rain) return stringResource(Res.string.shape_rain_arrived)
    if (kind == Intervention.Protect) return stringResource(Res.string.shape_refuge_applied, regionName(region))
    if (!showTicks && kind == Intervention.Reforest) {
        return stringResource(branchworld.shared.generated.resources.Res.string.grove_applied, amount)
    }
    val resource = if (kind == Intervention.Reforest) Res.string.plants_applied else Res.string.supplement_applied
    return stringResource(resource, tick.toString(), regionName(region), amount)
}

internal fun Modifier.submitTickOnEnter(submit: () -> Boolean): Modifier = onPreviewKeyEvent {
    it.type == KeyEventType.KeyDown && (it.key == Key.Enter || it.key == Key.NumPadEnter) && submit()
}
