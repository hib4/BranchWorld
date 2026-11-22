package id.branchworld.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import branchworld.shared.generated.resources.Res
import branchworld.shared.generated.resources.*
import id.branchworld.game.save.AtlasEntry
import id.branchworld.game.save.GameSettings
import id.branchworld.game.save.PlayerProfile
import id.branchworld.game.terrain.TerrainKey
import id.branchworld.game.terrain.WatershedWorldgenVersion
import org.jetbrains.compose.resources.stringResource
import kotlin.random.Random

/** Two separate pieces leave the live island visible between the name and the controls. */
@Composable
internal fun TitleMenu(
    profile: PlayerProfile,
    ready: Boolean,
    onEnter: (IslandSessionKey, Boolean) -> Unit,
    onScreen: (HomeScreen) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { warmMiniMaps(IslandGallery) }
    BoxWithConstraints(modifier) {
        val short = maxHeight < 440.dp
        val wide = maxWidth >= 600.dp
        val gutter = if (wide) 24.dp else LocalSpacing.current.edge(16.dp)
        Board(Modifier.align(Alignment.TopStart).padding(top = gutter, start = gutter, end = gutter), padding = 12.dp,
            gap = 2.dp) {
            Text(stringResource(Res.string.branchworld_title), Modifier.semantics { heading() },
                style = if (wide && !short) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displaySmall)
            if (!short) Text(stringResource(Res.string.title_tagline_short), color = Wood.WalnutSoft,
                style = MaterialTheme.typography.bodySmall)
        }
        val last = profile.lastPlayed
        val key = last?.key() ?: Story.FadingGrove.key
        Board(Modifier.align(Alignment.BottomStart).padding(gutter).widthIn(max = 352.dp).fillMaxWidth(), padding = 10.dp, depth = 4.dp) {
            Block(
                label = stringResource(when {
                    !ready -> Res.string.loading_world
                    last == null -> Res.string.menu_begin
                    else -> Res.string.menu_continue
                }),
                onClick = { onEnter(key, last == null) },
                modifier = Modifier.fillMaxWidth(),
                paint = chapterPaint(key.story, key.sandbox),
                detail = if (!ready) null else if (last == null) stringResource(Story.FadingGrove.title)
                    else stringResource(Res.string.menu_continue_detail, stringResource(islandTitle(key.terrain)), dayOf(last.tick)),
                icon = GameIcons.Play,
                enabled = ready,
                size = if (short) BlockSize.Medium else BlockSize.Large,
                sound = WorldSound.UiConfirm,
            )
            for (pair in listOf(HomeScreen.Stories to HomeScreen.Atlas, HomeScreen.Sandbox to HomeScreen.Settings)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (page in listOf(pair.first, pair.second)) {
                        val label = when (page) {
                            HomeScreen.Stories -> Res.string.menu_stories
                            HomeScreen.Atlas -> Res.string.menu_atlas
                            HomeScreen.Sandbox -> Res.string.menu_sandbox
                            else -> Res.string.menu_settings
                        }
                        Block(stringResource(label), { onScreen(page) }, Modifier.weight(1f),
                            paint = Wood.BeechLight, enabled = ready || page == HomeScreen.Settings, size = BlockSize.Small)
                    }
                }
            }
        }
    }
}

/** A titled menu page over the world: back arrow, heading, short introduction and scrolling content. */
@Composable
internal fun MenuPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StoriesScreen(profile: PlayerProfile, onBegin: (Story) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    MenuPage(stringResource(Res.string.stories_title), onBack, modifier) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (story in Story.entries) StoryCard(story, profile) { onBegin(story) }
        }
    }
}

@Composable
private fun StoryCard(story: Story, profile: PlayerProfile, onBegin: () -> Unit) {
    val unlocked = story.unlocked(profile)
    val entry = profile.atlas.firstOrNull { it.key() == story.key }
    val record = profile.chapters.firstOrNull { it.chapter == story.chapter.code }
    val paint = chapterPaint(story.chapter)
    Board(Modifier.widthIn(max = 312.dp).fillMaxWidth(), padding = 12.dp) {
        Box {
            IslandMiniMap(story.key.terrain, Modifier.fillMaxWidth().aspectRatio(2.1f), muted = !unlocked)
            ChapterStamp(story.numeral, paint, unlocked, Modifier.align(Alignment.TopStart).padding(10.dp))
        }
        Text(stringResource(story.title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(stringResource(story.premise), style = MaterialTheme.typography.bodySmall, color = Wood.WalnutSoft)
        val status = when {
            !unlocked -> stringResource(Res.string.story_locked, stringResource(checkNotNull(story.previous).title))
            record != null -> stringResource(Res.string.story_closed, record.branchName)
            entry != null -> stringResource(Res.string.story_progress, dayOf(entry.tick), entry.branches)
            else -> null
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Wood.Walnut) }
        if (unlocked) Block(stringResource(when {
            entry == null -> Res.string.story_begin
            record != null -> Res.string.story_revisit
            else -> Res.string.story_continue
        }),
            onBegin, Modifier.fillMaxWidth(), paint = paint, icon = GameIcons.Play, size = BlockSize.Small, sound = WorldSound.UiConfirm)
    }
}

@Composable
internal fun AtlasScreen(profile: PlayerProfile, onOpen: (AtlasEntry) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    MenuPage(stringResource(Res.string.atlas_title), onBack, modifier) {
        val entries = profile.atlas.sortedByDescending { it.order }
        if (entries.isEmpty()) Board(Modifier.widthIn(max = 320.dp)) {
            Text(stringResource(Res.string.atlas_empty), color = Wood.WalnutSoft)
        }
        for (entry in entries) AtlasRow(entry, profile) { onOpen(entry) }
    }
}

@Composable
private fun AtlasRow(entry: AtlasEntry, profile: PlayerProfile, onOpen: () -> Unit) {
    val key = entry.key()
    val record = profile.chapters.firstOrNull { it.slot == entry.slot }
    val marks = profile.bookmarks.count { it.slot == entry.slot }
    Board(Modifier.widthIn(max = 460.dp).fillMaxWidth(), padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IslandMiniMap(key.terrain, Modifier.size(width = 86.dp, height = 78.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(sessionTitle(key), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(Res.string.atlas_day, dayOf(entry.tick), entry.branches, entry.branchName),
                    style = MaterialTheme.typography.bodySmall, color = Wood.WalnutSoft)
            }
        }
        record?.let {
            Text(stringResource(Res.string.atlas_chapter_closed, it.branchName, it.people, it.deer),
                style = MaterialTheme.typography.bodySmall, color = Wood.WalnutSoft)
        }
        if (marks > 0) Text(stringResource(Res.string.atlas_marks, marks), style = MaterialTheme.typography.bodySmall, color = Wood.WalnutSoft)
        Block(stringResource(Res.string.atlas_open), onOpen, paint = chapterPaint(key.story, key.sandbox), size = BlockSize.Small)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SandboxScreen(onShape: (Long) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var selected by rememberSaveable { mutableStateOf(IslandSeeds[1]) }
    var seedText by rememberSaveable { mutableStateOf("") }
    val typed = seedText.trim().toLongOrNull()
    MenuPage(stringResource(Res.string.sandbox_title), onBack, modifier) {
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (seed in IslandSeeds) IslandChoice(TerrainKey(seed, WatershedWorldgenVersion), selected == seed && typed == null,
                stringResource(islandTitle(TerrainKey(seed, WatershedWorldgenVersion)))) {
                selected = seed
                seedText = ""
            }
        }
        Board(Modifier.widthIn(max = 480.dp).fillMaxWidth(), padding = 12.dp) {
            Text(stringResource(Res.string.sandbox_untold), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(seedText, { value -> seedText = value.filter { it.isDigit() || it == '-' }.take(19) },
                    Modifier.weight(1f), singleLine = true, label = { Text(stringResource(Res.string.sandbox_seed)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Wood.Walnut, unfocusedTextColor = Wood.Walnut, focusedBorderColor = Wood.Woad,
                        unfocusedBorderColor = Wood.WalnutSoft, focusedLabelColor = Wood.Woad, unfocusedLabelColor = Wood.WalnutSoft,
                        cursorColor = Wood.Woad,
                    ))
                PegButton(GameIcons.Dice, stringResource(Res.string.sandbox_roll),
                    { seedText = Random.nextLong(1, 1_000_000_000L).toString() })
            }
            if (seedText.isNotBlank() && typed == null) Text(stringResource(Res.string.sandbox_invalid), color = Wood.Walnut,
                style = MaterialTheme.typography.bodySmall)
            if (typed != null) IslandMiniMap(TerrainKey(typed, WatershedWorldgenVersion),
                Modifier.fillMaxWidth().aspectRatio(2f).clip(RoundedCornerShape(10.dp)))
        }
        Block(stringResource(Res.string.sandbox_shape), { onShape(typed ?: selected) },
            enabled = seedText.isBlank() || typed != null, paint = Wood.Leaf, icon = GameIcons.Sparkle)
    }
}

@Composable
private fun IslandChoice(key: TerrainKey, selected: Boolean, name: String, onSelect: () -> Unit) {
    val ui = LocalUi.current
    val face = if (selected) Wood.Leaf else Wood.BeechLight
    Board(Modifier.width(145.dp).selectable(selected, role = Role.RadioButton) { ui.click(); onSelect() },
        padding = 8.dp, gap = 6.dp, face = face) {
        IslandMiniMap(key, Modifier.fillMaxWidth().aspectRatio(1.45f))
        Text(name, style = MaterialTheme.typography.titleMedium)
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
            for (line in listOf(Res.string.controls_look, Res.string.controls_zoom, Res.string.controls_follow, Res.string.controls_pause)) {
                Text(stringResource(line), style = MaterialTheme.typography.bodySmall, color = Wood.WalnutSoft)
            }
            Text(stringResource(Res.string.settings_developer), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium)
            WoodSwitchRow(stringResource(Res.string.shape_tick_tools), null, settings.tickTools) { onChange(settings.copy(tickTools = it)) }
            Text(stringResource(Res.string.settings_saved), style = MaterialTheme.typography.bodySmall, color = Wood.WalnutSoft)
        }
    }
}
