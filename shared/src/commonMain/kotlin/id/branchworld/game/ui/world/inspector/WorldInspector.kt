package id.branchworld.game.ui.world.inspector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import branchworld.shared.generated.resources.Res
import branchworld.shared.generated.resources.commands_queued
import branchworld.shared.generated.resources.grove_applied
import branchworld.shared.generated.resources.grove_choose_region
import branchworld.shared.generated.resources.grove_forage_absent
import branchworld.shared.generated.resources.grove_forage_grazed
import branchworld.shared.generated.resources.grove_forage_holding
import branchworld.shared.generated.resources.grove_fork_to_plant
import branchworld.shared.generated.resources.grove_pending
import branchworld.shared.generated.resources.grove_target_help
import branchworld.shared.generated.resources.landmarks
import branchworld.shared.generated.resources.no_forage_deficit
import branchworld.shared.generated.resources.no_planting_sites
import branchworld.shared.generated.resources.plants_applied
import branchworld.shared.generated.resources.reforest_region
import branchworld.shared.generated.resources.region_details
import branchworld.shared.generated.resources.region_instructions
import branchworld.shared.generated.resources.regional_ecology
import branchworld.shared.generated.resources.settlement_inspector
import branchworld.shared.generated.resources.shape_firebreak_cut
import branchworld.shared.generated.resources.shape_rain_arrived
import branchworld.shared.generated.resources.shape_refuge_declared
import branchworld.shared.generated.resources.supplement_applied
import branchworld.shared.generated.resources.supplement_forage
import id.branchworld.game.domain.session.IslandLoad
import id.branchworld.game.ui.components.Block
import id.branchworld.game.ui.components.BlockSize
import id.branchworld.game.ui.theme.Wood
import id.branchworld.game.ui.theme.chapterPaint
import id.branchworld.game.ui.world.LandscapeGuide
import id.branchworld.simulation.ecology.Intervention
import id.branchworld.simulation.ecology.RegionWidth
import id.branchworld.simulation.ecology.region
import id.branchworld.simulation.projection.GroundPoint
import id.branchworld.simulation.terrain.LandmarkKind
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun WorldInspector(
    island: IslandLoad.Ready,
    compact: Boolean,
    onCreatureFocus: (Long) -> Unit,
    onLandmarkFocus: (LandmarkKind) -> Unit,
    onWildlifeLocation: (GroundPoint) -> Unit,
    playback: @Composable () -> Unit,
) {
    var regionsOpen by rememberSaveable { mutableStateOf(false) }
    var settlementsOpen by rememberSaveable { mutableStateOf(false) }
    var landmarksOpen by rememberSaveable { mutableStateOf(false) }
    val pose by island.clock.render.collectAsStateWithLifecycle()
    val settlements = island.clock.state.settlements
    val watershed = island.world.terrain.watershed
    val water = pose.climate?.watershed
    key(island.key, pose.branchId) { WildlifeInspector(pose, onCreatureFocus, onWildlifeLocation, playback) }
    HorizontalDivider()
    if (!compact && settlements != null) SettlementInspector(settlements)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (watershed != null && water != null) {
            Block(stringResource(Res.string.landmarks), { landmarksOpen = !landmarksOpen },
                paint = if (landmarksOpen) Wood.Woad else Wood.BeechLight, size = BlockSize.Compact, checked = landmarksOpen)
        }
        if (compact && settlements != null) {
            Block(stringResource(Res.string.settlement_inspector), { settlementsOpen = !settlementsOpen },
                paint = if (settlementsOpen) Wood.Woad else Wood.BeechLight, size = BlockSize.Compact, checked = settlementsOpen)
        }
        if (pose.shaping == null) Block(stringResource(Res.string.regional_ecology), { regionsOpen = !regionsOpen },
                paint = if (regionsOpen) Wood.Woad else Wood.BeechLight, size = BlockSize.Compact, checked = regionsOpen)
    }
    if (landmarksOpen && watershed != null && water != null) LandscapeGuide(watershed, water, onLandmarkFocus)
    if (compact && settlements != null && settlementsOpen) SettlementInspector(settlements, showHeading = false)
    if (regionsOpen && pose.shaping == null) RegionalControls(island)
}

@Composable
private fun RegionalControls(island: IslandLoad.Ready) {
    val pose by island.clock.render.collectAsStateWithLifecycle()
    val feedback by island.interventions.collectAsStateWithLifecycle()
    val timeline by island.timelineView.collectAsStateWithLifecycle()
    val regions = remember(island, pose.branchId, pose.tick) { island.inspectRegions() }
    if (regions.isEmpty()) return
    var selected by remember(island) {
        mutableStateOf(listOf(6, 5, 9, 10).firstOrNull { regions[it].canPlant } ?: 6)
    }
    RegionPicker(selected, onSelect = { selected = it })
    val region = regions[selected]
    Text(stringResource(Res.string.region_details, regionName(selected), region.forage, region.saplings))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { island.intervene(Intervention.Reforest, selected) },
            enabled = region.canPlant && timeline.editsAllowed,
        ) { Text(stringResource(Res.string.reforest_region)) }
        OutlinedButton(
            onClick = { island.intervene(Intervention.Supplement, selected) },
            enabled = region.canSupplement && timeline.editsAllowed,
        ) { Text(stringResource(Res.string.supplement_forage)) }
    }
    if (!region.canPlant) Text(stringResource(Res.string.no_planting_sites))
    if (!region.canSupplement) Text(stringResource(Res.string.no_forage_deficit))
    val live = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    if (feedback.queued > 0) Text(stringResource(Res.string.commands_queued, feedback.queued), live)
    for (result in feedback.applied) {
        val message = when (result.kind) {
            Intervention.Reforest -> Res.string.plants_applied
            Intervention.Supplement -> Res.string.supplement_applied
            Intervention.Rain -> Res.string.shape_rain_arrived
            Intervention.Protect -> Res.string.shape_refuge_declared
            Intervention.Firebreak -> Res.string.shape_firebreak_cut
            Intervention.Plan -> continue
        }
        Text(stringResource(message, result.tick.toString(), regionName(result.region), result.amount), live)
    }
}

@Composable
internal fun GroveControls(island: IslandLoad.Ready, onRegionFocus: (Int) -> Unit) {
    val timeline by island.timelineView.collectAsStateWithLifecycle()
    val feedback by island.interventions.collectAsStateWithLifecycle()
    val regions = remember(island, timeline.branchId, timeline.tick) { island.inspectRegions() }
    if (regions.isEmpty()) {
        Text(stringResource(Res.string.no_planting_sites))
        return
    }
    var selected by rememberSaveable(island.clock.seed) { mutableStateOf(6) }
    LaunchedEffect(timeline.branchId, selected) { onRegionFocus(selected) }
    Text(stringResource(Res.string.grove_target_help))
    RegionPicker(selected, onSelect = { selected = it })
    val region = regions[selected]
    val scheduled = feedback.queued > 0 && island.markers(timeline.branchId).any {
        it.queued && it.kind == Intervention.Reforest && it.region == selected
    }
    val applied = feedback.applied.firstOrNull { it.kind == Intervention.Reforest && it.region == selected }
    val forageMessage = when {
        region.canSupplement -> Res.string.grove_forage_grazed
        region.forage > 0 -> Res.string.grove_forage_holding
        else -> Res.string.grove_forage_absent
    }
    Text(stringResource(forageMessage))
    Block(stringResource(Res.string.grove_choose_region), { island.intervene(Intervention.Reforest, selected) },
        enabled = timeline.editsAllowed && region.canPlant && !scheduled, paint = chapterPaint(island.chapter, island.key.sandbox))
    val live = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    when {
        scheduled -> Text(stringResource(Res.string.grove_pending), live)
        applied != null -> Text(stringResource(Res.string.grove_applied, applied.amount), live)
        !region.canPlant -> Text(stringResource(Res.string.no_planting_sites))
        !timeline.editsAllowed && timeline.canFork -> Text(stringResource(Res.string.grove_fork_to_plant))
    }
}

@Composable
internal fun RegionPicker(selected: Int, onSelect: (Int) -> Unit) {
    Text(stringResource(Res.string.region_instructions))
    Column(Modifier.widthIn(max = 360.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(RegionWidth) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(RegionWidth) { column ->
                    val region = row * RegionWidth + column
                    val active = region == selected
                    Block(regionName(region), { onSelect(region) }, Modifier.weight(1f), selected = active,
                        paint = if (active) Wood.Woad else Wood.BeechLight, size = BlockSize.Compact)
                }
            }
        }
    }
}

internal fun regionName(region: Int) = "${'A' + region / RegionWidth}${region % RegionWidth + 1}"
