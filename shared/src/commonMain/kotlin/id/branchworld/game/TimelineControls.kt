package id.branchworld.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import branchworld.shared.generated.resources.Res
import branchworld.shared.generated.resources.close_timeline
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
import id.branchworld.game.timeline.MaxBranchNameLength
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TimelineControls(island: IslandLoad.Ready) {
    var open by remember(island) { mutableStateOf(false) }
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
    var expanded by remember { mutableStateOf(false) }
    var input by rememberSaveable(view.branchId) { mutableStateOf("") }
    var forkDialog by remember { mutableStateOf(false) }
    val target = input.toLongOrNull()
    val branch = view.active

    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = !view.seeking) {
            Text(stringResource(Res.string.timeline_branch, branch.name))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (candidate in view.branches) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(candidate.name)
                            Text(
                                stringResource(Res.string.timeline_range, candidate.start.toString(), candidate.head.toString()),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    },
                    enabled = candidate.id != view.branchId,
                    onClick = {
                        expanded = false
                        operations.launch { island.switchBranch(candidate.id) }
                    },
                )
            }
        }
    }
    Text(stringResource(Res.string.timeline_range, branch.start.toString(), branch.head.toString()))
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
    if (forkDialog) {
        ForkDialog(
            tick = view.tick,
            suggestedName = stringResource(Res.string.timeline_default_name, view.branches.size + 1),
            onDismiss = { forkDialog = false },
            onFork = { if (island.fork(it)) forkDialog = false },
        )
    }
}

@Composable
private fun ForkDialog(tick: Long, suggestedName: String, onDismiss: () -> Unit, onFork: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(suggestedName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.timeline_fork_at, tick.toString())) },
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
                Text(stringResource(Res.string.timeline_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.save_cancel)) }
        },
    )
}
