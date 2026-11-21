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
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
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
import id.branchworld.game.terrain.IslandLandscape
import id.branchworld.game.terrain.LandmarkKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DesktopEcologyUiTest {
    @Test
    fun namedGalleryLoadsEachLandscapeAndLandmarkFocus() = runComposeUiTest {
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        }
        var viewport: WorldRenderSource? = null
        var camera = WorldCamera()
        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                Box(Modifier.requiredSize(390.dp, 720.dp)) {
                    App(viewport = { _, world -> SideEffect { viewport = world } }, viewportCamera = { camera = it })
                }
            }
        }
        waitUntil(timeoutMillis = 5_000) { viewport?.terrain?.watershed?.landscape == IslandLandscape.Valley }
        val valley = viewport
        onNodeWithText("Explore islands").performScrollTo().performClick()
        onNodeWithText("Cairnwater").performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { viewport?.terrain?.watershed?.landscape == IslandLandscape.Tarn }
        onNodeWithText("Explore island").performScrollTo().performClick()
        onNodeWithText("Pause").performClick()
        onNodeWithText("Inspect").performClick()
        onNodeWithText("Landmarks").performClick()
        onNodeWithText("Cairnwater Lake").performScrollTo().performClick()
        waitUntil { camera.focus == WorldCamera.Focus.Landmark(LandmarkKind.Basin) }
        onNodeWithText("Close").performClick()
        onNodeWithText("Menu").performClick()
        onNodeWithText("Back to home").performScrollTo().performClick()
        onNodeWithText("Explore islands").performScrollTo().performClick()
        onNodeWithText("Heron Reach").performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { viewport?.terrain?.watershed?.landscape == IslandLandscape.Marsh }
        onNodeWithText("Explore island").performScrollTo().performClick()
        onNodeWithText("Pause").performClick()
        onNodeWithText("Inspect").performClick()
        onNodeWithText("Landmarks").performClick()
        onNodeWithText("Heron Marsh").performScrollTo().performClick()
        waitUntil { camera.focus == WorldCamera.Focus.Landmark(LandmarkKind.Basin) }
        onNodeWithText("Close").performClick()
        onNodeWithText("Menu").performClick()
        onNodeWithText("Back to home").performScrollTo().performClick()
        onNodeWithText("Explore islands").performScrollTo().performClick()
        onNodeWithText("The Fading Grove").performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { viewport === valley }
    }

    @Test
    fun compactInspectorDisclosesSettlementDetailsOnDemand() = runComposeUiTest {
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        }
        var viewport: WorldRenderSource? = null
        var camera = WorldCamera()
        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                Box(Modifier.requiredSize(390.dp, 720.dp)) {
                    App(viewport = { _, world -> SideEffect { viewport = world } }, viewportCamera = { camera = it })
                }
            }
        }
        waitUntil(timeoutMillis = 5_000) { viewport != null }
        onNodeWithText("Begin story").performScrollTo().performClick()
        onNodeWithText("Pause").performClick()
        onNodeWithText("Inspect").performClick()
        onNodeWithText("Select a creature").performScrollTo().performClick()
        onNodeWithText("Russet · Fox").performClick()
        onNodeWithText("Russet · Fox").assertExists()
        onNodeWithText("Play").assertExists()
        waitUntil { camera.focus == WorldCamera.Focus.Creature(8) }
        onNodeWithText("Wildlife history").performScrollTo().performClick()
        onNodeWithText("Births, changes of range, and losses will appear here as this history unfolds.").assertExists()
        onNodeWithText("Wildlife history").performClick()
        val settlement = onNodeWithText("Settlements")
        val searching = onNodeWithText("Settlers are looking for a site near freshwater and forage.")
        searching.assertDoesNotExist()
        settlement.performClick()
        searching.assertExists()
        settlement.performClick()
        searching.assertDoesNotExist()
        onNodeWithText("Close").performClick()
        waitUntil { camera.focus == WorldCamera.Focus.Creature(8) }
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
        assertEquals(9, opening.creatures.size)
        assertEquals(2, opening.wildlife?.foxes)
        onNodeWithText("The Fading Grove").assertExists()
        onNodeWithText("Begin story").performScrollTo().performClick()
        onNodeWithText("Pause").performClick()
        assertSame(world, viewport)
        onNodeWithText("Send bunny left").assertDoesNotExist()

        onNodeWithText("Shape").performClick()
        onNodeWithText("Watch Hollowmere").performScrollTo().performClick()
        waitUntil(timeoutMillis = 16_000) { world.poses.value.tick == 240L }
        val parked = world.poses.value.tick
        onNodeWithText("Shape").performClick()
        onNodeWithText("Restore grove").performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { camera.focus == WorldCamera.Focus.Region(9) && camera.targetSites.size == 6 }
        onNodeWithText("Plant saplings").performScrollTo().performClick()
        onNodeWithText("Queued. Watch the next harvest to see it take effect.").assertExists()
        onNodeWithText("Cancel intervention").performScrollTo().performClick()
        onNodeWithText("Plant saplings").performScrollTo().performClick()
        assertEquals(parked, world.poses.value.tick)
        assertEquals(0, world.poses.value.forage.count { it.planted })
        onNodeWithText("Watch the next harvest").performScrollTo().performClick()
        waitUntil(timeoutMillis = 6_000) { world.poses.value.tick == 300L }
        assertEquals(6, world.poses.value.forage.count { it.planted })
        waitUntil(timeoutMillis = 5_000) { camera.focus == null }

        onNodeWithText("Menu").performClick()
        onNodeWithText("Back to home").performScrollTo().performClick()
        onNodeWithText("Return to this history").performScrollTo().performClick()
        onNodeWithText("Play").assertExists()
        assertSame(world, viewport)
    }

    @Test
    fun shortLandscapeKeepsTheTargetingPanelBesideTheIsland() = runDesktopComposeUiTest(width = 850, height = 390) {
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
        onNodeWithText("Begin story").performScrollTo().performClick()
        onNodeWithText("Shape").performClick()
        val power = onNodeWithText("Call rain").fetchSemanticsNode().boundsInRoot
        assertTrue(power.left > 440f, "The targeting controls should sit to the right of the landscape")
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
