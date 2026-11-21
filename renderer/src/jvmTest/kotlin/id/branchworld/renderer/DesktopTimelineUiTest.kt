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
        var viewport: WorldRenderSource? = null
        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                App(viewport = { _, world -> SideEffect { viewport = world } })
            }
        }
        awaitText("Begin story")
        waitUntil(timeoutMillis = 5_000) { viewport != null }
        click("Begin story")
        onNodeWithText("Pause").performClick()
        onNodeWithText("Menu").performClick()
        click("Show diagnostics")
        click("Timeline tick tools")
        onNodeWithText("Close").performClick()
        awaitText("Timeline")
        onNodeWithText("Timeline").performClick()
        enterTick("60")
        onNodeWithText("Play to tick").performClick()
        waitUntil(timeoutMillis = 8_000) { viewport?.poses?.value?.tick == 60L }
        enterTick("20")
        onNodeWithText("Go to tick").performClick()
        waitUntil(timeoutMillis = 5_000) { viewport?.poses?.value?.tick == 20L }
        onNodeWithText("Fork here").performClick()
        onNodeWithText("Create a branch at tick 20").assertIsDisplayed()
        onAllNodes(hasSetTextAction())[1].performTextReplacement("Restored Forest")
        onNodeWithText("Create branch").performClick()
        awaitText("Branch Restored Forest")
        enterTick("60")
        onNodeWithText("Play to tick").performClick()
        waitUntil(timeoutMillis = 8_000) { viewport?.poses?.value?.tick == 60L }
        onNodeWithText("Main").performClick()
        awaitText("Branch Main")
        waitUntil(timeoutMillis = 8_000) { viewport?.poses?.value?.tick == 60L }
    }

    @Test
    fun compareScrubberAndViewportHoldTheSameTickForBothBranches() = runDesktopComposeUiTest(width = 720, height = 1000) {
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        }
        var viewport: WorldRenderSource? = null
        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                App(viewport = { _, world -> SideEffect { viewport = world } })
            }
        }
        waitUntil(timeoutMillis = 5_000) { viewport != null }
        click("Begin story")
        onNodeWithText("Pause").performClick()
        onNodeWithText("Menu").performClick()
        click("Show diagnostics")
        click("Timeline tick tools")
        onNodeWithText("Close").performClick()
        awaitText("Timeline")
        onNodeWithText("Timeline").performClick()
        enterTick("60")
        click("Play to tick")
        waitUntil(timeoutMillis = 8_000) { viewport?.poses?.value?.tick == 60L }
        enterTick("20")
        click("Go to tick")
        waitUntil(timeoutMillis = 5_000) { viewport?.poses?.value?.tick == 20L }
        click("Fork here")
        onNodeWithText("Create branch").performClick()
        awaitText("Branch Branch 2")
        enterTick("60")
        click("Play to tick")
        waitUntil(timeoutMillis = 8_000) { viewport?.poses?.value?.tick == 60L }
        val stableViewport = checkNotNull(viewport)
        click("Compare branches")
        awaitText("Viewing Main at tick 60")
        click("Compare details")
        onNodeWithContentDescription("Timeline position").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        awaitText("Viewing Main at tick 20")
        click("Show right")
        awaitText("Viewing Branch 2 at tick 20")
        assertEquals(2, stableViewport.poses.value.branchId)
        assertEquals(20L, stableViewport.poses.value.tick)
        assertEquals(stableViewport, viewport)
        click("Close comparison")
        waitUntil(timeoutMillis = 8_000) { viewport?.poses?.value?.tick == 60L }
        assertEquals(60L, stableViewport.poses.value.tick)
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
