package id.branchworld.game.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import branchworld.shared.generated.resources.*
import id.branchworld.game.data.GameSettings
import id.branchworld.game.data.PlayerProfile
import id.branchworld.game.data.WorldCodeImport
import id.branchworld.game.data.key
import id.branchworld.game.domain.AutosaveQuietMillis
import id.branchworld.game.domain.IslandSessionKey
import id.branchworld.game.domain.autosaveMoments
import id.branchworld.game.domain.places
import id.branchworld.game.domain.session.IslandLoad
import id.branchworld.game.platform.AppServices
import id.branchworld.game.ui.app.navigation.HomeScreen
import id.branchworld.game.ui.app.navigation.ShellRoute
import id.branchworld.game.ui.app.navigation.ShellState
import id.branchworld.game.ui.components.PlaceholderIsland
import id.branchworld.game.ui.home.HomeActions
import id.branchworld.game.ui.home.HomeRoute
import id.branchworld.game.ui.theme.GameTheme
import id.branchworld.game.ui.theme.LocalSpacing
import id.branchworld.game.ui.theme.Spacing
import id.branchworld.game.ui.theme.UiPreferences
import id.branchworld.game.ui.world.WorldActions
import id.branchworld.game.ui.world.WorldEffects
import id.branchworld.game.ui.world.WorldOverlay
import id.branchworld.game.ui.world.WorldScreen
import id.branchworld.game.ui.world.WorldScreenState
import id.branchworld.game.ui.world.WorldSoundState
import id.branchworld.game.ui.world.hud.HandMessages
import id.branchworld.simulation.ecology.life.PlacementCheck
import id.branchworld.simulation.ecology.life.WorldStart
import id.branchworld.simulation.projection.PlaceTool
import id.branchworld.simulation.projection.PlacementPreview
import id.branchworld.simulation.projection.WorldCamera
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** The sea-glass teal behind the world while it loads. Web hosts paint it on the page instead. */
private val ShellBackdrop = Color(0xFF4D8585)

/**
 * BranchWorld's interface on every platform: the title and its menus, or an open world over the 3D view. The host
 * lends its viewport, storage, sound and photos through [services]; [foreground] is false while the window is hidden.
 */
@OptIn(FlowPreview::class)
@Composable
fun App(services: AppServices = AppServices(), foreground: Boolean = true) {
    val viewport = services.viewport
    val sounds = services.sounds
    val shell = services.shell
    var state by rememberSaveable(stateSaver = ShellState.saver) { mutableStateOf(ShellState()) }
    val ui = rememberSaveable(saver = WorldScreenState.Saver) { WorldScreenState() }
    val isWorld = state.route == ShellRoute.World
    val owner = LocalViewModelStoreOwner.current
    val retained = if (owner == null) null else viewModel(viewModelStoreOwner = owner) { AppViewModel(services.saveStore) }
    val fallbackScope = rememberCoroutineScope()
    val model = retained ?: remember(services.saveStore) { AppViewModel(services.saveStore) }
    val islands = model.worlds
    val profiles = model.profiles
    val operations = retained?.viewModelScope ?: fallbackScope
    val profile by profiles.profile.collectAsState()
    val settings = profile?.settings ?: GameSettings()
    val storage by islands.saveStatus.collectAsStateWithLifecycle()
    val autosavedAt by islands.lastAutosave.collectAsStateWithLifecycle()
    val load by islands.state.collectAsStateWithLifecycle()
    val islandKey = state.island
    var comparisonDetailsOpen by rememberSaveable(state.terrain) { mutableStateOf(false) }
    val session = (load as? IslandLoad.Ready)?.takeIf { it.key == islandKey }
    val comparison = session?.comparison?.collectAsStateWithLifecycle()?.value
    val pose = session?.world?.poses?.collectAsStateWithLifecycle()?.value
    val interventions = session?.interventions?.collectAsStateWithLifecycle()?.value
    val timelineView = session?.timelineView?.collectAsStateWithLifecycle()?.value

    // The profile: settings, chapters, atlas and marks. The original single save is imported once, never rewritten.
    LaunchedEffect(profiles) {
        profiles.load { loaded ->
            loaded.lastPlayed?.let { last -> if (state.route == ShellRoute.Home) state = state.selectIsland(last.key()) }
        }
    }
    LaunchedEffect(profiles) { profiles.keepWritten() }
    val profileReady = profile != null
    LaunchedEffect(islands, islandKey, profileReady) { if (profileReady) model.open(islandKey) }

    // Keep the open world whenever something worth keeping happens, and whenever the app stops.
    LaunchedEffect(session, isWorld) {
        val island = session ?: return@LaunchedEffect
        if (!isWorld) return@LaunchedEffect
        model.autosave()
        island.autosaveMoments().drop(1).debounce(AutosaveQuietMillis).collect { model.autosave() }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, session) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && session != null) operations.launch { model.autosave() }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val playbackState = if (shell.playInUnfocusedWindow) Lifecycle.State.STARTED else Lifecycle.State.RESUMED
    val lifecycleState by lifecycle.currentStateAsState()
    val sound = WorldSoundState(sounds, settings.worldSounds, foreground && lifecycleState.isAtLeast(playbackState))
    val messages = HandMessages.current()
    WorldEffects(session, ui, isWorld, pose, comparison, interventions, sound, lifecycle, foreground, playbackState,
        stillShowing = { state.route == ShellRoute.World && state.island == islandKey }, messages,
        onComparisonClosed = { comparisonDetailsOpen = false })

    // The camera follows what the interface is looking at, and previews where the hand would set something down.
    val living = session?.key?.living == true
    val tool = ui.tool
    val hover = session?.world?.pointer?.hover?.collectAsStateWithLifecycle()?.value
    val placement = if (living && isWorld && tool.places && ui.overlay == null) hover?.ground?.let { ground ->
        PlacementPreview(ground, tool, remember(ground, tool, pose?.tick) { session.placementCheck(tool, ground) } == PlacementCheck.Ok)
    } else null
    val selected = ui.selectedId?.takeIf { id -> pose?.creatures?.any { it.id == id } == true }
    val cameraRequest = WorldCamera(ui.focus, settings.reducedMotion, opening = !isWorld, focusRequest = ui.focusRequest,
        targetSites = ui.shapeSites, placement = placement, selected = selected)
    SideEffect { viewport?.present(cameraRequest) }

    fun showWorld(key: IslandSessionKey, start: Boolean) {
        ui.showPanel(null)
        ui.overlay = null
        state = state.selectIsland(key).openWorld()
        ui.autoStart = start
    }
    fun leaveWorld() {
        session?.clock?.pause()
        operations.launch { model.autosave() }
        ui.showPanel(null)
        ui.overlay = null
        state = state.back()
    }
    val goBack: () -> Unit = {
        when {
            ui.closed != null -> ui.closed = null
            ui.overlay != null -> ui.overlay = null
            comparisonDetailsOpen -> comparisonDetailsOpen = false
            comparison != null -> session.closeComparison()
            ui.panel != null -> ui.showPanel(null)
            isWorld -> {
                session?.clock?.pause()
                ui.overlay = WorldOverlay.Pause
            }
            state.screen != HomeScreen.Title -> state = state.show(HomeScreen.Title)
        }
    }
    shell.backHandler(isWorld || ui.panel != null || state.screen != HomeScreen.Title, goBack)
    fun retryOpen() {
        operations.launch { model.open(islandKey) }
    }

    val preferences = UiPreferences(settings.interfaceSounds && sounds != null, settings.reducedMotion) { sounds?.play(it) }
    GameTheme(settings.textScale, preferences) {
        BoxWithConstraints(Modifier.fillMaxSize().background(if (shell.viewportUnderneath) Color.Transparent else ShellBackdrop).onPreviewKeyEvent {
            if (it.key == Key.Escape && it.type == KeyEventType.KeyUp && (ui.panel != null || isWorld || state.screen != HomeScreen.Title)) {
                goBack()
                true
            } else false
        }) {
            val layout = ShellLayout.of(this)
            CompositionLocalProvider(LocalSpacing provides Spacing(compact = layout.compact)) {
                if (viewport == null && session != null) {
                    PlaceholderIsland(Modifier.matchParentSize())
                } else {
                    viewport?.Viewport(Modifier.matchParentSize(), session?.world)
                }
                val failed = (load as? IslandLoad.Failed)?.key == islandKey
                if (!isWorld) {
                    HomeRoute(state.screen, profile, canContinue = profileReady && !failed, failed = failed,
                        worldSounds = sounds != null, layout = layout, importCode = model.importCode,
                        actions = object : HomeActions {
                            override fun enter(key: IslandSessionKey, start: Boolean) = showWorld(key, start)
                            override fun createWorld(seed: Long, start: WorldStart, name: String) {
                                ui.selectedId = null
                                ui.tool = PlaceTool.Rabbit
                                showWorld(profiles.createWorld(seed, start, name), start = true)
                            }
                            override fun show(screen: HomeScreen) { state = state.show(screen) }
                            override fun openCode(result: WorldCodeImport.Open) {
                                profiles.update { it.visit(result.entry) }
                                showWorld(result.entry.key(), start = false)
                            }
                            override fun retry() = retryOpen()
                            override fun changeSettings(next: GameSettings) = profiles.update { it.copy(settings = next) }
                        },
                        hud = viewport?.takeIf { session != null }?.let { { it.Hud() } })
                } else {
                    WorldScreen(session, failed, islandKey, ui, layout, profile, pose, comparison, timelineView, storage, autosavedAt,
                        viewport, services.photos, keepsWorlds = model.keepsWorlds, sound, messages, comparisonDetailsOpen,
                        onComparisonDetails = { comparisonDetailsOpen = it }, operations = operations, photoScope = fallbackScope,
                        actions = object : WorldActions {
                            override fun leave() = leaveWorld()
                            override fun leaveTo(screen: HomeScreen) {
                                leaveWorld()
                                state = state.show(screen)
                            }
                            override fun enter(key: IslandSessionKey, start: Boolean) = showWorld(key, start)
                            override fun retry() = retryOpen()
                            override fun autosave() { operations.launch { model.autosave() } }
                            override fun updateProfile(change: (PlayerProfile) -> PlayerProfile) = profiles.update(change)
                        })
                }
            }
        }
    }
}
