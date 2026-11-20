package id.branchworld.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import branchworld.shared.generated.resources.Res
import branchworld.shared.generated.resources.ecology_animals
import branchworld.shared.generated.resources.ecology_forage
import branchworld.shared.generated.resources.ecology_turnover
import branchworld.shared.generated.resources.pause_world
import branchworld.shared.generated.resources.play_world
import branchworld.shared.generated.resources.steer_left
import branchworld.shared.generated.resources.steer_right
import branchworld.shared.generated.resources.world_speed
import branchworld.shared.generated.resources.world_tick
import id.branchworld.game.simulation.WorldClock
import id.branchworld.game.simulation.WorldSpeed
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun WorldControls(clock: WorldClock<*>) {
    val playback by clock.playback.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            Button(onClick = clock::togglePlaying) {
                Text(stringResource(if (playback.playing) Res.string.pause_world else Res.string.play_world))
            }
            SingleChoiceSegmentedButtonRow {
                for ((index, speed) in WorldSpeed.entries.withIndex()) {
                    SegmentedButton(
                        selected = playback.speed == speed,
                        onClick = { clock.setSpeed(speed) },
                        shape = SegmentedButtonDefaults.itemShape(index, WorldSpeed.entries.size),
                        icon = {},
                        label = { Text(stringResource(Res.string.world_speed, speed.multiplier)) },
                    )
                }
            }
            WorldTick(clock)
        }
        WildlifeControls(clock)
    }
}

@Composable
private fun WorldTick(clock: WorldClock<*>) {
    val pose by clock.render.collectAsStateWithLifecycle()
    Text(stringResource(Res.string.world_tick, pose.tick.toString()), style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun WildlifeControls(clock: WorldClock<*>) {
    val world by clock.render.collectAsStateWithLifecycle()
    val canSteer = world.leadRabbit != null
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { clock.steer(left = true) }, enabled = canSteer && clock.canSteerLeft) {
            Text(stringResource(Res.string.steer_left))
        }
        OutlinedButton(onClick = { clock.steer(left = false) }, enabled = canSteer && clock.canSteerRight) {
            Text(stringResource(Res.string.steer_right))
        }
    }
    world.ecology?.let { ecology ->
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val style = MaterialTheme.typography.labelLarge
            Text(stringResource(Res.string.ecology_animals, ecology.rabbits, ecology.deer), style = style)
            Text(stringResource(Res.string.ecology_forage, ecology.forage), style = style)
            Text(stringResource(Res.string.ecology_turnover, ecology.births.toString(), ecology.deaths.toString()), style = style)
        }
    }
}
