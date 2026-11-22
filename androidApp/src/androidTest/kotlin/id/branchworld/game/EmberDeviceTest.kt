package id.branchworld.game

import android.app.UiAutomation
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Ember Season on a phone through ordinary touches: the storm gathers, Sedgewick asks, the Keeper answers and
 * opens the Shape sheet, which offers the firebreak. The player's own worlds are moved aside first and put back afterwards; only a profile with
 * the first two chapters closed is written, so the third story is open. Screens land in /data/local/tmp/ember-*.png.
 */
class EmberDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val input = instrumentation.uiAutomation

    @Test
    fun theDryStormOnAPhone() {
        val worlds = File(instrumentation.targetContext.filesDir, "worlds")
        val kept = File(instrumentation.targetContext.filesDir, "worlds-kept-by-ember-test")
        check(!kept.exists()) { "A previous run left the player's worlds in ${kept.path}; restore them first." }
        if (worlds.exists()) check(worlds.renameTo(kept))
        try {
            worlds.mkdirs()
            File(worlds, "profile.json").writeText(Profile)
            instrumentation.startActivitySync(
                Intent(instrumentation.targetContext, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
            click { it == "Stories" }
            // Chapter III is the last card; bring it into view, then begin it.
            waitUntil("Ember Season's Begin") {
                val title = find { it == "Ember Season" }?.let { Rect().also(it::getBoundsInScreen) }
                // Only the Begin on chapter III's own card, below its title.
                val begin = title?.let { heading ->
                    all { it == "Begin" }.map { it to Rect().also(it::getBoundsInScreen) }
                        .filter { (_, box) -> box.top > heading.bottom && box.left < heading.right && box.right > heading.left }
                        .minByOrNull { (_, box) -> box.top }?.first
                }
                if (begin == null) scrollForward() else shot("ember-00-stories")
                begin != null && clickNode(begin)
            }
            shot("ember-01-opening")
            find { it == "Play" }?.let(::clickNode)
            waitUntil("the dry storm petition", 60_000) { find { it.contains("Keep the old woods") } != null }
            SystemClock.sleep(1_500)
            shot("ember-02-petition")
            click { it.startsWith("Endorse") && it.contains("Keep the old woods") }
            click { it == "Shape" }
            SystemClock.sleep(1_500)
            shot("ember-02b-shape-open")
            // The phone sheet offers the new power beside the earlier ones.
            waitUntil("the firebreak power") { find { it == "Cut a firebreak" } != null && find { it == "Call rain" } != null }
            input.setRotation(UiAutomation.ROTATION_FREEZE_90)
            SystemClock.sleep(4_000)
            shot("ember-04-landscape")
            input.setRotation(UiAutomation.ROTATION_FREEZE_0)
            SystemClock.sleep(2_000)
            assertTrue(File(worlds, "story-ember-season.json").exists())
        } finally {
            input.setRotation(UiAutomation.ROTATION_UNFREEZE)
            worlds.deleteRecursively()
            if (kept.exists()) check(kept.renameTo(worlds))
        }
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
        if (button != null && button.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        return touch(node)
    }

    /** A touch at the control's centre, as a player's finger would land. Selectable wooden blocks need it. */
    private fun touch(node: AccessibilityNodeInfo): Boolean {
        val bounds = Rect().also(node::getBoundsInScreen)
        if (bounds.isEmpty) return false
        input.executeShellCommand("input tap ${bounds.centerX()} ${bounds.centerY()}").close()
        return true
    }

    private fun click(matches: (String) -> Boolean) {
        waitUntil("a control") {
            val node = find(matches)
            if (node == null) scrollForward()
            node != null && clickNode(node)
        }
    }

    private fun scrollForward() {
        fun walk(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.isScrollable && node.isVisibleToUser) return node
            for (index in 0 until node.childCount) walk(node.getChild(index))?.let { return it }
            return null
        }
        walk(input.rootInActiveWindow)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    private fun waitUntil(description: String, timeout: Long = 20_000, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            if (runCatching(condition).getOrDefault(false)) return
            SystemClock.sleep(250)
        }
        error("Timed out waiting for $description. Visible: ${all { it.isNotEmpty() }.map(::labelOf)}")
    }

    private companion object {
        const val Profile = """{"version":1,"chapters":[""" +
            """{"chapter":1,"slot":"story-fading-grove.json","branchId":2,"branchName":"Rain","tick":1000,"people":9,"departed":0,"deer":6,"homes":3},""" +
            """{"chapter":2,"slot":"story-last-crossing.json","branchId":2,"branchName":"Refuge","tick":7200,"people":8,"departed":1,"deer":7,"homes":4}""" +
            """],"legacyImported":true}"""
    }
}
