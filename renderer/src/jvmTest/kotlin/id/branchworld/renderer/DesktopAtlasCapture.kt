package id.branchworld.renderer

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.fabmax.kool.KoolConfigJvm
import de.fabmax.kool.createContext
import de.fabmax.kool.platform.swing.SwingWindowSubsystem
import id.branchworld.game.App
import id.branchworld.game.PhotoResult
import id.branchworld.game.save.WorldSaveStore
import java.awt.Canvas
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlinx.coroutines.runBlocking

/**
 * Captures the real game at Desktop size: the Kool renderer draws the island offscreen and the actual App composes
 * its menus and HUD over those frames. Runs only when build/verification/<phase>/capture.txt names a fixture folder,
 * because it opens a tiny window and needs a GPU.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopAtlasCapture {
    private class FolderStore(private val folder: File) : WorldSaveStore {
        override suspend fun read(maxBytes: Int): ByteArray? = File(folder, "island-v3.json").takeIf { it.exists() }?.readBytes()
        override suspend fun write(bytes: ByteArray, replace: Boolean) = false
        override suspend fun readFile(name: String, maxBytes: Int): ByteArray? = File(folder, "worlds/$name").takeIf { it.exists() }?.readBytes()
        override suspend fun writeFile(name: String, bytes: ByteArray): Boolean {
            File(folder, "worlds").mkdirs()
            File(folder, "worlds/$name").writeBytes(bytes)
            return true
        }
    }

    @Test
    fun captureTheJourney() {
        val root = File(System.getProperty("user.dir")).parentFile
        // The newest phase's request wins; captures land beside the request that asked for them.
        val request = listOf("living-worlds-09", "atlas-08").map { File(root, "build/verification/$it/capture.txt") }
            .firstOrNull { it.exists() } ?: return
        val (fixture, outName, size) = request.readText().trim().split(" ")
        val (width, height) = size.split("x").map { it.toInt() }
        val evidence = File(request.parentFile, outName).also { it.mkdirs() }
        val store = FolderStore(File(fixture))
        val steps = File(fixture, "steps.txt").readLines().filter { it.isNotBlank() }

        val canvas = Canvas()
        lateinit var frame: JFrame
        SwingUtilities.invokeAndWait {
            frame = JFrame("BranchWorld capture")
            frame.add(canvas)
            frame.setSize(64, 64)
            frame.isVisible = true
        }
        val controller = PreviewController()
        val output = DesktopFrameOutput(controller)
        val window = SwingWindowSubsystem(providedCanvas = canvas, makeFocusable = false)
        thread(name = "branchworld-capture-renderer", isDaemon = true) {
            val context = runBlocking {
                createContext(KoolConfigJvm(windowSubsystem = window, useOpenGlFallback = false, asyncSceneUpdate = false,
                    numSamples = 1, maxFrameRate = 60))
            }
            DioramaSession(context, controller, output)
            context.run()
        }
        try {
            runDesktopComposeUiTest(width = width, height = height) {
                val owner = object : LifecycleOwner {
                    override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
                }
                setContent {
                    CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                        App(
                            viewport = { modifier, projection ->
                                SideEffect { controller.showWorld(projection) }
                                val image by output.frame.collectAsState()
                                Box(modifier.onSizeChanged {
                                    controller.resize(it.width, it.height)
                                    output.resize(it)
                                }) { image?.let { Image(it, null, Modifier.matchParentSize(), contentScale = ContentScale.FillBounds) } }
                            },
                            viewportCamera = controller::present,
                            saveStore = store,
                            savePhoto = { moment ->
                                val png = output.capturePng()
                                if (png == null) PhotoResult.ViewNotReady else {
                                    val saved = File(evidence, "saved-photo-${moment.tick}.png").apply { writeBytes(png) }
                                    val picture = requireNotNull(ImageIO.read(saved))
                                    check(picture.width >= 300 && picture.height >= 300)
                                    println("Captured ${saved.name} ${picture.width}x${picture.height}")
                                    PhotoResult.Saved("the verification folder")
                                }
                            },
                        )
                    }
                }
                for (step in steps) {
                    val (verb, argument) = step.split(" ", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                    when (verb) {
                        // Keep the UI thread pumping so coroutines that resume on it (island loading, frames) proceed.
                        "wait" -> {
                            val end = System.nanoTime() + argument.toLong() * 1_000_000
                            while (System.nanoTime() < end) {
                                waitForIdle()
                                Thread.sleep(40)
                            }
                        }
                        // Live heap after collection, for comparing scenes.
                        "heap" -> {
                            waitForIdle()
                            repeat(3) { System.gc(); Thread.sleep(80) }
                            val runtime = Runtime.getRuntime()
                            println("BranchWorldHeap $argument usedMB=${(runtime.totalMemory() - runtime.freeMemory()) / 1_048_576}")
                        }
                        "await" -> waitUntil(timeoutMillis = 60_000) { onAllNodesWithText(argument, substring = true).fetchSemanticsNodes().isNotEmpty() }
                        "click" -> onAllNodesWithText(argument)[0].apply { runCatching { performScrollTo() } }.performClick()
                        "scroll" -> onAllNodesWithText(argument, substring = true).let { it[it.fetchSemanticsNodes().size - 1] }.performScrollTo()
                        // Only if it is there, such as another page of a list that may already be complete.
                        "maybe" -> onAllNodesWithText(argument).takeIf { it.fetchSemanticsNodes().isNotEmpty() }?.get(0)
                            ?.apply { runCatching { performScrollTo() } }?.performClick()
                        // The last match: the newest card on a shelf, such as a later chapter's Begin.
                        "last" -> onAllNodesWithText(argument).let { it[it.fetchSemanticsNodes().size - 1] }
                            .apply { runCatching { performScrollTo() } }.performClick()
                        "desc" -> onAllNodesWithContentDescription(argument)[0].performClick()
                        "shot" -> {
                            waitForIdle()
                            // Dialogs and popups compose in their own roots; paint each over the screen where it sits.
                            val roots = onAllNodes(isRoot())
                            val image = roots[0].captureToImage().toAwtImage()
                            val canvas2d = image.createGraphics()
                            for (index in 1 until roots.fetchSemanticsNodes().size) {
                                val node = roots[index]
                                val bounds = node.fetchSemanticsNode().boundsInWindow
                                canvas2d.drawImage(node.captureToImage().toAwtImage(), bounds.left.toInt(), bounds.top.toInt(), null)
                            }
                            canvas2d.dispose()
                            ImageIO.write(image, "png", File(evidence, "$argument.png"))
                            println("Captured $argument ${image.width}x${image.height}")
                        }
                    }
                }
            }
        } finally {
            SwingUtilities.invokeAndWait { frame.dispose() }
        }
    }
}
