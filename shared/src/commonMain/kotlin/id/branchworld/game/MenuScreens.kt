package id.branchworld.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import branchworld.shared.generated.resources.Res
import branchworld.shared.generated.resources.*
import id.branchworld.game.save.AtlasEntry
import id.branchworld.game.save.GameSettings
import id.branchworld.game.save.PlayerProfile
import id.branchworld.game.terrain.TerrainKey
import id.branchworld.game.terrain.WatershedWorldgenVersion
import org.jetbrains.compose.resources.stringResource
import kotlin.random.Random

/** What the journey asks for next, in one sentence. */
@Composable
internal fun nextStep(profile: PlayerProfile): String = stringResource(when {
    !profile.closed(Story.FadingGrove.chapter.code) && profile.atlas.none { it.key() == Story.FadingGrove.key } -> Res.string.next_begin
    !profile.closed(Story.FadingGrove.chapter.code) -> Res.string.next_close_grove
    profile.atlas.none { it.key() == Story.LastCrossing.key } -> Res.string.next_crossing
    !profile.closed(Story.LastCrossing.chapter.code) -> Res.string.next_close_crossing
    else -> Res.string.next_all
})

@Composable
internal fun TitleMenu(
    profile: PlayerProfile,
    ready: Boolean,
    onContinue: (AtlasEntry) -> Unit,
    onScreen: (HomeScreen) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { warmMiniMaps(IslandGallery) }
    BoxWithConstraints(modifier) {
    val compact = maxWidth < 420.dp
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 4.dp)) {
        Text(stringResource(Res.string.branchworld_title), Modifier.semantics { heading() }, color = Atlas.Vellum,
            style = MaterialTheme.typography.displayLarge.let { if (compact) it.copy(fontSize = 46.sp, lineHeight = 50.sp) else it },
            maxLines = 1, softWrap = false)
        Text(stringResource(Res.string.title_tagline), color = Atlas.Mist, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(6.dp))
        OrnamentRule(modifier = Modifier.widthIn(max = 360.dp))
        Spacer(Modifier.height(4.dp))
        profile.lastPlayed?.let { last ->
            val key = last.key()
            MenuItem(stringResource(Res.string.menu_continue), { onContinue(last) }, enabled = ready,
                detail = stringResource(Res.string.menu_continue_detail, sessionTitle(key), dayOf(last.tick), last.branchName),
                icon = GameIcons.Play, large = !compact)
        }
        MenuItem(stringResource(Res.string.menu_stories), { onScreen(HomeScreen.Stories) }, enabled = ready,
            detail = stringResource(Res.string.menu_stories_detail), icon = GameIcons.Book, large = !compact)
        MenuItem(stringResource(Res.string.menu_atlas), { onScreen(HomeScreen.Atlas) }, enabled = ready,
            detail = stringResource(Res.string.menu_atlas_detail), icon = GameIcons.Compass, large = !compact)
        MenuItem(stringResource(Res.string.menu_sandbox), { onScreen(HomeScreen.Sandbox) }, enabled = ready,
            detail = stringResource(Res.string.menu_sandbox_detail), icon = GameIcons.Sparkle, large = !compact)
        MenuItem(stringResource(Res.string.menu_settings), { onScreen(HomeScreen.Settings) },
            detail = stringResource(Res.string.menu_settings_detail), icon = GameIcons.Gear, large = !compact)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Top) {
            Text("◆", color = Atlas.Gold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 3.dp, end = 8.dp))
            Text(if (ready) nextStep(profile) else stringResource(Res.string.loading_world), color = Atlas.Mist,
                style = MaterialTheme.typography.bodyMedium)
        }
    }
    }
}

/** A titled menu page over the world: back arrow, heading, short introduction and scrolling content. */
@Composable
internal fun MenuPage(
    title: String,
    intro: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconAction(GameIcons.Back, stringResource(Res.string.menu_back), onBack)
            Spacer(Modifier.width(6.dp))
            Text(title, Modifier.semantics { heading() }, color = Atlas.Vellum, style = MaterialTheme.typography.displaySmall)
        }
        intro?.let { Text(it, color = Atlas.Mist, style = MaterialTheme.typography.bodyMedium) }
        OrnamentRule()
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StoriesScreen(profile: PlayerProfile, onBegin: (Story) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    MenuPage(stringResource(Res.string.stories_title), stringResource(Res.string.stories_intro), onBack, modifier) {
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
    GlassPanel(Modifier.widthIn(min = 260.dp, max = 340.dp)) {
        Box {
            IslandMiniMap(story.key.terrain, Modifier.fillMaxWidth().aspectRatio(2.2f).clip(RoundedCornerShape(10.dp)))
            if (!unlocked) Box(Modifier.matchParentSize().clip(RoundedCornerShape(10.dp)).background(Atlas.Ink.copy(alpha = 0.62f)))
        }
        SectionLabel(stringResource(Res.string.chapter_numeral, story.numeral))
        Text(stringResource(story.title), style = MaterialTheme.typography.headlineSmall, color = if (unlocked) Atlas.Vellum else Atlas.Faded)
        Text(stringResource(story.premise), style = MaterialTheme.typography.bodySmall, color = Atlas.Mist)
        val status = when {
            !unlocked -> stringResource(Res.string.story_locked, stringResource(checkNotNull(story.previous).title))
            record != null -> stringResource(Res.string.story_closed, record.branchName)
            entry != null -> stringResource(Res.string.story_progress, dayOf(entry.tick), entry.branches)
            else -> stringResource(Res.string.story_new)
        }
        Text(status, style = MaterialTheme.typography.labelMedium, color = if (unlocked) Atlas.Gold else Atlas.Faded)
        if (unlocked) StoryButton(stringResource(if (entry == null) Res.string.story_begin else Res.string.story_continue), onBegin,
            icon = GameIcons.Play)
    }
}

@Composable
internal fun AtlasScreen(profile: PlayerProfile, onOpen: (AtlasEntry) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    MenuPage(stringResource(Res.string.atlas_title), stringResource(Res.string.atlas_intro), onBack, modifier) {
        val entries = profile.atlas.sortedByDescending { it.order }
        if (entries.isEmpty()) Text(stringResource(Res.string.atlas_empty), color = Atlas.Mist)
        for (entry in entries) AtlasRow(entry, profile) { onOpen(entry) }
    }
}

@Composable
private fun AtlasRow(entry: AtlasEntry, profile: PlayerProfile, onOpen: () -> Unit) {
    val key = entry.key()
    val record = profile.chapters.firstOrNull { it.slot == entry.slot }
    val marks = profile.bookmarks.count { it.slot == entry.slot }
    GlassPanel(Modifier.fillMaxWidth().widthIn(max = 560.dp), padding = 12.dp, corners = false) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IslandMiniMap(key.terrain, Modifier.size(width = 92.dp, height = 70.dp).clip(RoundedCornerShape(8.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SectionLabel(stringResource(sessionKind(key)))
                Text(sessionTitle(key), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(Res.string.atlas_day, dayOf(entry.tick), entry.branches, entry.branchName),
                    style = MaterialTheme.typography.bodySmall, color = Atlas.Mist, maxLines = 1, overflow = TextOverflow.Ellipsis)
                record?.let {
                    Text(stringResource(Res.string.atlas_chapter_closed, it.branchName, it.people, it.deer),
                        style = MaterialTheme.typography.bodySmall, color = Atlas.Gold)
                }
                if (marks > 0) Text(stringResource(Res.string.atlas_marks, marks), style = MaterialTheme.typography.bodySmall, color = Atlas.Mist)
            }
            StoryButton(stringResource(Res.string.atlas_open), onOpen)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SandboxScreen(onShape: (Long) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var selected by rememberSaveable { mutableStateOf(IslandSeeds[1]) }
    var seedText by rememberSaveable { mutableStateOf("") }
    val typed = seedText.trim().toLongOrNull()
    MenuPage(stringResource(Res.string.sandbox_title), stringResource(Res.string.sandbox_intro), onBack, modifier) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (seed in IslandSeeds) IslandChoice(TerrainKey(seed, WatershedWorldgenVersion), selected == seed && typed == null,
                stringResource(islandTitle(TerrainKey(seed, WatershedWorldgenVersion)))) {
                selected = seed
                seedText = ""
            }
        }
        GlassPanel(Modifier.widthIn(max = 520.dp), padding = 12.dp, corners = false) {
            SectionLabel(stringResource(Res.string.sandbox_untold))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(seedText, { value -> seedText = value.filter { it.isDigit() || it == '-' }.take(19) },
                    Modifier.weight(1f), singleLine = true, label = { Text(stringResource(Res.string.sandbox_seed)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Atlas.Vellum, unfocusedTextColor = Atlas.Vellum, focusedBorderColor = Atlas.Gold,
                        unfocusedBorderColor = Atlas.Hairline, focusedLabelColor = Atlas.Gold, unfocusedLabelColor = Atlas.Mist,
                        cursorColor = Atlas.Gold,
                    ))
                StoryButton(stringResource(Res.string.sandbox_roll), { seedText = Random.nextLong(1, 1_000_000_000L).toString() },
                    primary = false, icon = GameIcons.Dice, onDark = true)
            }
            if (seedText.isNotBlank() && typed == null) Text(stringResource(Res.string.sandbox_invalid), color = Atlas.Gold,
                style = MaterialTheme.typography.bodySmall)
            if (typed != null) IslandMiniMap(TerrainKey(typed, WatershedWorldgenVersion),
                Modifier.fillMaxWidth().aspectRatio(2f).clip(RoundedCornerShape(10.dp)))
        }
        StoryButton(stringResource(Res.string.sandbox_shape), { onShape(typed ?: selected) },
            enabled = seedText.isBlank() || typed != null, icon = GameIcons.Sparkle)
    }
}

@Composable
private fun IslandChoice(key: TerrainKey, selected: Boolean, name: String, onSelect: () -> Unit) {
    val ui = LocalUi.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.width(168.dp).clip(shape).background(Atlas.InkGlass)
            .border(if (selected) 2.dp else 1.dp, if (selected) Atlas.Gold else Atlas.Hairline, shape)
            .clickable(role = Role.RadioButton) {
                ui.click()
                onSelect()
            }.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IslandMiniMap(key, Modifier.fillMaxWidth().aspectRatio(1.3f).clip(RoundedCornerShape(8.dp)))
        Text(name, color = if (selected) Atlas.Gold else Atlas.Vellum, style = MaterialTheme.typography.titleMedium)
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
    VellumSheet(stringResource(Res.string.settings_title), modifier, onClose = onClose) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionLabel(stringResource(Res.string.settings_sound), color = Atlas.Ember)
            if (worldSounds) SettingRow(stringResource(Res.string.world_sounds), stringResource(Res.string.settings_world_sounds_detail),
                settings.worldSounds) { onChange(settings.copy(worldSounds = it)) }
            SettingRow(stringResource(Res.string.settings_ui_sounds), stringResource(Res.string.settings_ui_sounds_detail),
                settings.interfaceSounds) { onChange(settings.copy(interfaceSounds = it)) }
            SectionLabel(stringResource(Res.string.settings_display), color = Atlas.Ember)
            SettingRow(stringResource(Res.string.reduced_camera_motion), stringResource(Res.string.settings_motion_detail),
                settings.reducedMotion) { onChange(settings.copy(reducedMotion = it)) }
            Text(stringResource(Res.string.settings_text), style = MaterialTheme.typography.titleMedium)
            val sizes = listOf(100 to Res.string.text_standard, 115 to Res.string.text_large, 130 to Res.string.text_largest)
            ChoiceRow(sizes.map { (scale, label) -> stringResource(label) to (settings.textScale == scale) }) {
                onChange(settings.copy(textScale = sizes[it].first))
            }
            SectionLabel(stringResource(Res.string.settings_controls), color = Atlas.Ember)
            for (line in listOf(Res.string.controls_look, Res.string.controls_zoom, Res.string.controls_follow, Res.string.controls_pause)) {
                Text(stringResource(line), style = MaterialTheme.typography.bodySmall, color = Atlas.InkSoft)
            }
            SectionLabel(stringResource(Res.string.settings_developer), color = Atlas.Ember)
            SettingRow(stringResource(Res.string.shape_tick_tools), null, settings.tickTools) { onChange(settings.copy(tickTools = it)) }
            Text(stringResource(Res.string.settings_saved), style = MaterialTheme.typography.bodySmall, color = Atlas.InkSoft)
        }
    }
}

@Composable
internal fun MenuScrim(modifier: Modifier = Modifier) {
    Box(modifier.background(Atlas.Ink.copy(alpha = 0.55f)))
}

@Composable
internal fun BackIcon(label: String, onBack: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onBack).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(GameIcons.Back, null, Modifier.size(18.dp), tint = Atlas.Vellum)
        Text(label, color = Atlas.Vellum, style = MaterialTheme.typography.labelLarge)
    }
}
