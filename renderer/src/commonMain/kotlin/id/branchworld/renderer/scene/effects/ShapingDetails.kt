package id.branchworld.renderer.scene.effects

import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.Vec4f
import de.fabmax.kool.modules.ksl.KslBlinnPhongShader
import de.fabmax.kool.modules.ksl.KslShader
import de.fabmax.kool.modules.ksl.blocks.cameraData
import de.fabmax.kool.modules.ksl.lang.*
import de.fabmax.kool.pipeline.BlendMode
import de.fabmax.kool.pipeline.CullMethod
import de.fabmax.kool.pipeline.PipelineConfig
import de.fabmax.kool.scene.Node
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.scene.VertexLayouts
import de.fabmax.kool.scene.addColorMesh
import de.fabmax.kool.scene.vertexAttrib
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.releaseDelayed
import id.branchworld.renderer.scene.CellSize
import id.branchworld.renderer.scene.ChunkSize
import id.branchworld.renderer.scene.cellPosition
import id.branchworld.renderer.scene.facets
import id.branchworld.renderer.scene.heightAt
import id.branchworld.renderer.scene.translucentSurface
import id.branchworld.simulation.projection.GroundPoint
import id.branchworld.simulation.projection.ShapingPose
import id.branchworld.simulation.terrain.Biome
import id.branchworld.simulation.terrain.TerrainMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Local weather and selected sites share terrain coordinates with the simulation. */
internal class ShapingDetails(private val scene: Scene, private val terrain: TerrainMap, private val compact: Boolean) {
    private val wetGround = mutableMapOf<Int, WetGround>()
    private var shownSites = emptyList<GroundPoint>()
    private var markers: Node? = null
    private val cloud by lazy { scene.addShowerCloud() }
    private val rainShader by lazy { ShowerDropShader() }
    private val drops by lazy { scene.addShowerDrops() }
    private var showedShower = false

    fun show(shape: ShapingPose?, points: List<GroundPoint>, seconds: Float, reducedMotion: Boolean): Vec4f {
        if (shownSites != points) {
            markers?.let {
                scene.removeNode(it)
                it.releaseDelayed(2)
            }
            markers = points.takeIf { it.isNotEmpty() }?.let { scene.addTargetSites(it) }
            shownSites = points
        }
        val pools = shape?.retainedWater.orEmpty()
        for ((region, ground) in wetGround) ground.node.isVisible = pools.any { it.region == region }
        for (pool in pools) {
            val ground = wetGround.getOrPut(pool.region) { scene.addWetGround(pool.region) }
            ground.node.isVisible = true
            ground.shader.wetness = (pool.retained / 100f).coerceAtMost(1f)
        }
        val shower = shape?.shower
        if (shower == null) {
            if (showedShower) {
                cloud.isVisible = false
                drops.isVisible = false
                showedShower = false
            }
            return Vec4f.ZERO
        }
        val (x, z) = regionCenter(shower.region)
        val crest = terrain.heightAt(x, z).coerceAtLeast(0f)
        val scale = (0.45f + shower.age / 24f).coerceAtMost(1f)
        showedShower = true
        cloud.isVisible = true
        val height = crest + 1.53f
        cloud.transform.setIdentity().translate(x, height, z).scale(Vec3f(scale, 0.58f + scale * 0.12f, scale))
        drops.isVisible = !reducedMotion
        if (!reducedMotion) {
            rainShader.clock = seconds
            rainShader.column = Vec4f(x, z, height - 0.15f, scale)
        }
        return Vec4f(x, z, if (reducedMotion) 0.30f else scale, 0f)
    }

    private fun regionCenter(region: Int) = pairAt(
        TerrainMap.MinCell + region % 4 * ChunkSize + 8, TerrainMap.MinCell + region / 4 * ChunkSize + 8,
    )

    private fun pairAt(x: Int, z: Int) = x * CellSize to z * CellSize

    private fun Scene.addShowerCloud() = addColorMesh("Passing silver cloud") {
        generate {
            for (row in -1..1) for (column in -2..2) {
                val seed = (row + 2) * (column + 4)
                if (row != 0 && column == -2 && row == -1) continue
                val x = column * 0.38f + row * 0.11f
                val z = row * 0.28f
                val y = (seed % 5) * 0.034f + if (column == 0) 0.10f else 0f
                val radius = 0.28f + seed % 5 * 0.038f
                color = if (row == 1) Color(0.52f, 0.63f, 0.68f, 0.90f)
                    else Color(0.67f, 0.74f, 0.76f, 0.92f)
                icoSphere { this.radius = radius; steps = 1; center.set(x, y, z) }
            }
        }
        shader = KslBlinnPhongShader {
            color { vertexColor() }
            specularStrength(0.02f)
        }
    }

    private fun Scene.addShowerDrops() = addColorMesh("Rain beneath the cloud") {
        isFrustumChecked = false // The shader positions the column over the selected region.
        generate {
            repeat(if (compact) 190 else 290) { index ->
                val x = ((index * 97) % 521) / 165f - 1.58f
                val z = ((index * 113) % 487) / 154f - 1.58f
                val phase = (index * 17 % 71) / 71f
                val weight = 0.60f + (index % 6) * 0.063f
                color = Color(phase, weight, 0f, 1f)
                val a = vertex(Vec3f(x, 0f, z), Vec3f.Z_AXIS)
                val b = vertex(Vec3f(x + 0.009f, 0f, z), Vec3f.Z_AXIS)
                val c = vertex(Vec3f(x + 0.031f, 0.16f, z + 0.018f), Vec3f.Z_AXIS)
                addTriIndices(a, b, c)
            }
        }
        shader = rainShader
    }

    private fun Scene.addTargetSites(points: List<GroundPoint>) = addColorMesh("Intervention sites") {
        generate {
            for (point in points) {
                val p = cellPosition(point.x, point.z, point.elevationTwice)
                color = Color(1f, 0.86f, 0.44f, 0.91f)
                val ring = List(24) { index ->
                    val angle = (index * PI / 12).toFloat()
                    fun at(radius: Float): Int {
                        val x = p.x + cos(angle) * radius
                        val z = p.z + sin(angle) * radius
                        return vertex(Vec3f(x, terrain.heightAt(x, z).coerceAtLeast(0f) + 0.04f, z), Vec3f.Y_AXIS)
                    }
                    at(0.100f) to at(0.081f)
                }
                ring.indices.forEach { index ->
                    val (outer, inner) = ring[index]
                    val (nextOuter, nextInner) = ring[(index + 1) % ring.size]
                    addTriIndices(outer, nextOuter, inner)
                    addTriIndices(nextOuter, nextInner, inner)
                }
                // A painted stake stands over each mark so the place still reads behind grass and low canopy.
                val ground = terrain.heightAt(p.x, p.z).coerceAtLeast(0f)
                for (turn in 0..1) {
                    val dx = if (turn == 0) 0.012f else 0f
                    val dz = if (turn == 0) 0f else 0.012f
                    color = Color(1f, 0.80f, 0.30f, 0.95f)
                    val a = vertex(Vec3f(p.x - dx, ground, p.z - dz), Vec3f.Y_AXIS)
                    val b = vertex(Vec3f(p.x + dx, ground, p.z + dz), Vec3f.Y_AXIS)
                    color = Color(1f, 0.93f, 0.62f, 0.95f)
                    val c = vertex(Vec3f(p.x + dx, ground + 0.17f, p.z + dz), Vec3f.Y_AXIS)
                    val d = vertex(Vec3f(p.x - dx, ground + 0.17f, p.z - dz), Vec3f.Y_AXIS)
                    addTriIndices(a, b, c)
                    addTriIndices(a, c, d)
                }
            }
        }
        shader = translucentSurface()
    }

    private fun Scene.addWetGround(region: Int): WetGround {
        val (cx, cz) = regionCenter(region)
        val material = WetSoilShader()
        val node = addColorMesh("Ground after the shower") {
            generate {
                for (z in TerrainMap.MinCell..TerrainMap.MaxCell step ChunkSize) {
                    for (x in TerrainMap.MinCell..TerrainMap.MaxCell step ChunkSize) {
                        for (face in terrain.facets(x, z)) {
                            if (face.biome == Biome.Beach || face.biome == Biome.RockyUpland) continue
                            fun opacity(p: Vec3f): Float {
                                val edge = maxOf(kotlin.math.abs(p.x - cx), kotlin.math.abs(p.z - cz))
                                return ((1.60f - edge) / 0.44f).coerceIn(0f, 1f)
                            }
                            if (opacity(face.a) + opacity(face.b) + opacity(face.c) <= 0f) continue
                            fun paint(p: Vec3f): Int {
                                color = Color(0.028f, 0.10f, 0.079f, opacity(p) * 0.46f)
                                return vertex(Vec3f(p.x, p.y + 0.004f, p.z), Vec3f.Y_AXIS)
                            }
                            addTriIndices(paint(face.a), paint(face.b), paint(face.c))
                        }
                    }
                }
            }
            shader = material
        }
        return WetGround(node, material)
    }

    private data class WetGround(val node: Node, val shader: WetSoilShader)
}

private class WetSoilShader : KslShader(KslProgram("Wet soil"), PipelineConfig(
    blendMode = BlendMode.BLEND_MULTIPLY_ALPHA, cullMethod = CullMethod.NO_CULLING, isWriteDepth = false,
)) {
    var wetness by uniform1f("uWetness")

    init {
        program.apply {
            val camera = cameraData()
            val tint = interStageFloat4("soilTint")
            val wet = uniformFloat1("uWetness")
            vertexStage {
                main {
                    outPosition set camera.viewProjMat * float4Value(vertexAttrib(VertexLayouts.Position.position), 1f.const)
                    tint.input set vertexAttrib(VertexLayouts.Color.color)
                }
            }
            fragmentStage { main { colorOutput(tint.output.rgb, tint.output.a * wet) } }
        }
    }
}

private class ShowerDropShader : KslShader(KslProgram("Falling local rain"), PipelineConfig(
    cullMethod = CullMethod.NO_CULLING, isWriteDepth = false,
)) {
    var clock by uniform1f("uShowerTime")
    var column by uniform4f("uShowerColumn")

    init {
        program.apply {
            val camera = cameraData()
            val opacity = interStageFloat1("streakOpacity")
            val time = uniformFloat1("uShowerTime")
            val shower = uniformFloat4("uShowerColumn")
            vertexStage {
                main {
                    val p = vertexAttrib(VertexLayouts.Position.position)
                    val data = vertexAttrib(VertexLayouts.Color.color)
                    val fall = float1Var(fract(data.r - time * 1.86f.const), "fall")
                    val edge = max(abs(p.x), abs(p.z))
                    val height = (shower.z - 0.13f.const) * fall + p.y
                    opacity.input set ((1f.const - smoothStep(0.75f.const, 1f.const, fall)) *
                        (1f.const - smoothStep(1.10f.const, 1.62f.const, edge)) * data.g * shower.w)
                    outPosition set camera.viewProjMat * float4Value(
                        p.x * shower.w + shower.x, height - 0.03f.const, p.z * shower.w + shower.y, 1f.const,
                    )
                }
            }
            fragmentStage { main { colorOutput(float3Value(0.73f, 0.89f, 0.96f), opacity.output * 0.75f.const) } }
        }
    }
}
