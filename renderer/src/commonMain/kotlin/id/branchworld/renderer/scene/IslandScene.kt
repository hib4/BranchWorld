package id.branchworld.renderer.scene

import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.Vec4f
import de.fabmax.kool.math.deg
import de.fabmax.kool.modules.ksl.KslBlinnPhongShader
import de.fabmax.kool.modules.ksl.KslPbrShader
import de.fabmax.kool.pipeline.ClearColorFill
import de.fabmax.kool.scene.Light
import de.fabmax.kool.scene.Node
import de.fabmax.kool.scene.PerspectiveCamera
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.scene.addColorMesh
import de.fabmax.kool.util.Color
import id.branchworld.renderer.camera.ViewFovYDegrees
import id.branchworld.simulation.terrain.TerrainMap

/**
 * The fixed island: terrain, sea, rivers and reeds, fine grass and the rain veil, with the materials that light and
 * weather adjust every frame. [compact] scenes (phones and the browser) draw fewer reeds and raindrops.
 */
internal class IslandScene(terrain: TerrainMap, private val compact: Boolean) {
    val water = CoastalWater()
    val inland = terrain.watershed?.let { InlandWater() }
    val wind = MeadowWind()
    private val ground = KslBlinnPhongShader {
        color { vertexColor() }
        specularStrength(0.035f)
        modelCustomizer = { coastalHaze() }
    }

    /** Pine foliage shaders, once the pines have loaded. */
    var canopy = emptyList<KslPbrShader>()
        private set
    var light = coastalLight(null, false)
        private set
    val fineGrass: Node
    val rain: Node

    val scene = Scene("Island ${terrain.key.seed}").apply {
        mainRenderPass.clearColor = ClearColorFill(Color(0.18f, 0.41f, 0.47f))
        camera.setClipRange(0.1f, 90f)
        (camera as? PerspectiveCamera)?.fovY = ViewFovYDegrees.deg
        lighting.singleDirectionalLight { setup(Vec3f(-0.55f, -1.4f, -0.9f)) }
        lighting.addDirectionalLight { setup(Vec3f(0.6f, -0.72f, 1f)) }
        onRelease { println("BranchWorldRenderer sceneReleased") }
    }

    init {
        show(light)
        scene.addIsland(terrain, water, ground)
        if (inland != null) {
            scene.addInlandWater(terrain, inland)
            scene.addReedBanks(terrain, wind, compact = compact)
        }
        fineGrass = scene.addLandscapeDetails(terrain, wind)
        rain = scene.addRain()
    }

    /** Lights the island for [next]: sun and fill, water and meadow tint, and the haze over the land. */
    fun show(next: CoastalLight) {
        light = next
        (scene.lighting.lights[0] as Light.Directional).setup(next.direction).setColor(next.lightColor, next.strength)
        scene.lighting.lights[1].setColor(next.fillColor, next.fill)
        water.apply {
            haze = next.haze
            atmosphere = Vec4f(next.sun, next.twilight, if (next.rain) 1f else 0f, 0f)
        }
        inland?.apply {
            haze = next.haze
            atmosphere = Vec4f(next.sun, next.twilight, if (next.rain) 1f else 0f, 0f)
        }
        wind.tint = Color(0.56f, 0.63f, 0.70f).mix(Color(1.08f, 1.02f, 0.85f), next.sun)
        updateLandHaze()
    }

    fun useCanopy(shaders: List<KslPbrShader>) {
        canopy = shaders
        updateLandHaze()
    }

    private fun updateLandHaze() {
        val haze = light.haze
        val mist = Vec4f(haze.r * haze.r, haze.g * haze.g, haze.b * haze.b, if (light.rain) 0.49f else 0.31f)
        ground.uniform4f("uLandHaze").set(mist)
        for (shader in canopy) shader.uniform4f("uLandHaze").set(mist)
    }

    private fun Scene.addRain() = addColorMesh("Light rain") {
        isVisible = false
        generate {
            color = Color(0.71f, 0.83f, 0.95f, 0.22f)
            repeat(if (compact) 136 else 196) { index ->
                // Repeat the same field vertically so moving it by one layer loops cleanly.
                val x = ((index * 83) % 521) / 43f - 6f
                val z = ((index * 197) % 491) / 41f - 6f
                val height = ((index * 13) % 37) / 37f * 1.7f
                for (layer in 0..3) {
                    val y = height + layer * 1.7f
                    val a = vertex(Vec3f(x, y, z), Vec3f.Z_AXIS)
                    val b = vertex(Vec3f(x + 0.014f, y, z), Vec3f.Z_AXIS)
                    val c = vertex(Vec3f(x + 0.047f, y + 0.19f, z + 0.018f), Vec3f.Z_AXIS)
                    addTriIndices(a, b, c)
                }
            }
        }
        shader = translucentSurface()
    }
}
