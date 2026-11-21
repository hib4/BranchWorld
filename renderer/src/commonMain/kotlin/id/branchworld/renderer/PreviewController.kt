package id.branchworld.renderer

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.branchworld.game.simulation.WorldCamera
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
    val presentation = MutableStateFlow(WorldCamera())
    val aspect = MutableStateFlow(1f)
    val manualOrbit = MutableStateFlow(0L)
    private val _tracking = MutableStateFlow(false)
    val tracking: Boolean get() = _tracking.value

    fun resize(width: Int, height: Int) {
        if (width < 2 || height < 2) return
        val ratio = width.toFloat() / height
        val opening = presentation.value.opening
        val before = heroCamera(aspect.value, opening)
        val after = heroCamera(ratio, opening)
        aspect.value = ratio
        camera.update { pose ->
            pose.copy(
                distance = (pose.distance + after.distance - before.distance).coerceIn(3f, 18f),
                targetX = (pose.targetX + after.targetX - before.targetX).coerceIn(-5f, 5f),
                targetZ = (pose.targetZ + after.targetZ - before.targetZ).coerceIn(-5f, 5f),
            )
        }
    }

    fun present(request: WorldCamera) {
        val previous = presentation.value
        if (previous == request) return
        presentation.value = request
        if (previous.opening != request.opening) {
            val target = heroCamera(aspect.value, request.opening)
            if (previous.opening) {
                // Carry the player's orbit and zoom through entering the chapter.
                val before = heroCamera(aspect.value, true)
                camera.update { it.copy(targetX = it.targetX + target.targetX - before.targetX,
                    targetZ = it.targetZ + target.targetZ - before.targetZ) }
            } else camera.value = target
        }
        if (previous.focus != request.focus || previous.focusRequest != request.focusRequest) {
            focusAnimation?.cancel()
            _tracking.value = request.focus != null
        }
    }

    val status = MutableStateFlow("Starting renderer…")
    val enteredAt = MutableStateFlow<TimeMark?>(null)
    val foreground = MutableStateFlow(true)

    fun showWorld(projection: WorldRenderSource?) {
        if (projection != null && projection !== world.value) {
            camera.value = heroCamera(aspect.value, presentation.value.opening)
            enteredAt.value = TimeSource.Monotonic.markNow()
            status.value = "Loading island…"
        }
        world.value = projection
    }

    private var focusAnimation: Job? = null

    fun orbit(dx: Float, dy: Float) {
        focusAnimation?.cancel()
        manualOrbit.update { it + 1 }
        camera.update { it.orbit(dx, dy) }
    }

    fun pan(dx: Float, dy: Float) {
        focusAnimation?.cancel()
        _tracking.value = false
        camera.update { it.pan(dx, dy) }
    }

    fun zoom(factor: Float) {
        focusAnimation?.cancel()
        _tracking.value = false
        camera.update { it.zoom(factor) }
    }

    fun focus(scope: CoroutineScope): Job {
        focusAnimation?.cancel()
        val start = camera.value
        _tracking.value = false
        val target = heroCamera(aspect.value, presentation.value.opening)
        return scope.launch {
            if (presentation.value.reducedMotion) {
                camera.value = target
                return@launch
            }
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
    val opening by controller.presentation.collectAsState()
    if (opening.opening && status == "Island ready") return
    val motion = rememberCoroutineScope()
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 6.dp)) {
            if (status != "Island ready") Text(status, Modifier.padding(6.dp), style = MaterialTheme.typography.labelLarge)
            if (!opening.opening) TextButton(onClick = { controller.focus(motion) }) { Text("Focus island") }
        }
    }
}
