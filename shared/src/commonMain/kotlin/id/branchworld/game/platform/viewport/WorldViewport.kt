package id.branchworld.game.platform.viewport

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import id.branchworld.simulation.projection.WorldCamera
import id.branchworld.simulation.projection.WorldRenderSource

/** The 3D view of the world. Each platform renders it with its own engine and surface. */
interface WorldViewport {
    /** The view itself, filling [modifier], drawing [world] or the title island while it loads. */
    @Composable
    fun Viewport(modifier: Modifier, world: WorldRenderSource?)

    /** Camera controls and renderer status drawn over the view. */
    @Composable
    fun Hud()

    /** Moves the camera toward [camera]; called whenever the interface changes what it looks at. */
    fun present(camera: WorldCamera)

    /** The current frame as PNG bytes, or null while no frame is ready. */
    suspend fun capturePng(): ByteArray?
}

/** A viewport assembled from functions: handy for tests and previews. */
fun worldViewport(
    present: (WorldCamera) -> Unit = {},
    hud: @Composable () -> Unit = {},
    capturePng: suspend () -> ByteArray? = { null },
    view: @Composable (Modifier, WorldRenderSource?) -> Unit,
): WorldViewport = object : WorldViewport {
    @Composable
    override fun Viewport(modifier: Modifier, world: WorldRenderSource?) = view(modifier, world)

    @Composable
    override fun Hud() = hud()

    override fun present(camera: WorldCamera) = present(camera)

    override suspend fun capturePng(): ByteArray? = capturePng()
}
