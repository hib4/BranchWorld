package id.branchworld.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import branchworld.shared.generated.resources.shape_compare_viewing
import branchworld.shared.generated.resources.shape_compare_loading
import branchworld.shared.generated.resources.shape_compare_disjoint
import branchworld.shared.generated.resources.shape_water_reserve
import branchworld.shared.generated.resources.shape_harvest
import branchworld.shared.generated.resources.shape_rooted
import branchworld.shared.generated.resources.shape_shortage_event
import branchworld.shared.generated.resources.shape_recovered_event
import id.branchworld.game.ecology.ShapingRulesVersion
import branchworld.shared.generated.resources.Res
import branchworld.shared.generated.resources.comparison_catches
import branchworld.shared.generated.resources.comparison_foxes
import branchworld.shared.generated.resources.comparison_births
import branchworld.shared.generated.resources.comparison_browse
import branchworld.shared.generated.resources.comparison_close
import branchworld.shared.generated.resources.comparison_deaths
import branchworld.shared.generated.resources.comparison_deer
import branchworld.shared.generated.resources.comparison_delta
import branchworld.shared.generated.resources.comparison_disjoint
import branchworld.shared.generated.resources.comparison_event_loading
import branchworld.shared.generated.resources.comparison_events
import branchworld.shared.generated.resources.comparison_forage
import branchworld.shared.generated.resources.comparison_homes
import branchworld.shared.generated.resources.comparison_left
import branchworld.shared.generated.resources.comparison_loading
import branchworld.shared.generated.resources.comparison_no_events
import branchworld.shared.generated.resources.comparison_open
import branchworld.shared.generated.resources.comparison_rabbits
import branchworld.shared.generated.resources.comparison_residents
import branchworld.shared.generated.resources.comparison_stored_food
import branchworld.shared.generated.resources.comparison_stored_wood
import branchworld.shared.generated.resources.comparison_right
import branchworld.shared.generated.resources.comparison_saplings
import branchworld.shared.generated.resources.comparison_select_left
import branchworld.shared.generated.resources.comparison_select_right
import branchworld.shared.generated.resources.comparison_ticks
import branchworld.shared.generated.resources.comparison_viewing
import branchworld.shared.generated.resources.timeline_range
import branchworld.shared.generated.resources.timeline_seek
import branchworld.shared.generated.resources.timeline_seek_failed
import branchworld.shared.generated.resources.timeline_tick_entry
import id.branchworld.game.timeline.BranchSummary
import id.branchworld.game.timeline.ComparedBranch
import id.branchworld.game.timeline.inTreeOrder
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ComparisonHeader(
    island: IslandLoad.Ready,
    view: ComparisonView,
    branches: List<BranchSummary>,
    showBranchPickers: Boolean,
    diagnostics: Boolean = false,
) {
    val target = view.target
    val event = view.event
    LaunchedEffect(island, target) { target?.let { island.loadComparison(it) } }
    LaunchedEffect(island, event) { event?.let { island.loadComparisonEvent(it) } }
    val order = remember(branches) { branches.inTreeOrder().map { it.branch } }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(Res.string.comparison_open),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showBranchPickers) {
                BranchPicker(stringResource(Res.string.comparison_left, view.left.name), order, view.left.id, view.right.id) {
                    island.selectComparison(it, view.right.id)
                }
                BranchPicker(stringResource(Res.string.comparison_right, view.right.name), order, view.right.id, view.left.id) {
                    island.selectComparison(view.left.id, it)
                }
            }
            OutlinedButton(onClick = island::closeComparison) { Text(stringResource(Res.string.comparison_close)) }
        }
        if (view.pair != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !view.showRight, onClick = { island.showComparisonSide(false) },
                    label = { Text(stringResource(Res.string.comparison_select_left)) })
                FilterChip(selected = view.showRight, onClick = { island.showComparisonSide(true) },
                    label = { Text(stringResource(Res.string.comparison_select_right)) })
            }
            val side = if (view.showRight) view.right else view.left
            Text(if (diagnostics || island.key.rules < ShapingRulesVersion) {
                stringResource(Res.string.comparison_viewing, side.name, view.pair.tick.toString())
            } else stringResource(Res.string.shape_compare_viewing, side.name))
        }
    }
}

@Composable
internal fun BranchPicker(
    label: String, branches: List<BranchSummary>, selected: Int, opposite: Int,
    showTicks: Boolean = true, onSelect: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text(label) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (branch in branches) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(branch.name)
                            if (showTicks) Text(
                                stringResource(Res.string.timeline_range, branch.start.toString(), branch.head.toString()),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    },
                    enabled = branch.id != selected && branch.id != opposite,
                    onClick = {
                        open = false
                        onSelect(branch.id)
                    },
                )
            }
        }
    }
}

@Composable
internal fun ComparisonOverlay(view: ComparisonView, modifier: Modifier = Modifier, diagnostics: Boolean = true) {
    val message = when {
        view.range.isEmpty() -> {
            val earlier = if (view.left.head < view.right.head) view.left else view.right
            if (diagnostics) stringResource(Res.string.comparison_disjoint, earlier.name, view.range.first.toString())
            else stringResource(Res.string.shape_compare_disjoint, earlier.name)
        }
        view.failed -> stringResource(Res.string.timeline_seek_failed)
        view.pair == null -> if (diagnostics) stringResource(Res.string.comparison_loading, view.tick.toString()) else stringResource(Res.string.shape_compare_loading)
        else -> return
    }
    Surface(modifier = modifier, shape = MaterialTheme.shapes.medium) {
        Text(message, Modifier.padding(14.dp).semantics { liveRegion = LiveRegionMode.Polite })
    }
}

@Composable
internal fun ComparisonDetails(island: IslandLoad.Ready, view: ComparisonView, diagnostics: Boolean = false) {
    val tick = view.tick ?: return
    var input by remember(view.left.id, view.right.id, tick) { mutableStateOf(tick.toString()) }
    val pending = input.toLongOrNull()
    val focus = LocalFocusManager.current
    val target = pending?.takeIf { it in view.range && it != tick }
    fun seek() {
        focus.clearFocus()
        target?.let(island::compareAt)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val showTicks = diagnostics || island.key.rules < ShapingRulesVersion
        if (showTicks) Text(stringResource(Res.string.comparison_ticks, view.range.first.toString(), view.range.last.toString()))
        TimelineScrubber(view.range, tick, enabled = true, showTicks = showTicks, onSeek = island::compareAt)
        if (showTicks) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { if (it.length <= 19 && it.all { char -> char in '0'..'9' }) input = it },
                modifier = Modifier.widthIn(max = 175.dp).submitTickOnEnter {
                    if (target == null) false else {
                        seek()
                        true
                    }
                },
                label = { Text(stringResource(Res.string.timeline_tick_entry)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (target != null) seek() }),
            )
            OutlinedButton(
                enabled = target != null,
                onClick = ::seek,
            ) { Text(stringResource(Res.string.timeline_seek)) }
        }
        val pair = view.pair ?: return@Column
        ComparisonTable(view, pair.left, pair.right)
        ComparisonEvents(island, view.left, pair.left, view)
        ComparisonEvents(island, view.right, pair.right, view)
    }
}

@Composable
internal fun ComparisonTable(view: ComparisonView, left: ComparedBranch, right: ComparedBranch) {
    val labels = listOf(
        Res.string.comparison_saplings, Res.string.comparison_browse, Res.string.comparison_forage,
        Res.string.comparison_rabbits, Res.string.comparison_deer, Res.string.comparison_births, Res.string.comparison_deaths,
    )
    val leftValues = left.metrics.values()
    val rightValues = right.metrics.values()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        BoxWithConstraints(Modifier.padding(12.dp)) {
            val compact = maxWidth < 420.dp || LocalDensity.current.fontScale >= 1.3f
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricRow("", view.left.name, view.right.name, stringResource(Res.string.comparison_delta), compact, header = true)
                HorizontalDivider()
                val firstShape = left.projection.shaping
                val secondShape = right.projection.shaping
                if (firstShape != null && secondShape != null) {
                    val first = firstShape.welfare
                    val second = secondShape.welfare
                    if (first != null && second != null) {
                        ComparisonValue(Res.string.shape_water_reserve, first.water, second.water, compact)
                        ComparisonValue(Res.string.shape_harvest, first.harvest, second.harvest, compact)
                    }
                    ComparisonValue(Res.string.shape_rooted, firstShape.rootedGroves, secondShape.rootedGroves, compact)
                    HorizontalDivider()
                }
                for (index in labels.indices) {
                    val a = leftValues[index]
                    val b = rightValues[index]
                    val change = b - a
                    val delta = if (change > 0) "+$change" else change.toString()
                    MetricRow(stringResource(labels[index]), a.toString(), b.toString(), delta, compact)
                }
                val leftWildlife = left.projection.wildlife
                val rightWildlife = right.projection.wildlife
                if (leftWildlife != null && rightWildlife != null) {
                    val foxDelta = rightWildlife.foxes - leftWildlife.foxes
                    val catchDelta = rightWildlife.catches - leftWildlife.catches
                    MetricRow(stringResource(Res.string.comparison_foxes), leftWildlife.foxes.toString(),
                        rightWildlife.foxes.toString(), if (foxDelta > 0) "+$foxDelta" else foxDelta.toString(), compact)
                    MetricRow(stringResource(Res.string.comparison_catches), leftWildlife.catches.toString(),
                        rightWildlife.catches.toString(), if (catchDelta > 0) "+$catchDelta" else catchDelta.toString(), compact)
                }
                val a = left.settlement
                val b = right.settlement
                if (a != null && b != null) {
                    for ((label, first, second) in listOf(
                        Triple(Res.string.comparison_residents, a.residents, b.residents),
                        Triple(Res.string.comparison_stored_food, a.food, b.food),
                        Triple(Res.string.comparison_stored_wood, a.wood, b.wood),
                        Triple(Res.string.comparison_homes, a.homes, b.homes),
                    )) {
                        val change = second - first
                        MetricRow(stringResource(label), first.toString(), second.toString(), if (change > 0) "+$change" else change.toString(), compact)
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonValue(label: org.jetbrains.compose.resources.StringResource, left: Int, right: Int, compact: Boolean) {
    val change = right - left
    MetricRow(stringResource(label), left.toString(), right.toString(),
        if (change > 0) "+$change" else change.toString(), compact)
}

@Composable
private fun MetricRow(label: String, left: String, right: String, delta: String, compact: Boolean, header: Boolean = false) {
    val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
    Column {
        if (compact && label.isNotEmpty()) Text(label, style = style)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!compact) Text(label, Modifier.weight(1.35f), style = style)
            Text(left, Modifier.weight(1f), textAlign = TextAlign.End, style = style)
            Text(right, Modifier.weight(1f), textAlign = TextAlign.End, style = style)
            Text(delta, Modifier.weight(if (compact) 1f else 0.85f), textAlign = TextAlign.End, style = style)
        }
    }
}

@Composable
private fun ComparisonEvents(island: IslandLoad.Ready, branch: BranchSummary, side: ComparedBranch, view: ComparisonView) {
    Text(stringResource(Res.string.comparison_events, branch.name), style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.semantics { heading() })
    val wildlifeMoments = side.projection.wildlife?.moments.orEmpty().filter { it.tick > branch.start }.takeLast(3)
    val welfare = side.projection.shaping?.welfare
    if (welfare?.firstShortage != null) Text(stringResource(Res.string.shape_shortage_event), style = MaterialTheme.typography.bodySmall)
    if (welfare?.firstRecovery != null) Text(stringResource(Res.string.shape_recovered_event), style = MaterialTheme.typography.bodySmall)
    if (side.events.isEmpty() && wildlifeMoments.isEmpty() && side.settlementEvents.isEmpty() && welfare?.firstShortage == null) {
        Text(stringResource(Res.string.comparison_no_events))
    }
    for (milestone in side.settlementEvents) Text(milestone.label(island.key.rules < ShapingRulesVersion), style = MaterialTheme.typography.bodySmall)
    for (moment in wildlifeMoments) Text(moment.description(), style = MaterialTheme.typography.bodySmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (marker in side.events) {
            OutlinedButton(onClick = { island.inspectComparisonEvent(branch.id, marker) }) { Text(marker.label(island.key.rules < ShapingRulesVersion)) }
        }
    }
    if (view.event?.branchId == branch.id) {
        val outcome = view.outcomes
        val live = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        when {
            view.outcomeFailed -> Text(stringResource(Res.string.timeline_seek_failed), live)
            outcome == null -> Text(stringResource(Res.string.comparison_event_loading), live)
            else -> for (result in outcome) Text(result.label(), live)
        }
    }
}
