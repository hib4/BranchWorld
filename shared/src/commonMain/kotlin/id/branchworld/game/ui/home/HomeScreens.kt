package id.branchworld.game.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.LaunchedEffect
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
import id.branchworld.game.data.AtlasEntry
import id.branchworld.game.domain.IslandGallery
import id.branchworld.game.data.PlayerProfile
import id.branchworld.game.data.WorldCodeImport
import id.branchworld.game.data.key
import id.branchworld.game.platform.sound.WorldSound
import id.branchworld.game.ui.app.navigation.HomeScreen
import id.branchworld.game.ui.components.Block
import id.branchworld.game.ui.components.BlockSize
import id.branchworld.game.ui.components.Board
import id.branchworld.game.ui.components.IslandMiniMap
import id.branchworld.game.ui.components.PegButton
import id.branchworld.game.ui.components.warmMiniMaps
import id.branchworld.game.ui.share.WorldCodeImportControl
import id.branchworld.game.ui.theme.GameIcons
import id.branchworld.game.ui.theme.LocalSpacing
import id.branchworld.game.ui.theme.LocalUi
import id.branchworld.game.ui.theme.Wood
import id.branchworld.game.ui.world.dayOf
import id.branchworld.game.ui.world.islandTitle
import id.branchworld.simulation.core.WatershedWorldgenVersion
import id.branchworld.simulation.ecology.chapters.IslandSeeds
import id.branchworld.simulation.ecology.life.WorldStart
import id.branchworld.simulation.terrain.TerrainKey
import org.jetbrains.compose.resources.stringResource
import kotlin.random.Random

/** The front door of a living-world sandbox: carry on, make a new world, or open one already made. */
@Composable
internal fun LivingTitleMenu(
    profile: PlayerProfile,
    ready: Boolean,
    onContinue: (AtlasEntry) -> Unit,
    onScreen: (HomeScreen) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { warmMiniMaps(IslandGallery) }
    BoxWithConstraints(modifier) {
        val short = maxHeight < 440.dp
        val wide = maxWidth >= 600.dp
        val gutter = if (wide) 24.dp else LocalSpacing.current.edge(16.dp)
        Board(Modifier.align(Alignment.TopStart).padding(top = gutter, start = gutter, end = gutter), padding = 12.dp, gap = 2.dp) {
            Text(stringResource(Res.string.branchworld_title), Modifier.semantics { heading() },
                style = if (wide && !short) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displaySmall)
            if (!short) Text(stringResource(Res.string.title_tagline_living), color = Wood.WalnutSoft,
                style = MaterialTheme.typography.bodySmall)
        }
        val last = profile.worlds.firstOrNull()
        Board(Modifier.align(Alignment.BottomStart).padding(gutter).widthIn(max = 352.dp).fillMaxWidth(), padding = 10.dp, depth = 4.dp) {
            if (last != null) {
                Block(stringResource(if (ready) Res.string.menu_continue else Res.string.loading_world), { onContinue(last) },
                    Modifier.fillMaxWidth(), paint = Wood.Leaf,
                    detail = stringResource(Res.string.menu_continue_world, worldName(last), dayOf(last.tick)),
                    icon = GameIcons.Play, enabled = ready, size = if (short) BlockSize.Medium else BlockSize.Large, sound = WorldSound.UiConfirm)
            }
            Block(stringResource(Res.string.menu_new_world), { onScreen(HomeScreen.NewWorld) }, Modifier.fillMaxWidth(),
                paint = if (last == null) Wood.Leaf else Wood.BeechLight,
                detail = if (last == null) stringResource(Res.string.menu_new_world_detail) else null, icon = GameIcons.Sparkle,
                enabled = ready, size = if (last == null && !short) BlockSize.Large else BlockSize.Small, sound = WorldSound.UiConfirm)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Block(stringResource(Res.string.menu_my_worlds), { onScreen(HomeScreen.Worlds) }, Modifier.weight(1f),
                    paint = Wood.BeechLight, enabled = ready, size = BlockSize.Small)
                Block(stringResource(Res.string.menu_settings), { onScreen(HomeScreen.Settings) }, Modifier.weight(1f),
                    paint = Wood.BeechLight, size = BlockSize.Small)
            }
            // The crafted stories and earlier sandbox stay playable for development, out of a player's way.
            if (profile.settings.earlierStories) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Block(stringResource(Res.string.menu_stories), { onScreen(HomeScreen.Stories) }, Modifier.weight(1f),
                    paint = Wood.Umber, enabled = ready, size = BlockSize.Small)
                Block(stringResource(Res.string.menu_sandbox), { onScreen(HomeScreen.Sandbox) }, Modifier.weight(1f),
                    paint = Wood.Umber, enabled = ready, size = BlockSize.Small)
            }
        }
    }
}

@Composable
internal fun worldName(entry: AtlasEntry): String =
    entry.name.ifBlank { stringResource(islandTitle(TerrainKey(entry.seed, entry.worldgen))) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NewWorldScreen(
    onCreate: (seed: Long, start: WorldStart, name: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable { mutableStateOf(IslandSeeds[1]) }
    var seedText by rememberSaveable { mutableStateOf("") }
    var startName by rememberSaveable { mutableStateOf(WorldStart.Empty.name) }
    var name by rememberSaveable { mutableStateOf("") }
    val typed = seedText.trim().toLongOrNull()
    val seed = typed ?: selected
    val start = WorldStart.valueOf(startName)
    val island = stringResource(islandTitle(TerrainKey(seed, WatershedWorldgenVersion)))
    MenuPage(stringResource(Res.string.new_world_title), onBack, modifier) {
        Text(stringResource(Res.string.new_world_island), style = MaterialTheme.typography.titleMedium, color = Wood.BeechLight)
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (choice in IslandSeeds) {
                val key = TerrainKey(choice, WatershedWorldgenVersion)
                WorldIslandChoice(key, selected == choice && typed == null, stringResource(islandTitle(key))) {
                    selected = choice
                    seedText = ""
                }
            }
        }
        Board(Modifier.widthIn(max = 480.dp).fillMaxWidth(), padding = 12.dp) {
            Text(stringResource(Res.string.sandbox_untold), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(seedText, { value -> seedText = value.filter { it.isDigit() || it == '-' }.take(19) },
                    Modifier.weight(1f), singleLine = true, label = { Text(stringResource(Res.string.sandbox_seed)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = woodFieldColors())
                PegButton(GameIcons.Dice, stringResource(Res.string.sandbox_roll), { seedText = Random.nextLong(1, 1_000_000_000L).toString() })
            }
            if (seedText.isNotBlank() && typed == null) Text(stringResource(Res.string.sandbox_invalid), style = MaterialTheme.typography.bodySmall)
            if (typed != null) IslandMiniMap(TerrainKey(typed, WatershedWorldgenVersion),
                Modifier.fillMaxWidth().aspectRatio(2f).clip(RoundedCornerShape(10.dp)))
        }
        Board(Modifier.widthIn(max = 480.dp).fillMaxWidth(), padding = 12.dp) {
            Text(stringResource(Res.string.new_world_life), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((option, label, detail) in listOf(
                    Triple(WorldStart.Empty, Res.string.start_empty, Res.string.start_empty_detail),
                    Triple(WorldStart.Wild, Res.string.start_wild, Res.string.start_wild_detail),
                )) {
                    Block(stringResource(label), { startName = option.name }, Modifier.weight(1f),
                        paint = if (start == option) Wood.Leaf else Wood.BeechLight, detail = stringResource(detail),
                        size = BlockSize.Small, selected = start == option)
                }
            }
            OutlinedTextField(name, { value -> name = value.take(32) }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(Res.string.new_world_name)) }, placeholder = { Text(island) }, colors = woodFieldColors())
        }
        Block(stringResource(Res.string.new_world_create), { onCreate(seed, start, name.trim().ifEmpty { island }) },
            enabled = seedText.isBlank() || typed != null, paint = Wood.Leaf, icon = GameIcons.Sparkle, sound = WorldSound.UiConfirm)
    }
}

@Composable
private fun woodFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Wood.Walnut, unfocusedTextColor = Wood.Walnut, focusedBorderColor = Wood.Leaf,
    unfocusedBorderColor = Wood.WalnutSoft, focusedLabelColor = Wood.Leaf, unfocusedLabelColor = Wood.WalnutSoft,
    cursorColor = Wood.Leaf,
)

@Composable
private fun WorldIslandChoice(key: TerrainKey, selected: Boolean, name: String, onSelect: () -> Unit) {
    val ui = LocalUi.current
    Board(Modifier.width(145.dp).selectable(selected, role = Role.RadioButton) { ui.click(); onSelect() },
        padding = 8.dp, gap = 6.dp, face = if (selected) Wood.Leaf else Wood.BeechLight) {
        IslandMiniMap(key, Modifier.fillMaxWidth().aspectRatio(1.45f))
        Text(name, style = MaterialTheme.typography.titleMedium, color = Wood.inkOn(if (selected) Wood.Leaf else Wood.BeechLight))
    }
}

@Composable
internal fun WorldsScreen(
    profile: PlayerProfile,
    onOpen: (AtlasEntry) -> Unit,
    onNew: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onImportCode: (suspend (String) -> WorldCodeImport)? = null,
    onOpenCode: (WorldCodeImport.Open) -> Unit = {},
) {
    var earlier by rememberSaveable { mutableStateOf(false) }
    MenuPage(stringResource(Res.string.worlds_title), onBack, modifier) {
        val worlds = profile.worlds
        if (worlds.isEmpty()) Board(Modifier.widthIn(max = 360.dp)) {
            Text(stringResource(Res.string.worlds_empty), color = Wood.WalnutSoft)
            Block(stringResource(Res.string.menu_new_world), onNew, paint = Wood.Leaf, icon = GameIcons.Sparkle, size = BlockSize.Small)
        }
        for (entry in worlds) WorldRow(entry) { onOpen(entry) }
        WorldCodeImportControl(onImportCode, onOpenCode)
        val older = profile.atlas.filter { it.rules < 12 }.sortedByDescending { it.order }
        if (older.isNotEmpty()) {
            Block(stringResource(if (earlier) Res.string.worlds_hide_earlier else Res.string.worlds_show_earlier), { earlier = !earlier },
                paint = Wood.BeechLight, size = BlockSize.Small, checked = earlier)
            if (earlier) {
                Board(Modifier.widthIn(max = 460.dp), padding = 10.dp) {
                    Text(stringResource(Res.string.worlds_earlier), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(Res.string.worlds_earlier_detail), style = MaterialTheme.typography.bodySmall, color = Wood.WalnutSoft)
                }
                for (entry in older) AtlasRow(entry, profile) { onOpen(entry) }
            }
        }
    }
}

@Composable
private fun WorldRow(entry: AtlasEntry, onOpen: () -> Unit) {
    val key = entry.key()
    Board(Modifier.widthIn(max = 460.dp).fillMaxWidth(), padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IslandMiniMap(key.terrain, Modifier.size(width = 86.dp, height = 78.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(worldName(entry), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(Res.string.worlds_line, dayOf(entry.tick), entry.branches,
                    stringResource(if (entry.start == WorldStart.Wild.code) Res.string.start_wild else Res.string.start_empty)),
                    style = MaterialTheme.typography.bodySmall, color = Wood.WalnutSoft)
            }
        }
        Block(stringResource(Res.string.worlds_open), onOpen, paint = Wood.Leaf, size = BlockSize.Small)
    }
}
