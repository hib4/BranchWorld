package id.branchworld.game

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DioramaDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val input = instrumentation.uiAutomation

    @Test
    fun dioramaRespondsToTouchAndSurvivesActivityRecreation() {
        input.serviceInfo = input.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        try {
            tap(bounds("Open world"))
            val hud = bounds("Island ready")
            val bottom = bounds("Back to home").top
            tap(bounds("Pause animation"))
            bounds("Resume animation")

            val screen = requireNotNull(input.takeScreenshot())
            val viewport = Rect(screen.width / 8, hud.bottom + 100, screen.width * 7 / 8, bottom - 30)
            screen.recycle()
            val start = point(viewport.centerX(), viewport.centerY())
            val span = viewport.width() / 7

            waitUntil("paused viewport pixels to settle") {
                difference(viewport, action = {}) < 0.002
            }
            assertTrue("One-finger drag did not orbit", difference(viewport) {
                gesture(start, start.copy(x = start.x + span))
            } > 0.01)
            resetCamera()
            assertTrue("Two-finger drag did not pan", difference(viewport) {
                val second = start.copy(x = start.x + span)
                gesture(start, start.copy(x = start.x - span), second, second.copy(x = second.x - span))
            } > 0.01)
            resetCamera()
            assertTrue("Pinch did not zoom", difference(viewport) {
                val second = start.copy(x = start.x + span)
                gesture(start, start.copy(x = start.x - span / 2), second, second.copy(x = second.x + span / 2))
            } > 0.01)
            resetCamera()
            tap(bounds("Resume animation"))
            bounds("Pause animation")
            assertTrue("Bunny did not animate", difference(viewport, action = {}) > 0.001)

            instrumentation.runOnMainSync { activity.recreate() }
            waitUntil("previous Activity destroyed") { activity.isDestroyed }
            bounds("Island ready")
            repeat(3) {
                tap(bounds("Back to home"))
                tap(bounds("Open world"))
                bounds("Island ready")
            }
            tap(bounds("Back to home"))
            bounds("Open world")
        } finally {
            if (!activity.isDestroyed) instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun resetCamera() {
        tap(bounds("Focus island"))
        SystemClock.sleep(500)
    }

    private fun difference(region: Rect, action: () -> Unit): Double {
        val before = requireNotNull(input.takeScreenshot())
        try {
            action()
            SystemClock.sleep(500)
            val after = requireNotNull(input.takeScreenshot())
            try {
                var changed = 0
                var samples = 0
                for (y in region.top until region.bottom step 6) {
                    for (x in region.left until region.right step 6) {
                        val a = before.getPixel(x, y)
                        val b = after.getPixel(x, y)
                        if (maxOf(channelDelta(a, b, 0), channelDelta(a, b, 8), channelDelta(a, b, 16)) > 8) {
                            changed++
                        }
                        samples++
                    }
                }
                return changed.toDouble() / samples
            } finally {
                after.recycle()
            }
        } finally {
            before.recycle()
        }
    }

    private fun channelDelta(a: Int, b: Int, shift: Int): Int =
        abs((a shr shift and 255) - (b shr shift and 255))

    private fun tap(bounds: Rect) {
        val tap = point(bounds.centerX(), bounds.centerY())
        val start = SystemClock.uptimeMillis()
        send(start, MotionEvent.ACTION_DOWN, tap)
        send(start, MotionEvent.ACTION_UP, tap)
    }

    private fun gesture(first: Point, firstEnd: Point, second: Point? = null, secondEnd: Point? = null) {
        val start = SystemClock.uptimeMillis()
        send(start, MotionEvent.ACTION_DOWN, first)
        if (second != null) send(start, MotionEvent.ACTION_POINTER_DOWN or IndexOne, first, second)
        for (step in 1..12) {
            val fraction = step / 12f
            send(start, MotionEvent.ACTION_MOVE, first.lerp(firstEnd, fraction), second?.lerp(checkNotNull(secondEnd), fraction))
            SystemClock.sleep(16)
        }
        if (secondEnd != null) send(start, MotionEvent.ACTION_POINTER_UP or IndexOne, firstEnd, secondEnd)
        send(start, MotionEvent.ACTION_UP, firstEnd)
    }

    private fun send(start: Long, action: Int, first: Point, second: Point? = null) {
        val points = listOfNotNull(first, second)
        val properties = Array(points.size) { index ->
            MotionEvent.PointerProperties().apply { id = index; toolType = MotionEvent.TOOL_TYPE_FINGER }
        }
        val coordinates = Array(points.size) { index ->
            MotionEvent.PointerCoords().apply {
                x = points[index].x
                y = points[index].y
                pressure = 1f
                size = 1f
            }
        }
        val event = MotionEvent.obtain(
            start, SystemClock.uptimeMillis(), action, points.size, properties, coordinates,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0,
        )
        try {
            assertTrue("Could not inject touch action $action", input.injectInputEvent(event, true))
        } finally {
            event.recycle()
        }
    }

    private fun bounds(label: String): Rect {
        var result: Rect? = null
        waitUntil(label) {
            if (Build.VERSION.SDK_INT >= 33) input.clearCache()
            result = input.rootInActiveWindow?.findLabel(label)
                ?.let { node -> Rect().also(node::getBoundsInScreen) }
            result != null
        }
        return requireNotNull(result)
    }

    private fun AccessibilityNodeInfo.findLabel(label: String): AccessibilityNodeInfo? {
        if (isVisibleToUser && (text?.toString()?.trim() == label || contentDescription?.toString()?.trim() == label)) {
            return this
        }
        for (index in 0 until childCount) {
            getChild(index)?.findLabel(label)?.let { return it }
        }
        return null
    }

    private fun waitUntil(description: String, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < end) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        val root = input.rootInActiveWindow
        error("Timed out waiting for $description. Active window package: ${root?.packageName}")
    }

    private data class Point(val x: Float, val y: Float) {
        fun lerp(end: Point, amount: Float) = Point(x + (end.x - x) * amount, y + (end.y - y) * amount)
    }

    private fun point(x: Int, y: Int) = Point(x.toFloat(), y.toFloat())

    private companion object {
        const val IndexOne = 1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT
    }
}
