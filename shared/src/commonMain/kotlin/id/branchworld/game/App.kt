package id.branchworld.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import branchworld.shared.generated.resources.Res
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
import branchworld.shared.generated.resources.show_diagnostics
import branchworld.shared.generated.resources.world_title
import id.branchworld.game.simulation.WorldClock
import id.branchworld.game.simulation.WorldRenderProjection
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource

private const val PreviewSeed = 104729L

private class WorldSession : ViewModel() {
    val clock = WorldClock(PreviewSeed)
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
    viewport: (@Composable (Modifier, StateFlow<WorldRenderProjection>?) -> Unit)? = null,
    viewportHud: (@Composable () -> Unit)? = null,
    registerBackHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit = { _, _ -> },
    foreground: Boolean = true,
) {
    var state by rememberSaveable(stateSaver = ShellState.saver) { mutableStateOf(ShellState()) }
    val isWorld = state.route == ShellRoute.World
    var hasOpenedWorld by remember { mutableStateOf(isWorld) }
    val goHome = { state = state.back() }
    registerBackHandler(isWorld, goHome)
    val owner = LocalViewModelStoreOwner.current
    val clock = if (owner == null) {
        remember { WorldClock(PreviewSeed) }
    } else {
        viewModel(viewModelStoreOwner = owner) { WorldSession() }.clock
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(clock, lifecycle, isWorld, foreground) {
        if (isWorld && foreground) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                clock.run(isActive = {
                    state.route == ShellRoute.World && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                })
            }
        }
    }

    MaterialTheme(colorScheme = BranchWorldColors) {
        Surface(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize().safeContentPadding()) {
                val viewportHeight = (maxHeight * 0.52f).coerceIn(176.dp, 480.dp)
                if (isWorld || (hasOpenedWorld && viewport != null)) {
                    WorldScreen(
                        clock = clock,
                        viewportHeight = viewportHeight,
                        active = isWorld,
                        viewport = viewport,
                        viewportHud = viewportHud,
                        diagnosticsVisible = state.diagnosticsVisible,
                        onToggleDiagnostics = { state = state.toggleDiagnostics() },
                        onBack = goHome,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
                if (!isWorld) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Box(contentAlignment = Alignment.Center) {
                            HomeScreen(onOpenWorld = {
                                hasOpenedWorld = true
                                state = state.openWorld()
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(onOpenWorld: () -> Unit, modifier: Modifier = Modifier) {
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
        Button(onClick = onOpenWorld) {
            Text(stringResource(Res.string.open_world))
        }
    }
}

@Composable
private fun WorldScreen(
    clock: WorldClock,
    viewportHeight: Dp,
    active: Boolean,
    viewport: (@Composable (Modifier, StateFlow<WorldRenderProjection>?) -> Unit)?,
    viewportHud: (@Composable () -> Unit)?,
    diagnosticsVisible: Boolean,
    onToggleDiagnostics: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                text = stringResource(Res.string.world_title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
            )
            WorldControls(clock)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = viewportHeight)
                .clip(MaterialTheme.shapes.large),
        ) {
            if (viewport == null) {
                WorldViewport(Modifier.matchParentSize())
            } else {
                viewport(Modifier.matchParentSize(), clock.render.takeIf { active })
            }
            if (active && viewportHud != null) {
                Box(Modifier.align(Alignment.TopStart).padding(12.dp)) { viewportHud() }
            }
            if (active && diagnosticsVisible) {
                Diagnostics(
                    clock = clock,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                )
            }
        }
        if (active) Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.back_to_home), textAlign = TextAlign.Center)
            }
            Button(onClick = onToggleDiagnostics, modifier = Modifier.weight(1f)) {
                val label = if (diagnosticsVisible) Res.string.hide_diagnostics else Res.string.show_diagnostics
                Text(stringResource(label), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun Diagnostics(clock: WorldClock, modifier: Modifier = Modifier) {
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
            Text(stringResource(Res.string.diagnostics_position, pose.x, pose.z))
            Text(stringResource(Res.string.diagnostics_lifecycle, lifecycle.name))
        }
    }
}
