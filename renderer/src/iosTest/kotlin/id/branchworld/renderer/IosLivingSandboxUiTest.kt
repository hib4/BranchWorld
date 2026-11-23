@file:OptIn(ExperimentalTestApi::class)

package id.branchworld.renderer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import id.branchworld.game.platform.AppServices
import id.branchworld.game.platform.storage.WorldSaveStore
import id.branchworld.game.ui.app.App
import id.branchworld.renderer.stage.BundleAssets
import platform.Foundation.NSData
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIView
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The first essential experience on iOS, through the real iOS viewport and Compose touch input: make an empty world,
 * tap rabbits onto the island, and see the session put the island and the animals on the stage. A recording stage
 * stands in for the Swift RealityKit one, so no GPU is needed.
 */
class IosLivingSandboxUiTest {
    private class RecordingStage : WorldStage {
        var meshes = 0
        val entities = mutableSetOf<Int>()
        var transformBytes = 0L
        var cleared = 0
        override fun makeView() = UIView()
        override fun clearScene() { cleared++; entities.clear() }
        override fun defineMesh(mesh: Int, positions: NSData, normals: NSData, colors: NSData, texCoords: NSData, indices: NSData) { meshes++ }
        override fun defineTexture(texture: Int, image: NSData) {}
        override fun defineMaterial(material: Int, shading: Int, texture: Int, r: Float, g: Float, b: Float, a: Float, variant: Int) {}
        override fun addEntity(entity: Int, parent: Int, mesh: Int, material: Int) { entities += entity }
        override fun removeEntity(entity: Int) { entities -= entity }
        override fun setTransforms(records: NSData) { transformBytes += records.length.toLong() }
        override fun setCamera(eyeX: Float, eyeY: Float, eyeZ: Float, targetX: Float, targetY: Float, targetZ: Float, fovDegrees: Float) {}
        override fun setLighting(texels: NSData, backgroundR: Float, backgroundG: Float, backgroundB: Float) {}
        override fun snapshot(done: (NSData?) -> Unit) = done(null)
        private var frames: StageFrames? = null
        private var clock = 0L
        override fun startFrames(frames: StageFrames) { this.frames = frames }

        /** One drawn frame, 16 ms after the last. */
        fun tick(): Boolean {
            clock += 16_000_000L
            frames?.frame(clock)
            return true
        }
    }

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
    fun aPlayerTapsRabbitsOntoTheIslandAndTheStageShowsThem() = runComposeUiTest {
        // The test binary runs from renderer/build; the models are read from the renderer's resources.
        val executable = NSProcessInfo.processInfo.arguments.first() as String
        BundleAssets.root = executable.substringBefore("/build/") + "/src/commonMain/resources"
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        }
        val stage = RecordingStage()
        val viewport = IosDiorama(stage, hostsNativeView = false)
        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                App(AppServices(viewport = viewport, saveStore = MemoryStore()))
            }
        }
        fun tap(label: String) {
            waitUntil(timeoutMillis = 10_000) {
                onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() ||
                    onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty()
            }
            val byText = onAllNodesWithText(label)
            (if (byText.fetchSemanticsNodes().isNotEmpty()) byText[0] else onAllNodesWithContentDescription(label)[0]).performClick()
            waitForIdle()
        }
        tap("New world")
        tap("Empty land")
        tap("Create world")
        waitUntil(timeoutMillis = 30_000) { onAllNodesWithText("Rabbit").fetchSemanticsNodes().isNotEmpty() ||
            onAllNodesWithContentDescription("Rabbit").fetchSemanticsNodes().isNotEmpty() }
        // The island went onto the stage: terrain chunks, sea, pines and details.
        waitUntil(timeoutMillis = 10_000) { stage.tick() && stage.meshes > 20 }
        val islandEntities = stage.entities.size
        tap("Pause")
        tap("Rabbit")
        // Touch taps across the middle of the island, through Compose touch input into the shared picking.
        val camera = onAllNodesWithContentDescription("Island camera", substring = true)[0]
        for ((fx, fy) in listOf(0.5f to 0.45f, 0.42f to 0.5f, 0.58f to 0.5f, 0.5f to 0.55f, 0.46f to 0.4f)) {
            camera.performTouchInput {
                down(Offset(width * fx, height * fy))
                up()
            }
            waitForIdle()
        }
        // Each rabbit is an entity tree from its glTF model: the stage gains entities and receives their poses.
        waitUntil(timeoutMillis = 10_000) { stage.tick() && stage.entities.size > islandEntities + 5 }
        val before = stage.transformBytes
        waitUntil(timeoutMillis = 5_000) { stage.tick() && stage.transformBytes > before }
        assertTrue(stage.cleared >= 1)
    }
}
