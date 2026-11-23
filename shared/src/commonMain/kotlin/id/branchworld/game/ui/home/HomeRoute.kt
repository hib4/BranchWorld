package id.branchworld.game.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import branchworld.shared.generated.resources.Res
import branchworld.shared.generated.resources.island_generation_failed
import branchworld.shared.generated.resources.retry_island
import id.branchworld.game.data.GameSettings
import id.branchworld.game.data.PlayerProfile
import id.branchworld.game.data.WorldCodeImport
import id.branchworld.game.data.key
import id.branchworld.game.domain.IslandSessionKey
import id.branchworld.game.ui.app.ShellLayout
import id.branchworld.game.ui.app.navigation.HomeScreen
import id.branchworld.game.ui.components.Block
import id.branchworld.game.ui.components.Board
import id.branchworld.game.ui.components.Scrim
import id.branchworld.game.ui.legacy.AtlasScreen
import id.branchworld.game.ui.legacy.SandboxScreen
import id.branchworld.game.ui.legacy.StoriesScreen
import id.branchworld.game.ui.theme.LocalSpacing
import id.branchworld.simulation.ecology.life.WorldStart
import org.jetbrains.compose.resources.stringResource

/** What the title side of the shell can ask for. */
internal interface HomeActions {
    fun enter(key: IslandSessionKey, start: Boolean)
    fun createWorld(seed: Long, start: WorldStart, name: String)
    fun show(screen: HomeScreen)
    fun openCode(result: WorldCodeImport.Open)
    fun retry()
    fun changeSettings(next: GameSettings)
}

/** The menus over the live title island: begin, continue, browse worlds and change settings. */
@Composable
internal fun BoxScope.HomeRoute(
    screen: HomeScreen,
    profile: PlayerProfile?,
    canContinue: Boolean,
    failed: Boolean,
    worldSounds: Boolean,
    layout: ShellLayout,
    importCode: (suspend (String) -> WorldCodeImport)?,
    actions: HomeActions,
    hud: (@Composable () -> Unit)?,
) {
    val spacing = LocalSpacing.current
    val page = Modifier.align(if (layout.sideIntroduction) Alignment.CenterStart else Alignment.BottomStart)
        .safeDrawingPadding().padding(if (layout.sideIntroduction) 24.dp else spacing.edge(16.dp))
    val current = profile ?: PlayerProfile()
    val title = { actions.show(HomeScreen.Title) }
    when (screen) {
        HomeScreen.Title -> LivingTitleMenu(current, canContinue, onContinue = { actions.enter(it.key(), start = false) },
            onScreen = actions::show, modifier = Modifier.matchParentSize().safeDrawingPadding())
        HomeScreen.NewWorld -> NewWorldScreen(onCreate = actions::createWorld, onBack = title,
            modifier = page.widthIn(max = 640.dp))
        HomeScreen.Worlds -> WorldsScreen(current, onOpen = { actions.enter(it.key(), start = false) },
            onNew = { actions.show(HomeScreen.NewWorld) }, onBack = title,
            modifier = page.widthIn(max = 600.dp), onImportCode = importCode, onOpenCode = actions::openCode)
        HomeScreen.Settings -> Unit
        HomeScreen.Stories -> StoriesScreen(current, onBegin = { actions.enter(it.key, start = true) },
            onBack = title, modifier = page.widthIn(max = 740.dp))
        HomeScreen.Atlas -> AtlasScreen(current, onOpen = { actions.enter(it.key(), start = false) },
            onBack = title, modifier = page.widthIn(max = 600.dp),
            onImportCode = importCode, onOpenCode = actions::openCode)
        HomeScreen.Sandbox -> SandboxScreen(onShape = { actions.enter(IslandSessionKey.sandbox(it), start = true) },
            onBack = title, modifier = page.widthIn(max = 640.dp),
            onImportCode = importCode, onOpenCode = actions::openCode)
    }
    if (failed) {
        Board(Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(spacing.edge(16.dp))) {
            Text(stringResource(Res.string.island_generation_failed))
            Block(stringResource(Res.string.retry_island), actions::retry)
        }
    }
    if (screen == HomeScreen.Settings) {
        Scrim(Modifier.matchParentSize())
        SettingsSheet(current.settings, worldSounds = worldSounds, onChange = actions::changeSettings, onClose = title,
            modifier = Modifier.align(Alignment.Center).safeDrawingPadding().padding(spacing.edge(16.dp)).widthIn(max = 480.dp)
                .heightIn(max = layout.height - 40.dp))
    }
    if (hud != null && screen == HomeScreen.Title) {
        Box(Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(spacing.edge(10.dp))) { hud() }
    }
}
