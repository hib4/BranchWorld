package id.branchworld.game.ui.world.hud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import branchworld.shared.generated.resources.Res
import branchworld.shared.generated.resources.moments_title
import branchworld.shared.generated.resources.open_timeline
import branchworld.shared.generated.resources.world_menu
import id.branchworld.game.data.PlayerProfile
import id.branchworld.game.domain.session.ComparisonView
import id.branchworld.game.domain.session.IslandLoad
import id.branchworld.game.domain.session.PlaceOutcome
import id.branchworld.game.domain.session.TimelineView
import id.branchworld.game.platform.sound.WorldSound
import id.branchworld.game.ui.app.ShellLayout
import id.branchworld.game.ui.components.Board
import id.branchworld.game.ui.components.PegButton
import id.branchworld.game.ui.theme.GameIcons
import id.branchworld.game.ui.theme.LocalSpacing
import id.branchworld.game.ui.world.WorldOverlay
import id.branchworld.game.ui.world.WorldPanel
import id.branchworld.game.ui.world.WorldScreenState
import id.branchworld.game.ui.world.WorldSoundState
import id.branchworld.game.ui.world.comparison.ComparisonOverlay
import id.branchworld.game.ui.world.worldTitle
import id.branchworld.simulation.ecology.environment.DayLength
import id.branchworld.simulation.projection.PlaceTool
import id.branchworld.simulation.projection.WorldCamera
import id.branchworld.simulation.projection.WorldPick
import id.branchworld.simulation.projection.WorldRenderProjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * A living world's interface (rules v12): its name and census, the creature card, the hand's notes, and the dock with
 * time, the palette of things to set down, moments, timeline and menu.
 */
@Composable
internal fun BoxScope.LivingWorldHud(
    session: IslandLoad.Ready,
    pose: WorldRenderProjection?,
    comparison: ComparisonView?,
    timelineView: TimelineView?,
    ui: WorldScreenState,
    layout: ShellLayout,
    profile: PlayerProfile?,
    diagnostics: Boolean,
    sound: WorldSoundState,
    messages: HandMessages,
    hud: (@Composable () -> Unit)?,
    operations: CoroutineScope,
    updateProfile: ((PlayerProfile) -> PlayerProfile) -> Unit,
) {
    val spacing = LocalSpacing.current
    val wide = layout.wide
    if (comparison == null && ui.overlay == null) {
        LivingTopBoard(worldTitle(session.key, profile), pose, wide,
            Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(spacing.edge(12.dp)))
        if (hud != null && (!layout.sidePanel || ui.panel == null)) {
            Box(Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(spacing.edge(12.dp))
                .widthIn(max = if (wide) 265.dp else 124.dp)) { hud() }
        }
    }
    val selected = ui.selectedId?.takeIf { id -> pose?.creatures?.any { it.id == id } == true }
    val card = selected?.let { id -> pose?.creatures?.firstOrNull { it.id == id } }
    val tool = ui.tool
    if (comparison == null && ui.overlay == null && ui.panel == null) {
        Column(Modifier.align(Alignment.BottomStart).safeDrawingPadding().padding(spacing.edge(10.dp)),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ui.pastPick?.let {
                PastEditPrompt(((pose?.tick ?: 0) / DayLength + 1).toInt(), onBranch = {
                    val name = messages.experiment((timelineView?.branches?.size ?: 1))
                    ui.pastPick = null
                    if (session.fork(name)) {
                        sound.play(WorldSound.Fork)
                        if (tool == PlaceTool.Eraser) session.erase(it) else it.ground?.let { ground -> session.place(tool, ground) }
                    }
                }, onNow = {
                    ui.pastPick = null
                    operations.launch { session.switchBranch(session.timelineView.value.branchId) }
                })
            }
            if (card != null) {
                val following = (ui.focus as? WorldCamera.Focus.Creature)?.id == card.id
                CreatureCard(card, checkNotNull(pose).tick, pose.life?.packs?.firstOrNull { it.id == card.life?.packId }?.members?.size ?: 1,
                    following, canRemove = timelineView?.editsAllowed == true,
                    onFollow = { if (following) ui.focus = null else ui.requestFocus(WorldCamera.Focus.Creature(card.id)) },
                    onRemove = {
                        when (session.erase(WorldPick(card.ground, creatureId = card.id))) {
                            PlaceOutcome.ViewingPast -> ui.pastPick = WorldPick(card.ground, creatureId = card.id)
                            else -> ui.selectedId = null
                        }
                    },
                    onClose = { ui.selectedId = null; ui.focus = null })
            }
            val note = ui.handNote ?: if (pose?.life?.census?.values?.sum() == 0 && profile?.seen("living-hand") != true)
                ToolHint(tool) else null
            note?.let { HandNote(it) }
            Board(padding = if (layout.tightDock) 4.dp else 6.dp, depth = 4.dp) {
                val playback by session.clock.playback.collectAsStateWithLifecycle()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TimeControls(playback.playing, playback.speed, enabled = timelineView?.seeking != true,
                        onToggle = session.clock::togglePlaying, onSpeed = session.clock::setSpeed)
                    PlacePalette(tool, compact = !wide) {
                        ui.tool = it
                        if (it != PlaceTool.Inspect) updateProfile { p -> p.see("living-hand") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        PegButton(GameIcons.Book, stringResource(Res.string.moments_title), { ui.showPanel(WorldPanel.Moments) })
                        PegButton(GameIcons.Branches, stringResource(Res.string.open_timeline), { ui.showPanel(WorldPanel.Timeline) })
                        PegButton(GameIcons.Menu, stringResource(Res.string.world_menu), {
                            session.clock.pause()
                            ui.overlay = WorldOverlay.Pause
                        })
                    }
                }
            }
        }
    }
    if (comparison != null) {
        ComparisonOverlay(comparison, Modifier.align(Alignment.Center).safeDrawingPadding().padding(spacing.edge(16.dp)),
            diagnostics = diagnostics)
    }
}
