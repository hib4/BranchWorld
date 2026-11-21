package id.branchworld.renderer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import id.branchworld.game.App
import id.branchworld.game.WorldAmbience
import id.branchworld.game.simulation.WorldCamera
import id.branchworld.game.simulation.WorldRenderSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

@OptIn(ExperimentalTestApi::class)
class DesktopEcologyUiTest {
    @Test
    fun compactInspectorDisclosesSettlementDetailsOnDemand() = runComposeUiTest {
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        }
        var viewport: WorldRenderSource? = null
        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                Box(Modifier.requiredSize(390.dp, 720.dp)) {
                    App(viewport = { _, world -> SideEffect { viewport = world } })
                }
            }
        }
        waitUntil(timeoutMillis = 5_000) { viewport != null }
        onNodeWithText("Begin story").performScrollTo().performClick()
        onNodeWithText("Pause").performClick()
        onNodeWithText("Inspect").performClick()
        val settlement = onNodeWithText("Settlements")
        val searching = onNodeWithText("Settlers are looking for a site near freshwater and forage.")
        searching.assertDoesNotExist()
        settlement.performClick()
        searching.assertExists()
        settlement.performClick()
        searching.assertDoesNotExist()
    }

    @Test
    fun openingKeepsTheSameWorldAndPlantedGroveAppearsOnTheNextTick() = runComposeUiTest {
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
        val opening = world.poses.value
        assertEquals(0L, opening.tick)
        assertEquals(7, opening.creatures.size)
        onNodeWithText("The Fading Grove").assertExists()
        onNodeWithText("Begin story").performScrollTo().performClick()
        onNodeWithText("Pause").performClick()
        assertSame(world, viewport)
        onNodeWithText("Send bunny left").assertDoesNotExist()

        val parked = world.poses.value.tick
        onNodeWithText("Restore grove").performClick()
        waitUntil(timeoutMillis = 5_000) { camera.focus == WorldCamera.Focus.Region(6) }
        onNodeWithText("Plant saplings").performScrollTo().performClick()
        onNodeWithText("Saplings will be planted when time moves forward.").assertExists()
        assertEquals(parked, world.poses.value.tick)
        assertEquals(0, world.poses.value.forage.count { it.planted })
        onNodeWithText("Play").performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { world.poses.value.forage.any { it.planted } }
        onNodeWithText("Pause").performScrollTo().performClick()
        onNodeWithText("Close").performClick()
        waitUntil(timeoutMillis = 5_000) { camera.focus == null }

        onNodeWithText("Menu").performClick()
        onNodeWithText("Back to home").performScrollTo().performClick()
        onNodeWithText("Begin story").performScrollTo().performClick()
        onNodeWithText("Play").assertExists()
        assertSame(world, viewport)
    }

    @Test
    fun ambienceHonorsMuteAndReturnsOnlyWhenTheWorldIsForegrounded() = runComposeUiTest {
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        }
        var mix = WorldAmbience.Silent
        var request = WorldCamera()
        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                App(playSound = {}, setAmbience = { mix = it }, viewportCamera = { request = it })
            }
        }
        waitUntil(timeoutMillis = 5_000) { mix == WorldAmbience.Sunlit }
        onNodeWithText("Menu").performScrollTo().performClick()
        onNodeWithText("Reduce motion").performScrollTo().performClick()
        waitUntil { request.reducedMotion }
        onNodeWithText("World sounds").performScrollTo().performClick()
        waitUntil { mix == WorldAmbience.Silent }
        onNodeWithText("World sounds").performClick()
        waitUntil { mix == WorldAmbience.Sunlit }
        runOnIdle { owner.lifecycle.currentState = Lifecycle.State.STARTED }
        waitUntil { mix == WorldAmbience.Silent }
        runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        waitUntil { mix == WorldAmbience.Sunlit }
    }
}
