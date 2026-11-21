package id.branchworld.renderer

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.branchworld.game.simulation.WorldRenderSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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

    private var focusAnimation: Job? = null

    fun orbit(dx: Float, dy: Float) {
        focusAnimation?.cancel()
        camera.update { it.orbit(dx, dy) }
    }

    fun pan(dx: Float, dy: Float) {
        focusAnimation?.cancel()
        camera.update { it.pan(dx, dy) }
    }

    fun zoom(factor: Float) {
        focusAnimation?.cancel()
        camera.update { it.zoom(factor) }
    }

    fun focus(scope: CoroutineScope): Job {
        focusAnimation?.cancel()
        val start = camera.value
        val target = PreviewCamera()
        return scope.launch {
            animate(0f, 1f, animationSpec = tween(180, easing = FastOutSlowInEasing)) { progress, _ ->
                camera.value = start.toward(target, progress)
            }
            camera.value = target
        }.also { focusAnimation = it }
    }
}

@Composable
internal fun PreviewHud(controller: PreviewController) {
    val status by controller.status.collectAsState()
    val motion = rememberCoroutineScope()
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(status, style = MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick = { controller.focus(motion) }) { Text("Focus island") }
        }
    }
}
