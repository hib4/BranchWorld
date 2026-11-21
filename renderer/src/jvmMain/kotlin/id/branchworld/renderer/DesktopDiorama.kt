package id.branchworld.renderer

import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import de.fabmax.kool.KoolConfigJvm
import de.fabmax.kool.createContext
import de.fabmax.kool.platform.swing.SwingWindowSubsystem
import id.branchworld.game.simulation.WorldRenderSource
import java.awt.BorderLayout
import java.awt.Canvas
import java.awt.event.HierarchyEvent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking

class DesktopDiorama {
    private val controller = PreviewController()
    private val output = DesktopFrameOutput(controller)
    private var subsystem: SwingWindowSubsystem? = null
    private var afterClose: (() -> Unit)? = null

    @Composable
    fun Viewport(modifier: Modifier, projection: WorldRenderSource?) {
        SideEffect { controller.showWorld(projection) }
        val frame by output.frame.collectAsState()
        val focus = remember { FocusRequester() }
        Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh).onSizeChanged(output::resize)) {
            // Kool's Vulkan backend needs an AWT peer. The visible viewport is the
            // separately sized offscreen output composited by Compose.
            SwingPanel(
                modifier = Modifier.align(Alignment.BottomEnd).size(2.dp),
                factory = ::createPanel,
            )
            if (projection != null) {
                frame?.let {
                    Image(
                        bitmap = it,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.matchParentSize()
                            .focusRequester(focus)
                            .onPreviewKeyEvent(::cameraKey)
                            .focusable()
                            .semantics { contentDescription = "Island camera. Arrow keys orbit. Shift and arrow keys pan." }
                            .desktopCameraInput(controller) { focus.requestFocus() },
                    )
                }
            }
        }
    }

    @Composable
    fun Hud() = PreviewHud(controller)

    fun foreground(visible: Boolean) { controller.foreground.value = visible }

    private fun cameraKey(event: KeyEvent): Boolean {
        if (!controller.active || event.type != KeyEventType.KeyDown) return false
        val direction = when (event.key) {
            Key.DirectionLeft -> Offset(-0.08f, 0f)
            Key.DirectionRight -> Offset(0.08f, 0f)
            Key.DirectionUp -> Offset(0f, -0.08f)
            Key.DirectionDown -> Offset(0f, 0.08f)
            else -> return false
        }
        if (event.isShiftPressed) controller.pan(direction.x, direction.y)
        else controller.orbit(direction.x, -direction.y)
        return true
    }

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
                        maxFrameRate = 30,
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
        controller.status.value = "Renderer unavailable. Reopen the app to try again."
        error.printStackTrace()
    }
}
