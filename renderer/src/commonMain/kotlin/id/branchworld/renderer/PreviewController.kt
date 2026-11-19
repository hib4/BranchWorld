package id.branchworld.renderer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.branchworld.game.simulation.WorldRenderSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal class PreviewController {
    private val world = MutableStateFlow<WorldRenderSource?>(null)
    val renderWorld: WorldRenderSource? get() = world.value
    val active: Boolean get() = world.value != null
    val camera = MutableStateFlow(PreviewCamera())
    val status = MutableStateFlow("Starting renderer…")
    val enteredAt = MutableStateFlow<TimeMark?>(null)
    val foreground = MutableStateFlow(true)

    fun showWorld(projection: WorldRenderSource?) {
        if (projection != null && projection !== world.value) {
            enteredAt.value = TimeSource.Monotonic.markNow()
            status.value = "Loading island…"
        }
        world.value = projection
    }

    fun orbit(dx: Float, dy: Float) = camera.update { it.orbit(dx, dy) }
    fun pan(dx: Float, dy: Float) = camera.update { it.pan(dx, dy) }
    fun zoom(factor: Float) = camera.update { it.zoom(factor) }
    fun focus() { camera.value = PreviewCamera() }
}

@Composable
internal fun PreviewHud(controller: PreviewController) {
    val status by controller.status.collectAsState()
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(status, style = MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick = controller::focus) { Text("Focus island") }
        }
    }
}
