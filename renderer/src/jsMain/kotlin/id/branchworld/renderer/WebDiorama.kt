package id.branchworld.renderer

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import de.fabmax.kool.KoolConfigJs
import de.fabmax.kool.createContext
import de.fabmax.kool.pipeline.backend.gl.RenderBackendGl
import id.branchworld.game.platform.viewport.WorldViewport
import id.branchworld.renderer.camera.PreviewController
import id.branchworld.renderer.camera.ViewportStatus
import id.branchworld.renderer.hud.PreviewHud
import id.branchworld.renderer.input.pointerCameraInput
import id.branchworld.renderer.scene.DioramaSession
import id.branchworld.simulation.projection.WorldCamera
import id.branchworld.simulation.projection.WorldRenderSource
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.w3c.dom.HTMLCanvasElement
import org.w3c.files.Blob
import kotlin.coroutines.resume

/**
 * The browser viewport. Kool draws into a page-level `<canvas>` that sits beneath the transparent
 * Compose canvas, so every HUD element stays on top while the world fills the window. Pointer and key
 * input arrive through Compose and drive the same [PreviewController] as Desktop.
 */
class WebDiorama(private val canvasId: String = DefaultCanvasId) : WorldViewport {
    private val controller = PreviewController()
    private var canvas: HTMLCanvasElement? = null
    private var started = false

    @Composable
    override fun Viewport(modifier: Modifier, world: WorldRenderSource?) {
        SideEffect { controller.showWorld(world) }
        LaunchedEffect(Unit) { start() }
        Box(modifier.onSizeChanged { controller.resize(it.width, it.height) }) {
            if (world != null) {
                Box(Modifier.matchParentSize().pointerCameraInput(controller).semantics {
                    contentDescription = "Island camera. Drag to turn, two fingers or Shift-drag to pan, " +
                        "scroll or pinch to zoom. Arrow keys orbit, Shift and arrow keys pan, plus and minus zoom, Home resets the view."
                })
            }
        }
    }

    override fun present(camera: WorldCamera) = controller.present(camera)

    @Composable
    override fun Hud() = PreviewHud(controller)

    fun foreground(visible: Boolean) { controller.foreground.value = visible }

    /** A PNG of the current frame, or null while the island is still loading. */
    override suspend fun capturePng(): ByteArray? {
        val target = canvas ?: return null
        if (!controller.readyForPhoto || target.width < 2 || target.height < 2) return null
        val blob = suspendCancellableCoroutine<Blob?> { continuation ->
            target.toBlob({ continuation.resume(it) }, "image/png")
        } ?: return null
        return blob.bytes()
    }

    private fun start() {
        if (started) return
        started = true
        val target = document.getElementById(canvasId) as? HTMLCanvasElement
        if (target == null) {
            controller.status.value = ViewportStatus.Failed("Renderer unavailable. Reload the page to try again.")
            return
        }
        // Kool asks the canvas for "webgl2" again and receives this same context. Creating it here first
        // keeps the drawing buffer for photos and lets an unsupported browser show a message instead of an alert.
        val gl = target.getContext("webgl2", WebGlOptions)
        if (gl == null) {
            controller.status.value = ViewportStatus.Failed("This browser cannot draw the island. Try a current Chrome, Edge, Firefox or Safari.")
            return
        }
        canvas = target
        MainScope().launch {
            try {
                val context = createContext(KoolConfigJs(
                    canvasName = canvasId,
                    renderBackend = RenderBackendGl,
                    useWebGlFallback = false,
                    isGlobalKeyEventGrabbing = false,
                    numSamples = 4,
                    deviceScaleLimit = 2.0,
                ))
                DioramaSession(context, controller)
                context.run()
            } catch (error: Throwable) {
                controller.status.value = ViewportStatus.Failed("Renderer unavailable. Reload the page to try again.")
                console.error("BranchWorld renderer could not start", error)
            }
        }
    }

    companion object {
        const val DefaultCanvasId = "branchworld-kool"

        private val WebGlOptions: dynamic = js("({ antialias: true, stencil: false, preserveDrawingBuffer: true, powerPreference: 'high-performance' })")
    }
}

private suspend fun Blob.bytes(): ByteArray {
    val buffer: ArrayBuffer = suspendCancellableCoroutine { continuation ->
        asDynamic().arrayBuffer().then { result: ArrayBuffer -> continuation.resume(result) }
    }
    return Int8Array(buffer).unsafeCast<ByteArray>()
}
