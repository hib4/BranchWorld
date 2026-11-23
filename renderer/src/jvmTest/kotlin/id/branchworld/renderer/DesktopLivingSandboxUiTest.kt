package id.branchworld.renderer

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import id.branchworld.game.App
import id.branchworld.game.ecology.CreatureAction
import id.branchworld.game.ecology.Species
import id.branchworld.game.save.WorldSaveStore
import id.branchworld.game.simulation.WorldCamera
import id.branchworld.game.simulation.WorldRenderSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The first essential experience through ordinary mouse input: make an empty world, click rabbits and wolves onto the
 * island and let them be. Clicks go through the real Desktop input adapter and picking; no GPU is needed.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopLivingSandboxUiTest {
    private class MemoryStore : WorldSaveStore {
        val files = mutableMapOf<String, ByteArray>()
        override suspend fun read(maxBytes: Int): ByteArray? = null
        override suspend fun write(bytes: ByteArray, replace: Boolean) = false
        override suspend fun readFile(name: String, maxBytes: Int) = files[name]
        override suspend fun writeFile(name: String, bytes: ByteArray): Boolean {
            files[name] = bytes
            return true
        }
    }

    @Test
    fun aPlayerSetsDownRabbitsAndWolvesAndTheyLiveOnTheirOwn() = runDesktopComposeUiTest(width = 1280, height = 800) {
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        }
        val controller = PreviewController()
        var world: WorldRenderSource? = null
        var camera = WorldCamera()
        var width = 1f
        var height = 1f
        val store = MemoryStore()
        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                App(
                    viewport = { modifier, projection ->
                        SideEffect {
                            controller.showWorld(projection)
                            world = projection
                        }
                        Box(modifier.onSizeChanged {
                            width = it.width.toFloat()
                            height = it.height.toFloat()
                            controller.resize(it.width, it.height)
                        }.testTag("viewport").pointerCameraInput(controller))
                    },
                    viewportCamera = {
                        camera = it
                        controller.present(it)
                    },
                    saveStore = store,
                )
            }
        }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("New world").fetchSemanticsNodes().isNotEmpty() }
        // The stories are kept out of a player's way.
        assertTrue(onAllNodesWithText("Stories").fetchSemanticsNodes().isEmpty())
        tap("New world")
        tap("Empty land")
        tap("Create world")
        waitUntil(timeoutMillis = 20_000) { world?.poses?.value?.life != null }
        val source = checkNotNull(world)
        assertTrue(source.poses.value.creatures.isEmpty())
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Rabbit").fetchSemanticsNodes().isNotEmpty() }

        /** A real click on the viewport where [x], [z] is drawn. */
        fun clickCell(x: Int, z: Int) {
            val terrain = source.terrain
            val center = cellPosition(x, z, terrain.cellAt(x, z)!!.elevationTwice)
            val (px, py) = controller.camera.value.screenPoint(Point3(center.x, terrain.heightAt(center.x, center.z), center.z), width, height)!!
            onNodeWithTag("viewport").performMouseInput {
                moveTo(Offset(px, py))
                press(MouseButton.Primary)
                release(MouseButton.Primary)
            }
            waitForIdle()
        }

        // Hovering shows where the rabbit would go.
        val first = source.terrain.cellAt(-2, 0)!!
        assertTrue(first.walkable)
        run {
            val center = cellPosition(-2, 0, first.elevationTwice)
            val (px, py) = controller.camera.value.screenPoint(Point3(center.x, source.terrain.heightAt(center.x, center.z), center.z), width, height)!!
            onNodeWithTag("viewport").performMouseInput { moveTo(Offset(px, py)) }
            waitForIdle()
            assertEquals(-2, source.pointer.hover.value?.ground?.x, "hover pick")
            waitUntil(timeoutMillis = 3_000) { camera.placement != null }
            assertEquals(true, camera.placement?.valid)
        }
        tap("Pause")
        for ((x, z) in listOf(-2 to 0, -1 to 1, -4 to -2, -4 to 4)) clickCell(x, z)
        waitUntil(timeoutMillis = 5_000) { source.poses.value.creatures.count { it.species == Species.Rabbit } == 4 }
        // A drag turns the camera and sets nothing down.
        onNodeWithTag("viewport").performMouseInput {
            moveTo(center)
            press(MouseButton.Primary)
            moveBy(Offset(120f, 30f))
            release(MouseButton.Primary)
        }
        waitForIdle()
        assertEquals(4, source.poses.value.creatures.size)

        tap("Wolf")
        clickCell(-7, 3)
        clickCell(-8, 1)
        waitUntil(timeoutMillis = 5_000) { source.poses.value.creatures.count { it.species == Species.Wolf } == 2 }
        assertTrue(onAllNodesWithText("You set down", substring = true).fetchSemanticsNodes().isEmpty())

        // Then nothing more from the player: let them be, fast.
        tap("Play")
        tap("8×")
        var chased = false
        var fled = false
        waitUntil(timeoutMillis = 60_000) {
            val creatures = source.poses.value.creatures
            if (creatures.any { it.action == CreatureAction.Chasing }) chased = true
            if (creatures.any { it.action == CreatureAction.Fleeing }) fled = true
            chased && fled
        }

        // Look at a wolf while time runs: its card says what it is doing.
        tap("Look")
        waitForIdle()
        tap("Pause")
        val wolf = source.poses.value.creatures.firstOrNull { it.species == Species.Wolf }
        if (wolf != null) {
            clickCell(wolf.ground.x, wolf.ground.z)
            waitUntil(timeoutMillis = 5_000) { camera.selected == wolf.id }
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Follow").fetchSemanticsNodes().isNotEmpty() ||
                onAllNodesWithText("Following").fetchSemanticsNodes().isNotEmpty() }
        }
        // The world was saved to its own slot.
        tap("Menu")
        waitUntil(timeoutMillis = 5_000) { store.files.keys.any { it.startsWith("world-") } }
    }
}
