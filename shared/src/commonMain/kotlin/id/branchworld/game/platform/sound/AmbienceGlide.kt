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

/** The shared level and fades for music, so a piece arrives and leaves the same way on every device. */
object MusicGlide {
    /** A piece plays clearly above the island (about −22 LUFS); see [Duck]. */
    const val Volume = 0.8f

    /** While a piece plays, the soundscape glides down to this share of its level (−4.4 dB) and back after. */
    const val Duck = 0.6f
    const val FadeInMillis = 3_000L
    const val FadeOutMillis = 2_000L

    /** Linear ramp: the volume [elapsedMillis] into a fade from [from] to [to] lasting [durationMillis]. */
    fun ramp(from: Float, to: Float, elapsedMillis: Long, durationMillis: Long): Float =
        if (elapsedMillis >= durationMillis) to else from + (to - from) * elapsedMillis / durationMillis
}
