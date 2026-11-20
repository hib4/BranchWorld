package id.branchworld.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.layout.height
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import branchworld.shared.generated.resources.Res
import branchworld.shared.generated.resources.close_timeline
import branchworld.shared.generated.resources.comparison_open
import branchworld.shared.generated.resources.comparison_needs_fork
import branchworld.shared.generated.resources.open_timeline
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
import androidx.compose.ui.semantics.contentDescription
import id.branchworld.game.ecology.Intervention
import id.branchworld.game.ecology.InterventionResult
import id.branchworld.game.timeline.InterventionMarker
import id.branchworld.game.timeline.MaxBranchNameLength
import id.branchworld.game.timeline.RecentInterventions
import id.branchworld.game.timeline.fractionAt
import id.branchworld.game.timeline.inTreeOrder
import id.branchworld.game.timeline.tickAt
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TimelineControls(island: IslandLoad.Ready, visible: Boolean) {
    var open by rememberSaveable(island.clock.seed) { mutableStateOf(false) }
    if (!visible) return
    val view by island.timelineView.collectAsStateWithLifecycle()
    OutlinedButton(onClick = { open = !open }) {
        Text(stringResource(if (open) Res.string.close_timeline else Res.string.open_timeline))
    }
    if (open) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.large,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TimelinePanel(island, view)
            }
        }
    }
}

@Composable
private fun TimelinePanel(island: IslandLoad.Ready, view: TimelineView) {
    val operations = rememberCoroutineScope()
    var input by rememberSaveable(view.branchId) { mutableStateOf("") }
    var forkDialog by remember { mutableStateOf(false) }
    var renameDialog by remember { mutableStateOf(false) }
    val target = input.toLongOrNull()
    val branch = view.active
    val feedback by island.interventions.collectAsStateWithLifecycle()
    val markers = remember(island, branch.id, branch.interventionCount, feedback.queued) { island.markers(branch.id) }
    val executed = remember(markers) { markers.filterNot { it.queued } }
    val tree = remember(view.branches) { view.branches.inTreeOrder() }

    Text(stringResource(Res.string.timeline_branch, branch.name), style = MaterialTheme.typography.titleMedium)
    for ((entry, depth) in tree) {
        FilterChip(
            selected = entry.id == branch.id,
            onClick = { if (entry.id != branch.id) operations.launch { island.switchBranch(entry.id) } },
            enabled = !view.seeking,
            modifier = Modifier.fillMaxWidth().padding(start = (depth.coerceAtMost(5) * 12).dp),
            label = {
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text(entry.name, style = MaterialTheme.typography.labelLarge)
                    val detail = if (entry.parentId == null) {
                        stringResource(Res.string.timeline_range, entry.start.toString(), entry.head.toString())
                    } else {
                        stringResource(Res.string.timeline_forked_at, entry.start.toString(), entry.head.toString())
                    }
                    Text(detail, style = MaterialTheme.typography.bodySmall)
                }
            },
        )
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { renameDialog = true }, enabled = !view.seeking) {
            Text(stringResource(Res.string.timeline_rename))
        }
        OutlinedButton(onClick = island::openComparison, enabled = !view.seeking && tree.size > 1) {
            Text(stringResource(Res.string.comparison_open))
        }
    }
    if (tree.size == 1) Text(stringResource(Res.string.comparison_needs_fork))
    Text(stringResource(Res.string.timeline_range, branch.start.toString(), branch.head.toString()))
    TimelineScrubber(
        branch.start..branch.head, view.tick, !view.seeking,
        markers = executed, onStart = island.clock::pause,
    ) { requested ->
        if (requested != view.tick) operations.launch { island.seek(requested) }
    }
    OutlinedTextField(
        value = input,
        onValueChange = { if (it.length <= 19 && it.all { char -> char in '0'..'9' }) input = it },
        modifier = Modifier.widthIn(max = 220.dp),
        label = { Text(stringResource(Res.string.timeline_tick_entry)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        enabled = !view.seeking,
        singleLine = true,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { target?.let { operations.launch { island.seek(it) } } },
            enabled = !view.seeking && target != null && target in branch.start..branch.head && target != view.tick,
        ) { Text(stringResource(Res.string.timeline_seek)) }
        OutlinedButton(
            onClick = { target?.let(island::playTo) },
            enabled = !view.seeking && target != null && target > view.tick,
        ) { Text(stringResource(Res.string.timeline_play_to)) }
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
    if (view.tick < branch.head) Text(stringResource(Res.string.timeline_past))
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
                ) { Text(marker.label()) }
            }
        }
        for (marker in markers.asSequence().filter { it.queued }.take(RecentInterventions)) {
            Text(stringResource(Res.string.timeline_queued_event, marker.label()))
        }
        if (previousEvents > RecentInterventions) {
            Text(stringResource(Res.string.timeline_earlier_events))
        }
    }
    for (result in feedback.applied) Text(result.label(), live)
    if (forkDialog) {
        BranchNameDialog(
            title = stringResource(Res.string.timeline_fork_at, view.tick.toString()),
            action = stringResource(Res.string.timeline_create),
            suggestedName = stringResource(Res.string.timeline_default_name, view.branches.size + 1),
            onDismiss = { forkDialog = false },
            onFork = { if (island.fork(it)) forkDialog = false },
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
    markers: List<InterventionMarker> = emptyList(),
    onStart: () -> Unit = {},
    onSeek: (Long) -> Unit,
) {
    var drag by remember(range.first, range.last) { mutableStateOf<Float?>(null) }
    val value = drag ?: range.fractionAt(tick)
    val label = stringResource(Res.string.timeline_scrubber)
    val position = stringResource(Res.string.world_tick, range.tickAt(value).toString())
    Slider(
        value = value,
        onValueChange = {
            if (drag == null) onStart()
            drag = it
        },
        onValueChangeFinished = {
            val target = drag
            drag = null
            if (target != null) onSeek(range.tickAt(target))
        },
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = label
            stateDescription = position
        },
        enabled = enabled && range.first < range.last,
    )
    if (markers.isNotEmpty()) {
        val ink = MaterialTheme.colorScheme.tertiary
        Canvas(Modifier.fillMaxWidth().height(7.dp).padding(horizontal = 10.dp)) {
            for (event in markers) {
                drawCircle(ink, 2.5.dp.toPx(), Offset(range.fractionAt(event.tick) * size.width, size.height / 2))
            }
        }
    }
}

@Composable
internal fun InterventionMarker.label(): String {
    val resource = if (kind == Intervention.Reforest) Res.string.timeline_reforest_event else Res.string.timeline_supplement_event
    return stringResource(resource, regionName(region), tick.toString())
}

@Composable
internal fun InterventionResult.label(): String {
    val resource = if (kind == Intervention.Reforest) Res.string.plants_applied else Res.string.supplement_applied
    return stringResource(resource, tick.toString(), regionName(region), amount)
}
