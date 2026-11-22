package id.branchworld.game

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ContentUris
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DioramaDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val input = instrumentation.uiAutomation

    @Test
    @SdkSuppress(minSdkVersion = 29)
    fun photoModePublishesADecodableGameFrame() {
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        val resolver = instrumentation.targetContext.contentResolver
        val pictures = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        fun photoIds() = resolver.query(
            pictures,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.RELATIVE_PATH} = ?",
            arrayOf("Pictures/BranchWorld/"),
            null,
        )?.use { rows -> buildSet { while (rows.moveToNext()) add(rows.getLong(0)) } }.orEmpty()
        val before = photoIds()
        try {
            openStory()
            waitUntil("terrain assets to load") {
                findVisible { it.text in listOf("Starting renderer…", "Loading island…") } == null
            }
            tap(bounds("Menu"))
            tap(bounds("Photo mode"))
            // A photo needs the island's first complete frame. The game says so and asks to try again, as a player would.
            waitUntil("Photo saved to Pictures/BranchWorld.") {
                if (findVisible { it.text?.toString()?.trim() == "Photo saved to Pictures/BranchWorld." } != null) return@waitUntil true
                findVisible { it.isEnabled && it.text?.toString()?.trim() == "Keep this view" }
                    ?.let { node -> tap(Rect().also(node::getBoundsInScreen)) }
                Thread.sleep(1_500)
                false
            }

            val photo = ContentUris.withAppendedId(pictures, (photoIds() - before).single())
            val bitmap = requireNotNull(resolver.openInputStream(photo)?.use(BitmapFactory::decodeStream))
            try {
                assertTrue("Expected a game-sized photo", bitmap.width >= 300 && bitmap.height >= 300)
                val colors = buildSet {
                    for (x in 1..5) for (y in 1..5) add(bitmap.getPixel(x * bitmap.width / 6, y * bitmap.height / 6))
                }
                assertTrue("Expected a rendered scene", colors.size > 4)
            } finally {
                bitmap.recycle()
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            for (id in photoIds() - before) resolver.delete(ContentUris.withAppendedId(pictures, id), null, null)
        }
    }

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
            openStory()
            // A new story starts playing; a continued one opens paused. Either way, pause before measuring.
            waitUntil("Play or Pause") { findVisible { it.contentDescription?.toString() in setOf("Play", "Pause") } != null }
            findVisible { it.contentDescription?.toString() == "Pause" }?.let { tap(Rect().also(it::getBoundsInScreen)) }
            bounds("Play")
            bounds("Focus island")
            assertTrue(findVisible { it.text == "Send bunny left" } == null)
            tap(bounds("Menu"))
            tap(bounds("Settings"))
            tap(bounds("Reduce motion"))
            tap(bounds("Close"))

            val screen = requireNotNull(input.takeScreenshot())
            val viewport = Rect(screen.width / 8, screen.height / 3, screen.width * 7 / 8, screen.height * 2 / 3)
            screen.recycle()
            val start = point(viewport.centerX(), viewport.centerY())
            val span = viewport.width() / 7

            waitUntil("terrain assets to load") {
                findVisible { it.text in listOf("Starting renderer…", "Loading island…") } == null
            }
            waitUntil("paused viewport pixels to settle") { difference(viewport, action = {}) < 0.002 }
            val orbit = firstVisibleOrbit(viewport, start, span)
            println("BranchWorldInput orbitToVisibleMs=$orbit surface=${viewport.width()}x${viewport.height()}")
            resetCamera()
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

            tap(bounds("Shape"))
            tap(bounds("Watch Hollowmere"))
            bounds("Play")
            tap(bounds("Shape"))
            tap(bounds("Restore grove"))
            tap(bounds("Plant saplings"))
            bounds("Queued. Watch the next harvest to see it take effect.")
            tap(bounds("Watch the next harvest"))
            waitUntil("planting feedback") { findVisible { it.text?.endsWith("saplings are in the ground.") == true } != null }
            bounds("Play")

            tap(bounds("Menu"))
            tap(bounds("Settings"))
            tap(revealed("Timeline tick tools"))
            waitUntil("tick tools switched on") {
                // The label is a child of the switch row, which carries the checked state.
                findVisible { it.text?.toString()?.trim() == "Timeline tick tools" }?.parent?.isChecked == true
            }
            tap(bounds("Close"))
            tap(bounds("Menu"))
            tap(bounds("Diagnostics"))
            bounds("Seed: 104729")
            val parked = currentTick()
            val parkedForage = currentForage()
            assertTrue(parked > 0 && parkedForage > 0)
            SystemClock.sleep(500)
            assertEquals(parked, currentTick())
            assertEquals(parkedForage, currentForage())

            instrumentation.runOnMainSync { activity.recreate() }
            waitUntil("previous Activity destroyed") { activity.isDestroyed }
            bounds("Focus island")
            bounds("Seed: 104729")
            bounds("Tick $parked")
            tap(bounds("2×"))
            tap(bounds("4×"))
            tap(bounds("Play"))
            waitUntil("running clock at 4×") { currentTick() >= parked + 8 }
            tap(bounds("1×"))
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
            tap(bounds("Close"))
            tap(bounds("Menu"))
            tap(bounds("Return to title"))
            bounds("Continue")
        } finally {
            if (!activity.isDestroyed) instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun resetCamera() {
        tap(bounds("Focus island"))
        SystemClock.sleep(500)
    }

    private fun firstVisibleOrbit(region: Rect, start: Point, span: Int): Long {
        val before = requireNotNull(input.takeScreenshot())
        try {
            val end = start.copy(x = start.x + span)
            val timestamp = SystemClock.uptimeMillis()
            send(timestamp, MotionEvent.ACTION_DOWN, start)
            send(timestamp, MotionEvent.ACTION_MOVE, start.lerp(end, 0.25f))
            val movement = SystemClock.uptimeMillis()
            send(timestamp, MotionEvent.ACTION_MOVE, end)
            send(timestamp, MotionEvent.ACTION_UP, end)
            repeat(45) {
                val after = requireNotNull(input.takeScreenshot())
                try {
                    if (changedFraction(region, before, after) > 0.01) return SystemClock.uptimeMillis() - movement
                } finally {
                    after.recycle()
                }
            }
            error("The camera drag never appeared on the Android display")
        } finally {
            before.recycle()
        }
    }

    private fun difference(region: Rect, action: () -> Unit): Double {
        val before = requireNotNull(input.takeScreenshot())
        try {
            action()
            SystemClock.sleep(500)
            val after = requireNotNull(input.takeScreenshot())
            try {
                return changedFraction(region, before, after)
            } finally {
                after.recycle()
            }
        } finally {
            before.recycle()
        }
    }

    private fun changedFraction(region: Rect, before: Bitmap, after: Bitmap): Double {
        var changed = 0
        var samples = 0
        for (y in region.top until region.bottom step 8) {
            for (x in region.left until region.right step 8) {
                val a = before.getPixel(x, y)
                val b = after.getPixel(x, y)
                if (maxOf(channelDelta(a, b, 0), channelDelta(a, b, 8), channelDelta(a, b, 16)) > 8) changed++
                samples++
            }
        }
        return changed.toDouble() / samples
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

    private fun bounds(label: String, enabled: Boolean = false): Rect {
        var result: Rect? = null
        waitUntil(label) {
            result = findVisible {
                (!enabled || it.isEnabled) && (it.text?.toString()?.trim() == label || it.contentDescription?.toString()?.trim() == label)
            }
                ?.let { node -> Rect().also(node::getBoundsInScreen) }
            result != null
        }
        return requireNotNull(result)
    }

    private fun openStory() {
        // Title screen → Stories → the first chapter's Begin (or Continue once it has a history).
        for (label in listOf("Stories", null)) {
            waitUntil("enabled ${label ?: "Begin or Continue"} button") {
                val text = findVisible {
                    val value = it.text?.toString()?.trim()
                    if (label != null) value == label else value == "Begin" || value == "Continue"
                }
                val button = generateSequence(text) { it.parent }.firstOrNull { it.isClickable && it.isEnabled }
                button?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            }
        }
        bounds("Focus island")
    }

    private fun currentTick(): Long =
        findVisible { it.text?.startsWith("Tick ") == true }?.text?.toString()?.removePrefix("Tick ")?.toLongOrNull() ?: -1

    /** Scrolls the open sheet the way a player would until [label] is on screen, then returns its bounds. */
    private fun revealed(label: String): Rect {
        fun where(): Rect? = findVisible { it.text?.toString()?.trim() == label || it.contentDescription?.toString()?.trim() == label }
            ?.let { Rect().also(it::getBoundsInScreen) }
        repeat(8) {
            if (where() != null) {
                // Accessibility scrolling animates; only tap once the row has stopped moving.
                var last: Rect? = null
                waitUntil("$label to settle") {
                    val now = where()
                    val settled = now != null && now == last
                    last = now
                    settled
                }
                return requireNotNull(last)
            }
            findVisible { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            SystemClock.sleep(300)
        }
        return bounds(label)
    }

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
        val visible = buildList {
            fun walk(node: AccessibilityNodeInfo?) {
                if (node == null) return
                val text = (node.text ?: node.contentDescription)?.toString()
                if (node.isVisibleToUser && node.isCheckable) add("<switch checked=${node.isChecked}>")
                if (node.isVisibleToUser && !text.isNullOrBlank()) add(text)
                for (index in 0 until node.childCount) walk(node.getChild(index))
            }
            walk(root)
        }
        error("Timed out waiting for $description. Active window package: ${root?.packageName}. Visible: $visible")
    }

    private data class Point(val x: Float, val y: Float) {
        fun lerp(end: Point, amount: Float) = Point(x + (end.x - x) * amount, y + (end.y - y) * amount)
    }

    private fun point(x: Int, y: Int) = Point(x.toFloat(), y.toFloat())

    private companion object {
        const val IndexOne = 1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT
    }
}
