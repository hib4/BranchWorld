package id.branchworld.renderer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import id.branchworld.game.App
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class DesktopTimelineUiTest {
    @Test
    fun seekForkAndSwitchAtTheSameTick() = runDesktopComposeUiTest(width = 1000, height = 1400) {
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        }
        setContent { CompositionLocalProvider(LocalLifecycleOwner provides owner) { App() } }
        onNodeWithText("Open world").performClick()
        awaitText("Timeline")
        onNodeWithText("Timeline").performScrollTo().performClick()
        enterTick("8")
        onNodeWithText("Play to tick").performClick()
        awaitText("Tick 8")
        awaitText("Play")
        enterTick("4")
        onNodeWithText("Go to tick").performClick()
        awaitText("Tick 4")
        onNodeWithText("Fork here").performClick()
        onNodeWithText("Create a branch at tick 4").assertIsDisplayed()
        onAllNodes(hasSetTextAction())[1].performTextReplacement("Restored Forest")
        onNodeWithText("Create branch").performClick()
        awaitText("Branch Restored Forest")
        enterTick("8")
        onNodeWithText("Play to tick").performClick()
        awaitText("Tick 8")
        onNodeWithText("Branch Restored Forest").performClick()
        onNodeWithText("Main").performClick()
        awaitText("Branch Main")
        awaitText("Tick 8")
    }

    private fun ComposeUiTest.enterTick(tick: String) {
        onNode(hasSetTextAction()).performScrollTo().performTextReplacement(tick)
    }

    private fun ComposeUiTest.awaitText(text: String) {
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
