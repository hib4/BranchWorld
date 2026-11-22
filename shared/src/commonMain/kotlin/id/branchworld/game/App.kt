package id.branchworld.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import branchworld.shared.generated.resources.Res
import branchworld.shared.generated.resources.*
import id.branchworld.game.ecology.Chapter
import id.branchworld.game.ecology.Intervention
import id.branchworld.game.ecology.Season
import id.branchworld.game.ecology.ShapingRulesVersion
import id.branchworld.game.ecology.VillageRulesVersion
import id.branchworld.game.ecology.Weather
import id.branchworld.game.save.Bookmark
import id.branchworld.game.save.ChapterRecord
import id.branchworld.game.save.PlayerProfile
import id.branchworld.game.save.PlayerProfileCodec
import id.branchworld.game.save.ProfileFile
import id.branchworld.game.save.WorldSaveStore
import id.branchworld.game.simulation.GroundPoint
import id.branchworld.game.simulation.WorldCamera
import id.branchworld.game.simulation.WorldRenderSource
import id.branchworld.game.timeline.ChapterOutcome
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private class WorldSession : ViewModel() {
    val islands = IslandSession()
    val profile = MutableStateFlow<PlayerProfile?>(null)
}


private enum class WorldPanel { Inspect, Grove, Village, Timeline, Diagnostics, Settings }
private enum class Overlay { Pause, Photo, Closing }

@OptIn(FlowPreview::class)
@Composable
fun App(
    viewport: (@Composable (Modifier, WorldRenderSource?) -> Unit)? = null,
    viewportHud: (@Composable () -> Unit)? = null,
    registerBackHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit = { _, _ -> },
    foreground: Boolean = true,
    saveStore: WorldSaveStore? = null,
    playSound: ((WorldSound) -> Unit)? = null,
    viewportCamera: (WorldCamera) -> Unit = {},
    playInUnfocusedWindow: Boolean = false,
    setAmbience: ((WorldAmbience) -> Unit)? = null,
) {
    var state by rememberSaveable(stateSaver = ShellState.saver) { mutableStateOf(ShellState()) }
    var panelName by rememberSaveable { mutableStateOf<String?>(null) }
    val panel = panelName?.let(WorldPanel::valueOf)
    var overlayName by rememberSaveable { mutableStateOf<String?>(null) }
    val overlay = overlayName?.let(Overlay::valueOf)
    var closed by remember { mutableStateOf<Pair<Story, String>?>(null) }
    var autoStart by remember { mutableStateOf(false) }
    var focus by remember { mutableStateOf<WorldCamera.Focus?>(null) }
    var focusRequest by remember { mutableIntStateOf(0) }
    var shapeSites by remember { mutableStateOf<List<GroundPoint>>(emptyList()) }
    val requestFocus: (WorldCamera.Focus) -> Unit = {
        focus = it
        focusRequest++
    }
    val isWorld = state.route == ShellRoute.World
    val owner = LocalViewModelStoreOwner.current
    val retained = if (owner == null) null else viewModel(viewModelStoreOwner = owner) { WorldSession() }
    val fallbackScope = rememberCoroutineScope()
    val islands = retained?.islands ?: remember { IslandSession() }
    val profileFlow = retained?.profile ?: remember { MutableStateFlow<PlayerProfile?>(null) }
    val operations = retained?.viewModelScope ?: fallbackScope
    val profile by profileFlow.collectAsState()
    val settings = profile?.settings ?: id.branchworld.game.save.GameSettings()
    val soundEnabled = settings.worldSounds
    val diagnostics = settings.tickTools
    val reducedMotion = settings.reducedMotion
    val updateProfile: ((PlayerProfile) -> PlayerProfile) -> Unit = { change -> profileFlow.value = profileFlow.value?.let(change) }
    val storage by islands.saveStatus.collectAsStateWithLifecycle()
    val autosavedAt by islands.lastAutosave.collectAsStateWithLifecycle()
    val load by islands.state.collectAsStateWithLifecycle()
    val islandKey = state.island
    var comparisonDetailsOpen by rememberSaveable(state.terrain) { mutableStateOf(false) }
    val session = (load as? IslandLoad.Ready)?.takeIf { it.key == islandKey }
    val comparison = session?.comparison?.collectAsStateWithLifecycle()?.value
    val pose = session?.world?.poses?.collectAsStateWithLifecycle()?.value
    val clock = session?.clock
    val interventions = session?.interventions?.collectAsStateWithLifecycle()?.value
    val timelineView = session?.timelineView?.collectAsStateWithLifecycle()?.value
    val planting = interventions?.applied?.firstOrNull { it.kind == Intervention.Reforest && it.amount > 0 }
    var plantingNotice by remember(session) { mutableStateOf<Int?>(null) }
    var announcedPlanting by rememberSaveable { mutableStateOf<String?>(null) }
    val showPanel: (WorldPanel?) -> Unit = { destination ->
        if (destination != panel || destination == null) {
            val keepFollowing = panel == WorldPanel.Inspect && destination == null && focus is WorldCamera.Focus.Creature
            if (!keepFollowing) focus = null
            shapeSites = emptyList()
            panelName = destination?.name
        }
    }

    // The profile: settings, chapters, atlas and marks. The original single save is imported once, never rewritten.
    LaunchedEffect(saveStore) {
        if (profileFlow.value != null) return@LaunchedEffect
        var loaded = saveStore?.let { store ->
            val bytes = try { store.readFile(ProfileFile, 512 * 1024) } catch (_: Exception) { null }
            PlayerProfileCodec.decode(bytes) ?: PlayerProfile().also {
                if (bytes != null) runCatching { store.writeFile("profile-unreadable.json", bytes) }
            }
        } ?: PlayerProfile()
        if (saveStore != null && !loaded.legacyImported) {
            val entry = islands.importLegacy(saveStore)
            loaded = (entry?.let(loaded::visit) ?: loaded).copy(legacyImported = true)
        }
        loaded.lastPlayed?.let { last -> if (state.route == ShellRoute.Home) state = state.selectIsland(last.key()) }
        profileFlow.value = loaded
        saveStore?.let { runCatching { it.writeFile(ProfileFile, PlayerProfileCodec.encode(loaded)) } }
    }
    LaunchedEffect(saveStore, profileFlow) {
        val store = saveStore ?: return@LaunchedEffect
        profileFlow.filterNotNull().distinctUntilChanged().drop(1).conflate().collect { latest ->
            try {
                store.writeFile(ProfileFile, PlayerProfileCodec.encode(latest))
            } catch (error: Exception) {
                println("BranchWorldProfile write failed: ${error::class.simpleName}")
            }
        }
    }
    val profileReady = profile != null
    LaunchedEffect(islands, islandKey, profileReady) { if (profileReady) islands.open(islandKey, saveStore) }

    suspend fun autosave() {
        val entry = saveStore?.let { islands.autosave(it) } ?: if (saveStore == null) islands.summary() else null
        entry?.let { visited -> updateProfile { it.visit(visited) } }
    }
    LaunchedEffect(session, isWorld) {
        val island = session ?: return@LaunchedEffect
        if (!isWorld) return@LaunchedEffect
        autosave()
        merge(
            island.clock.playback.map { it.playing }.distinctUntilChanged().filter { !it }.map { 0L },
            island.timelineView.map { it.branches.size * 1_000_003L + it.branchId }.distinctUntilChanged(),
            island.interventions.map { it.applied.hashCode().toLong() }.distinctUntilChanged(),
            island.world.poses.map { it.tick / AutosaveTicks }.distinctUntilChanged(),
        ).drop(1).debounce(900).collect { autosave() }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, session) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && session != null) operations.launch { autosave() }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(session, planting) {
        if (session == null || planting == null) {
            plantingNotice = null
            announcedPlanting = null
            return@LaunchedEffect
        }
        val event = "${state.terrain.worldgenVersion}/${state.seed}/${session.timelineView.value.branchId}/${planting.tick}/${planting.region}"
        if (announcedPlanting == event) return@LaunchedEffect
        announcedPlanting = event
        plantingNotice = planting.amount
        if (soundEnabled) playSound?.invoke(WorldSound.Grove)
        delay(5_000)
        plantingNotice = null
    }
    val shower = pose?.shaping?.shower
    val liveRain = interventions?.applied?.firstOrNull { it.kind == Intervention.Rain && it.amount > 0 }
    LaunchedEffect(session, liveRain) {
        if (soundEnabled && comparison == null && liveRain != null) playSound?.invoke(WorldSound.RainChime)
    }
    // A story or sandbox begun from the menus starts its clock once the island is ready.
    LaunchedEffect(session, autoStart, isWorld) {
        if (!autoStart || !isWorld || clock == null) return@LaunchedEffect
        autoStart = false
        if (clock.state.tick == 0L && !clock.playback.value.playing) {
            if (session.chapter == Chapter.FadingGrove && state.rules >= ShapingRulesVersion) clock.playTo(240) else clock.togglePlaying()
        }
    }
    val playbackState = if (playInUnfocusedWindow) Lifecycle.State.STARTED else Lifecycle.State.RESUMED
    if (setAmbience != null) {
        val lifecycleState by lifecycle.currentStateAsState()
        val sink by rememberUpdatedState(setAmbience)
        val mix = worldAmbience(soundEnabled, foreground && lifecycleState.isAtLeast(playbackState), pose?.climate,
            calledRain = shower != null)
        LaunchedEffect(mix) { sink(mix) }
        DisposableEffect(Unit) { onDispose { sink(WorldAmbience.Silent) } }
    }
    LaunchedEffect(clock, lifecycle, isWorld, foreground, playbackState) {
        if (clock != null && isWorld && foreground) {
            lifecycle.repeatOnLifecycle(playbackState) {
                clock.run(isActive = {
                    state.route == ShellRoute.World && state.island == islandKey &&
                        lifecycle.currentState.isAtLeast(playbackState)
                })
            }
        }
    }
    LaunchedEffect(session, pose?.branchId?.takeIf { comparison == null }, comparison != null) {
        focus = null
        if (comparison == null) comparisonDetailsOpen = false
    }
    val creatureFocus = focus as? WorldCamera.Focus.Creature
    if (creatureFocus != null && pose?.creatures?.none { it.id == creatureFocus.id } == true) {
        LaunchedEffect(creatureFocus) { focus = null }
    }
    val cameraRequest = WorldCamera(focus, reducedMotion, opening = !isWorld, focusRequest = focusRequest, targetSites = shapeSites)
    SideEffect { viewportCamera(cameraRequest) }

    fun enter(key: IslandSessionKey, start: Boolean) {
        showPanel(null)
        overlayName = null
        state = state.selectIsland(key).openWorld()
        autoStart = start
    }
    fun leaveWorld() {
        clock?.pause()
        operations.launch { autosave() }
        showPanel(null)
        overlayName = null
        state = state.back()
    }
    val goBack: () -> Unit = {
        when {
            closed != null -> closed = null
            overlay == Overlay.Photo || overlay == Overlay.Closing -> overlayName = null
            overlay == Overlay.Pause -> overlayName = null
            comparisonDetailsOpen -> comparisonDetailsOpen = false
            comparison != null -> session.closeComparison()
            panel != null -> showPanel(null)
            isWorld -> {
                clock?.pause()
                overlayName = Overlay.Pause.name
            }
            state.screen != HomeScreen.Title -> state = state.show(HomeScreen.Title)
        }
    }
    registerBackHandler(isWorld || panel != null || state.screen != HomeScreen.Title, goBack)

    val preferences = UiPreferences(settings.interfaceSounds && playSound != null, reducedMotion) { playSound?.invoke(it) }
    GameTheme(settings.textScale, preferences) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF4D8585)).onPreviewKeyEvent {
            if (it.key == Key.Escape && it.type == KeyEventType.KeyUp && (panel != null || isWorld || state.screen != HomeScreen.Title)) {
                goBack()
                true
            } else false
        }) {
            val wide = maxWidth >= 760.dp && maxHeight >= 420.dp
            val sideIntroduction = maxWidth >= 760.dp && maxWidth > maxHeight * 1.3f
            val tightDock = maxWidth < 460.dp
            val shortLandscape = !wide && maxWidth >= 640.dp && maxWidth > maxHeight * 1.45f
            val sidePanel = wide || shortLandscape
            val height = maxHeight
            val spacing = Spacing(compact = maxWidth < 600.dp)
            CompositionLocalProvider(LocalSpacing provides spacing) {
            if (viewport == null && session != null) {
                WorldViewport(Modifier.matchParentSize())
            } else {
                viewport?.invoke(Modifier.matchParentSize(), session?.world)
            }
            val title = Story.of(islandKey)?.let { stringResource(it.title) } ?: stringResource(islandTitle(islandKey.terrain))
            val failed = (load as? IslandLoad.Failed)?.key == islandKey
            val story = Story.of(session?.chapter)

            if (!isWorld) {
                val page = Modifier.align(if (sideIntroduction) Alignment.CenterStart else Alignment.BottomStart)
                    .safeDrawingPadding().padding(if (sideIntroduction) 24.dp else spacing.edge(16.dp))
                val current = profile ?: PlayerProfile()
                when (state.screen) {
                    HomeScreen.Title -> TitleMenu(current, profileReady && !failed, ::enter,
                        onScreen = { state = state.show(it) }, modifier = Modifier.matchParentSize().safeDrawingPadding())
                    HomeScreen.Settings -> Unit
                    HomeScreen.Stories -> StoriesScreen(current, onBegin = { enter(it.key, start = true) },
                        onBack = { state = state.show(HomeScreen.Title) }, modifier = page.widthIn(max = 740.dp))
                    HomeScreen.Atlas -> AtlasScreen(current, onOpen = { enter(it.key(), start = false) },
                        onBack = { state = state.show(HomeScreen.Title) }, modifier = page.widthIn(max = 600.dp))
                    HomeScreen.Sandbox -> SandboxScreen(onShape = { enter(IslandSessionKey.sandbox(it), start = true) },
                        onBack = { state = state.show(HomeScreen.Title) }, modifier = page.widthIn(max = 640.dp))
                }
                if (failed) {
                    Board(Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(spacing.edge(16.dp))) {
                        Text(stringResource(Res.string.island_generation_failed))
                        Block(stringResource(Res.string.retry_island), { operations.launch { islands.open(islandKey, saveStore) } })
                    }
                }
                if (state.screen == HomeScreen.Settings) {
                    Scrim(Modifier.matchParentSize())
                    SettingsSheet(settings, worldSounds = setAmbience != null || playSound != null,
                        onChange = { next -> updateProfile { it.copy(settings = next) } },
                        onClose = { state = state.show(HomeScreen.Title) },
                        modifier = Modifier.align(Alignment.Center).safeDrawingPadding().padding(spacing.edge(16.dp)).widthIn(max = 480.dp)
                            .heightIn(max = height - 40.dp))
                }
                if (viewportHud != null && session != null && state.screen == HomeScreen.Title) {
                    Box(Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(spacing.edge(10.dp))) { viewportHud() }
                }
            }

            if (isWorld && session != null && overlay == Overlay.Photo) {
                PhotoControls({ overlayName = null }, Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(spacing.edge(12.dp)))
            } else if (isWorld && session != null) {
                val state0 = session.clock.state
                val paint = chapterPaint(session.chapter, session.key.sandbox)
                val asking = pose?.let { state0.settlements?.village?.openPetition() }
                val compactTargeting = !wide && panel == WorldPanel.Grove && pose?.shaping != null
                if (!compactTargeting && comparison == null && overlay == null && closed == null) Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().safeDrawingPadding().padding(spacing.edge(12.dp)),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        Board(Modifier.widthIn(max = 330.dp), padding = 13.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                story?.let { Peg(it.numeral, paint = paint) }
                                Text(title, style = if (wide) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.weight(1f, fill = false).semantics { heading() })
                            }
                            val climate = pose?.climate
                            val watershed = climate?.watershed
                            val isHollowmere = session.chapter == Chapter.FadingGrove
                            val shaping = pose?.shaping
                            val village = shaping?.welfare
                            val concern = when {
                                session.chapter == Chapter.LastCrossing -> crossingConcern(state0) ?: Res.string.island_opening
                                isHollowmere && village != null && village.unmet > 0 -> Res.string.shape_shortage
                                isHollowmere && village?.firstRecovery != null && pose.tick - village.firstRecovery <= 90 -> Res.string.shape_recovered
                                shaping?.shower != null -> Res.string.shape_rain_arrived
                                isHollowmere && shaping?.warningAt != null && pose.tick - shaping.warningAt <= 60 -> Res.string.shape_warning
                                watershed?.season == Season.Wet && climate.weather == Weather.Rain -> Res.string.valley_storm_note
                                watershed != null && climate.weather == Weather.Rain -> Res.string.valley_refill_note
                                watershed != null && watershed.recharge < 52 -> Res.string.valley_low_note
                                watershed != null -> Res.string.valley_clear_note
                                climate?.weather == Weather.Rain -> Res.string.world_rain_note
                                climate?.weather == Weather.Dry && isHollowmere -> Res.string.grove_dry_note
                                climate?.weather == Weather.Dry -> Res.string.world_dry_note
                                else -> Res.string.island_opening
                            }
                            val note = if (wide && asking != null) stringResource(Res.string.petition_open, settlementName(asking.siteId, session.chapter))
                                else stringResource(concern)
                            Text(note, style = MaterialTheme.typography.bodySmall, color = Wood.WalnutSoft)
                            if (panel != WorldPanel.Grove) {
                                plantingNotice?.let { amount ->
                                    Text(stringResource(Res.string.grove_applied, amount),
                                        Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            pose?.let { Peg(dayLabel(it.tick, it.climate?.dayPhase)) }
                            val route = session.world.herdRoute
                            if (session.chapter == Chapter.LastCrossing && route.isNotEmpty()) {
                                Block(stringResource(Res.string.crossing_show_way), {
                                    requestFocus(WorldCamera.Focus.EchoSite(route, false))
                                }, size = BlockSize.Small, icon = GameIcons.Eye, paint = Wood.BeechLight)
                            }
                            if (story != null && session.canCloseChapter && timelineView?.seeking == false) {
                                Block(stringResource(Res.string.close_chapter), {
                                    clock?.pause()
                                    overlayName = Overlay.Closing.name
                                }, icon = GameIcons.Seal, paint = paint, size = BlockSize.Small)
                            }
                        }
                    }
                    if (viewportHud != null && (!sidePanel || panel == null)) {
                        Box(Modifier.widthIn(max = if (wide) 265.dp else 124.dp)) { viewportHud() }
                    }
                }
                if (comparison == null && panel == null && overlay == null && closed == null) {
                    val guide = profile?.let { guideFor(it, session, timelineView, pose?.tick) }
                    if (shortLandscape) {
                        val side = Modifier.align(Alignment.CenterEnd).safeDrawingPadding().padding(spacing.edge(10.dp))
                            .widthIn(max = (maxWidth * 0.42f).coerceAtMost(332.dp)).heightIn(max = height - 105.dp)
                        if (asking != null) PetitionCard(session, side, compact = true)
                        else guide?.let { (id, text) -> KeeperGuide(text, { updateProfile { it.see(id) } }, side) }
                    }
                    Column(Modifier.align(Alignment.BottomStart).safeDrawingPadding().padding(spacing.edge(10.dp)),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!shortLandscape && asking == null) guide?.let { (id, text) -> KeeperGuide(text, { updateProfile { it.see(id) } }) }
                        if (!shortLandscape) PetitionCard(session, compact = !wide,
                            modifier = Modifier.heightIn(max = height * if (wide) 0.52f else 0.40f))
                        Board(padding = if (tightDock) 4.dp else 6.dp, depth = 4.dp) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                val playback by session.clock.playback.collectAsStateWithLifecycle()
                                val playing = playback.playing
                                val compact = !wide
                                @Composable fun action(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String,
                                                       enabled: Boolean = true, onClick: () -> Unit) {
                                    if (compact) PegButton(icon, label, onClick, enabled)
                                    else Block(label, onClick, icon = icon, enabled = enabled, size = BlockSize.Small, paint = Wood.BeechLight)
                                }
                                action(if (playing) GameIcons.Pause else GameIcons.Play,
                                    stringResource(if (playing) Res.string.pause_world else Res.string.play_world),
                                    enabled = timelineView?.seeking != true, onClick = session.clock::togglePlaying)
                                Block(stringResource(if (session.key.rules >= ShapingRulesVersion) Res.string.shape_world else Res.string.restore_grove), {
                                    if (pose?.shaping != null) session.clock.pause()
                                    showPanel(WorldPanel.Grove)
                                }, icon = if (compact) null else GameIcons.Leaf, paint = paint, size = if (compact) BlockSize.Compact else BlockSize.Small)
                                if (session.key.rules >= VillageRulesVersion) {
                                    action(GameIcons.Home, settlementName(1, session.chapter)) {
                                        showPanel(WorldPanel.Village)
                                        val home = pose?.settlements?.firstOrNull()?.village
                                        if (home != null) requestFocus(WorldCamera.Focus.Village(home.structures.map { it.ground } + home.center))
                                    }
                                }
                                action(GameIcons.Eye, stringResource(Res.string.inspect_story)) { showPanel(WorldPanel.Inspect) }
                                action(GameIcons.Branches, stringResource(Res.string.open_timeline)) { showPanel(WorldPanel.Timeline) }
                                action(GameIcons.Menu, stringResource(Res.string.world_menu)) {
                                    clock?.pause()
                                    overlayName = Overlay.Pause.name
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
                                .widthIn(max = if (sidePanel) (maxWidth * 0.54f).coerceAtMost(480.dp) else 340.dp)) {
                            requestFocus(WorldCamera.Focus.EchoSite(it, sidePanel))
                        }
                    }
                }
            }
            if (isWorld && session == null) {
                Board(Modifier.align(Alignment.Center).safeDrawingPadding().padding(spacing.edge(16.dp))) {
                    Text(stringResource(if (failed) Res.string.island_generation_failed else Res.string.island_generating))
                    if (failed) TextButton(onClick = { operations.launch { islands.open(islandKey, saveStore) } }) {
                        Text(stringResource(Res.string.retry_island), color = Wood.Woad)
                    }
                }
            }

            val activePanel = if (comparison != null) WorldPanel.Timeline else panel
            if (isWorld && activePanel != null && overlay == null && closed == null) {
                val panelSize = if (sidePanel) {
                    Modifier.widthIn(max = if (shortLandscape) 320.dp else 390.dp).fillMaxWidth().heightIn(max = height - 32.dp)
                } else Modifier.fillMaxWidth().heightIn(max = height *
                    if (comparison != null && !comparisonDetailsOpen && !diagnostics) 0.40f else 0.65f)
                val placement = Modifier.align(if (sidePanel) Alignment.CenterEnd else Alignment.BottomCenter)
                    .safeDrawingPadding().padding(spacing.edge(10.dp)).then(panelSize)
                if (activePanel == WorldPanel.Settings) {
                    SettingsSheet(settings, worldSounds = setAmbience != null || playSound != null,
                        onChange = { next -> updateProfile { it.copy(settings = next) } }, onClose = { showPanel(null) }, modifier = placement)
                } else {
                    val content: @Composable () -> Unit = {
                        ScrollColumn(Modifier.fillMaxWidth(), spacing = 10.dp) {
                            PanelContent(activePanel, session, comparison, state, wide, comparisonDetailsOpen,
                                onDetails = { comparisonDetailsOpen = it }, requestFocus = requestFocus,
                                onShapeSites = { shapeSites = it }, closePanel = { showPanel(null) },
                                onForked = { if (soundEnabled) playSound?.invoke(WorldSound.Fork) },
                                toggleDiagnostics = {
                                    updateProfile { it.copy(settings = it.settings.copy(tickTools = !it.settings.tickTools)) }
                                },
                                diagnostics = diagnostics,
                                bookmarks = profile?.bookmarks.orEmpty().filter { it.slot == islandKey.slot },
                                onVisit = { mark -> operations.launch { session?.visit(mark.branchId, mark.tick) } },
                                onForget = { mark -> updateProfile { it.unmark(mark) } })
                        }
                    }
                    if (comparison != null) {
                        Board(placement) { content() }
                    } else {
                        BoardSheet(stringResource(activePanel.title(session?.chapter, (session?.key?.rules ?: 0) >= ShapingRulesVersion)),
                            placement, onClose = { showPanel(null) }) { content() }
                    }
                }
            }

            if (isWorld && session != null && overlay == Overlay.Pause) {
                Scrim(Modifier.matchParentSize())
                val tick = pose?.tick ?: session.clock.state.tick
                val view = timelineView
                val marked = view != null && profile?.bookmarks?.any {
                    it.slot == islandKey.slot && it.branchId == view.branchId && it.tick == tick
                } == true
                PauseMenu(
                    paint = chapterPaint(session.chapter, session.key.sandbox),
                    saved = when {
                        saveStore == null -> stringResource(Res.string.autosave_off)
                        autosavedAt != null -> stringResource(Res.string.autosave_done, dayOf(checkNotNull(autosavedAt)))
                        else -> stringResource(Res.string.autosave_waiting)
                    },
                    marked = marked,
                    diagnostics = settings.tickTools,
                    onResume = { overlayName = null },
                    onTimeline = { overlayName = null; showPanel(WorldPanel.Timeline) },
                    onMark = {
                        if (view != null) updateProfile { it.mark(Bookmark(islandKey.slot, view.branchId, tick, view.active.name)) }
                    },
                    onPhoto = { overlayName = Overlay.Photo.name; showPanel(null) },
                    onSettings = { overlayName = null; showPanel(WorldPanel.Settings) },
                    onAtlas = { leaveWorld(); state = state.show(HomeScreen.Atlas) },
                    onTitle = { leaveWorld() },
                    onDiagnostics = { overlayName = null; showPanel(WorldPanel.Diagnostics) },
                    modifier = Modifier.align(Alignment.Center).safeDrawingPadding().padding(spacing.edge(16.dp)).heightIn(max = height - 32.dp),
                )
                if (storage != null && storage != SaveStatus.Busy) {
                    Board(Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(spacing.edge(12.dp)), padding = 8.dp) {
                        SaveMessage(storage, diagnostics)
                    }
                }
            }

            if (isWorld && session != null && story != null && overlay == Overlay.Closing) {
                Scrim(Modifier.matchParentSize())
                val outcomes = remember(session, timelineView) { session.outcomes() }
                ChapterClosing(story, outcomes, timelineView?.branchId ?: 1, onChoose = { outcome ->
                    updateProfile { it.record(outcome.record(story, islandKey.slot)) }
                    operations.launch { autosave() }
                    overlayName = null
                    closed = story to outcome.branch.name
                }, onCancel = { overlayName = null },
                    modifier = Modifier.align(Alignment.Center).safeDrawingPadding().padding(spacing.edge(16.dp)).widthIn(max = 560.dp)
                        .heightIn(max = height - 32.dp))
            }
            closed?.let { (story, name) ->
                Scrim(Modifier.matchParentSize())
                ChapterClosed(story, name, onNext = story.next?.let { next -> {
                    closed = null
                    leaveWorld()
                    enter(next.key, start = true)
                } }, onStay = { closed = null },
                    modifier = Modifier.align(Alignment.Center).safeDrawingPadding().padding(spacing.edge(16.dp)))
            }
            }
        }
    }
}

private const val AutosaveTicks = 600L

private fun ChapterOutcome.record(story: Story, slot: String) = ChapterRecord(
    story.chapter.code, slot, branch.id, branch.name, tick, people, departed.values.sum(), deer, homes,
)

/** The one first-time explanation the current moment calls for, if the player has not seen it yet. */
private fun guideFor(profile: PlayerProfile, island: IslandLoad.Ready, view: TimelineView?, tick: Long?): Pair<String, StringResource>? {
    val state = island.clock.state
    val warning = state.shaping?.warningAt
    val now = tick ?: state.tick
    val candidates = buildList {
        add("look" to Res.string.guide_look)
        if (warning != null && now - warning in 0..240) add("warning" to Res.string.guide_warning)
        if (island.openPetition() != null) add("petition" to Res.string.guide_petition)
        if (island.canCloseChapter && island.chapter?.let { profile.closed(it.code) } == false) add("close" to Res.string.guide_close)
        val active = view?.branches?.firstOrNull { it.id == view.branchId }
        if (warning != null && view?.branches?.size == 1 && (active?.interventionCount ?: 0) > 0 && now > warning + 300) {
            add("rewind" to Res.string.guide_rewind)
        }
    }
    return candidates.firstOrNull { !profile.seen(it.first) }
}

@Composable
private fun PanelContent(
    panel: WorldPanel,
    session: IslandLoad.Ready?,
    comparison: ComparisonView?,
    state: ShellState,
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
            TimelineControls(island, diagnostics, onForked)
            if (bookmarks.isNotEmpty()) MarkedMoments(bookmarks, island, onVisit, onForget)
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
            Text(stringResource(Res.string.diagnostics_seed, state.seed.toString()))
            island.clock.state.creatures.firstOrNull()?.let { lead ->
                Text(stringResource(Res.string.animal_needs, "${lead.food / 10}%", "${lead.water / 10}%", "${lead.energy / 10}%"))
            }
        }
        WorldPanel.Settings -> Unit
    }
}

@Composable
private fun MarkedMoments(bookmarks: List<Bookmark>, island: IslandLoad.Ready, onVisit: (Bookmark) -> Unit, onForget: (Bookmark) -> Unit) {
    val view by island.timelineView.collectAsStateWithLifecycle()
    Text(stringResource(Res.string.timeline_marks), style = MaterialTheme.typography.titleMedium)
    for (mark in bookmarks.sortedWith(compareBy<Bookmark> { it.branchId }.thenBy { it.tick })) {
        val branch = view.branches.firstOrNull { it.id == mark.branchId } ?: continue
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(branch.name, style = MaterialTheme.typography.titleSmall)
                Text(dayLabel(mark.tick, null), style = MaterialTheme.typography.bodySmall, color = Wood.WalnutSoft)
            }
            Block(stringResource(Res.string.timeline_mark_go), { onVisit(mark) }, size = BlockSize.Small, enabled = !view.seeking)
            PegButton(GameIcons.Close, stringResource(Res.string.timeline_mark_forget), { onForget(mark) })
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

private fun WorldPanel.title(chapter: Chapter?, shaping: Boolean): StringResource = when (this) {
    WorldPanel.Inspect -> Res.string.inspect_story
    WorldPanel.Grove -> if (shaping) Res.string.shape_world else Res.string.restore_grove
    WorldPanel.Village -> Res.string.village_panel
    WorldPanel.Timeline -> Res.string.open_timeline
    WorldPanel.Diagnostics -> Res.string.diagnostics_title
    WorldPanel.Settings -> Res.string.settings_title
}
