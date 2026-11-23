package id.branchworld.renderer

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import kotlin.math.exp

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun Modifier.pointerCameraInput(controller: PreviewController): Modifier {
    val drag = remember(controller) { ViewportDrag(controller) }
    val touch = remember(controller) { TouchCamera(controller) }
    val focus = remember { FocusRequester() }
    val motion = rememberCoroutineScope()
    return focusRequester(focus).onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionLeft -> controller.moveByKey(-0.08f, 0f, event.isShiftPressed)
            Key.DirectionRight -> controller.moveByKey(0.08f, 0f, event.isShiftPressed)
            Key.DirectionUp -> controller.moveByKey(0f, -0.08f, event.isShiftPressed)
            Key.DirectionDown -> controller.moveByKey(0f, 0.08f, event.isShiftPressed)
            Key.Equals, Key.Plus, Key.NumPadAdd -> controller.zoom(1.16f)
            Key.Minus, Key.NumPadSubtract -> controller.zoom(1f / 1.16f)
            Key.MoveHome -> controller.focus(motion)
            else -> return@onPreviewKeyEvent false
        }
        true
    }.focusable().onPointerEvent(PointerEventType.Scroll) { event ->
        focus.requestFocus()
        val delta = event.changes.first().scrollDelta.y / ScrollNotch
        controller.zoom(exp(-0.12f * delta))
        event.changes.forEach { it.consume() }
    }.onPointerEvent(PointerEventType.Press) { event ->
        if (touch.handle(event, size.width, size.height, density)) return@onPointerEvent
        if (event.buttons.isPrimaryPressed || event.buttons.isSecondaryPressed) {
            focus.requestFocus()
            val change = event.changes.first()
            drag.start(change.position, event.buttons.isSecondaryPressed || event.keyboardModifiers.isShiftPressed)
            change.consume()
        }
    }.onPointerEvent(PointerEventType.Move) { event ->
        if (touch.handle(event, size.width, size.height, density)) return@onPointerEvent
        val change = event.changes.first()
        if (drag.move(change.position, minOf(size.width, size.height))) change.consume()
        else if (!event.buttons.isPrimaryPressed && !event.buttons.isSecondaryPressed) {
            controller.hover(change.position.x, change.position.y, size.width.toFloat(), size.height.toFloat(), MousePickRadius * density)
        }
    }.onPointerEvent(PointerEventType.Enter) { event ->
        if (event.isTouch) return@onPointerEvent
        val change = event.changes.first()
        controller.hover(change.position.x, change.position.y, size.width.toFloat(), size.height.toFloat(), MousePickRadius * density)
    }.onPointerEvent(PointerEventType.Exit) {
        controller.hover(null, null, size.width.toFloat(), size.height.toFloat(), 0f)
    }.onPointerEvent(PointerEventType.Release) { event ->
        if (touch.handle(event, size.width, size.height, density)) return@onPointerEvent
        val change = event.changes.first()
        if (drag.move(change.position, minOf(size.width, size.height))) change.consume()
        // A press and release that barely moved is a click on the world, not a turn of the camera.
        if (drag.stop() && !drag.panning) {
            controller.tap(change.position.x, change.position.y, size.width.toFloat(), size.height.toFloat(), MousePickRadius * density)
        }
    }
}

private fun PreviewController.moveByKey(x: Float, y: Float, panning: Boolean) {
    if (panning) pan(x, y) else orbit(x, -y)
}

private class ViewportDrag(private val controller: PreviewController) {
    private var previous: Offset? = null
    private var origin: Offset? = null
    private var dragging = false
    private var pan = false
    val panning get() = pan

    fun start(position: Offset, pan: Boolean) {
        previous = position
        origin = position
        dragging = false
        this.pan = pan
    }

    fun move(position: Offset, side: Int): Boolean {
        val start = previous ?: return false
        if (start == position) return false
        // Small wobbles during a click neither turn the camera nor stop it counting as a click.
        if (!dragging && (position - checkNotNull(origin)).getDistance() < ClickSlop) return true
        dragging = true
        previous = position
        val delta = (position - start) / side.coerceAtLeast(1).toFloat()
        if (pan) controller.pan(delta.x, delta.y) else controller.orbit(delta.x, -delta.y)
        return true
    }

    /** Ends the press; true when it never became a drag. */
    fun stop(): Boolean {
        val click = origin != null && !dragging
        previous = null
        origin = null
        dragging = false
        return click
    }
}

/** One wheel notch in the platform's scroll units: lines on Desktop, CSS pixels in the browser. */
internal expect val ScrollNotch: Float

private val PointerEvent.isTouch get() = changes.any { it.type == PointerType.Touch }

/**
 * Touch on a browser or touchscreen laptop, matching the Android viewport: one finger turns the camera,
 * two fingers pan and pinch, and a short tap that stays within the slop picks the ground.
 */
private class TouchCamera(private val controller: PreviewController) {
    private var centroid = Offset.Zero
    private var span = 0f
    private var fingers = 0
    private var origin = Offset.Zero
    private var downAt = 0L
    private var gesture = false

    /** Returns false for mouse and pen events, leaving them to the pointer handlers. */
    fun handle(event: PointerEvent, width: Int, height: Int, density: Float): Boolean {
        if (!event.isTouch) return false
        val down = event.changes.filter { it.pressed }
        when (event.type) {
            PointerEventType.Press -> {
                if (fingers == 0 && down.isNotEmpty()) {
                    origin = down.first().position
                    downAt = down.first().uptimeMillis
                    gesture = false
                }
                if (down.size >= 2) gesture = true
            }
            PointerEventType.Move -> {
                // Hold the camera still until the finger clearly means to turn it.
                if (!gesture && down.size == 1 && (down[0].position - origin).getDistance() > TouchSlop * density) {
                    gesture = true
                }
                if (gesture && fingers > 0 && down.size == fingers) {
                    val (nextCentroid, nextSpan) = shape(down)
                    val delta = (nextCentroid - centroid) / minOf(width, height).coerceAtLeast(1).toFloat()
                    if (fingers >= 2) {
                        controller.pan(delta.x, delta.y)
                        if (span > 0f && nextSpan > 0f) controller.zoom(nextSpan / span)
                    } else {
                        controller.orbit(delta.x, -delta.y)
                    }
                }
            }
            PointerEventType.Release -> {
                val lifted = event.changes.firstOrNull { !it.pressed && it.previousPressed }
                if (down.isEmpty() && !gesture && lifted != null && lifted.uptimeMillis - downAt < TapMillis) {
                    controller.tap(lifted.position.x, lifted.position.y, width.toFloat(), height.toFloat(), TouchPickRadius * density)
                }
                if (down.isEmpty()) gesture = false
            }
        }
        // Rebase on the fingers still down, so adding or lifting one never jumps the camera.
        fingers = down.size
        if (down.isNotEmpty()) shape(down).let { (nextCentroid, nextSpan) -> centroid = nextCentroid; span = nextSpan }
        event.changes.forEach { it.consume() }
        return true
    }

    private fun shape(down: List<PointerInputChange>): Pair<Offset, Float> {
        val a = down[0].position
        val b = down.getOrNull(1)?.position ?: a
        return (a + b) / 2f to (b - a).getDistance()
    }
}

private const val ClickSlop = 4f
private const val TouchSlop = 8f
private const val TapMillis = 350L
private const val TouchPickRadius = 24f
private const val MousePickRadius = 16f
