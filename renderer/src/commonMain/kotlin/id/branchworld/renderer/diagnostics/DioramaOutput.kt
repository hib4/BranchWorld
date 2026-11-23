package id.branchworld.renderer.diagnostics

import de.fabmax.kool.scene.Scene

/** An off-screen target the session renders into instead of a window (the desktop's Compose image). */
internal interface DioramaOutput {
    fun attach(scene: Scene)
    fun update()
    fun detach()
}
