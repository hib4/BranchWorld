package id.branchworld.renderer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import id.branchworld.game.App
import id.branchworld.game.simulation.WorldCamera
import id.branchworld.game.simulation.WorldRenderSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DesktopVillageUiTest {
    /** A new player reaches Hollowmere's petition with ordinary controls and answers it. */
    @Test
    fun hollowmereAsksAndTheKeeperAnswers() = runDesktopComposeUiTest(width = 1280, height = 800) {
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        }
        var viewport: WorldRenderSource? = null
        var camera = WorldCamera()
        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                App(viewport = { _, world -> SideEffect { viewport = world } }, viewportCamera = { camera = it })
            }
        }
        waitUntil(timeoutMillis = 5_000) { viewport != null }
        val world = checkNotNull(viewport)
        onNodeWithText("Begin story").performScrollTo().performClick()
        waitUntil(timeoutMillis = 20_000) { world.poses.value.tick == 240L }
        onNodeWithText("Play").performClick()
        // The village raises its petition from its own thirst and the clock holds for the Keeper.
        waitUntil(timeoutMillis = 30_000) { onAllNodesWithText("Hollowmere asks for guidance").fetchSemanticsNodes().isNotEmpty() }
        val asked = world.poses.value.tick
        assertEquals(420L, asked)
        onNodeWithText("Hollowmere is waiting for an answer").assertExists()
        onNodeWithText("Endorse · Settle the river flats").performClick()
        onNodeWithText("You endorsed: Settle the river flats. The village gathers to begin it.").assertExists()
        onNodeWithText("Change my answer").performClick()
        onNodeWithText("Endorse · Dig a well").performClick()
        onNodeWithText("You endorsed: Dig a well. The village gathers to begin it.").assertExists()
        assertEquals(asked, world.poses.value.tick)

        onNodeWithText("Play").performClick()
        waitUntil(timeoutMillis = 15_000) { world.poses.value.tick >= 480L }
        onNodeWithText("Pause").performClick()
        val well = world.poses.value.settlements!!.first().village!!.structures
        assertTrue(well.any { it.kind == id.branchworld.game.ecology.StructureKind.Well })

        onAllNodesWithText("Hollowmere")[0].performClick()
        waitUntil(timeoutMillis = 5_000) { camera.focus is WorldCamera.Focus.Village }
        onNodeWithText("Chronicle").assertExists()
        onNodeWithText("The Keeper answered: Dig a well.").performScrollTo().assertExists()
        onNodeWithText("Hollowmere asked the Keeper how to face the drought.").performScrollTo().assertExists()
        onNodeWithText("Work began on a well.").performScrollTo().assertExists()
    }

    @Test
    fun shortLandscapeKeepsThePetitionCompact() = runDesktopComposeUiTest(width = 850, height = 390) {
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
        val world = checkNotNull(viewport)
        onNodeWithText("Begin story").performScrollTo().performClick()
        waitUntil(timeoutMillis = 20_000) { world.poses.value.tick == 240L }
        onNodeWithText("Play").performClick()
        waitUntil(timeoutMillis = 30_000) { onAllNodesWithText("Hollowmere asks for guidance").fetchSemanticsNodes().isNotEmpty() }
        // Reasons stay one tap away so the card leaves the valley visible.
        onNodeWithText("Endorse · Dig a well").assertExists()
        onAllNodesWithText("The banks are failing and families are hauling water. How should the village face the drought?")
            .fetchSemanticsNodes().let { assertTrue(it.isEmpty()) }
        onNodeWithText("What each plan means").performClick()
        onNodeWithText("The banks are failing and families are hauling water. How should the village face the drought?").assertExists()
        val card = onNodeWithText("Hollowmere asks for guidance").fetchSemanticsNode().boundsInRoot
        assertTrue(card.top >= 0f)
    }
}
