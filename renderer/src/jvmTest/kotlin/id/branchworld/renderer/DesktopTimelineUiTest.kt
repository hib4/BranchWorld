package id.branchworld.renderer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import id.branchworld.game.App
import id.branchworld.game.simulation.WorldRenderSource
import kotlin.test.Test
import kotlin.test.assertEquals

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
        onNodeWithText("Main").performClick()
        awaitText("Branch Main")
        awaitText("Tick 8")
    }

    @Test
    fun compareScrubberAndViewportHoldTheSameTickForBothBranches() = runDesktopComposeUiTest(width = 1000, height = 1500) {
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        }
        var viewport: WorldRenderSource? = null
        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                App(viewport = { _, world -> SideEffect { viewport = world } })
            }
        }
        click("Open world")
        awaitText("Timeline")
        click("Timeline")
        enterTick("8")
        click("Play to tick")
        awaitText("Tick 8")
        enterTick("4")
        click("Go to tick")
        awaitText("Tick 4")
        click("Fork here")
        onNodeWithText("Create branch").performClick()
        awaitText("Branch Branch 2")
        enterTick("8")
        click("Play to tick")
        awaitText("Tick 8")
        val stableViewport = checkNotNull(viewport)
        click("Compare branches")
        awaitText("Viewing Main at tick 8")
        onNodeWithContentDescription("Timeline position").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        awaitText("Viewing Main at tick 4")
        click("Show right")
        awaitText("Viewing Branch 2 at tick 4")
        assertEquals(2, stableViewport.poses.value.branchId)
        assertEquals(4L, stableViewport.poses.value.tick)
        assertEquals(stableViewport, viewport)
        click("Close comparison")
        awaitText("Tick 8")
        assertEquals(8L, stableViewport.poses.value.tick)
    }

    private fun ComposeUiTest.click(text: String) {
        onNodeWithText(text).performScrollTo().performClick()
    }

    private fun ComposeUiTest.enterTick(tick: String) {
        onNode(hasSetTextAction()).performScrollTo().performTextReplacement(tick)
    }

    private fun ComposeUiTest.awaitText(text: String) {
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
