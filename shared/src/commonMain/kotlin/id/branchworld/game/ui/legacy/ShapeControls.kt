package id.branchworld.game.ui.legacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
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
import branchworld.shared.generated.resources.crossing_wait_warning
import branchworld.shared.generated.resources.ember_wait_warning
import branchworld.shared.generated.resources.grove_choose_region
import branchworld.shared.generated.resources.no_planting_sites
import branchworld.shared.generated.resources.restore_grove
import branchworld.shared.generated.resources.shape_already_protected
import branchworld.shared.generated.resources.shape_already_raining
import branchworld.shared.generated.resources.shape_blocked_no_fuel
import branchworld.shared.generated.resources.shape_call_rain
import branchworld.shared.generated.resources.shape_cancel
import branchworld.shared.generated.resources.shape_choose_region
import branchworld.shared.generated.resources.shape_confirm_firebreak
import branchworld.shared.generated.resources.shape_confirm_protect
import branchworld.shared.generated.resources.shape_confirm_rain
import branchworld.shared.generated.resources.shape_exact_sites
import branchworld.shared.generated.resources.shape_firebreak
import branchworld.shared.generated.resources.shape_firebreak_forecast
import branchworld.shared.generated.resources.shape_firebreak_length
import branchworld.shared.generated.resources.shape_fork_to_act
import branchworld.shared.generated.resources.shape_grove_forecast
import branchworld.shared.generated.resources.shape_next_harvest
import branchworld.shared.generated.resources.shape_no_ground
import branchworld.shared.generated.resources.shape_opportunity
import branchworld.shared.generated.resources.shape_pasture_area
import branchworld.shared.generated.resources.shape_protect
import branchworld.shared.generated.resources.shape_protect_forecast
import branchworld.shared.generated.resources.shape_protect_pasture
import branchworld.shared.generated.resources.shape_queued
import branchworld.shared.generated.resources.shape_rain_forecast
import branchworld.shared.generated.resources.shape_reaches_bank
import branchworld.shared.generated.resources.shape_reaches_field
import branchworld.shared.generated.resources.shape_reaches_local_bank
import branchworld.shared.generated.resources.shape_reaches_local_field
import branchworld.shared.generated.resources.shape_reaches_local_settlement
import branchworld.shared.generated.resources.shape_reaches_village
import branchworld.shared.generated.resources.shape_spent
import branchworld.shared.generated.resources.shape_wait_warning
import branchworld.shared.generated.resources.shape_watch_warning
import branchworld.shared.generated.resources.shape_water_far
import id.branchworld.game.domain.session.IslandLoad
import id.branchworld.game.ui.components.Block
import id.branchworld.game.ui.components.BlockSize
import id.branchworld.game.ui.theme.Wood
import id.branchworld.game.ui.theme.chapterPaint
import id.branchworld.game.ui.world.hud.label
import id.branchworld.game.ui.world.inspector.RegionPicker
import id.branchworld.game.ui.world.inspector.label
import id.branchworld.game.ui.world.inspector.regionName
import id.branchworld.game.ui.world.timeline.label
import id.branchworld.simulation.core.FireRulesVersion
import id.branchworld.simulation.core.RangeRulesVersion
import id.branchworld.simulation.ecology.Intervention
import id.branchworld.simulation.ecology.chapters.Chapter
import id.branchworld.simulation.ecology.environment.ShapeBlock
import id.branchworld.simulation.ecology.region
import id.branchworld.simulation.ecology.settlement.SettlementTurn
import id.branchworld.simulation.projection.GroundPoint
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
        // An endorsed plan also waits in the queue, but it is not a power this panel can show or cancel.
        island.markers(timeline.branchId).firstOrNull { it.queued && it.kind in ShapePowers }
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
    var breakRegion by remember(island, timeline.branchId) {
        mutableIntStateOf(pending?.takeIf { it.kind == Intervention.Firebreak }?.region
            ?: island.clock.state.settlements?.sites?.firstOrNull()?.center?.region() ?: groveRegion)
    }
    val hollowmere = island.chapter == Chapter.FadingGrove
    val ember = island.chapter == Chapter.EmberSeason
    val refuges = island.key.rules >= RangeRulesVersion
    val firebreaks = island.key.rules >= FireRulesVersion
    var picking by remember(island) { mutableStateOf(false) }
    val region = when (power) {
        Intervention.Rain -> rainRegion
        Intervention.Protect -> refugeRegion
        Intervention.Firebreak -> breakRegion
        else -> groveRegion
    }
    val target = remember(island, power, region, timeline.branchId, timeline.tick, feedback.queued) {
        island.shapePreview(power, region)
    } ?: return
    LaunchedEffect(timeline.branchId, power, region, target.highlights) { onTarget(region, power == Intervention.Rain, target.highlights) }

    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((choice, label) in listOf(Intervention.Rain to Res.string.shape_call_rain, Intervention.Reforest to Res.string.restore_grove,
            Intervention.Protect to Res.string.shape_protect, Intervention.Firebreak to Res.string.shape_firebreak)) {
            if (choice == Intervention.Protect && !refuges) continue
            if (choice == Intervention.Firebreak && !firebreaks) continue
            val selected = power == choice
            Block(stringResource(label), { power = choice }, paint = if (selected) Wood.Woad else Wood.BeechLight,
                size = BlockSize.Compact, selected = selected)
        }
    }
    Text(stringResource(when (power) {
        Intervention.Rain -> Res.string.shape_rain_forecast
        Intervention.Protect -> Res.string.shape_protect_forecast
        Intervention.Firebreak -> Res.string.shape_firebreak_forecast
        else -> Res.string.shape_grove_forecast
    }), style = MaterialTheme.typography.bodyMedium)
    if (power == Intervention.Firebreak && target.cut > 0) {
        Text(stringResource(Res.string.shape_firebreak_length, target.cut), style = MaterialTheme.typography.bodySmall)
    }
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
    val targetName = if (power == Intervention.Protect && region == island.pastureRegion()) {
        stringResource(Res.string.shape_pasture_area)
    } else regionName(region)
    Block(stringResource(Res.string.shape_choose_region) + " · " + targetName, { picking = !picking },
        paint = if (picking) Wood.Woad else Wood.BeechLight, size = BlockSize.Compact, checked = picking)
    if (picking) RegionPicker(region) {
        when (power) {
            Intervention.Rain -> rainRegion = it
            Intervention.Protect -> refugeRegion = it
            Intervention.Firebreak -> breakRegion = it
            else -> groveRegion = it
        }
    }

    val blocked = when (target.blocked) {
        ShapeBlock.AwaitWarning -> when {
            hollowmere -> Res.string.shape_wait_warning
            ember -> Res.string.ember_wait_warning
            else -> Res.string.crossing_wait_warning
        }
        ShapeBlock.Spent -> Res.string.shape_spent
        ShapeBlock.AlreadyRaining -> Res.string.shape_already_raining
        ShapeBlock.NoGround -> Res.string.shape_no_ground
        ShapeBlock.NoSites -> Res.string.no_planting_sites
        ShapeBlock.Reserved -> Res.string.shape_queued
        ShapeBlock.AlreadyProtected -> Res.string.shape_already_protected
        ShapeBlock.NoFuel -> Res.string.shape_blocked_no_fuel
        null -> if (!timeline.editsAllowed && timeline.canFork) Res.string.shape_fork_to_act else null
    }
    val live = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    blocked?.let { Text(stringResource(it), live) }
    feedback.applied.firstOrNull { it.region == region && it.kind == power }?.let {
        Text(it.label(), live)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (pending != null) {
            Block(stringResource(Res.string.shape_cancel), island::cancelShape, enabled = !timeline.seeking,
                paint = Wood.BeechLight, size = BlockSize.Compact)
        } else {
            Block(stringResource(when (power) {
                Intervention.Rain -> Res.string.shape_confirm_rain
                Intervention.Protect -> Res.string.shape_confirm_protect
                Intervention.Firebreak -> Res.string.shape_confirm_firebreak
                else -> Res.string.grove_choose_region
            }), { island.intervene(power, region) }, enabled = target.blocked == null && timeline.editsAllowed,
                paint = chapterPaint(island.chapter, island.key.sandbox), size = BlockSize.Small)
        }
        if (!timeline.seeking && timeline.tick < Long.MAX_VALUE - SettlementTurn) {
            val watchWarning = hollowmere && target.blocked == ShapeBlock.AwaitWarning && timeline.tick < 240
            Block(stringResource(if (watchWarning) Res.string.shape_watch_warning else Res.string.shape_next_harvest), {
                val next = if (watchWarning) 240L else (timeline.tick / SettlementTurn + 1) * SettlementTurn
                island.playTo(next)
                onObserve()
            }, paint = Wood.BeechLight, size = BlockSize.Compact)
        }
    }
}

/** The powers the Shape panel offers, previews and cancels. */
private val ShapePowers = setOf(Intervention.Rain, Intervention.Reforest, Intervention.Protect, Intervention.Firebreak)
