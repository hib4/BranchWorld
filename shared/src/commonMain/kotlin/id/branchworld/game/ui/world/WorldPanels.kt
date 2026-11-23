package id.branchworld.game.ui.world

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import branchworld.shared.generated.resources.*
import branchworld.shared.generated.resources.Res
import branchworld.shared.generated.resources.history_title
import id.branchworld.game.data.Bookmark
import id.branchworld.game.data.GameSettings
import id.branchworld.game.data.PlayerProfile
import id.branchworld.game.data.key
import id.branchworld.game.domain.session.ComparisonView
import id.branchworld.game.domain.session.IslandLoad
import id.branchworld.game.platform.getPlatform
import id.branchworld.game.platform.sound.WorldSound
import id.branchworld.game.ui.app.ShellLayout
import id.branchworld.game.ui.components.Block
import id.branchworld.game.ui.components.BlockSize
import id.branchworld.game.ui.components.Board
import id.branchworld.game.ui.components.BoardSheet
import id.branchworld.game.ui.components.ScrollColumn
import id.branchworld.game.ui.components.WoodSwitchRow
import id.branchworld.game.ui.home.SettingsSheet
import id.branchworld.game.ui.legacy.ShapeControls
import id.branchworld.game.ui.legacy.VillagePanel
import id.branchworld.game.ui.share.WorldCodeShareControl
import id.branchworld.game.ui.theme.GameIcons
import id.branchworld.game.ui.theme.LocalSpacing
import id.branchworld.game.ui.theme.Wood
import id.branchworld.game.ui.world.comparison.ComparisonDetails
import id.branchworld.game.ui.world.comparison.ComparisonHeader
import id.branchworld.game.ui.world.echo.LivingEchoDock
import id.branchworld.game.ui.world.history.HistoryBoard
import id.branchworld.game.ui.world.inspector.GroveControls
import id.branchworld.game.ui.world.inspector.WorldInspector
import id.branchworld.game.ui.world.timeline.TimelineControls
import id.branchworld.simulation.core.ShapingRulesVersion
import id.branchworld.simulation.ecology.chapters.Chapter
import id.branchworld.simulation.ecology.region
import id.branchworld.simulation.projection.GroundPoint
import id.branchworld.simulation.projection.WorldCamera
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The open panel beside or below the world: settings, or a board with the panel's content. */
@Composable
internal fun BoxScope.WorldPanelHost(
    session: IslandLoad.Ready?,
    comparison: ComparisonView?,
    ui: WorldScreenState,
    layout: ShellLayout,
    settings: GameSettings,
    worldSounds: Boolean,
    seed: Long,
    comparisonDetailsOpen: Boolean,
    onComparisonDetails: (Boolean) -> Unit,
    bookmarks: List<Bookmark>,
    sound: WorldSoundState,
    operations: CoroutineScope,
    updateProfile: ((PlayerProfile) -> PlayerProfile) -> Unit,
) {
    val diagnostics = settings.tickTools
    val activePanel = (if (comparison != null) WorldPanel.Timeline else ui.panel) ?: return
    val spacing = LocalSpacing.current
    val height = layout.height
    val panelSize = if (layout.sidePanel) {
        Modifier.widthIn(max = if (layout.shortLandscape) 320.dp else 390.dp).fillMaxWidth().heightIn(max = height - 32.dp)
    } else Modifier.fillMaxWidth().heightIn(max = height *
        if (comparison != null && !comparisonDetailsOpen && !diagnostics) 0.40f else 0.65f)
    val placement = Modifier.align(if (layout.sidePanel) Alignment.CenterEnd else Alignment.BottomCenter)
        .safeDrawingPadding().padding(spacing.edge(10.dp)).then(panelSize)
    if (activePanel == WorldPanel.Settings) {
        SettingsSheet(settings, worldSounds = worldSounds,
            onChange = { next -> updateProfile { it.copy(settings = next) } }, onClose = { ui.showPanel(null) }, modifier = placement)
    } else {
        val content: @Composable () -> Unit = {
            ScrollColumn(Modifier.fillMaxWidth(), spacing = 10.dp) {
                PanelContent(activePanel, session, comparison, seed, layout.wide, comparisonDetailsOpen,
                    onDetails = onComparisonDetails, requestFocus = ui::requestFocus,
                    onShapeSites = { ui.shapeSites = it }, closePanel = { ui.showPanel(null) },
                    onForked = { sound.play(WorldSound.Fork) },
                    toggleDiagnostics = {
                        updateProfile { it.copy(settings = it.settings.copy(tickTools = !it.settings.tickTools)) }
                    },
                    diagnostics = diagnostics,
                    bookmarks = bookmarks,
                    onVisit = { mark -> operations.launch { session?.visit(mark.branchId, mark.tick) } },
                    onForget = { mark -> updateProfile { it.unmark(mark) } })
            }
        }
        if (comparison != null) {
            Board(placement) { content() }
        } else {
            BoardSheet(stringResource(activePanel.title(session?.chapter, (session?.key?.rules ?: 0) >= ShapingRulesVersion)),
                placement, onClose = { ui.showPanel(null) }) { content() }
        }
    }
}

@Composable
private fun PanelContent(
    panel: WorldPanel,
    session: IslandLoad.Ready?,
    comparison: ComparisonView?,
    seed: Long,
    wide: Boolean,
    comparisonDetailsOpen: Boolean,
    onDetails: (Boolean) -> Unit,
    requestFocus: (WorldCamera.Focus) -> Unit,
    onShapeSites: (List<GroundPoint>) -> Unit,
    closePanel: () -> Unit,
    onForked: () -> Unit,
    toggleDiagnostics: () -> Unit,
    diagnostics: Boolean,
    bookmarks: List<Bookmark>,
    onVisit: (Bookmark) -> Unit,
    onForget: (Bookmark) -> Unit,
) {
    val island = session ?: return
    when (panel) {
        WorldPanel.Inspect -> WorldInspector(
            island, compact = !wide,
            onCreatureFocus = { id -> requestFocus(WorldCamera.Focus.Creature(id)) },
            onLandmarkFocus = { kind -> requestFocus(WorldCamera.Focus.Landmark(kind)) },
            onWildlifeLocation = { ground -> requestFocus(WorldCamera.Focus.WildlifeLocation(ground)) },
            playback = { PlaybackText(island) },
        )
        WorldPanel.Grove -> if (island.key.rules >= ShapingRulesVersion) {
            ShapeControls(island, onTarget = { region, rain, sites ->
                onShapeSites(sites)
                requestFocus(WorldCamera.Focus.Region(region, rain))
            }, onObserve = closePanel)
        } else {
            GroveControls(island, onRegionFocus = { region -> requestFocus(WorldCamera.Focus.Region(region)) })
            PlaybackText(island)
        }
        WorldPanel.Village -> VillagePanel(island, onFocus = { ground -> requestFocus(WorldCamera.Focus.WildlifeLocation(ground)) })
        WorldPanel.Timeline -> if (comparison == null) {
            var showChronicle by rememberSaveable(island.key) { mutableStateOf(false) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Block(stringResource(if (showChronicle) Res.string.open_timeline else Res.string.history_title),
                    { showChronicle = !showChronicle }, paint = Wood.Woad, size = BlockSize.Small)
                if (!showChronicle) WorldCodeShareControl(island)
            }
            if (showChronicle) {
                HistoryBoard(island, bookmarks, onVisit, onForget) { ground ->
                    closePanel()
                    requestFocus(WorldCamera.Focus.WildlifeLocation(ground))
                }
            } else TimelineControls(island, diagnostics, bookmarks, onForked)
        } else {
            val timeline by island.timelineView.collectAsStateWithLifecycle()
            if (diagnostics) {
                val showDetails = wide || comparisonDetailsOpen
                ComparisonHeader(island, comparison, timeline.branches, showBranchPickers = showDetails, diagnostics = true)
                if (!wide) {
                    TextButton(onClick = { onDetails(!comparisonDetailsOpen) }) {
                        Text(stringResource(if (comparisonDetailsOpen) Res.string.hide_comparison_details else Res.string.show_comparison_details))
                    }
                }
                if (showDetails) ComparisonDetails(island, comparison, diagnostics = true)
            } else {
                LivingEchoDock(island, comparison, timeline, comparisonDetailsOpen, onDetails = onDetails)
            }
        }
        WorldPanel.Diagnostics -> {
            val timeline by island.timelineView.collectAsStateWithLifecycle()
            WoodSwitchRow(stringResource(Res.string.shape_tick_tools), null, diagnostics) { toggleDiagnostics() }
            WorldControls(island.clock, playbackEnabled = !timeline.seeking, editsEnabled = timeline.editsAllowed)
            Text(stringResource(Res.string.diagnostics_platform, remember { getPlatform().name }))
            Text(stringResource(Res.string.diagnostics_seed, seed.toString()))
            island.clock.state.creatures.firstOrNull()?.let { lead ->
                Text(stringResource(Res.string.animal_needs, "${lead.food / 10}%", "${lead.water / 10}%", "${lead.energy / 10}%"))
            }
        }
        WorldPanel.Settings -> Unit
        WorldPanel.Moments -> HistoryBoard(island, bookmarks, onVisit, onForget) { ground ->
            closePanel()
            requestFocus(WorldCamera.Focus.WildlifeLocation(ground))
        }
    }
}

@Composable
private fun PlaybackText(session: IslandLoad.Ready) {
    val playback by session.clock.playback.collectAsStateWithLifecycle()
    val navigation by session.timelineView.collectAsStateWithLifecycle()
    Block(stringResource(if (playback.playing) Res.string.pause_world else Res.string.play_world), session.clock::togglePlaying,
        size = BlockSize.Small, enabled = !navigation.seeking, icon = if (playback.playing) GameIcons.Pause else GameIcons.Play)
}

internal fun WorldPanel.title(chapter: Chapter?, shaping: Boolean): StringResource = when (this) {
    WorldPanel.Inspect -> Res.string.inspect_story
    WorldPanel.Grove -> if (shaping) Res.string.shape_world else Res.string.restore_grove
    WorldPanel.Village -> Res.string.village_panel
    WorldPanel.Timeline -> Res.string.open_timeline
    WorldPanel.Diagnostics -> Res.string.diagnostics_title
    WorldPanel.Settings -> Res.string.settings_title
    WorldPanel.Moments -> Res.string.moments_title
}
