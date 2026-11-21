package id.branchworld.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import branchworld.shared.generated.resources.Res
import branchworld.shared.generated.resources.bank_dry
import branchworld.shared.generated.resources.bank_holding
import branchworld.shared.generated.resources.bank_recharged
import branchworld.shared.generated.resources.cairnwater
import branchworld.shared.generated.resources.cairnwater_opening
import branchworld.shared.generated.resources.dry_season
import branchworld.shared.generated.resources.fading_grove
import branchworld.shared.generated.resources.fen_outlet
import branchworld.shared.generated.resources.grove_opening
import branchworld.shared.generated.resources.grove_valley_opening
import branchworld.shared.generated.resources.heron_marsh
import branchworld.shared.generated.resources.heron_reach
import branchworld.shared.generated.resources.heron_reach_opening
import branchworld.shared.generated.resources.hollow_bend
import branchworld.shared.generated.resources.island_opening
import branchworld.shared.generated.resources.other_island
import branchworld.shared.generated.resources.pine_headwaters
import branchworld.shared.generated.resources.reed_estuary
import branchworld.shared.generated.resources.salt_headwaters
import branchworld.shared.generated.resources.stone_headwaters
import branchworld.shared.generated.resources.stone_lake
import branchworld.shared.generated.resources.tarn_outlet
import branchworld.shared.generated.resources.wet_season
import id.branchworld.game.ecology.Season
import id.branchworld.game.simulation.BankCondition
import id.branchworld.game.simulation.WatershedPose
import id.branchworld.game.terrain.IslandLandscape
import id.branchworld.game.terrain.LandmarkKind
import id.branchworld.game.terrain.TerrainKey
import id.branchworld.game.terrain.Watershed
import org.jetbrains.compose.resources.stringResource

internal fun islandTitle(key: TerrainKey) = when (key) {
    IslandGallery[0] -> Res.string.fading_grove
    IslandGallery[1] -> Res.string.cairnwater
    IslandGallery[2] -> Res.string.heron_reach
    else -> if (key.seed == IslandSeeds.first()) Res.string.fading_grove else Res.string.other_island
}

internal fun islandDescription(key: TerrainKey) = when (key) {
    IslandGallery[0] -> Res.string.grove_valley_opening
    IslandGallery[1] -> Res.string.cairnwater_opening
    IslandGallery[2] -> Res.string.heron_reach_opening
    else -> if (key.seed == IslandSeeds.first()) Res.string.grove_opening else Res.string.island_opening
}

@Composable
internal fun IslandPicker(selected: IslandSessionKey, enabled: Boolean, onSelect: (TerrainKey) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (island in IslandGallery) {
            OutlinedCard(
                onClick = { onSelect(island) }, enabled = enabled, modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.outlinedCardColors(containerColor = if (IslandSessionKey(island) == selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(stringResource(islandTitle(island)), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(islandDescription(island)), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
internal fun LandscapeGuide(watershed: Watershed, pose: WatershedPose, onFocus: (LandmarkKind) -> Unit) {
    var selected by rememberSaveable(watershed.landscape) { mutableStateOf(LandmarkKind.Basin.name) }
    val current = LandmarkKind.valueOf(selected)
    Text(stringResource(if (pose.season == Season.Dry) Res.string.dry_season else Res.string.wet_season),
        style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (landmark in watershed.landmarks) {
            FilterChip(selected = current == landmark.kind, onClick = {
                selected = landmark.kind.name
                onFocus(landmark.kind)
            }, label = { Text(stringResource(landmarkName(watershed.landscape, landmark.kind))) })
        }
    }
    val condition = when (pose.bankConditions[current]) {
        BankCondition.Parched -> Res.string.bank_dry
        BankCondition.Recharged -> Res.string.bank_recharged
        BankCondition.Holding -> Res.string.bank_holding
        null -> return
    }
    Text(stringResource(condition), style = MaterialTheme.typography.bodySmall)
}

private fun landmarkName(landscape: IslandLandscape, kind: LandmarkKind) = when (kind) {
    LandmarkKind.Headwaters -> when (landscape) {
        IslandLandscape.Valley -> Res.string.pine_headwaters
        IslandLandscape.Tarn -> Res.string.stone_headwaters
        IslandLandscape.Marsh -> Res.string.salt_headwaters
    }
    LandmarkKind.Basin -> when (landscape) {
        IslandLandscape.Valley -> Res.string.hollow_bend
        IslandLandscape.Tarn -> Res.string.stone_lake
        IslandLandscape.Marsh -> Res.string.heron_marsh
    }
    LandmarkKind.Estuary -> when (landscape) {
        IslandLandscape.Valley -> Res.string.reed_estuary
        IslandLandscape.Tarn -> Res.string.tarn_outlet
        IslandLandscape.Marsh -> Res.string.fen_outlet
    }
}
