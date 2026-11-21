package id.branchworld.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import branchworld.shared.generated.resources.Res
import branchworld.shared.generated.resources.close_inspector
import branchworld.shared.generated.resources.close_timeline
import branchworld.shared.generated.resources.inspect_world
import branchworld.shared.generated.resources.open_timeline
import branchworld.shared.generated.resources.branch_guide
import branchworld.shared.generated.resources.branch_guide_play
import branchworld.shared.generated.resources.branch_guide_fork
import branchworld.shared.generated.resources.branch_guide_compare
import branchworld.shared.generated.resources.retry_island
import branchworld.shared.generated.resources.world_sounds
import branchworld.shared.generated.resources.back_to_home
import branchworld.shared.generated.resources.branchworld_tagline
import branchworld.shared.generated.resources.branchworld_title
import branchworld.shared.generated.resources.diagnostics_lifecycle
import branchworld.shared.generated.resources.diagnostics_platform
import branchworld.shared.generated.resources.diagnostics_position
import branchworld.shared.generated.resources.diagnostics_seed
import branchworld.shared.generated.resources.diagnostics_title
import branchworld.shared.generated.resources.hide_diagnostics
import branchworld.shared.generated.resources.open_world
import branchworld.shared.generated.resources.island_seed
import branchworld.shared.generated.resources.island_generating
import branchworld.shared.generated.resources.island_generation_failed
import branchworld.shared.generated.resources.show_diagnostics
import branchworld.shared.generated.resources.world_title
import id.branchworld.game.simulation.WorldClock
import id.branchworld.game.simulation.WorldRenderSource
import id.branchworld.game.save.WorldSaveStore
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private class WorldSession : ViewModel() {
    val islands = IslandSession()
}

private val BranchWorldColors = lightColorScheme(
    primary = Color(0xFF285648),
    onPrimary = Color.White,
    background = Color(0xFFF4F5ED),
    onBackground = Color(0xFF152C25),
    surface = Color(0xFFF4F5ED),
    onSurface = Color(0xFF152C25),
    surfaceContainerHigh = Color(0xFFF7F8EF),
)

@Composable
fun App(
    viewport: (@Composable (Modifier, WorldRenderSource?) -> Unit)? = null,
    viewportHud: (@Composable () -> Unit)? = null,
    registerBackHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit = { _, _ -> },
    foreground: Boolean = true,
    saveStore: WorldSaveStore? = null,
    playSound: ((WorldSound) -> Unit)? = null,
) {
    var state by rememberSaveable(stateSaver = ShellState.saver) { mutableStateOf(ShellState()) }
    var soundEnabled by rememberSaveable { mutableStateOf(false) }
    val isWorld = state.route == ShellRoute.World
    var hasOpenedWorld by remember { mutableStateOf(isWorld) }
    val owner = LocalViewModelStoreOwner.current
    val retained = if (owner == null) null else viewModel(viewModelStoreOwner = owner) { WorldSession() }
    val fallbackScope = rememberCoroutineScope()
    val islands = retained?.islands ?: remember { IslandSession() }
    val operations = retained?.viewModelScope ?: fallbackScope
    val storage by islands.saveStatus.collectAsStateWithLifecycle()
    LaunchedEffect(storage) {
        val restored = storage as? SaveStatus.Restored ?: return@LaunchedEffect
        hasOpenedWorld = true
        state = state.copy(seed = restored.seed).openWorld()
        islands.acknowledgeRestore(restored.tick)
    }
    val load by islands.state.collectAsStateWithLifecycle()
    val selectedSeed = state.seed
    val session = (load as? IslandLoad.Ready)?.takeIf { it.clock.seed == selectedSeed }
    val clock = session?.clock
    val goHome = {
        if (session?.comparison?.value != null) {
            session.closeComparison()
        } else {
            islands.dismissSaveStatus()
            state = state.back()
        }
    }
    registerBackHandler(isWorld, goHome)
    LaunchedEffect(islands, isWorld, selectedSeed) {
        if (isWorld) islands.open(selectedSeed)
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(clock, lifecycle, isWorld, foreground) {
        if (clock != null && isWorld && foreground) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                clock.run(isActive = {
                    state.route == ShellRoute.World && state.seed == clock.seed &&
                        lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                })
            }
        }
    }

    MaterialTheme(colorScheme = BranchWorldColors) {
        Surface(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize().safeContentPadding()) {
                val viewportHeight = (maxHeight * 0.47f).coerceIn(176.dp, 480.dp)
                if (isWorld || (hasOpenedWorld && viewport != null)) {
                    WorldScreen(
                        session = session,
                        world = session?.world,
                        seed = selectedSeed,
                        generationFailed = (load as? IslandLoad.Failed)?.seed == selectedSeed,
                        viewportHeight = viewportHeight,
                        active = isWorld,
                        viewport = viewport,
                        viewportHud = viewportHud,
                        diagnosticsVisible = state.diagnosticsVisible,
                        onToggleDiagnostics = { state = state.toggleDiagnostics() },
                        onBack = goHome,
                        onRetry = { operations.launch { islands.open(selectedSeed) } },
                        onForked = { if (soundEnabled) playSound?.invoke(WorldSound.Fork) },
                        saveControls = saveStore?.let {
                            {
                                SaveControls(
                                    status = storage,
                                    onSave = { replace ->
                                        operations.launch {
                                            if (islands.save(it, replace) && soundEnabled) playSound?.invoke(WorldSound.Save)
                                        }
                                    },
                                    onDismiss = islands::dismissSaveStatus,
                                )
                            }
                        },
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
                if (!isWorld) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Box(contentAlignment = Alignment.Center) {
                            HomeScreen(
                                seed = selectedSeed,
                                onSelectSeed = { state = state.selectSeed(it) },
                                onOpenWorld = {
                                    islands.dismissSaveStatus()
                                    hasOpenedWorld = true
                                    state = state.openWorld()
                                },
                                onLoad = saveStore?.let { { operations.launch { islands.restore(it) } } },
                                saveStatus = storage,
                                soundEnabled = soundEnabled,
                                onToggleSound = if (playSound != null) ({ soundEnabled = !soundEnabled }) else null,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    seed: Long,
    onSelectSeed: (Long) -> Unit,
    onOpenWorld: () -> Unit,
    onLoad: (() -> Unit)?,
    saveStatus: SaveStatus?,
    soundEnabled: Boolean,
    onToggleSound: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var showGuide by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(Res.string.branchworld_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(Res.string.branchworld_tagline),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Text(stringResource(Res.string.island_seed), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow {
            IslandSeeds.forEachIndexed { index, candidate ->
                SegmentedButton(
                    selected = seed == candidate,
                    onClick = { onSelectSeed(candidate) },
                    enabled = saveStatus != SaveStatus.Busy,
                    shape = SegmentedButtonDefaults.itemShape(index, IslandSeeds.size),
                    icon = {},
                ) { Text(candidate.toString()) }
            }
        }
        Button(onClick = onOpenWorld, enabled = saveStatus != SaveStatus.Busy) {
            Text(stringResource(Res.string.open_world))
        }
        if (onLoad != null) LoadControls(saveStatus, onLoad)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = showGuide, onClick = { showGuide = !showGuide },
                label = { Text(stringResource(Res.string.branch_guide)) })
            if (onToggleSound != null) {
                FilterChip(selected = soundEnabled, onClick = onToggleSound,
                    label = { Text(stringResource(Res.string.world_sounds)) })
            }
        }
        if (showGuide) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.branch_guide_play))
                Text(stringResource(Res.string.branch_guide_fork))
                Text(stringResource(Res.string.branch_guide_compare))
            }
        }
    }
}

@Composable
private fun WorldScreen(
    session: IslandLoad.Ready?,
    world: WorldRenderSource?,
    seed: Long,
    generationFailed: Boolean,
    viewportHeight: Dp,
    active: Boolean,
    viewport: (@Composable (Modifier, WorldRenderSource?) -> Unit)?,
    viewportHud: (@Composable () -> Unit)?,
    diagnosticsVisible: Boolean,
    onToggleDiagnostics: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onForked: () -> Unit,
    saveControls: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var timelineOpen by rememberSaveable(seed) { mutableStateOf(false) }
    var inspectorOpen by remember(session) { mutableStateOf(false) }
    val clock = session?.clock
    val comparison = session?.comparison?.collectAsStateWithLifecycle()?.value
    Column(
        modifier = modifier
            .widthIn(max = 1000.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (active) {
            Text(
                text = stringResource(Res.string.world_title, seed.toString()),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
            )
            if (session != null) {
                val timeline by session.timelineView.collectAsStateWithLifecycle()
                if (comparison == null) {
                    WorldControls(session.clock, playbackEnabled = !timeline.seeking, editsEnabled = timeline.editsAllowed)
                } else {
                    ComparisonHeader(session, comparison, timeline.branches)
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = viewportHeight)
                .clip(MaterialTheme.shapes.large),
        ) {
            if (viewport == null && world != null) {
                WorldViewport(Modifier.matchParentSize())
            } else {
                viewport?.invoke(Modifier.matchParentSize(), world.takeIf { active })
            }
            if (active && world == null) {
                val message = if (generationFailed) Res.string.island_generation_failed else Res.string.island_generating
                Column(Modifier.align(Alignment.Center).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(message))
                    if (generationFailed) TextButton(onClick = onRetry) { Text(stringResource(Res.string.retry_island)) }
                }
            }
            if (active && world != null && viewportHud != null) {
                Box(Modifier.align(Alignment.TopStart).padding(12.dp)) { viewportHud() }
            }
            if (active && clock != null && diagnosticsVisible && comparison == null) {
                Diagnostics(
                    clock = clock,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                )
            }
            if (active && comparison != null) {
                ComparisonOverlay(comparison, Modifier.align(Alignment.Center).padding(16.dp))
            }
        }
        if (active && comparison == null) {
            if (session != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { timelineOpen = !timelineOpen }) {
                        Text(stringResource(if (timelineOpen) Res.string.close_timeline else Res.string.open_timeline))
                    }
                    OutlinedButton(onClick = { inspectorOpen = !inspectorOpen }) {
                        Text(stringResource(if (inspectorOpen) Res.string.close_inspector else Res.string.inspect_world))
                    }
                }
                saveControls?.invoke()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.back_to_home), textAlign = TextAlign.Center)
                }
                OutlinedButton(onClick = onToggleDiagnostics, modifier = Modifier.weight(1f), enabled = clock != null) {
                    val label = if (diagnosticsVisible) Res.string.hide_diagnostics else Res.string.show_diagnostics
                    Text(stringResource(label), textAlign = TextAlign.Center)
                }
            }
            if (session != null) {
                if (timelineOpen) TimelineControls(session, onForked)
                if (inspectorOpen) WorldInspector(session)
            }
        } else if (active && session != null && comparison != null) {
            ComparisonDetails(session, comparison)
        }
    }
}

@Composable
private fun Diagnostics(clock: WorldClock<*>, modifier: Modifier = Modifier) {
    val platform = remember { getPlatform().name }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val pose by clock.render.collectAsStateWithLifecycle()
    Surface(
        modifier = modifier.widthIn(max = 264.dp).fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(Res.string.diagnostics_title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleSmall,
            )
            Text(stringResource(Res.string.diagnostics_platform, platform))
            Text(stringResource(Res.string.diagnostics_seed, clock.seed.toString()))
            pose.leadRabbit?.ground?.let {
                Text(stringResource(Res.string.diagnostics_position, it.x, it.z))
            }
            Text(stringResource(Res.string.diagnostics_lifecycle, lifecycle.name))
        }
    }
}
