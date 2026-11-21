package id.branchworld.renderer

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntSize
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.pipeline.AttachmentConfig
import de.fabmax.kool.pipeline.OffscreenPass2d
import de.fabmax.kool.pipeline.TexFormat
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.Uint8Buffer
import de.fabmax.kool.util.releaseWith
import de.fabmax.kool.util.useRaw
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import kotlin.math.roundToInt

internal class DesktopFrameOutput(private val controller: PreviewController) : DioramaOutput {
    val frame = MutableStateFlow<ImageBitmap?>(null)
    private val requestedSize = MutableStateFlow(Vec2i(640, 360))
    private var target: Target? = null

    fun resize(size: IntSize) {
        if (size.width < 2 || size.height < 2) return
        val ratio = minOf(1f, 1152f / maxOf(size.width, size.height))
        requestedSize.value = Vec2i(
            (size.width * ratio).roundToInt().coerceAtLeast(2),
            (size.height * ratio).roundToInt().coerceAtLeast(2),
        )
    }

    override fun attach(scene: Scene) {
        val pass = OffscreenPass2d(
            drawNode = scene,
            attachmentConfig = AttachmentConfig {
                addColor(TexFormat.RGBA, scene.clearColor)
                defaultDepth()
            },
            initialSize = requestedSize.value,
            name = "Compose viewport",
        ).apply {
            camera = scene.camera
            lighting = scene.lighting
            isReleaseDrawNode = false
        }
        val copy = pass.copyOutput(isCopyColor = true, isCopyDepth = false).releaseWith(scene)
        scene.addOffscreenPass(pass)
        scene.mainRenderPass.isEnabled = false
        target = Target(scene, pass, copy.colorCopy2d)
    }

    override fun update() {
        val current = target ?: return
        val size = requestedSize.value
        if (size.x != current.pass.width || size.y != current.pass.height) {
            current.pass.setSize(size.x, size.y)
            current.resizeRevision++
        }
        if (!current.texture.isLoaded || current.readback?.isActive == true || current.failed) return
        val resizeRevision = current.resizeRevision
        current.readback = current.scene.coroutineScope.launch {
            try {
                val pixels = current.texture.download()
                if (target !== current || !controller.foreground.value || requestedSize.value != size ||
                    resizeRevision != current.resizeRevision || pixels.width != size.x || pixels.height != size.y
                ) return@launch
                val bytes = ByteArray(pixels.width * pixels.height * 4)
                (pixels.data as Uint8Buffer).useRaw { it.duplicate().clear().get(bytes) }
                // The scene fills the viewport. Vulkan blend pipelines can leave translucent
                // fragment alpha in the attachment even though RGB is already composited.
                for (alpha in 3 until bytes.size step 4) bytes[alpha] = 0xff.toByte()
                val info = ImageInfo(pixels.width, pixels.height, ColorType.RGBA_8888, ColorAlphaType.OPAQUE)
                Image.makeRaster(info, bytes, pixels.width * 4).use { image ->
                    frame.value = image.toComposeImageBitmap()
                }
                if (!current.reported && controller.status.value == "Island ready") {
                    current.reported = true
                    val elapsed = controller.enteredAt.value?.elapsedNow()?.inWholeMilliseconds
                    println("BranchWorldRenderer composeAssetFrameMs=$elapsed viewport=${pixels.width}x${pixels.height}")
                }
                if (current.reported) current.timing.frame()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (target !== current) return@launch
                current.failed = true
                controller.status.value = "Could not display the rendered island"
                error.printStackTrace()
            }
        }
    }

    override fun detach() {
        target?.readback?.cancel()
        target = null
        frame.value = null
    }

    private class Target(val scene: Scene, val pass: OffscreenPass2d, val texture: Texture2d) {
        var readback: Job? = null
        var failed = false
        var reported = false
        var resizeRevision = 0
        val timing = FrameTiming("compose")
    }
}
