package id.branchworld.renderer

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import de.fabmax.kool.KoolConfigAndroid
import de.fabmax.kool.createKoolContext
import de.fabmax.kool.platform.KoolContextAndroid
import de.fabmax.kool.platform.KoolSurfaceView
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay
import kotlin.math.hypot

class AndroidDiorama(context: Context) {
    private val appContext = context.applicationContext
    private val controller = AndroidRenderer.controller

    @Composable
    fun Viewport(modifier: Modifier, active: Boolean) {
        SideEffect { controller.setActive(active) }
        AndroidView(
            modifier = modifier,
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
                if (active && view is GLSurfaceView && view.renderMode != GLSurfaceView.RENDERMODE_CONTINUOUSLY) {
                    view.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                }
            },
        )
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

    fun close() { controller.setActive(false) }
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
            surface.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
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
        if (!controller.active.value) return false
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
