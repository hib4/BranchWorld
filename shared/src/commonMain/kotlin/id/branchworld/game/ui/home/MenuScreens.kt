package id.branchworld.game.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import branchworld.shared.generated.resources.*
import branchworld.shared.generated.resources.Res
import branchworld.shared.generated.resources.atlas_ember_burned
import id.branchworld.game.data.AtlasEntry
import id.branchworld.game.data.GameSettings
import id.branchworld.game.data.PlayerProfile
import id.branchworld.game.data.key
import id.branchworld.game.ui.components.Block
import id.branchworld.game.ui.components.BlockChoice
import id.branchworld.game.ui.components.BlockSize
import id.branchworld.game.ui.components.Board
import id.branchworld.game.ui.components.BoardSheet
import id.branchworld.game.ui.components.IslandMiniMap
import id.branchworld.game.ui.components.Peg
import id.branchworld.game.ui.components.PegButton
import id.branchworld.game.ui.components.ScrollColumn
import id.branchworld.game.ui.components.WoodSwitchRow
import id.branchworld.game.ui.theme.GameIcons
import id.branchworld.game.ui.theme.LocalSpacing
import id.branchworld.game.ui.theme.Wood
import id.branchworld.game.ui.theme.chapterPaint
import id.branchworld.game.ui.world.dayOf
import id.branchworld.game.ui.world.sessionKind
import id.branchworld.game.ui.world.sessionTitle
import org.jetbrains.compose.resources.stringResource

/**
 * A titled menu page over the world: back arrow, heading, short introduction and scrolling content. The page scrolls
 * the window's full height and keeps the top and bottom safe areas as padding inside the scroll, so every board can be
 * scrolled clear of the status bar and the home indicator.
 */
@Composable
internal fun MenuPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val edge = LocalSpacing.current.edge(16.dp)
    Column(modifier.verticalScroll(rememberScrollState())
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical)).padding(vertical = edge),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Board(padding = 6.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PegButton(GameIcons.Back, stringResource(Res.string.menu_back), onBack)
                Text(title, Modifier.padding(start = 4.dp, end = 14.dp).semantics { heading() },
                    style = MaterialTheme.typography.headlineLarge)
            }
        }
        content()
    }
}

@Composable
internal fun AtlasRow(entry: AtlasEntry, profile: PlayerProfile, onOpen: () -> Unit) {
    val key = entry.key()
    val record = profile.chapters.firstOrNull { it.slot == entry.slot }
    val marks = profile.bookmarks.count { it.slot == entry.slot }
    Board(Modifier.widthIn(max = 460.dp).fillMaxWidth(), padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IslandMiniMap(key.terrain, Modifier.size(width = 86.dp, height = 78.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(sessionTitle(key), style = MaterialTheme.typography.titleLarge)
                if (key.isShared) Peg(stringResource(sessionKind(key)), paint = Wood.Woad)
                Text(stringResource(Res.string.atlas_day, dayOf(entry.tick), entry.branches, entry.branchName),
                    style = MaterialTheme.typography.bodySmall, color = Wood.WalnutSoft)
            }
        }
        record?.let {
            Text(stringResource(Res.string.atlas_chapter_closed, it.branchName, it.people, it.deer),
                style = MaterialTheme.typography.bodySmall, color = Wood.WalnutSoft)
            it.burned?.let { count ->
                Text(stringResource(Res.string.atlas_ember_burned, count),
                    style = MaterialTheme.typography.bodySmall, color = Wood.WalnutSoft)
            }
        }
        if (marks > 0) Text(stringResource(Res.string.atlas_marks, marks), style = MaterialTheme.typography.bodySmall, color = Wood.WalnutSoft)
        Block(stringResource(Res.string.atlas_open), onOpen, paint = chapterPaint(key.story, key.sandbox), size = BlockSize.Small)
    }
}

@Composable
internal fun SettingsSheet(
    settings: GameSettings,
    worldSounds: Boolean,
    onChange: (GameSettings) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoardSheet(stringResource(Res.string.settings_title), modifier, onClose = onClose) {
        ScrollColumn(spacing = 6.dp) {
            Text(stringResource(Res.string.settings_sound), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium)
            if (worldSounds) WoodSwitchRow(stringResource(Res.string.world_sounds), stringResource(Res.string.settings_world_sounds_detail),
                settings.worldSounds) { onChange(settings.copy(worldSounds = it)) }
            if (worldSounds) WoodSwitchRow(stringResource(Res.string.settings_music), stringResource(Res.string.settings_music_detail),
                settings.music) { onChange(settings.copy(music = it)) }
            WoodSwitchRow(stringResource(Res.string.settings_ui_sounds), stringResource(Res.string.settings_ui_sounds_detail),
                settings.interfaceSounds) { onChange(settings.copy(interfaceSounds = it)) }
            Text(stringResource(Res.string.settings_display), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium)
            WoodSwitchRow(stringResource(Res.string.reduced_camera_motion), stringResource(Res.string.settings_motion_detail),
                settings.reducedMotion) { onChange(settings.copy(reducedMotion = it)) }
            Text(stringResource(Res.string.settings_text), style = MaterialTheme.typography.titleMedium)
            val sizes = listOf(100 to Res.string.text_standard, 115 to Res.string.text_large, 130 to Res.string.text_largest)
            BlockChoice(sizes.map { (scale, label) -> stringResource(label) to (settings.textScale == scale) }) {
                onChange(settings.copy(textScale = sizes[it].first))
            }
            Text(stringResource(Res.string.settings_controls), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium)
            for (line in listOf(Res.string.controls_place, Res.string.controls_look, Res.string.controls_zoom, Res.string.controls_follow,
                Res.string.controls_pause)) {
                Text(stringResource(line), style = MaterialTheme.typography.bodySmall, color = Wood.WalnutSoft)
            }
            Text(stringResource(Res.string.settings_developer), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium)
            WoodSwitchRow(stringResource(Res.string.shape_tick_tools), null, settings.tickTools) { onChange(settings.copy(tickTools = it)) }
            WoodSwitchRow(stringResource(Res.string.settings_earlier_stories), stringResource(Res.string.settings_earlier_stories_detail),
                settings.earlierStories) { onChange(settings.copy(earlierStories = it)) }
            Text(stringResource(Res.string.settings_credits), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(Res.string.credits_music), style = MaterialTheme.typography.bodySmall, color = Wood.WalnutSoft)
            Text(stringResource(Res.string.settings_saved), style = MaterialTheme.typography.bodySmall, color = Wood.WalnutSoft)
        }
    }
}
