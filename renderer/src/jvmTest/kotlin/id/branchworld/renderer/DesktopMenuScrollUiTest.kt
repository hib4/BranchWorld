package id.branchworld.renderer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import id.branchworld.game.ui.app.App
import kotlin.test.Test
import kotlin.test.assertTrue

/** A menu page taller than a short phone window scrolls its last board fully into view, never leaving it cut off. */
@OptIn(ExperimentalTestApi::class)
class DesktopMenuScrollUiTest {
    @Test
    fun newWorldPageScrollsItsCreateButtonFullyIntoView() = runDesktopComposeUiTest(width = 390, height = 560) {
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        }
        setContent { CompositionLocalProvider(LocalLifecycleOwner provides owner) { App() } }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("New world").fetchSemanticsNodes().isNotEmpty() }
        tap("New world")
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Create world").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Create world").performScrollTo()
        waitForIdle()
        val bounds = onNodeWithText("Create world").fetchSemanticsNode().boundsInRoot
        val bottom = bounds.bottom / density.density
        assertTrue(bounds.top >= 0f && bottom <= 560f, "Create world sits at ${bounds.top / density.density}..$bottom dp in a 560 dp window")
    }
}
