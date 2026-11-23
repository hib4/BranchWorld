package id.branchworld.renderer.input

import id.branchworld.renderer.camera.PreviewController
import kotlin.math.hypot

/** A finger on the view, in pixels from its top-left corner. */
internal data class Finger(val x: Float, val y: Float)

/**
 * Touch on every platform: one finger turns the camera, two fingers pan and pinch, and a short touch that never left
 * the slop is a tap that picks the ground. Platforms report the fingers still down after each change; adding or
 * lifting one rebases the gesture, so the camera never jumps.
 */
internal class TouchGestureTracker(private val controller: PreviewController) {
    private var centroidX = 0f
    private var centroidY = 0f
    private var span = 0f
    private var fingers = 0
    private var originX = 0f
    private var originY = 0f
    private var downAt = 0L
    // A touch is a tap until it travels past the slop, takes a second finger, is cancelled or lasts too long.
    private var gesture = false

    /** A finger touched down; [down] holds every finger now on the view. */
    fun press(down: List<Finger>, timeMillis: Long) {
        if (fingers == 0 && down.isNotEmpty()) {
            originX = down[0].x
            originY = down[0].y
            downAt = timeMillis
            gesture = false
        }
        if (down.size >= 2) gesture = true
        rebase(down)
    }

    /** Fingers moved within a [width]×[height] view; [slop] is how far, in pixels, a tap may wander. */
    fun move(down: List<Finger>, width: Int, height: Int, slop: Float) {
        // Hold the camera still until the finger clearly means to turn it.
        if (!gesture && down.size == 1 && hypot(down[0].x - originX, down[0].y - originY) > slop) gesture = true
        if (gesture && fingers > 0 && down.size == fingers) {
            val (x, y, nextSpan) = shape(down)
            val side = minOf(width, height).coerceAtLeast(1).toFloat()
            val dx = (x - centroidX) / side
            val dy = (y - centroidY) / side
            if (fingers >= 2) {
                controller.pan(dx, dy)
                if (span > 0f && nextSpan > 0f) controller.zoom(nextSpan / span)
            } else {
                controller.orbit(dx, -dy)
            }
        }
        rebase(down)
    }

    /** A finger lifted at [lifted], when the platform says where; [down] holds the fingers still on the view. */
    fun release(lifted: Finger?, down: List<Finger>, timeMillis: Long, width: Int, height: Int, pickRadius: Float) {
        if (lifted != null && down.isEmpty() && !gesture && timeMillis - downAt < TapMillis) {
            controller.tap(lifted.x, lifted.y, width.toFloat(), height.toFloat(), pickRadius)
        }
        if (down.isEmpty()) gesture = false
        rebase(down)
    }

    /** The system took the touch away: nothing that follows is a tap. */
    fun cancel() {
        gesture = true
        fingers = 0
    }

    private fun rebase(down: List<Finger>) {
        fingers = down.size
        if (down.isNotEmpty()) shape(down).let { (x, y, nextSpan) -> centroidX = x; centroidY = y; span = nextSpan }
    }

    private fun shape(down: List<Finger>): Triple<Float, Float, Float> {
        val a = down[0]
        val b = down.getOrElse(1) { a }
        return Triple((a.x + b.x) / 2, (a.y + b.y) / 2, hypot(b.x - a.x, b.y - a.y))
    }

    companion object {
        /** Longer than this, a touch is a press rather than a tap. */
        const val TapMillis = 350L

        /** How near a tap must land to pick a creature, in density-independent pixels. */
        const val PickRadius = 24f
    }
}
