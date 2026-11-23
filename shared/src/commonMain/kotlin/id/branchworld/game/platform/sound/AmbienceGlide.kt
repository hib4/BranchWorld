package id.branchworld.game.platform.sound

import kotlin.math.abs

/**
 * The shared fade for ambient layers. Platform players call [step] for every layer each [StepMillis] while any layer
 * is still moving, so the soundscape glides the same way on every device.
 */
object AmbienceGlide {
    const val StepMillis = 42L
    private const val Rate = 0.17f
    private const val Snap = 0.007f

    /** The next volume of a layer fading from [current] toward [target]; equal to [target] once it arrives. */
    fun step(current: Float, target: Float): Float =
        if (abs(target - current) < Snap) target else current + (target - current) * Rate
}
