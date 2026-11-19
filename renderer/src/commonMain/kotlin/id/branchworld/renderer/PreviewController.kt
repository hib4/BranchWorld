package id.branchworld.renderer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal class PreviewController {
    val active = MutableStateFlow(false)
    val paused = MutableStateFlow(false)
    val camera = MutableStateFlow(PreviewCamera())
    val status = MutableStateFlow("Starting renderer…")
    val enteredAt = MutableStateFlow<TimeMark?>(null)
    val foreground = MutableStateFlow(true)

    fun setActive(active: Boolean) {
        if (active && !this.active.value) {
            enteredAt.value = TimeSource.Monotonic.markNow()
        }
        this.active.value = active
    }

    fun orbit(dx: Float, dy: Float) = camera.update { it.orbit(dx, dy) }
    fun pan(dx: Float, dy: Float) = camera.update { it.pan(dx, dy) }
    fun zoom(factor: Float) = camera.update { it.zoom(factor) }
    fun focus() { camera.value = PreviewCamera() }
}

@Composable
internal fun PreviewHud(controller: PreviewController) {
    val status by controller.status.collectAsState()
    val paused by controller.paused.collectAsState()
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(status, style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = controller::focus) { Text("Focus island") }
                OutlinedButton(onClick = { controller.paused.update { !it } }) {
                    Text(if (paused) "Resume animation" else "Pause animation")
                }
            }
        }
    }
}
