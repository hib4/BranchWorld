package id.branchworld.renderer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.fabmax.kool.KoolConfigJvm
import de.fabmax.kool.createContext
import de.fabmax.kool.platform.swing.SwingWindowSubsystem
import id.branchworld.game.platform.viewport.WorldViewport
import id.branchworld.renderer.camera.PreviewController
import id.branchworld.renderer.camera.ViewportStatus
import id.branchworld.renderer.hud.PreviewHud
import id.branchworld.renderer.input.pointerCameraInput
import id.branchworld.renderer.scene.DioramaSession
import id.branchworld.simulation.projection.WorldCamera
import id.branchworld.simulation.projection.WorldRenderSource
import kotlinx.coroutines.runBlocking
import java.awt.BorderLayout
import java.awt.Canvas
import java.awt.event.HierarchyEvent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

class DesktopDiorama : WorldViewport {
    private val controller = PreviewController()
    private val output = DesktopFrameOutput(controller)
    private var subsystem: SwingWindowSubsystem? = null
    private var afterClose: (() -> Unit)? = null

    @Composable
    override fun Viewport(modifier: Modifier, world: WorldRenderSource?) {
        SideEffect { controller.showWorld(world) }
        val frame by output.frame.collectAsState()
        Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh).onSizeChanged { size ->
            controller.resize(size.width, size.height)
            output.resize(size)
        }) {
            // Kool's Vulkan backend needs an AWT peer. The visible viewport is the
            // separately sized offscreen output composited by Compose.
            SwingPanel(
                modifier = Modifier.align(Alignment.BottomEnd).size(2.dp),
                factory = ::createPanel,
            )
            if (world != null) {
                Box(Modifier.matchParentSize().pointerCameraInput(controller).semantics {
                    contentDescription = "Island camera. Arrow keys orbit. Shift and arrow keys pan. Plus and minus zoom. Home resets the view."
                }) {
                    frame?.let {
                        Image(it, contentDescription = null, modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.FillBounds)
                    }
                }
            }
        }
    }

    override fun present(camera: WorldCamera) = controller.present(camera)

    override suspend fun capturePng(): ByteArray? = output.capturePng()

    @Composable
    override fun Hud() = PreviewHud(controller)

    fun foreground(visible: Boolean) { controller.foreground.value = visible }

    fun close(onClosed: () -> Unit) {
        controller.showWorld(null)
        val window = subsystem
        if (window == null) {
            onClosed()
        } else {
            afterClose = onClosed
            window.close {}
        }
    }

    private fun createPanel(): JPanel {
        val canvas = Canvas().apply { isFocusable = false }
        canvas.addHierarchyListener { event ->
            if (subsystem == null && event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L && canvas.isShowing) {
                startRenderer(canvas)
            }
        }
        return JPanel(BorderLayout()).apply { add(canvas, BorderLayout.CENTER) }
    }

    private fun startRenderer(canvas: Canvas) {
        val window = SwingWindowSubsystem(providedCanvas = canvas, makeFocusable = false)
        subsystem = window
        thread(name = "branchworld-renderer", isDaemon = true) {
            try {
                val context = runBlocking {
                    createContext(KoolConfigJvm(
                        windowSubsystem = window,
                        useOpenGlFallback = false,
                        asyncSceneUpdate = false,
                        numSamples = 1,
                        maxFrameRate = 60,
                    ))
                }
                DioramaSession(context, controller, output)
                context.run()
            } catch (error: Exception) {
                initializationFailed(error)
            } catch (error: LinkageError) {
                initializationFailed(error)
            } finally {
                SwingUtilities.invokeLater {
                    subsystem = null
                    afterClose?.invoke()
                    afterClose = null
                }
            }
        }
    }

    private fun initializationFailed(error: Throwable) {
        controller.status.value = ViewportStatus.Failed("Renderer unavailable. Reopen the app to try again.")
        error.printStackTrace()
    }
}
