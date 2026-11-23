package id.branchworld.game.ui.world.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import branchworld.shared.generated.resources.*
import id.branchworld.game.domain.species
import id.branchworld.game.ui.components.Block
import id.branchworld.game.ui.components.BlockSize
import id.branchworld.game.ui.components.Board
import id.branchworld.game.ui.components.PegButton
import id.branchworld.game.ui.legacy.label
import id.branchworld.game.ui.theme.GameIcons
import id.branchworld.game.ui.theme.Wood
import id.branchworld.game.ui.world.dayLabel
import id.branchworld.game.ui.world.inspector.label
import id.branchworld.game.ui.world.inspector.plural
import id.branchworld.game.ui.world.inspector.wildlifeName
import id.branchworld.game.ui.world.timeline.label
import id.branchworld.simulation.ecology.Species
import id.branchworld.simulation.ecology.environment.DayLength
import id.branchworld.simulation.ecology.life.PlacementCheck
import id.branchworld.simulation.ecology.life.Sex
import id.branchworld.simulation.projection.CreaturePose
import id.branchworld.simulation.projection.CreatureThought
import id.branchworld.simulation.projection.PlaceTool
import id.branchworld.simulation.projection.ThoughtKind
import id.branchworld.simulation.projection.WorldRenderProjection
import id.branchworld.simulation.runtime.WorldSpeed
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// ----- the title -----

// ----- making a world -----

// ----- the player's worlds -----

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
