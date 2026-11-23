package id.branchworld.game.ui.legacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import branchworld.shared.generated.resources.*
import branchworld.shared.generated.resources.Res
import id.branchworld.game.data.AtlasEntry
import id.branchworld.game.data.PlayerProfile
import id.branchworld.game.data.WorldCodeImport
import id.branchworld.game.data.key
import id.branchworld.game.platform.sound.WorldSound
import id.branchworld.game.ui.components.Block
import id.branchworld.game.ui.components.BlockSize
import id.branchworld.game.ui.components.Board
import id.branchworld.game.ui.components.ChapterStamp
import id.branchworld.game.ui.components.IslandMiniMap
import id.branchworld.game.ui.components.PegButton
import id.branchworld.game.ui.home.AtlasRow
import id.branchworld.game.ui.home.MenuPage
import id.branchworld.game.ui.share.WorldCodeImportControl
import id.branchworld.game.ui.theme.GameIcons
import id.branchworld.game.ui.theme.LocalUi
import id.branchworld.game.ui.theme.Wood
import id.branchworld.game.ui.theme.chapterPaint
import id.branchworld.game.ui.world.dayOf
import id.branchworld.game.ui.world.islandTitle
import id.branchworld.simulation.core.WatershedWorldgenVersion
import id.branchworld.simulation.ecology.chapters.IslandSeeds
import id.branchworld.simulation.terrain.TerrainKey
import org.jetbrains.compose.resources.stringResource
import kotlin.random.Random

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
internal fun AtlasScreen(
    profile: PlayerProfile,
    onOpen: (AtlasEntry) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onImportCode: (suspend (String) -> WorldCodeImport)? = null,
    onOpenCode: (WorldCodeImport.Open) -> Unit = {},
) {
    MenuPage(stringResource(Res.string.atlas_title), onBack, modifier) {
        WorldCodeImportControl(onImportCode, onOpenCode)
        val entries = profile.atlas.sortedByDescending { it.order }
        if (entries.isEmpty()) Board(Modifier.widthIn(max = 320.dp)) {
            Text(stringResource(Res.string.atlas_empty), color = Wood.WalnutSoft)
        }
        for (entry in entries) AtlasRow(entry, profile) { onOpen(entry) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SandboxScreen(
    onShape: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onImportCode: (suspend (String) -> WorldCodeImport)? = null,
    onOpenCode: (WorldCodeImport.Open) -> Unit = {},
) {
    var selected by rememberSaveable { mutableStateOf(IslandSeeds[1]) }
    var seedText by rememberSaveable { mutableStateOf("") }
    val typed = seedText.trim().toLongOrNull()
    MenuPage(stringResource(Res.string.sandbox_title), onBack, modifier) {
        WorldCodeImportControl(onImportCode, onOpenCode)
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
