package id.branchworld.renderer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import id.branchworld.game.App
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalTestApi::class)
class DesktopEcologyUiTest {
    @Test
    fun wildlifeHudChangesWithPlaybackAndRetainsThePausedIsland() = runComposeUiTest {
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        }
        setContent { CompositionLocalProvider(LocalLifecycleOwner provides owner) { App() } }
        onNodeWithText("Open world").performClick()
        waitUntil(timeoutMillis = 5_000) { numberAfter("Forage ") != null }
        onNodeWithText("Rabbits 4 · Deer 3").assertExists()
        onNodeWithText("Births 0 · Deaths 0").assertExists()
        val initialForage = assertNotNull(numberAfter("Forage "))

        onNodeWithText("Play").performClick()
        waitUntil(timeoutMillis = 5_000) { numberAfter("Forage ")?.let { it < initialForage } == true }
        onNodeWithText("Pause").performClick()
        onNodeWithText("Play").assertExists()
        val parkedTick = assertNotNull(numberAfter("Tick "))
        val parkedForage = assertNotNull(numberAfter("Forage "))
        mainClock.advanceTimeBy(500)
        assertEquals(parkedTick, numberAfter("Tick "))
        assertEquals(parkedForage, numberAfter("Forage "))

        onNodeWithText("Back to home").performClick()
        onNodeWithText("Open world").performClick()
        onNodeWithText("Play").assertExists()
        assertEquals(parkedTick, numberAfter("Tick "))
        assertEquals(parkedForage, numberAfter("Forage "))
    }

    private fun ComposeUiTest.numberAfter(prefix: String): Long? =
        onAllNodesWithText(prefix, substring = true).fetchSemanticsNodes().firstOrNull()
            ?.config?.get(SemanticsProperties.Text)?.firstOrNull()?.text?.removePrefix(prefix)?.toLongOrNull()
}
