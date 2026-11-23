package id.branchworld.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import branchworld.shared.generated.resources.*
import id.branchworld.game.ecology.DayLength
import id.branchworld.game.ecology.PlacementCheck
import id.branchworld.game.ecology.Sex
import id.branchworld.game.ecology.Species
import id.branchworld.game.ecology.WorldStart
import id.branchworld.game.save.AtlasEntry
import id.branchworld.game.save.PlayerProfile
import id.branchworld.game.simulation.CreaturePose
import id.branchworld.game.simulation.CreatureThought
import id.branchworld.game.simulation.PlaceTool
import id.branchworld.game.simulation.ThoughtKind
import id.branchworld.game.simulation.WorldRenderProjection
import id.branchworld.game.simulation.WorldSpeed
import id.branchworld.game.terrain.TerrainKey
import id.branchworld.game.terrain.WatershedWorldgenVersion
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.random.Random

// ----- the title -----

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

// ----- making a world -----

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

// ----- the player's worlds -----

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

// ----- in the world -----

internal val PlaceTool.icon: ImageVector get() = when (this) {
    PlaceTool.Inspect -> GameIcons.Eye
    PlaceTool.Rabbit -> GameIcons.Rabbit
    PlaceTool.Wolf -> GameIcons.Wolf
    PlaceTool.Deer -> GameIcons.Deer
    PlaceTool.Fox -> GameIcons.Fox
    PlaceTool.Tree -> GameIcons.Pine
    PlaceTool.Meadow -> GameIcons.Grass
    PlaceTool.Eraser -> GameIcons.Lift
}

internal val PlaceTool.label: StringResource get() = when (this) {
    PlaceTool.Inspect -> Res.string.tool_inspect
    PlaceTool.Rabbit -> Res.string.tool_rabbit
    PlaceTool.Wolf -> Res.string.tool_wolf
    PlaceTool.Deer -> Res.string.tool_deer
    PlaceTool.Fox -> Res.string.tool_fox
    PlaceTool.Tree -> Res.string.tool_tree
    PlaceTool.Meadow -> Res.string.tool_meadow
    PlaceTool.Eraser -> Res.string.tool_eraser
}

internal val PlaceTool.species: Species? get() = when (this) {
    PlaceTool.Rabbit -> Species.Rabbit
    PlaceTool.Wolf -> Species.Wolf
    PlaceTool.Deer -> Species.Deer
    PlaceTool.Fox -> Species.Fox
    else -> null
}

internal val PlaceTool.places get() = this != PlaceTool.Inspect && this != PlaceTool.Eraser

internal val Species.icon: ImageVector get() = when (this) {
    Species.Rabbit -> GameIcons.Rabbit
    Species.Wolf -> GameIcons.Wolf
    Species.Deer -> GameIcons.Deer
    Species.Fox -> GameIcons.Fox
}

/** The world's name, the time of day and who lives here, as small painted pegs. */
@Composable
internal fun LivingTopBoard(title: String, pose: WorldRenderProjection?, wide: Boolean, modifier: Modifier = Modifier) {
    Board(modifier.widthIn(max = 360.dp), padding = 12.dp, gap = 6.dp) {
        Text(title, Modifier.semantics { heading() },
            style = if (wide) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge, maxLines = 1)
        val census = pose?.life?.census.orEmpty()
        pose?.let { Text(dayLabel(it.tick, it.climate?.dayPhase), style = MaterialTheme.typography.bodySmall, color = Wood.WalnutSoft) }
        if (census.values.all { it == 0 }) {
            Text(stringResource(Res.string.hud_empty_world), style = MaterialTheme.typography.bodySmall, color = Wood.WalnutSoft)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (species in Species.entries) {
                    val count = census[species] ?: 0
                    if (count == 0) continue
                    val label = "${species.plural()} $count"
                    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Wood.BeechLight).padding(horizontal = 8.dp, vertical = 3.dp)
                        .semantics(mergeDescendants = true) { contentDescription = label },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(species.icon, null, Modifier.size(16.dp), tint = Wood.Walnut)
                        Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = Wood.Walnut)
                    }
                }
            }
        }
    }
}

/** Play, pause and how fast time runs. */
@Composable
internal fun TimeControls(playing: Boolean, speed: WorldSpeed, enabled: Boolean, onToggle: () -> Unit, onSpeed: (WorldSpeed) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        PegButton(if (playing) GameIcons.Pause else GameIcons.Play,
            stringResource(if (playing) Res.string.pause_world else Res.string.play_world), onToggle, enabled)
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (option in WorldSpeed.entries) {
                val label = stringResource(option.label)
                Block(label, { onSpeed(option) }, Modifier.semantics { contentDescription = "Speed $label" },
                    paint = if (option == speed) Wood.Woad else Wood.BeechLight, size = BlockSize.Compact, selected = option == speed,
                    enabled = enabled)
            }
        }
    }
}

private val WorldSpeed.label get() = when (this) {
    WorldSpeed.Normal -> Res.string.speed_1
    WorldSpeed.Double -> Res.string.speed_2
    WorldSpeed.Quadruple -> Res.string.speed_4
    WorldSpeed.Octuple -> Res.string.speed_8
}

/** The player's hand: wooden pegs for every animal, plant and the lifting hand. One is always chosen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlacePalette(tool: PlaceTool, compact: Boolean, onTool: (PlaceTool) -> Unit) {
    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (option in PlaceTool.entries) {
            val chosen = option == tool
            Block(stringResource(option.label), { onTool(option) }, icon = option.icon, iconOnly = compact,
                paint = when {
                    !chosen -> Wood.BeechLight
                    option == PlaceTool.Eraser -> Wood.Madder
                    option == PlaceTool.Inspect -> Wood.Woad
                    else -> Wood.Leaf
                }, size = BlockSize.Compact, selected = chosen)
        }
    }
}

/** What the chosen tool does, said once in plain words above the dock. */
@Composable
internal fun ToolHint(tool: PlaceTool): String = when {
    tool.species != null -> stringResource(Res.string.tool_hint_place, checkNotNull(tool.species).label().lowercase())
    tool == PlaceTool.Tree -> stringResource(Res.string.tool_hint_plant, stringResource(Res.string.plant_tree))
    tool == PlaceTool.Meadow -> stringResource(Res.string.tool_hint_plant, stringResource(Res.string.plant_meadow))
    tool == PlaceTool.Eraser -> stringResource(Res.string.tool_hint_erase)
    else -> stringResource(Res.string.tool_hint_inspect)
}

@Composable
internal fun placementMessage(check: PlacementCheck, tool: PlaceTool): String = when (check) {
    PlacementCheck.Ok -> ""
    PlacementCheck.Water -> stringResource(Res.string.place_water)
    PlacementCheck.Unreachable -> stringResource(Res.string.place_unreachable)
    PlacementCheck.Unsuitable -> stringResource(Res.string.place_unsuitable)
    PlacementCheck.Occupied -> stringResource(Res.string.place_occupied)
    PlacementCheck.Burning -> stringResource(Res.string.place_burning)
    PlacementCheck.SpeciesFull -> stringResource(Res.string.place_species_full, tool.species?.plural()?.lowercase().orEmpty())
    PlacementCheck.IslandFull -> stringResource(Res.string.place_island_full)
    PlacementCheck.PlantsFull -> stringResource(Res.string.place_plants_full)
    PlacementCheck.NotLiving -> stringResource(Res.string.place_not_living)
}

/** A short note that fades on its own: why a placement could not happen, or what the tool does. */
@Composable
internal fun HandNote(text: String, modifier: Modifier = Modifier) {
    Board(modifier.widthIn(max = 360.dp), padding = 10.dp) {
        Text(text, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Changing a past moment begins a new history beside the one the player left. */
@Composable
internal fun PastEditPrompt(day: Int, onBranch: () -> Unit, onNow: () -> Unit, modifier: Modifier = Modifier) {
    Board(modifier.widthIn(max = 400.dp), padding = 14.dp) {
        Text(stringResource(Res.string.past_title, day), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(stringResource(Res.string.past_detail), style = MaterialTheme.typography.bodySmall, color = Wood.WalnutSoft)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Block(stringResource(Res.string.past_branch), onBranch, Modifier.weight(1f), paint = Wood.Woad, icon = GameIcons.Branches, size = BlockSize.Small)
            Block(stringResource(Res.string.past_now), onNow, Modifier.weight(1f), paint = Wood.BeechLight, size = BlockSize.Small)
        }
    }
}

/** One creature, as the player picked it out of the world: who it is, what it is doing and how it is faring. */
@Composable
internal fun CreatureCard(
    creature: CreaturePose,
    tick: Long,
    packSize: Int,
    following: Boolean,
    canRemove: Boolean,
    onFollow: () -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val life = creature.life
    Board(modifier.widthIn(max = 340.dp), padding = 12.dp, gap = 6.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(creature.species.icon, null, Modifier.size(26.dp), tint = Wood.Walnut)
            Column(Modifier.weight(1f)) {
                Text(wildlifeName(creature.id), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                if (life != null) {
                    val days = ((tick - life.bornAt) / DayLength).toInt()
                    Text(stringResource(Res.string.card_identity, creature.species.label(),
                        stringResource(if (life.sex == Sex.Female) Res.string.sex_female else Res.string.sex_male),
                        if (days == 0 && !life.placed) stringResource(Res.string.card_age_young) else stringResource(Res.string.card_age_days, days)),
                        style = MaterialTheme.typography.bodySmall, color = Wood.WalnutSoft)
                }
            }
            PegButton(GameIcons.Close, stringResource(Res.string.card_close), onClose)
        }
        creature.thought?.let { Text(thoughtLine(it), style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        if (life != null) {
            Meter(stringResource(Res.string.card_food), life.food)
            Meter(stringResource(Res.string.card_water), life.water)
            Meter(stringResource(Res.string.card_energy), life.energy)
            Meter(stringResource(Res.string.card_stamina), life.stamina)
            val facts = buildList {
                add(stringResource(if (life.placed) Res.string.card_placed else Res.string.card_born))
                if (creature.species == Species.Wolf) add(if (life.packId != 0) stringResource(Res.string.card_pack, packSize) else stringResource(Res.string.card_alone))
                if (life.kills > 0) add(stringResource(Res.string.card_kills, life.kills))
                if (life.litters > 0) add(stringResource(Res.string.card_litters, life.litters))
                if (life.carrying) add(stringResource(Res.string.card_carrying))
            }
            Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Wood.WalnutSoft)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Block(stringResource(if (following) Res.string.card_following else Res.string.card_follow), onFollow, Modifier.weight(1f),
                paint = if (following) Wood.Woad else Wood.BeechLight, icon = GameIcons.Eye, size = BlockSize.Small, checked = following)
            if (canRemove) Block(stringResource(Res.string.card_remove), onRemove, Modifier.weight(1f), paint = Wood.BeechLight,
                icon = GameIcons.Lift, size = BlockSize.Small)
        }
    }
}

@Composable
private fun Meter(label: String, value: Int) {
    val fraction = (value / 1000f).coerceIn(0f, 1f)
    val paint = when {
        fraction < 0.2f -> Wood.Madder
        fraction < 0.45f -> Wood.Weld
        else -> Wood.Leaf
    }
    Row(Modifier.semantics(mergeDescendants = true) { contentDescription = "$label ${(fraction * 100).toInt()}%" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.width(64.dp), style = MaterialTheme.typography.labelMedium, color = Wood.WalnutSoft)
        Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Wood.BeechEdge.copy(alpha = 0.45f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(RoundedCornerShape(5.dp)).background(paint))
        }
    }
}

/** What a creature is doing, in a sentence that names the other animal when there is one. */
@Composable
internal fun thoughtLine(thought: CreatureThought): String {
    val other = thought.subjectId?.let(::wildlifeName)
    return when (thought.kind) {
        ThoughtKind.Arriving -> stringResource(Res.string.thought_arriving)
        ThoughtKind.Grazing -> stringResource(Res.string.thought_grazing)
        ThoughtKind.Browsing -> stringResource(Res.string.thought_browsing)
        ThoughtKind.Drinking -> stringResource(Res.string.thought_drinking)
        ThoughtKind.Resting -> stringResource(Res.string.thought_resting)
        ThoughtKind.Sleeping -> stringResource(Res.string.thought_sleeping)
        ThoughtKind.Wandering -> stringResource(Res.string.thought_wandering)
        ThoughtKind.Watchful -> stringResource(Res.string.thought_watchful)
        ThoughtKind.Alarmed -> if (other != null) stringResource(Res.string.thought_alarmed_by, other) else stringResource(Res.string.thought_alarmed)
        ThoughtKind.Fleeing -> if (other != null) stringResource(Res.string.thought_fleeing_from, other) else stringResource(Res.string.thought_fleeing)
        ThoughtKind.Hiding -> if (other != null) stringResource(Res.string.thought_hiding_from, other) else stringResource(Res.string.thought_hiding)
        ThoughtKind.FleeingFire -> stringResource(Res.string.thought_fleeing_fire)
        ThoughtKind.Stalking -> stringResource(if (thought.withPack) Res.string.thought_stalking_pack else Res.string.thought_stalking, other.orEmpty())
        ThoughtKind.Chasing -> stringResource(if (thought.withPack) Res.string.thought_chasing_pack else Res.string.thought_chasing, other.orEmpty())
        ThoughtKind.Hunting -> stringResource(Res.string.thought_hunting)
        ThoughtKind.Feeding -> stringResource(Res.string.thought_feeding)
        ThoughtKind.LookingForMate -> stringResource(Res.string.thought_mate)
        ThoughtKind.Courting -> stringResource(Res.string.thought_courting)
        ThoughtKind.Digging -> stringResource(Res.string.thought_digging)
        ThoughtKind.Howling -> stringResource(Res.string.thought_howling)
        ThoughtKind.Patrolling -> stringResource(Res.string.thought_patrolling)
        ThoughtKind.Regrouping -> stringResource(Res.string.thought_regrouping)
        ThoughtKind.ReturningToDen -> stringResource(Res.string.thought_den)
        ThoughtKind.SeekingCompany -> stringResource(Res.string.thought_company)
        ThoughtKind.Thirsty -> stringResource(Res.string.thought_thirsty)
        ThoughtKind.Hungry -> stringResource(Res.string.thought_hungry)
        ThoughtKind.Starving -> stringResource(Res.string.thought_starving)
    }
}
