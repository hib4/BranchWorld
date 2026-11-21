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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import kotlin.math.exp

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun Modifier.desktopCameraInput(controller: PreviewController): Modifier {
    val drag = remember(controller) { ViewportDrag(controller) }
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
        val delta = event.changes.first().scrollDelta.y
        controller.zoom(exp(-0.12f * delta))
        event.changes.forEach { it.consume() }
    }.onPointerEvent(PointerEventType.Press) { event ->
        if (event.buttons.isPrimaryPressed || event.buttons.isSecondaryPressed) {
            focus.requestFocus()
            val change = event.changes.first()
            drag.start(change.position, event.buttons.isSecondaryPressed || event.keyboardModifiers.isShiftPressed)
            change.consume()
        }
    }.onPointerEvent(PointerEventType.Move) { event ->
        val change = event.changes.first()
        if (drag.move(change.position, minOf(size.width, size.height))) change.consume()
    }.onPointerEvent(PointerEventType.Release) { event ->
        val change = event.changes.first()
        if (drag.move(change.position, minOf(size.width, size.height))) change.consume()
        drag.stop()
    }
}

private fun PreviewController.moveByKey(x: Float, y: Float, panning: Boolean) {
    if (panning) pan(x, y) else orbit(x, -y)
}

private class ViewportDrag(private val controller: PreviewController) {
    private var previous: Offset? = null
    private var pan = false

    fun start(position: Offset, pan: Boolean) {
        previous = position
        this.pan = pan
    }

    fun move(position: Offset, side: Int): Boolean {
        val start = previous ?: return false
        if (start == position) return false
        previous = position
        val delta = (position - start) / side.coerceAtLeast(1).toFloat()
        if (pan) controller.pan(delta.x, delta.y) else controller.orbit(delta.x, -delta.y)
        return true
    }

    fun stop() { previous = null }
}
