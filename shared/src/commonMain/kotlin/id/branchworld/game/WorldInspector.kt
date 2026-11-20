package id.branchworld.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import branchworld.shared.generated.resources.Res
import branchworld.shared.generated.resources.animal_activity
import branchworld.shared.generated.resources.animal_drinking
import branchworld.shared.generated.resources.animal_eating
import branchworld.shared.generated.resources.animal_idle
import branchworld.shared.generated.resources.animal_label
import branchworld.shared.generated.resources.animal_needs
import branchworld.shared.generated.resources.animal_resting
import branchworld.shared.generated.resources.animal_walking
import branchworld.shared.generated.resources.close_inspector
import branchworld.shared.generated.resources.commands_queued
import branchworld.shared.generated.resources.inspect_animal
import branchworld.shared.generated.resources.inspect_world
import branchworld.shared.generated.resources.no_forage_deficit
import branchworld.shared.generated.resources.no_planting_sites
import branchworld.shared.generated.resources.no_wildlife
import branchworld.shared.generated.resources.plants_applied
import branchworld.shared.generated.resources.reforest_region
import branchworld.shared.generated.resources.region_details
import branchworld.shared.generated.resources.region_instructions
import branchworld.shared.generated.resources.species_deer
import branchworld.shared.generated.resources.species_rabbit
import branchworld.shared.generated.resources.supplement_applied
import branchworld.shared.generated.resources.supplement_forage
import id.branchworld.game.ecology.Creature
import id.branchworld.game.ecology.CreatureAction
import id.branchworld.game.ecology.Intervention
import id.branchworld.game.ecology.RegionWidth
import id.branchworld.game.ecology.Species
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun WorldInspector(island: IslandLoad.Ready) {
    var open by remember(island) { mutableStateOf(false) }
    OutlinedButton(onClick = { open = !open }) {
        Text(stringResource(if (open) Res.string.close_inspector else Res.string.inspect_world))
    }
    if (open) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.large,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RegionalControls(island)
                val pose by island.clock.render.collectAsStateWithLifecycle()
                val creatures = remember(island, pose.branchId, pose.tick) { island.clock.state.creatures }
                key(pose.branchId) { CreatureInspector(creatures) }
            }
        }
    }
}

@Composable
private fun RegionalControls(island: IslandLoad.Ready) {
    val pose by island.clock.render.collectAsStateWithLifecycle()
    val feedback by island.interventions.collectAsStateWithLifecycle()
    val timeline by island.timelineView.collectAsStateWithLifecycle()
    val regions = remember(island, pose.branchId, pose.tick) { island.inspectRegions() }
    if (regions.isEmpty()) return
    var selected by remember(island) {
        mutableStateOf(listOf(6, 5, 9, 10).firstOrNull { regions[it].canPlant } ?: 6)
    }
    Text(stringResource(Res.string.region_instructions))
    Column(Modifier.widthIn(max = 360.dp)) {
        repeat(RegionWidth) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(RegionWidth) { column ->
                    val region = row * RegionWidth + column
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = region == selected,
                        onClick = { selected = region },
                        label = { Text(regionName(region)) },
                    )
                }
            }
        }
    }
    val region = regions[selected]
    Text(stringResource(Res.string.region_details, regionName(selected), region.forage, region.saplings))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { island.intervene(Intervention.Reforest, selected) },
            enabled = region.canPlant && timeline.editsAllowed,
        ) { Text(stringResource(Res.string.reforest_region)) }
        OutlinedButton(
            onClick = { island.intervene(Intervention.Supplement, selected) },
            enabled = region.canSupplement && timeline.editsAllowed,
        ) { Text(stringResource(Res.string.supplement_forage)) }
    }
    if (!region.canPlant) Text(stringResource(Res.string.no_planting_sites))
    if (!region.canSupplement) Text(stringResource(Res.string.no_forage_deficit))
    val live = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    if (feedback.queued > 0) Text(stringResource(Res.string.commands_queued, feedback.queued), live)
    for (result in feedback.applied) {
        val message = when (result.kind) {
            Intervention.Reforest -> Res.string.plants_applied
            Intervention.Supplement -> Res.string.supplement_applied
        }
        Text(stringResource(message, result.tick.toString(), regionName(result.region), result.amount), live)
    }
}

@Composable
private fun CreatureInspector(creatures: List<Creature>) {
    if (creatures.isEmpty()) {
        Text(stringResource(Res.string.no_wildlife))
        return
    }
    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Long?>(null) }
    val creature = creatures.firstOrNull { it.id == selected }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(if (creature == null) stringResource(Res.string.inspect_animal) else creature.label())
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (option in creatures) {
                DropdownMenuItem(
                    text = { Text(option.label()) },
                    onClick = {
                        selected = option.id
                        expanded = false
                    },
                )
            }
        }
    }
    if (creature != null) {
        val activity = stringResource(when (creature.action) {
            CreatureAction.Idle -> Res.string.animal_idle
            CreatureAction.Walking -> Res.string.animal_walking
            CreatureAction.Eating -> Res.string.animal_eating
            CreatureAction.Drinking -> Res.string.animal_drinking
            CreatureAction.Resting -> Res.string.animal_resting
        })
        Text(stringResource(Res.string.animal_activity, activity, creature.cell.x, creature.cell.z))
        Text(stringResource(Res.string.animal_needs, reserve(creature.food), reserve(creature.water), reserve(creature.energy)))
    }
}

@Composable
private fun Creature.label(): String {
    val name = stringResource(if (species == Species.Rabbit) Res.string.species_rabbit else Res.string.species_deer)
    return stringResource(Res.string.animal_label, name, id.toString())
}

private fun regionName(region: Int) = "${'A' + region / RegionWidth}${region % RegionWidth + 1}"

private fun reserve(value: Int) = "${value / 10}%"
