package id.branchworld.game.ui.world.hud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import branchworld.shared.generated.resources.*
import branchworld.shared.generated.resources.Res
import id.branchworld.simulation.ecology.life.PlacementCheck
import id.branchworld.simulation.projection.PlaceTool
import org.jetbrains.compose.resources.stringResource

/** Words the tap handler needs outside composition, read once per composition. */
internal class HandMessages(
    val erase: String,
    val busy: String,
    private val checks: Map<Pair<PlacementCheck, PlaceTool>, String>,
    private val experimentFormat: (Int) -> String,
) {
    fun of(check: PlacementCheck, tool: PlaceTool) = checks[check to tool] ?: checks[check to PlaceTool.Rabbit].orEmpty()
    fun experiment(number: Int) = experimentFormat(number)

    companion object {
        @Composable
        fun current(): HandMessages {
            val checks = buildMap {
                for (check in PlacementCheck.entries) for (tool in PlaceTool.entries) put(check to tool, placementMessage(check, tool))
            }
            val format = stringResource(Res.string.experiment_name, 999)
            return HandMessages(stringResource(Res.string.tool_hint_erase), stringResource(Res.string.place_busy), checks) { n ->
                format.replace("999", n.toString())
            }
        }
    }
}
