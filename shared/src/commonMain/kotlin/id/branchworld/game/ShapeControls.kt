package id.branchworld.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import branchworld.shared.generated.resources.Res
import branchworld.shared.generated.resources.grove_choose_region
import branchworld.shared.generated.resources.no_planting_sites
import branchworld.shared.generated.resources.restore_grove
import branchworld.shared.generated.resources.shape_already_raining
import branchworld.shared.generated.resources.shape_call_rain
import branchworld.shared.generated.resources.shape_cancel
import branchworld.shared.generated.resources.shape_choose_region
import branchworld.shared.generated.resources.shape_confirm_rain
import branchworld.shared.generated.resources.shape_exact_sites
import branchworld.shared.generated.resources.shape_fork_to_act
import branchworld.shared.generated.resources.shape_grove_forecast
import branchworld.shared.generated.resources.shape_next_harvest
import branchworld.shared.generated.resources.shape_no_ground
import branchworld.shared.generated.resources.shape_opportunity
import branchworld.shared.generated.resources.shape_queued
import branchworld.shared.generated.resources.shape_already_protected
import branchworld.shared.generated.resources.shape_rain_forecast
import branchworld.shared.generated.resources.shape_reaches_bank
import branchworld.shared.generated.resources.shape_reaches_field
import branchworld.shared.generated.resources.shape_reaches_local_field
import branchworld.shared.generated.resources.shape_reaches_local_bank
import branchworld.shared.generated.resources.shape_reaches_local_settlement
import branchworld.shared.generated.resources.shape_reaches_village
import branchworld.shared.generated.resources.shape_spent
import branchworld.shared.generated.resources.shape_wait_warning
import branchworld.shared.generated.resources.crossing_wait_warning
import branchworld.shared.generated.resources.shape_watch_warning
import branchworld.shared.generated.resources.shape_water_far
import id.branchworld.game.ecology.Chapter
import id.branchworld.game.ecology.RangeRulesVersion
import branchworld.shared.generated.resources.shape_protect
import branchworld.shared.generated.resources.shape_protect_forecast
import branchworld.shared.generated.resources.shape_protect_pasture
import branchworld.shared.generated.resources.shape_confirm_protect
import id.branchworld.game.ecology.Intervention
import id.branchworld.game.ecology.SettlementTurn
import id.branchworld.game.ecology.ShapeBlock
import id.branchworld.game.simulation.GroundPoint
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ShapeControls(
    island: IslandLoad.Ready,
    onTarget: (Int, Boolean, List<GroundPoint>) -> Unit,
    onObserve: () -> Unit,
) {
    val timeline by island.timelineView.collectAsStateWithLifecycle()
    val feedback by island.interventions.collectAsStateWithLifecycle()
    val pending = remember(island, timeline.branchId, feedback.queued) {
        island.markers(timeline.branchId).firstOrNull { it.queued }
    }
    var power by remember(island, timeline.branchId) { mutableStateOf(pending?.kind ?: Intervention.Rain) }
    var rainRegion by remember(island, timeline.branchId) {
        mutableIntStateOf(pending?.takeIf { it.kind == Intervention.Rain }?.region ?: island.recommendedRainRegion())
    }
    var groveRegion by remember(island, timeline.branchId) {
        mutableIntStateOf(pending?.takeIf { it.kind == Intervention.Reforest }?.region ?: island.recommendedGroveRegion())
    }
    var refugeRegion by remember(island, timeline.branchId) {
        mutableIntStateOf(pending?.takeIf { it.kind == Intervention.Protect }?.region ?: island.pastureRegion() ?: groveRegion)
    }
    val hollowmere = island.chapter == Chapter.FadingGrove
    val refuges = island.key.rules >= RangeRulesVersion
    var picking by remember(island) { mutableStateOf(false) }
    val region = when (power) {
        Intervention.Rain -> rainRegion
        Intervention.Protect -> refugeRegion
        else -> groveRegion
    }
    val target = remember(island, power, region, timeline.branchId, timeline.tick, feedback.queued) {
        island.shapePreview(power, region)
    } ?: return
    LaunchedEffect(timeline.branchId, power, region, target.highlights) { onTarget(region, power == Intervention.Rain, target.highlights) }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(power == Intervention.Rain, { power = Intervention.Rain },
            label = { Text(stringResource(Res.string.shape_call_rain)) })
        FilterChip(power == Intervention.Reforest, { power = Intervention.Reforest },
            label = { Text(stringResource(Res.string.restore_grove)) })
        if (refuges) FilterChip(power == Intervention.Protect, { power = Intervention.Protect },
            label = { Text(stringResource(Res.string.shape_protect)) })
    }
    Text(stringResource(when (power) {
        Intervention.Rain -> Res.string.shape_rain_forecast
        Intervention.Protect -> Res.string.shape_protect_forecast
        else -> Res.string.shape_grove_forecast
    }), style = MaterialTheme.typography.bodyMedium)
    if (power == Intervention.Protect && region == island.pastureRegion()) {
        Text(stringResource(Res.string.shape_protect_pasture), style = MaterialTheme.typography.bodySmall)
    }
    Text(stringResource(Res.string.shape_opportunity), style = MaterialTheme.typography.labelLarge)

    if (power == Intervention.Reforest && target.sites.isNotEmpty()) {
        Text(stringResource(Res.string.shape_exact_sites, target.sites.size))
    } else if (target.field != null || target.bank != null) {
        Text(stringResource(when {
            target.field != null && target.bank != null ->
                if (hollowmere) Res.string.shape_reaches_village else Res.string.shape_reaches_local_settlement
            target.field != null -> if (hollowmere) Res.string.shape_reaches_field else Res.string.shape_reaches_local_field
            else -> if (hollowmere) Res.string.shape_reaches_bank else Res.string.shape_reaches_local_bank
        }))
    } else if (power == Intervention.Rain && hollowmere &&
        island.clock.state.settlements?.sites?.isNotEmpty() == true
    ) {
        Text(stringResource(Res.string.shape_water_far))
    }
    FilterChip(picking, { picking = !picking }, label = {
        Text(stringResource(Res.string.shape_choose_region) + " · " + regionName(region))
    })
    if (picking) RegionPicker(region) {
        when (power) {
            Intervention.Rain -> rainRegion = it
            Intervention.Protect -> refugeRegion = it
            else -> groveRegion = it
        }
    }

    val blocked = when (target.blocked) {
        ShapeBlock.AwaitWarning -> if (hollowmere) Res.string.shape_wait_warning else Res.string.crossing_wait_warning
        ShapeBlock.Spent -> Res.string.shape_spent
        ShapeBlock.AlreadyRaining -> Res.string.shape_already_raining
        ShapeBlock.NoGround -> Res.string.shape_no_ground
        ShapeBlock.NoSites -> Res.string.no_planting_sites
        ShapeBlock.Reserved -> Res.string.shape_queued
        ShapeBlock.AlreadyProtected -> Res.string.shape_already_protected
        null -> if (!timeline.editsAllowed && timeline.canFork) Res.string.shape_fork_to_act else null
    }
    val live = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    blocked?.let { Text(stringResource(it), live) }
    feedback.applied.firstOrNull { it.region == region && it.kind == power }?.let {
        Text(it.label(), live)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (pending != null) {
            OutlinedButton(onClick = island::cancelShape, enabled = !timeline.seeking) {
                Text(stringResource(Res.string.shape_cancel))
            }
        } else {
            Button(onClick = { island.intervene(power, region) }, enabled = target.blocked == null && timeline.editsAllowed) {
                Text(stringResource(when (power) {
                    Intervention.Rain -> Res.string.shape_confirm_rain
                    Intervention.Protect -> Res.string.shape_confirm_protect
                    else -> Res.string.grove_choose_region
                }))
            }
        }
        if (!timeline.seeking && timeline.tick < Long.MAX_VALUE - SettlementTurn) {
            val watchWarning = hollowmere && target.blocked == ShapeBlock.AwaitWarning && timeline.tick < 240
            OutlinedButton(onClick = {
                val next = if (watchWarning) 240L else (timeline.tick / SettlementTurn + 1) * SettlementTurn
                island.playTo(next)
                onObserve()
            }) {
                Text(stringResource(if (watchWarning) Res.string.shape_watch_warning else Res.string.shape_next_harvest))
            }
        }
    }
}
