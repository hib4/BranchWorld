package id.branchworld.renderer

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.viewinterop.AndroidView
import de.fabmax.kool.KoolConfigAndroid
import de.fabmax.kool.createKoolContext
import de.fabmax.kool.platform.KoolContextAndroid
import de.fabmax.kool.platform.KoolSurfaceView
import id.branchworld.game.simulation.WorldCamera
import id.branchworld.game.simulation.WorldRenderSource
import java.io.ByteArrayOutputStream
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay
import kotlin.coroutines.resume
import kotlin.math.hypot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class AndroidDiorama(context: Context) {
    private val appContext = context.applicationContext
    private val controller = AndroidRenderer.controller

    @Composable
    fun Viewport(modifier: Modifier, projection: WorldRenderSource?) {
        AndroidView(
            modifier = modifier.onSizeChanged { controller.resize(it.width, it.height) },
            factory = {
                try {
                    AndroidRenderer.surface(appContext)
                } catch (error: IllegalStateException) {
                    controller.status.value = "Renderer unavailable. Reopen the app to try again."
                    android.util.Log.e("BranchWorldRenderer", "Could not start the Kool surface", error)
                    View(it)
                }
            },
            update = { view ->
                if (view is GLSurfaceView) {
                    controller.showWorld(projection)
                    if (projection != null && view.renderMode != GLSurfaceView.RENDERMODE_CONTINUOUSLY) {
                        view.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                    }
                }
            },
        )
    }

    fun present(camera: WorldCamera) = controller.present(camera)

    suspend fun capturePng(): ByteArray? = withContext(Dispatchers.Main.immediate) {
        val surface = AndroidRenderer.kool?.surfaceView ?: return@withContext null
        if (!controller.active || !controller.foreground.value || controller.status.value != "Island ready" ||
            !surface.isAttachedToWindow || !surface.holder.surface.isValid
        ) return@withContext null
        val frame = surface.holder.surfaceFrame
        if (frame.width() < 2 || frame.height() < 2) return@withContext null
        val bitmap = copyPhotoSurface(surface, frame.width(), frame.height()) ?: return@withContext null
        try {
            withContext(Dispatchers.Default) {
                ByteArrayOutputStream().use { stream ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                    stream.toByteArray()
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Composable
    fun Hud() = PreviewHud(controller)

    fun pause() {
        controller.foreground.value = false
        AndroidRenderer.kool?.onPause()
    }

    fun resume() {
        controller.foreground.value = true
        AndroidRenderer.kool?.onResume()
    }

    fun close() { controller.showWorld(null) }
}

private suspend fun copyPhotoSurface(surface: GLSurfaceView, width: Int, height: Int): Bitmap? =
    suspendCancellableCoroutine { continuation ->
        val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            PixelCopy.request(surface, image, { status ->
                if (status == PixelCopy.SUCCESS) {
                    continuation.resume(image) { _, dropped, _ -> dropped?.recycle() }
                } else {
                    image.recycle()
                    continuation.resume(null)
                }
            }, Handler(Looper.getMainLooper()))
        } catch (_: IllegalArgumentException) {
            image.recycle()
            continuation.resume(null)
        }
    }

// Kool 0.19 creates a single Android backend for the process. The surface holds only
// the application context and reattaches when the Activity is recreated.
private object AndroidRenderer {
    val controller = PreviewController()
    var kool: KoolContextAndroid? = null
        private set

    fun surface(appContext: Context): GLSurfaceView {
        val existing = kool?.surfaceView
        if (existing != null) {
            (existing.parent as? ViewGroup)?.removeView(existing)
            return existing
        }
        val surface = KoolSurfaceView(appContext)
        surface.setEGLContextFactory(ProcessEglContext())
        val context = createKoolContext(KoolConfigAndroid(appContext, surfaceView = surface))
        kool = context
        DioramaSession(context, controller, onIdle = {
            val lastEntry = controller.enteredAt.value
            surface.post {
                // Serialize idle and wake-up on the UI thread. A fast reopen may overtake this callback.
                if (!controller.active && controller.enteredAt.value == lastEntry) {
                    surface.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                }
            }
        })
        surface.setOnTouchListener(CameraTouch(controller))
        surface.setOnKeyListener(null)
        return surface
    }
}

private class ProcessEglContext : GLSurfaceView.EGLContextFactory {
    private var retained: EGLContext? = null

    @Synchronized
    override fun createContext(egl: EGL10, display: EGLDisplay, config: EGLConfig): EGLContext {
        retained?.let { return it }
        val context = egl.eglCreateContext(
            display, config, EGL10.EGL_NO_CONTEXT, intArrayOf(EGL_CONTEXT_CLIENT_VERSION, 3, EGL10.EGL_NONE),
        )
        check(context != EGL10.EGL_NO_CONTEXT) { "Could not create the GLES 3 context" }
        // Keep one display reference when GLSurfaceView terminates its own reference on detach.
        if (!egl.eglInitialize(display, null)) {
            egl.eglDestroyContext(display, context)
            error("Could not retain the EGL display")
        }
        return context.also { retained = it }
    }

    override fun destroyContext(egl: EGL10, display: EGLDisplay, context: EGLContext) {
        // The View's GL thread is joining and the window surface is already unbound.
        // The app process owns the EGL context and its display reference.
    }

    private companion object {
        const val EGL_CONTEXT_CLIENT_VERSION = 0x3098
    }
}

private class CameraTouch(private val controller: PreviewController) : View.OnTouchListener {
    private var x = 0f
    private var y = 0f
    private var span = 0f

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        if (!controller.active) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            view.parent?.requestDisallowInterceptTouchEvent(true)
        }
        val count = event.pointerCount
        val lifting = event.actionMasked == MotionEvent.ACTION_POINTER_UP
        val remaining = count - if (lifting) 1 else 0
        val first = if (lifting && event.actionIndex == 0) 1 else 0
        val second = (first + 1 until count).firstOrNull { !lifting || event.actionIndex != it }
        val ax = event.getX(first)
        val ay = event.getY(first)
        val bx = second?.let(event::getX) ?: ax
        val by = second?.let(event::getY) ?: ay
        val nextX = (ax + bx) / 2
        val nextY = (ay + by) / 2
        val nextSpan = hypot(bx - ax, by - ay)
        if (event.actionMasked == MotionEvent.ACTION_MOVE) {
            val size = minOf(view.width, view.height).coerceAtLeast(1)
            val dx = (nextX - x) / size
            val dy = (nextY - y) / size
            if (remaining >= 2) {
                controller.pan(dx, dy)
                if (span > 0f) controller.zoom(nextSpan / span)
            } else {
                controller.orbit(dx, -dy)
            }
        }
        x = nextX
        y = nextY
        span = nextSpan
        return true
    }
}
