package id.branchworld.renderer

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import id.branchworld.game.platform.viewport.WorldViewport
import id.branchworld.renderer.camera.PreviewController
import id.branchworld.renderer.hud.PreviewHud
import id.branchworld.renderer.input.pointerCameraInput
import id.branchworld.renderer.stage.StageSession
import id.branchworld.renderer.stage.toByteArray
import id.branchworld.simulation.projection.WorldCamera
import id.branchworld.simulation.projection.WorldRenderSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The iOS viewport: a RealityKit [stage] (written in Swift) hosted beneath the Compose interface. Shared Kotlin drives
 * it every frame, and touches arrive through Compose into the same camera, picking and tap-to-place as every platform.
 */
class IosDiorama internal constructor(
    private val stage: WorldStage,
    /** False only in Compose UI tests, whose harness cannot host native views; everything else runs unchanged. */
    private val hostsNativeView: Boolean,
) : WorldViewport {
    constructor(stage: WorldStage) : this(stage, hostsNativeView = true)

    private val controller = PreviewController()
    private val session = StageSession(stage, controller)

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    override fun Viewport(modifier: Modifier, world: WorldRenderSource?) {
        SideEffect { controller.showWorld(world) }
        LaunchedEffect(Unit) {
            // RealityKit paces the frames; Compose only draws the interface over them.
            stage.startFrames(object : StageFrames {
                override fun frame(nanos: Long) = session.frame(nanos)
            })
        }
        Box(modifier.onSizeChanged { controller.resize(it.width, it.height) }) {
            if (hostsNativeView) UIKitView(
                factory = { stage.makeView() },
                modifier = Modifier.matchParentSize(),
                // Compose handles every gesture, so the shared camera and picking see them; the view only draws.
                properties = UIKitInteropProperties(interactionMode = null, isNativeAccessibilityEnabled = false),
            )
            if (world != null) {
                Box(Modifier.matchParentSize().pointerCameraInput(controller).semantics {
                    contentDescription = "Island camera. Drag to turn, two fingers to pan, pinch to zoom."
                })
            }
        }
    }

    override fun present(camera: WorldCamera) = controller.present(camera)

    @Composable
    override fun Hud() = PreviewHud(controller)

    /** The app is hidden or shown; nothing is drawn while it is in the background. */
    fun foreground(visible: Boolean) { controller.foreground.value = visible }

    override suspend fun capturePng(): ByteArray? {
        if (!controller.readyForPhoto) return null
        return suspendCancellableCoroutine { continuation ->
            stage.snapshot { data -> if (continuation.isActive) continuation.resume(data?.toByteArray()) }
        }
    }
}
