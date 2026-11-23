package id.branchworld.renderer.scene.life

import de.fabmax.kool.scene.Node
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.scene.addColorMesh
import de.fabmax.kool.util.releaseDelayed
import id.branchworld.renderer.geometry.livingEcho
import id.branchworld.renderer.scene.addSurface
import id.branchworld.renderer.scene.translucentSurface
import id.branchworld.simulation.projection.LivingEchoProjection
import id.branchworld.simulation.terrain.TerrainMap

/** A single depth-tested layer follows the visible history without recreating the island. */
internal class LivingEcho(private val scene: Scene, private val terrain: TerrainMap) {
    private var drawn: LivingEchoProjection? = null
    private var mesh: Node? = null
    private var previous: Pair<LivingEchoProjection, Node>? = null

    fun show(echo: LivingEchoProjection?) {
        if (echo == null || echo.sites.isEmpty()) {
            mesh?.isVisible = false
            return
        }
        if (drawn != echo) {
            mesh?.isVisible = false
            val cached = previous
            previous = drawn?.let { current -> mesh?.let { current to it } }
            mesh = if (cached?.first == echo) cached.second else {
                cached?.second?.let {
                    scene.removeNode(it)
                    it.releaseDelayed(2)
                }
                scene.addEcho(echo)
            }
            drawn = echo
        }
        mesh?.isVisible = true
    }

    private fun Scene.addEcho(echo: LivingEchoProjection) = addColorMesh("Living Echo") {
        generate { addSurface(terrain.livingEcho(echo)) }
        shader = translucentSurface()
    }
}
