package id.branchworld.game

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DioramaDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val input = instrumentation.uiAutomation

    @Test
    fun dioramaRunsWorldClockAndSurvivesActivityRecreation() {
        input.serviceInfo = input.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        try {
            tap(bounds("Open world"))
            bounds("Island ready")
            val focus = bounds("Focus island")
            val bottom = bounds("Back to home").top
            bounds("Tick 0")
            bounds("Rabbits 4 · Deer 3")
            bounds("Births 0 · Deaths 0")
            val initialForage = currentForage()
            assertTrue(initialForage > 0)

            val screen = requireNotNull(input.takeScreenshot())
            val viewport = Rect(screen.width / 8, focus.bottom + 35, screen.width * 7 / 8, bottom - 30)
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
            tap(bounds("Send bunny left"))
            tap(bounds("Send bunny right"))
            bounds("Tick 0")
            tap(bounds("Play"))
            bounds("Pause")
            waitUntil("world clock to advance") { currentTick() >= 3 }
            assertTrue("Wildlife pixels did not move", difference(viewport, action = {}) > 0.001)
            waitUntil("forage to decrease") { currentForage() in 0 until initialForage }
            tap(bounds("Pause"))
            bounds("Play")
            val parked = currentTick()
            val parkedForage = currentForage()
            assertTrue(parked > 0)
            waitUntil("paused viewport pixels to settle again") { difference(viewport, action = {}) < 0.002 }
            assertEquals(parked, currentTick())
            assertEquals(parkedForage, currentForage())

            tap(bounds("Show diagnostics"))
            bounds("Seed: 104729")
            tap(bounds("Send bunny right"))
            tap(bounds("2×"))
            tap(bounds("4×"))
            tap(bounds("Play"))
            waitUntil("running clock at 4×") { currentTick() >= parked + 8 }
            tap(bounds("Pause"))
            bounds("Play")
            val retained = currentTick()

            instrumentation.runOnMainSync { activity.recreate() }
            waitUntil("previous Activity destroyed") { activity.isDestroyed }
            bounds("Island ready")
            bounds("Seed: 104729")
            bounds("Tick $retained")
            tap(bounds("Hide diagnostics"))
            repeat(3) {
                tap(bounds("Back to home"))
                tap(bounds("Open world"))
                bounds("Island ready")
                bounds("Tick $retained")
            }

            tap(bounds("1×"))
            tap(bounds("Play"))
            waitUntil("world to resume at 1×") { currentTick() >= retained + 3 }
            val beforeHome = currentTick()
            assertTrue(input.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME))
            waitUntil("launcher to become visible") {
                input.rootInActiveWindow?.packageName?.toString()?.let { it != instrumentation.targetContext.packageName } == true
            }
            SystemClock.sleep(2_000)
            ParcelFileDescriptor.AutoCloseInputStream(
                input.executeShellCommand("am start -W -n id.branchworld.game/.MainActivity"),
            ).use { it.readBytes() }
            waitUntil("world to resume after foregrounding") { currentTick() > beforeHome }
            assertTrue("World caught up on time spent in the background", currentTick() - beforeHome < 30)
            tap(bounds("Pause"))
            tap(bounds("Back to home"))
            bounds("Open world")

            for (seed in listOf("271828", "424242", "104729")) {
                tap(bounds(seed))
                tap(bounds("Open world"))
                bounds("Island $seed")
                bounds("Island ready")
                bounds("Tick 0")
                tap(bounds("Show diagnostics"))
                bounds("Seed: $seed")
                tap(bounds("Back to home"))
            }
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
            result = findVisible { it.text?.toString()?.trim() == label || it.contentDescription?.toString()?.trim() == label }
                ?.let { node -> Rect().also(node::getBoundsInScreen) }
            result != null
        }
        return requireNotNull(result)
    }

    private fun currentTick(): Long =
        findVisible { it.text?.startsWith("Tick ") == true }?.text?.toString()?.removePrefix("Tick ")?.toLongOrNull() ?: -1

    private fun currentForage(): Int =
        findVisible { it.text?.startsWith("Forage ") == true }?.text?.toString()?.removePrefix("Forage ")?.toIntOrNull() ?: -1

    private fun findVisible(matches: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (Build.VERSION.SDK_INT >= 33) input.clearCache()
        return input.rootInActiveWindow?.findMatching(matches)
    }

    private fun AccessibilityNodeInfo.findMatching(matches: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (isVisibleToUser && matches(this)) return this
        for (index in 0 until childCount) {
            getChild(index)?.findMatching(matches)?.let { return it }
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
