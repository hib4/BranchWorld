package id.branchworld.game.ui.legacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import branchworld.shared.generated.resources.Res
import branchworld.shared.generated.resources.close_chapter
import branchworld.shared.generated.resources.crossing_show_way
import branchworld.shared.generated.resources.grove_applied
import branchworld.shared.generated.resources.inspect_story
import branchworld.shared.generated.resources.open_timeline
import branchworld.shared.generated.resources.pause_world
import branchworld.shared.generated.resources.petition_open
import branchworld.shared.generated.resources.play_world
import branchworld.shared.generated.resources.restore_grove
import branchworld.shared.generated.resources.shape_world
import branchworld.shared.generated.resources.world_menu
import id.branchworld.game.data.PlayerProfile
import id.branchworld.game.domain.session.ComparisonView
import id.branchworld.game.domain.session.IslandLoad
import id.branchworld.game.domain.session.TimelineView
import id.branchworld.game.ui.app.ShellLayout
import id.branchworld.game.ui.components.Block
import id.branchworld.game.ui.components.BlockSize
import id.branchworld.game.ui.components.Board
import id.branchworld.game.ui.components.Peg
import id.branchworld.game.ui.components.PegButton
import id.branchworld.game.ui.theme.GameIcons
import id.branchworld.game.ui.theme.LocalSpacing
import id.branchworld.game.ui.theme.Wood
import id.branchworld.game.ui.theme.chapterPaint
import id.branchworld.game.ui.world.WorldOverlay
import id.branchworld.game.ui.world.WorldPanel
import id.branchworld.game.ui.world.WorldScreenState
import id.branchworld.game.ui.world.comparison.ComparisonOverlay
import id.branchworld.game.ui.world.dayLabel
import id.branchworld.game.ui.world.echo.LivingEchoPlaces
import id.branchworld.simulation.core.ShapingRulesVersion
import id.branchworld.simulation.core.VillageRulesVersion
import id.branchworld.simulation.ecology.chapters.Chapter
import id.branchworld.simulation.projection.WorldCamera
import id.branchworld.simulation.projection.WorldRenderProjection
import org.jetbrains.compose.resources.stringResource

/**
 * The interface of the earlier chapters and sandboxes (rules v8–v11): the chapter board and its concern, the guide or
 * the village's petition, and the dock with shaping, the village, inspection, timeline and menu.
 */
@Composable
internal fun BoxScope.ChapterWorldHud(
    session: IslandLoad.Ready,
    title: String,
    story: Story?,
    pose: WorldRenderProjection?,
    comparison: ComparisonView?,
    timelineView: TimelineView?,
    ui: WorldScreenState,
    layout: ShellLayout,
    profile: PlayerProfile?,
    diagnostics: Boolean,
    comparisonDetailsOpen: Boolean,
    hud: (@Composable () -> Unit)?,
    updateProfile: ((PlayerProfile) -> PlayerProfile) -> Unit,
) {
    val spacing = LocalSpacing.current
    val wide = layout.wide
    val panel = ui.panel
    val state0 = session.clock.state
    val paint = chapterPaint(session.chapter, session.key.sandbox)
    val asking = pose?.let { state0.settlements?.village?.openPetition() }
    val compactTargeting = !wide && panel == WorldPanel.Grove && pose?.shaping != null
    if (!compactTargeting && comparison == null && ui.overlay == null && ui.closed == null) Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().safeDrawingPadding().padding(spacing.edge(12.dp)),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) {
            Board(Modifier.widthIn(max = 330.dp), padding = 13.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    story?.let { Peg(it.numeral, paint = paint) }
                    Text(title, style = if (wide) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f, fill = false).semantics { heading() })
                }
                val note = if (wide && asking != null) stringResource(Res.string.petition_open, settlementName(asking.siteId, session.chapter))
                    else stringResource(chapterConcern(session.chapter, state0, pose))
                Text(note, style = MaterialTheme.typography.bodySmall, color = Wood.WalnutSoft)
                if (panel != WorldPanel.Grove) {
                    ui.plantingNotice?.let { amount ->
                        Text(stringResource(Res.string.grove_applied, amount),
                            Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodySmall)
                    }
                }
                pose?.let { Peg(dayLabel(it.tick, it.climate?.dayPhase)) }
                val route = session.world.herdRoute
                if (session.chapter == Chapter.LastCrossing && route.isNotEmpty()) {
                    Block(stringResource(Res.string.crossing_show_way), {
                        ui.requestFocus(WorldCamera.Focus.EchoSite(route, false))
                    }, size = BlockSize.Small, icon = GameIcons.Eye, paint = Wood.BeechLight)
                }
                fireFocus(pose?.fire)?.let { (label, grounds) ->
                    Block(stringResource(label), {
                        ui.requestFocus(WorldCamera.Focus.EchoSite(grounds, false))
                    }, size = BlockSize.Small, icon = GameIcons.Eye, paint = Wood.BeechLight)
                }
                if (story != null && session.canCloseChapter && timelineView?.seeking == false) {
                    Block(stringResource(Res.string.close_chapter), {
                        session.clock.pause()
                        ui.overlay = WorldOverlay.Closing
                    }, icon = GameIcons.Seal, paint = paint, size = BlockSize.Small)
                }
            }
        }
        if (hud != null && (!layout.sidePanel || panel == null)) {
            Box(Modifier.widthIn(max = if (wide) 265.dp else 124.dp)) { hud() }
        }
    }
    if (comparison == null && panel == null && ui.overlay == null && ui.closed == null) {
        val guide = profile?.let { guideFor(it, session, timelineView, pose?.tick) }
        if (layout.shortLandscape) {
            val side = Modifier.align(Alignment.CenterEnd).safeDrawingPadding().padding(spacing.edge(10.dp))
                .widthIn(max = (layout.maxWidth * 0.42f).coerceAtMost(332.dp)).heightIn(max = layout.height - 105.dp)
            if (asking != null) PetitionCard(session, side, compact = true)
            else guide?.let { (id, text) -> KeeperGuide(text, { updateProfile { it.see(id) } }, side) }
        }
        Column(Modifier.align(Alignment.BottomStart).safeDrawingPadding().padding(spacing.edge(10.dp)),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!layout.shortLandscape && asking == null) guide?.let { (id, text) -> KeeperGuide(text, { updateProfile { it.see(id) } }) }
            if (!layout.shortLandscape) PetitionCard(session, compact = !wide,
                modifier = Modifier.heightIn(max = layout.height * if (wide) 0.52f else 0.40f))
            Board(padding = if (layout.tightDock) 4.dp else 6.dp, depth = 4.dp) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val playback by session.clock.playback.collectAsStateWithLifecycle()
                    val playing = playback.playing
                    val compact = !wide
                    @Composable fun action(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
                        if (compact) PegButton(icon, label, onClick, enabled)
                        else Block(label, onClick, icon = icon, enabled = enabled, size = BlockSize.Small, paint = Wood.BeechLight)
                    }
                    action(if (playing) GameIcons.Pause else GameIcons.Play,
                        stringResource(if (playing) Res.string.pause_world else Res.string.play_world),
                        enabled = timelineView?.seeking != true, onClick = session.clock::togglePlaying)
                    Block(stringResource(if (session.key.rules >= ShapingRulesVersion) Res.string.shape_world else Res.string.restore_grove), {
                        if (pose?.shaping != null) session.clock.pause()
                        ui.showPanel(WorldPanel.Grove)
                    }, icon = if (compact) null else GameIcons.Leaf, paint = paint, size = if (compact) BlockSize.Compact else BlockSize.Small)
                    if (session.key.rules >= VillageRulesVersion) {
                        action(GameIcons.Home, settlementName(1, session.chapter)) {
                            ui.showPanel(WorldPanel.Village)
                            val home = pose?.settlements?.firstOrNull()?.village
                            if (home != null) ui.requestFocus(WorldCamera.Focus.Village(home.structures.map { it.ground } + home.center))
                        }
                    }
                    action(GameIcons.Eye, stringResource(Res.string.inspect_story)) { ui.showPanel(WorldPanel.Inspect) }
                    action(GameIcons.Branches, stringResource(Res.string.open_timeline)) { ui.showPanel(WorldPanel.Timeline) }
                    action(GameIcons.Menu, stringResource(Res.string.world_menu)) {
                        session.clock.pause()
                        ui.overlay = WorldOverlay.Pause
                    }
                }
            }
        }
    }
    if (comparison != null) {
        ComparisonOverlay(comparison, Modifier.align(Alignment.Center).safeDrawingPadding().padding(spacing.edge(16.dp)),
            diagnostics = diagnostics)
        if (!diagnostics && !comparisonDetailsOpen) {
            LivingEchoPlaces(session, comparison,
                Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(spacing.edge(12.dp))
                    .widthIn(max = if (layout.sidePanel) (layout.maxWidth * 0.54f).coerceAtMost(480.dp) else 340.dp)) {
                ui.requestFocus(WorldCamera.Focus.EchoSite(it, layout.sidePanel))
            }
        }
    }
}
