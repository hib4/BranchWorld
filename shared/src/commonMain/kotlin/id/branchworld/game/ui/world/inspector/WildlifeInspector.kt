package id.branchworld.game.ui.world.inspector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import branchworld.shared.generated.resources.Res
import branchworld.shared.generated.resources.animal_drinking
import branchworld.shared.generated.resources.animal_eating
import branchworld.shared.generated.resources.animal_idle
import branchworld.shared.generated.resources.animal_label
import branchworld.shared.generated.resources.animal_resting
import branchworld.shared.generated.resources.animal_walking
import branchworld.shared.generated.resources.grove_focus_creature
import branchworld.shared.generated.resources.inspect_animal
import branchworld.shared.generated.resources.no_wildlife
import branchworld.shared.generated.resources.species_deer
import branchworld.shared.generated.resources.species_deer_plural
import branchworld.shared.generated.resources.species_fox
import branchworld.shared.generated.resources.species_foxes
import branchworld.shared.generated.resources.species_rabbit
import branchworld.shared.generated.resources.species_rabbits
import branchworld.shared.generated.resources.species_wolf
import branchworld.shared.generated.resources.species_wolves
import branchworld.shared.generated.resources.wildlife_age
import branchworld.shared.generated.resources.wildlife_alert
import branchworld.shared.generated.resources.wildlife_berries
import branchworld.shared.generated.resources.wildlife_birth
import branchworld.shared.generated.resources.wildlife_browse
import branchworld.shared.generated.resources.wildlife_caught
import branchworld.shared.generated.resources.wildlife_chase
import branchworld.shared.generated.resources.wildlife_court
import branchworld.shared.generated.resources.wildlife_dig
import branchworld.shared.generated.resources.wildlife_drink
import branchworld.shared.generated.resources.wildlife_feed
import branchworld.shared.generated.resources.wildlife_flee
import branchworld.shared.generated.resources.wildlife_forage
import branchworld.shared.generated.resources.wildlife_herd
import branchworld.shared.generated.resources.wildlife_hide
import branchworld.shared.generated.resources.wildlife_history
import branchworld.shared.generated.resources.wildlife_history_empty
import branchworld.shared.generated.resources.wildlife_history_line
import branchworld.shared.generated.resources.wildlife_howl
import branchworld.shared.generated.resources.wildlife_juvenile
import branchworld.shared.generated.resources.wildlife_location
import branchworld.shared.generated.resources.wildlife_meet
import branchworld.shared.generated.resources.wildlife_missing
import branchworld.shared.generated.resources.wildlife_name_species
import branchworld.shared.generated.resources.wildlife_placed
import branchworld.shared.generated.resources.wildlife_range
import branchworld.shared.generated.resources.wildlife_removed
import branchworld.shared.generated.resources.wildlife_scarcity
import branchworld.shared.generated.resources.wildlife_shelter
import branchworld.shared.generated.resources.wildlife_sleep
import branchworld.shared.generated.resources.wildlife_stalk
import branchworld.shared.generated.resources.wildlife_water
import id.branchworld.simulation.ecology.CreatureAction
import id.branchworld.simulation.ecology.Species
import id.branchworld.simulation.ecology.environment.DayLength
import id.branchworld.simulation.ecology.wildlife.WildlifeMilestone
import id.branchworld.simulation.projection.CreaturePose
import id.branchworld.simulation.projection.GroundPoint
import id.branchworld.simulation.projection.WildlifeIntent
import id.branchworld.simulation.projection.WildlifeMoment
import id.branchworld.simulation.projection.WorldRenderProjection
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun WildlifeInspector(
    pose: WorldRenderProjection,
    onCreatureFocus: (Long) -> Unit,
    onLocation: (GroundPoint) -> Unit,
    playback: @Composable () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    val creature = pose.creatures.firstOrNull { it.id == selected }
    val history = pose.wildlife?.moments
    val loss = if (creature == null) history?.lastOrNull { it.id == selected && it.kind in Losses } else null
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (pose.creatures.isNotEmpty()) {
            Column {
                OutlinedButton(onClick = { expanded = true }) {
                    Text(if (creature == null) stringResource(Res.string.inspect_animal) else creature.label(pose.wildlife != null))
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    for (option in pose.creatures) {
                        DropdownMenuItem(
                            text = { Text(option.label(pose.wildlife != null)) },
                            onClick = {
                                selected = option.id
                                expanded = false
                                onCreatureFocus(option.id)
                            },
                        )
                    }
                }
            }
        }
        if (history != null) {
            FilterChip(selected = historyOpen, onClick = { historyOpen = !historyOpen },
                label = { Text(stringResource(Res.string.wildlife_history)) })
        }
    }
    when {
        creature != null -> {
            Text(creature.activity(), style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onCreatureFocus(creature.id) }) { Text(stringResource(Res.string.grove_focus_creature)) }
                playback()
            }
        }
        loss != null -> {
            Text(loss.description(), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { onLocation(loss.ground) }) { Text(stringResource(Res.string.wildlife_location)) }
        }
        selected != null && history != null -> Text(stringResource(Res.string.wildlife_missing))
        pose.creatures.isEmpty() -> Text(stringResource(Res.string.no_wildlife))
    }
    if (historyOpen && history != null) {
        if (history.isEmpty()) Text(stringResource(Res.string.wildlife_history_empty), style = MaterialTheme.typography.bodySmall)
        for (moment in history.asReversed()) {
            TextButton(onClick = {
                selected = moment.id
                if (pose.creatures.any { it.id == moment.id }) onCreatureFocus(moment.id) else onLocation(moment.ground)
            }) { Text(moment.description()) }
        }
    }
}

@Composable
private fun CreaturePose.label(named: Boolean): String {
    val speciesName = species.label()
    if (!named) return stringResource(Res.string.animal_label, speciesName, id.toString())
    val age = if (juvenile) stringResource(Res.string.wildlife_juvenile, speciesName) else speciesName
    return stringResource(Res.string.wildlife_name_species, wildlifeName(id), age)
}

@Composable
internal fun WildlifeMoment.description(): String {
    val subject = wildlifeName(id)
    val action = stringResource(when (kind) {
        WildlifeMilestone.Birth -> Res.string.wildlife_birth
        WildlifeMilestone.NewRange -> Res.string.wildlife_range
        WildlifeMilestone.Caught -> Res.string.wildlife_caught
        WildlifeMilestone.Scarcity -> Res.string.wildlife_scarcity
        WildlifeMilestone.OldAge -> Res.string.wildlife_age
        WildlifeMilestone.Placed -> Res.string.wildlife_placed
        WildlifeMilestone.Removed -> Res.string.wildlife_removed
    }, subject)
    return stringResource(Res.string.wildlife_history_line, tick / DayLength + 1, action)
}

@Composable
internal fun Species.label() = stringResource(when (this) {
    Species.Rabbit -> Res.string.species_rabbit
    Species.Deer -> Res.string.species_deer
    Species.Fox -> Res.string.species_fox
    Species.Wolf -> Res.string.species_wolf
})

@Composable
internal fun Species.plural() = stringResource(when (this) {
    Species.Rabbit -> Res.string.species_rabbits
    Species.Deer -> Res.string.species_deer_plural
    Species.Fox -> Res.string.species_foxes
    Species.Wolf -> Res.string.species_wolves
})

@Composable
private fun CreaturePose.activity(): String = stringResource(when (action) {
    CreatureAction.Stalking -> Res.string.wildlife_stalk
    CreatureAction.Chasing -> Res.string.wildlife_chase
    CreatureAction.Fleeing -> if (intent == WildlifeIntent.Refuge && ground == destination)
        Res.string.wildlife_hide else Res.string.wildlife_flee
    CreatureAction.Drinking -> if (intent != null) Res.string.wildlife_drink else Res.string.animal_drinking
    CreatureAction.Resting -> Res.string.animal_resting
    CreatureAction.Eating -> when (intent) {
        WildlifeIntent.Browse -> Res.string.wildlife_browse
        WildlifeIntent.Berries -> Res.string.wildlife_berries
        WildlifeIntent.Roam -> if (species == Species.Fox) Res.string.wildlife_feed else Res.string.animal_eating
        else -> Res.string.animal_eating
    }
    CreatureAction.Walking -> when (intent) {
        WildlifeIntent.Water -> Res.string.wildlife_water
        WildlifeIntent.Shelter -> Res.string.wildlife_shelter
        WildlifeIntent.Forage, WildlifeIntent.Browse, WildlifeIntent.Berries -> Res.string.wildlife_forage
        WildlifeIntent.Range -> if (species == Species.Deer) Res.string.wildlife_herd else Res.string.animal_walking
        WildlifeIntent.Meet -> Res.string.wildlife_meet
        else -> Res.string.animal_walking
    }
    CreatureAction.Idle -> Res.string.animal_idle
    CreatureAction.Alert -> Res.string.wildlife_alert
    CreatureAction.Howling -> Res.string.wildlife_howl
    CreatureAction.Sleeping -> Res.string.wildlife_sleep
    CreatureAction.Digging -> Res.string.wildlife_dig
    CreatureAction.Courting -> Res.string.wildlife_court
})

private val Losses = setOf(WildlifeMilestone.Caught, WildlifeMilestone.Scarcity, WildlifeMilestone.OldAge)
private val Founders = listOf("Clover", "Bramble", "Pippin", "Thistle", "Alder", "Wren", "Fern", "Russet", "Ember")
private val Stems = listOf("Ash", "Beech", "Birch", "Briar", "Brook", "Fawn", "Frost", "Hazel", "Lark", "Meadow", "Mist", "Moss", "Rain", "Reed", "Rowan", "Willow")
private val Endings = listOf("bud", "foot", "leaf", "light", "root", "spring", "step", "tail")
internal fun wildlifeName(id: Long): String {
    if (id in 1L..Founders.size.toLong()) return Founders[id.toInt() - 1]
    val index = (id - Founders.size - 1).coerceAtLeast(0)
    return Stems[(index % Stems.size).toInt()] + Endings[(index / Stems.size % Endings.size).toInt()]
}
