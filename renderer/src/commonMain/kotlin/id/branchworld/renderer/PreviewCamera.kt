package id.branchworld.renderer

import kotlin.math.cos
import kotlin.math.sin

internal data class PreviewCamera(
    val yaw: Float = 0.62f,
    val pitch: Float = 0.70f,
    val distance: Float = 8.05f,
    val targetX: Float = 0f,
    val targetZ: Float = 0f,
    val targetY: Float = 0.31f,
) {
    fun toward(target: PreviewCamera, fraction: Float): PreviewCamera {
        fun mix(start: Float, end: Float) = start + (end - start) * fraction
        val turn = (((target.yaw - yaw + Tau / 2f) % Tau + Tau) % Tau) - Tau / 2f
        return PreviewCamera(
            yaw = yaw + turn * fraction,
            pitch = mix(pitch, target.pitch),
            distance = mix(distance, target.distance),
            targetX = mix(targetX, target.targetX),
            targetZ = mix(targetZ, target.targetZ),
            targetY = mix(targetY, target.targetY),
        )
    }

    fun orbit(dx: Float, dy: Float): PreviewCamera = copy(
        yaw = (yaw - dx * 4f) % Tau,
        pitch = (pitch + dy * 3f).coerceIn(0.17f, 1.35f),
    )

    fun pan(dx: Float, dy: Float): PreviewCamera {
        val right = dx * distance
        val forward = dy * distance
        return copy(
            targetX = (targetX - right * cos(yaw) - forward * sin(yaw)).coerceIn(-5f, 5f),
            targetZ = (targetZ + right * sin(yaw) - forward * cos(yaw)).coerceIn(-5f, 5f),
        )
    }

    fun zoom(factor: Float): PreviewCamera =
        if (factor.isFinite() && factor > 0f) copy(distance = (distance / factor).coerceIn(3f, 18f)) else this
}

internal fun heroCamera(aspect: Float, opening: Boolean): PreviewCamera {
    val camera = PreviewCamera()
    val narrow = aspect < 0.83f
    val distance = camera.distance + if (narrow) ((0.83f - aspect) * 8f).coerceAtMost(3f) else 0f
    if (!opening) return camera.copy(distance = distance)
    return if (aspect > 1.3f) {
        // Shift the look-at point left so the foreground clears the chapter introduction.
        camera.copy(distance = distance, targetX = -1.75f * cos(camera.yaw), targetZ = 1.75f * sin(camera.yaw))
    } else {
        camera.copy(distance = distance, targetX = 0.95f * sin(camera.yaw), targetZ = 0.95f * cos(camera.yaw))
    }
}

private const val Tau = 6.2831855f
