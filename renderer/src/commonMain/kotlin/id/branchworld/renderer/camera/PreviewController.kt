package id.branchworld.renderer.camera

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import id.branchworld.simulation.projection.WorldCamera
import id.branchworld.simulation.projection.WorldPick
import id.branchworld.simulation.projection.WorldRenderSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
        val before = heroCamera(aspect.value, opening, renderWorld?.terrain)
        val after = heroCamera(ratio, opening, renderWorld?.terrain)
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
            val target = heroCamera(aspect.value, request.opening, renderWorld?.terrain)
            if (previous.opening) {
                // Carry the player's orbit and zoom through entering the chapter.
                val before = heroCamera(aspect.value, true, renderWorld?.terrain)
                camera.update { it.copy(targetX = it.targetX + target.targetX - before.targetX,
                    targetZ = it.targetZ + target.targetZ - before.targetZ) }
            } else camera.value = target
        }
        if (previous.focus != request.focus || previous.focusRequest != request.focusRequest) {
            focusAnimation?.cancel()
            _tracking.value = request.focus != null
        }
    }

    val status = MutableStateFlow<ViewportStatus>(ViewportStatus.Starting)
    val enteredAt = MutableStateFlow<TimeMark?>(null)
    val foreground = MutableStateFlow(true)

    /** A world is shown, in front of the player, and its island is fully drawn: a photo would show what they see. */
    val readyForPhoto: Boolean get() = active && foreground.value && status.value == ViewportStatus.Ready

    fun showWorld(projection: WorldRenderSource?) {
        if (projection != null && projection !== world.value) {
            camera.value = heroCamera(aspect.value, presentation.value.opening, projection.terrain)
            enteredAt.value = TimeSource.Monotonic.markNow()
            status.value = ViewportStatus.Loading
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

    /** What lies under a pixel of a [width]×[height] view of the island. */
    fun pick(x: Float, y: Float, width: Float, height: Float, radius: Float): WorldPick? {
        val world = renderWorld ?: return null
        if (width < 2f || height < 2f) return null
        return pickWorld(camera.value, width, height, x, y, world.terrain, world.poses.value.creatures, radius)
    }

    /** A tap or click that did not turn into a drag. The interface decides what it means. */
    fun tap(x: Float, y: Float, width: Float, height: Float, radius: Float) {
        val world = renderWorld ?: return
        pick(x, y, width, height, radius)?.let(world.pointer::tap)
    }

    /** The pointer resting over the island, or null when it leaves. */
    fun hover(x: Float?, y: Float?, width: Float, height: Float, radius: Float) {
        val world = renderWorld ?: return
        world.pointer.hover(if (x == null || y == null) null else pick(x, y, width, height, radius))
    }

    fun focus(scope: CoroutineScope): Job {
        focusAnimation?.cancel()
        val start = camera.value
        _tracking.value = false
        val target = heroCamera(aspect.value, presentation.value.opening, renderWorld?.terrain)
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
