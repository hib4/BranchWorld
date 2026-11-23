package id.branchworld.game.ui.world

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import branchworld.shared.generated.resources.Res
import branchworld.shared.generated.resources.autosave_done
import branchworld.shared.generated.resources.autosave_off
import branchworld.shared.generated.resources.autosave_waiting
import branchworld.shared.generated.resources.island_generating
import branchworld.shared.generated.resources.island_generation_failed
import branchworld.shared.generated.resources.menu_atlas
import branchworld.shared.generated.resources.menu_my_worlds
import branchworld.shared.generated.resources.retry_island
import id.branchworld.game.data.Bookmark
import id.branchworld.game.data.GameSettings
import id.branchworld.game.data.PlayerProfile
import id.branchworld.game.data.SaveStatus
import id.branchworld.game.domain.IslandSessionKey
import id.branchworld.game.domain.session.ComparisonView
import id.branchworld.game.domain.session.IslandLoad
import id.branchworld.game.domain.session.TimelineView
import id.branchworld.game.platform.photo.PhotoMeta
import id.branchworld.game.platform.photo.PhotoResult
import id.branchworld.game.platform.photo.PhotoSaver
import id.branchworld.game.platform.viewport.WorldViewport
import id.branchworld.game.ui.app.ShellLayout
import id.branchworld.game.ui.app.navigation.HomeScreen
import id.branchworld.game.ui.components.Board
import id.branchworld.game.ui.components.Scrim
import id.branchworld.game.ui.legacy.ChapterClosed
import id.branchworld.game.ui.legacy.ChapterClosing
import id.branchworld.game.ui.legacy.ChapterWorldHud
import id.branchworld.game.ui.legacy.Story
import id.branchworld.game.ui.legacy.record
import id.branchworld.game.ui.theme.LocalSpacing
import id.branchworld.game.ui.theme.Wood
import id.branchworld.game.ui.theme.chapterPaint
import id.branchworld.game.ui.world.hud.HandMessages
import id.branchworld.game.ui.world.hud.LivingWorldHud
import id.branchworld.simulation.projection.WorldRenderProjection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** What an open world can ask of the shell around it. */
internal interface WorldActions {
    /** Leaves for the title, keeping the world. */
    fun leave()

    /** Leaves for another home screen. */
    fun leaveTo(screen: HomeScreen)
    fun enter(key: IslandSessionKey, start: Boolean)
    fun retry()
    fun autosave()
    fun updateProfile(change: (PlayerProfile) -> PlayerProfile)
}

/** Everything an open world draws over its 3D view. */
@Composable
internal fun BoxScope.WorldScreen(
    session: IslandLoad.Ready?,
    failed: Boolean,
    islandKey: IslandSessionKey,
    ui: WorldScreenState,
    layout: ShellLayout,
    profile: PlayerProfile?,
    pose: WorldRenderProjection?,
    comparison: ComparisonView?,
    timelineView: TimelineView?,
    storage: SaveStatus?,
    autosavedAt: Long?,
    viewport: WorldViewport?,
    photos: PhotoSaver?,
    keepsWorlds: Boolean,
    sound: WorldSoundState,
    messages: HandMessages,
    comparisonDetailsOpen: Boolean,
    onComparisonDetails: (Boolean) -> Unit,
    operations: CoroutineScope,
    photoScope: CoroutineScope,
    actions: WorldActions,
) {
    val spacing = LocalSpacing.current
    val settings = profile?.settings ?: GameSettings()
    val diagnostics = settings.tickTools
    val hud: (@Composable () -> Unit)? = viewport?.let { { it.Hud() } }
    val story = Story.of(session?.chapter)

    if (session != null && ui.overlay == WorldOverlay.Photo) {
        PhotoControls(
            onLeave = { ui.overlay = null },
            onSave = photos?.let { saver ->
                {
                    if (!ui.savingPhoto) {
                        val moment = PhotoMeta(session.key.terrain.seed, pose?.tick ?: session.clock.state.tick)
                        ui.savingPhoto = true
                        ui.photoResult = null
                        photoScope.launch {
                            try {
                                val png = viewport?.capturePng()
                                ui.photoResult = if (png == null) PhotoResult.ViewNotReady else saver.save(png, moment)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Exception) {
                                ui.photoResult = PhotoResult.CouldNotSave
                            } finally {
                                ui.savingPhoto = false
                            }
                        }
                    }
                }
            },
            saving = ui.savingPhoto,
            result = ui.photoResult,
            modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(spacing.edge(12.dp)),
        )
    } else if (session != null && session.key.living) {
        LivingWorldHud(session, pose, comparison, timelineView, ui, layout, profile, diagnostics, sound, messages, hud,
            operations, actions::updateProfile)
    } else if (session != null) {
        val title = Story.of(islandKey)?.let { stringResource(it.title) } ?: stringResource(islandTitle(islandKey.terrain))
        ChapterWorldHud(session, title, story, pose, comparison, timelineView, ui, layout, profile, diagnostics,
            comparisonDetailsOpen, hud, actions::updateProfile)
    }
    if (session == null) {
        Board(Modifier.align(Alignment.Center).safeDrawingPadding().padding(spacing.edge(16.dp))) {
            Text(stringResource(if (failed) Res.string.island_generation_failed else Res.string.island_generating))
            if (failed) TextButton(onClick = actions::retry) {
                Text(stringResource(Res.string.retry_island), color = Wood.Woad)
            }
        }
    }

    if (ui.overlay == null && ui.closed == null) {
        WorldPanelHost(session, comparison, ui, layout, settings, worldSounds = sound.sounds != null, seed = islandKey.terrain.seed,
            comparisonDetailsOpen, onComparisonDetails,
            bookmarks = profile?.bookmarks.orEmpty().filter { it.slot == islandKey.slot },
            sound = sound, operations = operations, updateProfile = actions::updateProfile)
    }

    if (session != null && ui.overlay == WorldOverlay.Pause) {
        Scrim(Modifier.matchParentSize())
        val tick = pose?.tick ?: session.clock.state.tick
        val view = timelineView
        val marked = view != null && profile?.bookmarks?.any {
            it.slot == islandKey.slot && it.branchId == view.branchId && it.tick == tick
        } == true
        val living = session.key.living
        PauseMenu(
            paint = chapterPaint(session.chapter, session.key.sandbox),
            saved = when {
                !keepsWorlds -> stringResource(Res.string.autosave_off)
                autosavedAt != null -> stringResource(Res.string.autosave_done, dayOf(autosavedAt))
                else -> stringResource(Res.string.autosave_waiting)
            },
            marked = marked,
            diagnostics = diagnostics,
            onResume = { ui.overlay = null },
            onTimeline = { ui.overlay = null; ui.showPanel(WorldPanel.Timeline) },
            onMark = {
                if (view != null) actions.updateProfile { it.mark(Bookmark(islandKey.slot, view.branchId, tick, view.active.name)) }
            },
            onPhoto = {
                ui.photoResult = null
                ui.overlay = WorldOverlay.Photo
                ui.showPanel(null)
            },
            onSettings = { ui.overlay = null; ui.showPanel(WorldPanel.Settings) },
            onAtlas = { actions.leaveTo(if (living) HomeScreen.Worlds else HomeScreen.Atlas) },
            atlasLabel = stringResource(if (living) Res.string.menu_my_worlds else Res.string.menu_atlas),
            onTitle = actions::leave,
            onDiagnostics = { ui.overlay = null; ui.showPanel(WorldPanel.Diagnostics) },
            modifier = Modifier.align(Alignment.Center).safeDrawingPadding().padding(spacing.edge(16.dp)).heightIn(max = layout.height - 32.dp),
        )
        if (storage != null) {
            Board(Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(spacing.edge(12.dp)), padding = 8.dp) {
                SaveMessage(storage, diagnostics)
            }
        }
    }

    if (session != null && story != null && !islandKey.isShared && ui.overlay == WorldOverlay.Closing) {
        Scrim(Modifier.matchParentSize())
        val outcomes = remember(session, timelineView) { session.outcomes() }
        ChapterClosing(story, outcomes, timelineView?.branchId ?: 1, onChoose = { outcome ->
            actions.updateProfile { it.record(outcome.record(story, islandKey.slot)) }
            actions.autosave()
            ui.overlay = null
            ui.closed = story to outcome.branch.name
        }, onCancel = { ui.overlay = null },
            modifier = Modifier.align(Alignment.Center).safeDrawingPadding().padding(spacing.edge(16.dp)).widthIn(max = 560.dp)
                .heightIn(max = layout.height - 32.dp))
    }
    ui.closed?.let { (closedStory, name) ->
        Scrim(Modifier.matchParentSize())
        ChapterClosed(closedStory, name, onNext = closedStory.next?.let { next -> {
            ui.closed = null
            actions.leave()
            actions.enter(next.key, start = true)
        } }, onStay = { ui.closed = null },
            modifier = Modifier.align(Alignment.Center).safeDrawingPadding().padding(spacing.edge(16.dp)))
    }
}
