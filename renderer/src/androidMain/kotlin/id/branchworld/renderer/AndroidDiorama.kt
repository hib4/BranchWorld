package id.branchworld.renderer

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.viewinterop.AndroidView
import de.fabmax.kool.KoolConfigAndroid
import de.fabmax.kool.createKoolContext
import de.fabmax.kool.platform.KoolContextAndroid
import de.fabmax.kool.platform.KoolSurfaceView
import id.branchworld.game.platform.viewport.WorldViewport
import id.branchworld.renderer.camera.PreviewController
import id.branchworld.renderer.camera.ViewportStatus
import id.branchworld.renderer.hud.PreviewHud
import id.branchworld.renderer.input.Finger
import id.branchworld.renderer.input.TouchGestureTracker
import id.branchworld.renderer.scene.DioramaSession
import id.branchworld.simulation.projection.WorldCamera
import id.branchworld.simulation.projection.WorldRenderSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay
import kotlin.coroutines.resume

class AndroidDiorama(context: Context) : WorldViewport {
    private val appContext = context.applicationContext
    private val controller = AndroidRenderer.controller

    @Composable
    override fun Viewport(modifier: Modifier, world: WorldRenderSource?) {
        AndroidView(
            modifier = modifier.onSizeChanged { controller.resize(it.width, it.height) },
            factory = {
                try {
                    AndroidRenderer.surface(appContext)
                } catch (error: IllegalStateException) {
                    controller.status.value = ViewportStatus.Failed("Renderer unavailable. Reopen the app to try again.")
                    android.util.Log.e("BranchWorldRenderer", "Could not start the Kool surface", error)
                    View(it)
                }
            },
            update = { view ->
                if (view is GLSurfaceView) {
                    controller.showWorld(world)
                    if (world != null && view.renderMode != GLSurfaceView.RENDERMODE_CONTINUOUSLY) {
                        view.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                    }
                }
            },
        )
    }

    override fun present(camera: WorldCamera) = controller.present(camera)

    override suspend fun capturePng(): ByteArray? = withContext(Dispatchers.Main.immediate) {
        val surface = AndroidRenderer.kool?.surfaceView ?: return@withContext null
        if (!controller.readyForPhoto ||
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
    override fun Hud() = PreviewHud(controller)

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

/** Feeds the view's touches to the shared [TouchGestureTracker]. */
private class CameraTouch(private val controller: PreviewController) : View.OnTouchListener {
    private val tracker = TouchGestureTracker(controller)

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        if (!controller.active) return false
        val lifting = event.actionMasked == MotionEvent.ACTION_POINTER_UP || event.actionMasked == MotionEvent.ACTION_UP
        val down = (0 until event.pointerCount).filter { !lifting || it != event.actionIndex }
            .map { Finger(event.getX(it), event.getY(it)) }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                view.parent?.requestDisallowInterceptTouchEvent(true)
                tracker.press(down, event.eventTime)
            }
            MotionEvent.ACTION_POINTER_DOWN -> tracker.press(down, event.eventTime)
            MotionEvent.ACTION_MOVE ->
                tracker.move(down, view.width, view.height, ViewConfiguration.get(view.context).scaledTouchSlop.toFloat())
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                val lifted = Finger(event.getX(event.actionIndex), event.getY(event.actionIndex))
                tracker.release(lifted, down, event.eventTime, view.width, view.height,
                    TouchGestureTracker.PickRadius * view.resources.displayMetrics.density)
            }
            MotionEvent.ACTION_CANCEL -> tracker.cancel()
        }
        return true
    }
}
