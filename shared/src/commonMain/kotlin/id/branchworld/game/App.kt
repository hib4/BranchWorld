package id.branchworld.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import branchworld.shared.generated.resources.shape_world
import branchworld.shared.generated.resources.shape_tick_tools
import branchworld.shared.generated.resources.shape_warning
import branchworld.shared.generated.resources.shape_shortage
import branchworld.shared.generated.resources.shape_recovered
import branchworld.shared.generated.resources.shape_rain_arrived
import branchworld.shared.generated.resources.Res
import branchworld.shared.generated.resources.back_to_home
import branchworld.shared.generated.resources.begin_story
import branchworld.shared.generated.resources.branchworld_title
import branchworld.shared.generated.resources.close_panel
import branchworld.shared.generated.resources.continue_story
import branchworld.shared.generated.resources.hide_comparison_details
import branchworld.shared.generated.resources.show_comparison_details
import branchworld.shared.generated.resources.diagnostics_platform
import branchworld.shared.generated.resources.diagnostics_seed
import branchworld.shared.generated.resources.diagnostics_title
import branchworld.shared.generated.resources.grove_applied
import branchworld.shared.generated.resources.grove_dry_note
import branchworld.shared.generated.resources.inspect_story
import branchworld.shared.generated.resources.island_generating
import branchworld.shared.generated.resources.island_generation_failed
import branchworld.shared.generated.resources.island_opening
import branchworld.shared.generated.resources.open_timeline
import branchworld.shared.generated.resources.other_islands
import branchworld.shared.generated.resources.pause_world
import branchworld.shared.generated.resources.play_world
import branchworld.shared.generated.resources.reduced_camera_motion
import branchworld.shared.generated.resources.restore_grove
import branchworld.shared.generated.resources.retry_island
import branchworld.shared.generated.resources.save_world
import branchworld.shared.generated.resources.show_diagnostics
import branchworld.shared.generated.resources.resume_island
import branchworld.shared.generated.resources.animal_needs
import branchworld.shared.generated.resources.world_dry_note
import branchworld.shared.generated.resources.world_journal
import branchworld.shared.generated.resources.world_menu
import branchworld.shared.generated.resources.world_rain_note
import branchworld.shared.generated.resources.world_sounds
import branchworld.shared.generated.resources.explore_island
import branchworld.shared.generated.resources.valley_storm_note
import branchworld.shared.generated.resources.valley_refill_note
import branchworld.shared.generated.resources.valley_low_note
import branchworld.shared.generated.resources.valley_clear_note
import id.branchworld.game.ecology.Intervention
import id.branchworld.game.ecology.ShapingRulesVersion
import id.branchworld.game.simulation.GroundPoint
import id.branchworld.game.ecology.Weather
import id.branchworld.game.ecology.Season
import id.branchworld.game.save.WorldSaveStore
import id.branchworld.game.simulation.WorldCamera
import id.branchworld.game.simulation.WorldRenderSource
import id.branchworld.game.terrain.WatershedWorldgenVersion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private class WorldSession : ViewModel() {
    val islands = IslandSession()
}

internal val NightGreen = Color(0xFF102D28)
internal val Parchment = Color(0xFFF8F0DC)
private val BranchWorldColors = lightColorScheme(
    primary = Color(0xFF285648), onPrimary = Color.White,
    secondaryContainer = Color(0xFFDCE7CF), onSecondaryContainer = NightGreen,
    background = Parchment, onBackground = NightGreen,
    surface = Parchment, onSurface = NightGreen,
    surfaceContainerHigh = Color(0xFFEFE6CF), onSurfaceVariant = Color(0xFF40534C),
    outline = Color(0xFF66766A),
)
private val StoryTypography = Typography().let {
    it.copy(
        displayLarge = it.displayLarge.copy(fontFamily = FontFamily.Serif, letterSpacing = (-0.03).em),
        displaySmall = it.displaySmall.copy(fontFamily = FontFamily.Serif, letterSpacing = (-0.02).em),
        headlineSmall = it.headlineSmall.copy(fontFamily = FontFamily.Serif),
    )
}
private enum class WorldPanel { Menu, Inspect, Grove, Timeline, Save, Diagnostics, Islands }

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
    var soundEnabled by rememberSaveable { mutableStateOf(true) }
    var reducedMotion by rememberSaveable { mutableStateOf(false) }
    var panel by rememberSaveable { mutableStateOf<WorldPanel?>(null) }
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
    val operations = retained?.viewModelScope ?: fallbackScope
    val storage by islands.saveStatus.collectAsStateWithLifecycle()
    val load by islands.state.collectAsStateWithLifecycle()
    val seed = state.seed
    val terrain = state.terrain
    val islandKey = state.island
    var comparisonDetailsOpen by rememberSaveable(terrain) { mutableStateOf(false) }
    val session = (load as? IslandLoad.Ready)?.takeIf { it.key == islandKey }
    val comparison = session?.comparison?.collectAsStateWithLifecycle()?.value
    val pose = session?.world?.poses?.collectAsStateWithLifecycle()?.value
    val clock = session?.clock
    val interventions = session?.interventions?.collectAsStateWithLifecycle()?.value
    val planting = interventions?.applied?.firstOrNull { it.kind == Intervention.Reforest && it.amount > 0 }
    var plantingNotice by remember(session) { mutableStateOf<Int?>(null) }
    var announcedPlanting by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(session, planting) {
        if (session == null || planting == null) {
            plantingNotice = null
            announcedPlanting = null
            return@LaunchedEffect
        }
        val event = "${terrain.worldgenVersion}/$seed/${session.timelineView.value.branchId}/${planting.tick}/${planting.region}"
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
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(islands, islandKey) { islands.open(islandKey) }
    LaunchedEffect(storage) {
        val restored = storage as? SaveStatus.Restored ?: return@LaunchedEffect
        state = state.copy(terrain = restored.key.terrain, rules = restored.key.rules).openWorld()
        panel = null
        focus = null
        islands.acknowledgeRestore(restored.tick)
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

    val showPanel: (WorldPanel?) -> Unit = { destination ->
        if (destination != panel || destination == null) {
            val keepFollowing = panel == WorldPanel.Inspect && destination == null && focus is WorldCamera.Focus.Creature
            if (!keepFollowing) focus = null
            shapeSites = emptyList()
            if (panel == WorldPanel.Save) islands.dismissSaveStatus()
            panel = destination
        }
    }
    val goBack: () -> Unit = {
        when {
            comparisonDetailsOpen -> comparisonDetailsOpen = false
            comparison != null -> session.closeComparison()
            panel != null -> showPanel(null)
            isWorld -> {
                clock?.pause()
                state = state.back()
            }
        }
    }
    registerBackHandler(isWorld || panel != null, goBack)

    MaterialTheme(colorScheme = BranchWorldColors, typography = StoryTypography) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF4D8585)).onPreviewKeyEvent {
            if (it.key == Key.Escape && it.type == KeyEventType.KeyUp && (panel != null || isWorld)) {
                goBack()
                true
            } else false
        }) {
            val wide = maxWidth >= 760.dp && maxHeight >= 420.dp
            val sideIntroduction = maxWidth >= 760.dp && maxWidth > maxHeight * 1.3f
            val shortLandscape = !wide && maxWidth >= 640.dp && maxWidth > maxHeight * 1.45f
            val sidePanel = wide || shortLandscape
            val short = maxHeight < 540.dp
            val height = maxHeight
            if (viewport == null && session != null) {
                WorldViewport(Modifier.matchParentSize())
            } else {
                viewport?.invoke(Modifier.matchParentSize(), session?.world)
            }
            val chapter = stringResource(islandTitle(terrain))
            val failed = (load as? IslandLoad.Failed)?.key == islandKey
            if (!isWorld) {
                val veil = listOf(NightGreen.copy(alpha = 0.94f), NightGreen.copy(alpha = 0.52f), Color.Transparent)
                Box(Modifier.matchParentSize().background(if (sideIntroduction) {
                    if (terrain.worldgenVersion == WatershedWorldgenVersion) {
                        Brush.horizontalGradient(0f to veil[0], 0.31f to NightGreen.copy(alpha = 0.82f),
                            0.51f to NightGreen.copy(alpha = 0.71f), 0.67f to veil[2])
                    } else Brush.horizontalGradient(0f to veil[0], 0.26f to veil[1], 0.59f to veil[2])
                } else {
                    Brush.verticalGradient(0.51f to veil[2], 0.74f to veil[1], 1f to veil[0])
                }))
                Column(
                    Modifier.align(if (sideIntroduction) Alignment.CenterStart else Alignment.BottomStart)
                        .safeContentPadding().widthIn(max = if (sideIntroduction) 380.dp else 460.dp)
                        .heightIn(max = if (sideIntroduction || short) height else height * 0.65f)
                        .verticalScroll(rememberScrollState()).padding(if (sideIntroduction) 32.dp else 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(stringResource(Res.string.branchworld_title), color = Color(0xFFFFD89C), style = MaterialTheme.typography.titleMedium)
                    Text(chapter, Modifier.semantics { heading() }, color = Parchment,
                        style = if (sideIntroduction && !short) MaterialTheme.typography.displayLarge.copy(lineHeight = 52.sp)
                        else MaterialTheme.typography.displaySmall)
                    Text(stringResource(islandDescription(terrain)),
                        color = Parchment, style = MaterialTheme.typography.bodyLarge)
                    if (session == null && !failed && storage != SaveStatus.Busy) {
                        Text(stringResource(Res.string.island_generating), color = Parchment)
                    }
                    if (failed) {
                        Text(stringResource(Res.string.island_generation_failed), color = Parchment)
                        Button(onClick = { operations.launch { islands.open(islandKey) } }) { Text(stringResource(Res.string.retry_island)) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = session != null && storage != SaveStatus.Busy,
                            onClick = {
                                islands.dismissSaveStatus()
                                state = state.openWorld()
                                showPanel(null)
                                if (clock != null && clock.state.tick == 0L && !clock.playback.value.playing) {
                                    if (state.rules == ShapingRulesVersion && state.seed == IslandSeeds.first()) clock.playTo(240)
                                    else clock.togglePlaying()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD89C), contentColor = NightGreen),
                        ) {
                            Text(stringResource(when {
                                session?.clock?.state?.tick?.let { it > 0 } == true -> Res.string.resume_island
                                seed == IslandSeeds.first() -> Res.string.begin_story
                                else -> Res.string.explore_island
                            }))
                        }
                        if (saveStore != null) {
                            TextButton(
                                onClick = { operations.launch { islands.restore(saveStore) } },
                                enabled = (session != null || failed) && storage != SaveStatus.Busy,
                                colors = ButtonDefaults.textButtonColors(contentColor = Parchment),
                            ) { Text(stringResource(Res.string.continue_story)) }
                        }
                    }
                    if (storage != null) CompositionLocalProvider(LocalContentColor provides Parchment) { SaveMessage(storage, state.diagnosticsVisible || state.rules != ShapingRulesVersion) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { showPanel(WorldPanel.Islands) }, enabled = storage != SaveStatus.Busy,
                            colors = ButtonDefaults.textButtonColors(contentColor = Parchment),
                        ) { Text(stringResource(Res.string.other_islands)) }
                        TextButton(
                            onClick = { showPanel(WorldPanel.Menu) },
                            colors = ButtonDefaults.textButtonColors(contentColor = Parchment),
                        ) { Text(stringResource(Res.string.world_menu)) }
                    }
                }
            }
            if (isWorld && session != null) {
                val compactTargeting = !wide && panel == WorldPanel.Grove && pose?.shaping != null
                if (!compactTargeting && comparison == null) Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().safeContentPadding().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        Column(Modifier.widthIn(max = 310.dp)) {
                            Readout {
                                Text(chapter, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                                val climate = pose?.climate
                                val watershed = climate?.watershed
                                val isHollowmere = seed == IslandSeeds.first()
                                val shaping = pose?.shaping
                                val village = shaping?.welfare
                                val concern = when {
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
                                Text(stringResource(concern), style = MaterialTheme.typography.bodySmall)
                                if (panel != WorldPanel.Grove) {
                                    plantingNotice?.let { amount ->
                                        Text(stringResource(Res.string.grove_applied, amount),
                                            Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                    if (viewportHud != null && (!sidePanel || panel == null)) {
                        Box(Modifier.widthIn(max = if (wide) 265.dp else 124.dp)) { viewportHud() }
                    }
                }
                if (comparison == null && panel == null) {
                    Surface(Modifier.align(Alignment.BottomStart).safeContentPadding().padding(10.dp),
                        color = NightGreen.copy(alpha = 0.96f), contentColor = Parchment, shape = MaterialTheme.shapes.large) {
                        FlowRow(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            StoryActions(session, {
                                if (pose?.shaping != null) session.clock.pause()
                                showPanel(WorldPanel.Grove)
                            }, { showPanel(WorldPanel.Inspect) },
                                { showPanel(WorldPanel.Timeline) }, { showPanel(WorldPanel.Menu) })
                        }
                    }
                }
                if (comparison != null) {
                    ComparisonOverlay(comparison, Modifier.align(Alignment.Center).padding(16.dp),
                        diagnostics = state.diagnosticsVisible)
                    if (!state.diagnosticsVisible && !comparisonDetailsOpen) {
                        LivingEchoPlaces(session, comparison,
                            Modifier.align(Alignment.TopStart).safeContentPadding().padding(12.dp)
                                .widthIn(max = if (sidePanel) (maxWidth * 0.54f).coerceAtMost(480.dp) else 340.dp)) {
                            requestFocus(WorldCamera.Focus.EchoSite(it, sidePanel))
                        }
                    }
                }
            }
            if (!isWorld && viewportHud != null && session != null) {
                Box(Modifier.align(Alignment.TopEnd).safeContentPadding().padding(10.dp)) { viewportHud() }
            }
            if (isWorld && session == null) {
                Box(Modifier.align(Alignment.Center).padding(16.dp)) {
                    Readout {
                        Text(stringResource(if (failed) Res.string.island_generation_failed else Res.string.island_generating))
                        if (failed) TextButton(onClick = { operations.launch { islands.open(islandKey) } }) { Text(stringResource(Res.string.retry_island)) }
                    }
                }
            }

            val activePanel = if (comparison != null) WorldPanel.Timeline else panel
            if (activePanel != null) {
                val panelSize = if (sidePanel) {
                    Modifier.widthIn(max = if (shortLandscape) 310.dp else 370.dp).fillMaxWidth().heightIn(max = height - 32.dp)
                } else Modifier.fillMaxWidth().heightIn(max = height *
                    if (comparison != null && !comparisonDetailsOpen && !state.diagnosticsVisible) 0.40f else 0.65f)
                Surface(Modifier.align(if (sidePanel) Alignment.CenterEnd else Alignment.BottomCenter)
                    .safeContentPadding().padding(10.dp).then(panelSize),
                    shape = MaterialTheme.shapes.large, shadowElevation = 8.dp) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (comparison == null) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(activePanel.title(session?.key?.rules == ShapingRulesVersion)), Modifier.weight(1f).semantics { heading() },
                                    style = MaterialTheme.typography.headlineSmall)
                                TextButton(onClick = { showPanel(null) }) { Text(stringResource(Res.string.close_panel)) }
                            }
                        }
                        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            when (activePanel) {
                                WorldPanel.Menu -> {
                                    if (isWorld) {
                                        PanelButton(Res.string.save_world, saveStore != null) { showPanel(WorldPanel.Save) }
                                        PanelButton(Res.string.back_to_home) {
                                            clock?.pause()
                                            state = state.back()
                                            showPanel(null)
                                        }
                                        PanelButton(Res.string.show_diagnostics) { showPanel(WorldPanel.Diagnostics) }
                                    }
                                    if (playSound != null || setAmbience != null) {
                                        ToggleSetting(Res.string.world_sounds, soundEnabled) { soundEnabled = it }
                                    }
                                    ToggleSetting(Res.string.reduced_camera_motion, reducedMotion) { reducedMotion = it }
                                }
                                WorldPanel.Islands -> {
                                    IslandPicker(islandKey, enabled = storage != SaveStatus.Busy) { candidate ->
                                        state = state.selectIsland(candidate)
                                        showPanel(null)
                                    }
                                }
                                WorldPanel.Inspect -> session?.let {
                                    WorldInspector(
                                        it, compact = !wide,
                                        onCreatureFocus = { id -> requestFocus(WorldCamera.Focus.Creature(id)) },
                                        onLandmarkFocus = { kind -> requestFocus(WorldCamera.Focus.Landmark(kind)) },
                                        onWildlifeLocation = { ground -> requestFocus(WorldCamera.Focus.WildlifeLocation(ground)) },
                                        playback = { PlaybackButton(it) },
                                    )
                                }
                                WorldPanel.Grove -> session?.let {
                                    if (it.key.rules == ShapingRulesVersion) {
                                        ShapeControls(it, onTarget = { region, rain, sites ->
                                            shapeSites = sites
                                            requestFocus(WorldCamera.Focus.Region(region, rain))
                                        }, onObserve = { showPanel(null) })
                                    } else {
                                        GroveControls(it, onRegionFocus = { region -> requestFocus(WorldCamera.Focus.Region(region)) })
                                        PlaybackButton(it)
                                    }
                                }
                                WorldPanel.Timeline -> if (session != null) {
                                    if (comparison == null) TimelineControls(session, state.diagnosticsVisible) { if (soundEnabled) playSound?.invoke(WorldSound.Fork) }
                                    else {
                                        val timeline by session.timelineView.collectAsStateWithLifecycle()
                                        if (state.diagnosticsVisible) {
                                            val showDetails = wide || comparisonDetailsOpen
                                            ComparisonHeader(session, comparison, timeline.branches, showBranchPickers = showDetails, diagnostics = true)
                                            if (!wide) {
                                                TextButton(onClick = { comparisonDetailsOpen = !comparisonDetailsOpen }) {
                                                    val label = if (comparisonDetailsOpen) Res.string.hide_comparison_details
                                                    else Res.string.show_comparison_details
                                                    Text(stringResource(label))
                                                }
                                            }
                                            if (showDetails) ComparisonDetails(session, comparison, diagnostics = true)
                                        } else {
                                            LivingEchoDock(session, comparison, timeline, comparisonDetailsOpen,
                                                onDetails = { comparisonDetailsOpen = it })
                                        }
                                    }
                                }
                                WorldPanel.Save -> saveStore?.let { store ->
                                    SaveControls(storage, showTicks = state.diagnosticsVisible || state.rules != ShapingRulesVersion, onSave = { replace ->
                                        operations.launch {
                                            if (islands.save(store, replace) && soundEnabled) playSound?.invoke(WorldSound.Save)
                                        }
                                    }, onDismiss = islands::dismissSaveStatus)
                                }
                                WorldPanel.Diagnostics -> if (session != null) {
                                    val timeline by session.timelineView.collectAsStateWithLifecycle()
                                    ToggleSetting(Res.string.shape_tick_tools, state.diagnosticsVisible) { state = state.toggleDiagnostics() }
                                    WorldControls(session.clock, playbackEnabled = !timeline.seeking, editsEnabled = timeline.editsAllowed)
                                    Text(stringResource(Res.string.diagnostics_platform, remember { getPlatform().name }))
                                    Text(stringResource(Res.string.diagnostics_seed, seed.toString()))
                                    pose?.let {
                                        session.clock.state.creatures.firstOrNull()?.let { lead ->
                                            Text(stringResource(Res.string.animal_needs, "${lead.food / 10}%", "${lead.water / 10}%", "${lead.energy / 10}%"))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Readout(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = NightGreen.copy(alpha = 0.96f), contentColor = Parchment, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(horizontal = 15.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp), content = content)
    }
}

@Composable
private fun StoryActions(session: IslandLoad.Ready, grove: () -> Unit, inspect: () -> Unit, timeline: () -> Unit, menu: () -> Unit) {
    val colors = ButtonDefaults.textButtonColors(contentColor = Parchment)
    PlaybackButton(session, colors)
    Button(onClick = grove, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD89C), contentColor = NightGreen)) {
        Text(stringResource(if (session.key.rules == ShapingRulesVersion) Res.string.shape_world else Res.string.restore_grove))
    }
    TextButton(onClick = inspect, colors = colors) { Text(stringResource(Res.string.inspect_story)) }
    TextButton(onClick = timeline, colors = colors) { Text(stringResource(Res.string.open_timeline)) }
    TextButton(onClick = menu, colors = colors) { Text(stringResource(Res.string.world_menu)) }
}

@Composable
private fun PlaybackButton(session: IslandLoad.Ready, colors: ButtonColors = ButtonDefaults.textButtonColors()) {
    val playback by session.clock.playback.collectAsStateWithLifecycle()
    val navigation by session.timelineView.collectAsStateWithLifecycle()
    TextButton(onClick = session.clock::togglePlaying, enabled = !navigation.seeking, colors = colors) {
        Text(stringResource(if (playback.playing) Res.string.pause_world else Res.string.play_world))
    }
}

@Composable
private fun PanelButton(label: org.jetbrains.compose.resources.StringResource, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled) { Text(stringResource(label)) }
}

@Composable
private fun ToggleSetting(label: org.jetbrains.compose.resources.StringResource, enabled: Boolean, update: (Boolean) -> Unit) {
    FilterChip(selected = enabled, onClick = { update(!enabled) }, label = { Text(stringResource(label)) })
}

private fun WorldPanel.title(shaping: Boolean) = when (this) {
    WorldPanel.Menu -> Res.string.world_menu
    WorldPanel.Inspect -> Res.string.inspect_story
    WorldPanel.Grove -> if (shaping) Res.string.shape_world else Res.string.restore_grove
    WorldPanel.Timeline -> Res.string.open_timeline
    WorldPanel.Save -> Res.string.world_journal
    WorldPanel.Diagnostics -> Res.string.diagnostics_title
    WorldPanel.Islands -> Res.string.other_islands
}
