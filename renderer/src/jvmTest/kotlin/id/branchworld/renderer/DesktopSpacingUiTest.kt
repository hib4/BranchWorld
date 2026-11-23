package id.branchworld.renderer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import id.branchworld.game.App
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Phones keep the wooden pieces close to the screen edge; Desktop keeps its wider gutter. */
@OptIn(ExperimentalTestApi::class)
class DesktopSpacingUiTest {
    private fun shelfInset(width: Int, height: Int): Float {
        var inset = Float.NaN
        runDesktopComposeUiTest(width = width, height = height) {
            val owner = object : LifecycleOwner {
                override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
            }
            setContent { CompositionLocalProvider(LocalLifecycleOwner provides owner) { App() } }
            waitUntil(timeoutMillis = 10_000) { runCatching { onNodeWithText("My worlds").fetchSemanticsNode() }.isSuccess }
            // The My worlds block's left edge: the screen gutter plus the shelf's own 10 dp padding.
            inset = onNodeWithText("My worlds").fetchSemanticsNode().boundsInRoot.left / density.density
        }
        return inset
    }

    @Test
    fun phoneTitleShelfSitsTwelveDpFromTheEdge() {
        val inset = shelfInset(390, 844)
        assertTrue(inset <= 22.5f, "Shelf block starts $inset dp from the edge on a phone")
    }

    @Test
    fun desktopTitleShelfKeepsItsGutter() {
        assertEquals(34f, shelfInset(1280, 800), 0.5f)
    }
}
