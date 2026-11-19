package id.branchworld.renderer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
    return onPointerEvent(PointerEventType.Scroll) { event ->
        val delta = event.changes.first().scrollDelta.y
        controller.zoom(exp(-0.12f * delta))
        event.changes.forEach { it.consume() }
    }.onPointerEvent(PointerEventType.Press) { event ->
        if (event.buttons.isPrimaryPressed || event.buttons.isSecondaryPressed) {
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

private class ViewportDrag(private val controller: PreviewController) {
    private var previous: Offset? = null
    private var pan = false

    fun start(position: Offset, pan: Boolean) {
        previous = position
        this.pan = pan
    }

    fun move(position: Offset, side: Int): Boolean {
        val start = previous ?: return false
        previous = position
        val delta = (position - start) / side.coerceAtLeast(1).toFloat()
        if (pan) controller.pan(delta.x, delta.y) else controller.orbit(delta.x, -delta.y)
        return true
    }

    fun stop() { previous = null }
}
