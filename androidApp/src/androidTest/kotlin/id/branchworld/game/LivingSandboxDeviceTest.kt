package id.branchworld.game

import android.app.UiAutomation
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The first essential experience on a phone, through ordinary touches: New world, Empty land, Create, then taps on the
 * 3D island set rabbits and wolves down, and they are left to live at 8×. The player's own worlds are moved aside
 * first and put back afterwards. Screens land in /data/local/tmp/living-*.png.
 */
class LivingSandboxDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val input = instrumentation.uiAutomation

    @Test
    fun rabbitsAndWolvesOnAPhone() {
        val worlds = File(instrumentation.targetContext.filesDir, "worlds")
        val kept = File(instrumentation.targetContext.filesDir, "worlds-kept-by-living-test")
        check(!kept.exists()) { "A previous run left the player's worlds in ${kept.path}; restore them first." }
        if (worlds.exists()) check(worlds.renameTo(kept))
        try {
            input.setRotation(UiAutomation.ROTATION_FREEZE_0)
            instrumentation.startActivitySync(
                Intent(instrumentation.targetContext, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
            click { it == "New world" }
            SystemClock.sleep(1_000)
            shot("living-01-new-world")
            click { it == "Create world" }
            // The island is ready when the palette is.
            waitUntil("the palette", 60_000) { find { it == "Rabbit" } != null }
            SystemClock.sleep(6_000)
            shot("living-02-empty-land")
            click { it == "Pause" }
            val size = screen()
            // Finger taps over the middle of the island: water and cliffs say no, land takes a rabbit.
            val spots = buildList {
                for (row in 0 until 5) for (column in 0 until 5) {
                    add(size.first * (0.22f + column * 0.14f) to size.second * (0.30f + row * 0.07f))
                }
            }
            // Accessibility counts lag behind the screen, so tap a fixed set of spots and then wait for the count.
            for ((x, y) in spots.take(8)) {
                tap(x, y)
                SystemClock.sleep(350)
            }
            shot("living-02b-rabbits")
            waitUntil("rabbits set down: ${dump()}", 15_000) { population("Rabbits") >= 3 }
            click { it == "Wolf" }
            for ((x, y) in spots.takeLast(3)) {
                tap(x, y)
                SystemClock.sleep(350)
            }
            waitUntil("wolves set down: ${dump()}", 15_000) { population("Wolves") >= 2 }
            shot("living-03-placed")
            click { it == "Play" }
            click { it == "Speed 8×" }
            SystemClock.sleep(25_000)
            shot("living-04-life")
            input.setRotation(UiAutomation.ROTATION_FREEZE_90)
            SystemClock.sleep(5_000)
            shot("living-05-landscape")
            input.setRotation(UiAutomation.ROTATION_FREEZE_0)
            SystemClock.sleep(2_000)
            assertTrue(File(worlds, "world-1.json").exists())
        } finally {
            input.setRotation(UiAutomation.ROTATION_UNFREEZE)
            worlds.deleteRecursively()
            if (kept.exists()) check(kept.renameTo(worlds))
        }
    }

    /** The count on the world's population peg, e.g. "Rabbits 4", or 0 when there are none. */
    private fun population(plural: String): Int {
        // The peg merges its icon and number; its spoken label carries the species.
        var count = 0
        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null) return
            val spoken = node.contentDescription?.toString()?.trim().orEmpty()
            if (spoken.startsWith("$plural ")) count = spoken.removePrefix("$plural ").trim().toIntOrNull() ?: count
            for (index in 0 until node.childCount) walk(node.getChild(index))
        }
        walk(input.rootInActiveWindow)
        return count
    }

    private fun dump(): String = buildList {
        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null) return
            if (node.text != null || node.contentDescription != null) add("${node.text}|${node.contentDescription}")
            for (index in 0 until node.childCount) walk(node.getChild(index))
        }
        walk(input.rootInActiveWindow)
    }.joinToString("; ")

    private fun screen(): Pair<Float, Float> {
        val metrics = instrumentation.targetContext.resources.displayMetrics
        return metrics.widthPixels.toFloat() to metrics.heightPixels.toFloat()
    }

    private fun tap(x: Float, y: Float) {
        input.executeShellCommand("input tap ${x.toInt()} ${y.toInt()}").close()
    }

    private fun shot(name: String) {
        input.executeShellCommand("screencap -p /data/local/tmp/$name.png").close()
        SystemClock.sleep(800)
    }

    private fun labelOf(node: AccessibilityNodeInfo) = (node.text ?: node.contentDescription)?.toString()?.trim().orEmpty()

    private fun all(matches: (String) -> Boolean): List<AccessibilityNodeInfo> = buildList {
        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null) return
            if (node.isVisibleToUser && matches(labelOf(node))) add(node)
            for (index in 0 until node.childCount) walk(node.getChild(index))
        }
        walk(input.rootInActiveWindow)
    }

    private fun find(matches: (String) -> Boolean) = all(matches).firstOrNull()

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        val button = generateSequence(node) { it.parent }.firstOrNull { it.isClickable && it.isEnabled }
        return button != null && button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun click(matches: (String) -> Boolean) {
        waitUntil("a control") {
            val node = find(matches)
            node != null && clickNode(node)
        }
    }

    private fun waitUntil(description: String, timeout: Long = 20_000, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            if (runCatching(condition).getOrDefault(false)) return
            SystemClock.sleep(250)
        }
        error("Timed out waiting for $description. Visible: ${all { it.isNotEmpty() }.map(::labelOf)}")
    }
}
