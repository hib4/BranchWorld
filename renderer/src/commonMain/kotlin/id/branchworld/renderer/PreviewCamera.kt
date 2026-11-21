package id.branchworld.renderer

import kotlin.math.cos
import kotlin.math.sin

internal data class PreviewCamera(
    val yaw: Float = 0.7f,
    val pitch: Float = 0.57f,
    val distance: Float = 12f,
    val targetX: Float = 0f,
    val targetZ: Float = 0f,
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

private const val Tau = 6.2831855f
