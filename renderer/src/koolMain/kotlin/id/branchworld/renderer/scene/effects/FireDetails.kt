package id.branchworld.renderer.scene.effects

import de.fabmax.kool.math.Vec2f
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
import id.branchworld.renderer.geometry.CellSize
import id.branchworld.renderer.scene.cellPosition
import id.branchworld.renderer.geometry.heightAt
import id.branchworld.renderer.scene.addSurface
import id.branchworld.renderer.geometry.lightningBolt
import id.branchworld.renderer.geometry.fireScars
import id.branchworld.renderer.geometry.fireFront
import id.branchworld.renderer.geometry.fallenPines
import id.branchworld.renderer.scene.translucentSurface
import id.branchworld.simulation.ecology.fire.Succession
import id.branchworld.simulation.projection.FirePose
import id.branchworld.simulation.projection.GroundPoint
import id.branchworld.simulation.terrain.TerrainMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Presents rules-v11 fire from the authoritative projection: flames and smoke on burning tiles, ash and returning
 * grass on scars, stumps where pines fell, the gathering storm and its lightning. Nothing here feeds back.
 */
internal class FireDetails(private val scene: Scene, private val terrain: TerrainMap, private val compact: Boolean) {
    private val flameShader by lazy { FlameShader() }
    private val smokeShader by lazy { SmokeShader() }
    private val scarShader by lazy { ScarShader() }
    private var shown: FirePose? = null
    private var scars: Node? = null
    private var flames: Node? = null
    private var smoke: Node? = null
    private var stumps: Node? = null
    private var storm: Node? = null
    private var bolt: Node? = null

    /** [targeting]: the Keeper is choosing a place, so the storm steps aside rather than fill the close view. */
    fun show(fire: FirePose?, seconds: Float, reducedMotion: Boolean, sunlight: Float, targeting: Boolean = false) {
        val previous = shown
        if (previous !== fire) {
            val burning = fire?.burning.orEmpty()
            val oldBurning = previous?.burning.orEmpty()
            val footprintChanged = burning.size != oldBurning.size || burning.indices.any { burning[it].tile != oldBurning[it].tile }
            if (fire?.scars != previous?.scars || footprintChanged) {
                scars = replace(scars, fire?.takeIf { it.scars.isNotEmpty() }?.let { scene.addScars(it) })
            }
            if (burning != oldBurning) {
                val active = fire?.takeIf { burning.isNotEmpty() }
                flames = replace(flames, active?.let { scene.addFlames(it) })
                smoke = replace(smoke, active?.let { scene.addSmoke(it) })
            }
            if (fire?.stands != previous?.stands) {
                val downed = fire?.stands.orEmpty().filterValues { it == Succession.Ash || it == Succession.Meadow }
                stumps = replace(stumps, downed.takeIf { it.isNotEmpty() }?.let {
                    scene.addStumps(it.keys, fire?.scars.orEmpty().filter { scar -> scar.felled }.map { scar -> scar.tile }.toSet())
                })
            }
            val gathering = fire?.storm.orEmpty()
            if (gathering != previous?.storm.orEmpty()) {
                storm = replace(storm, gathering.takeIf { it.isNotEmpty() }?.let { scene.addStorm(it) })
            }
            val strike = fire.visibleStrike()
            if (strike != previous.visibleStrike()) bolt = replace(bolt, strike?.let { scene.addBolt(it) })
            shown = fire
        }
        if (scars != null) scarShader.light = sunlight
        if (flames != null && smoke != null) {
            val direction = checkNotNull(fire).wind
            val drift = Vec2f(StepX[direction] * 0.30f, StepZ[direction] * 0.30f)
            flameShader.clock = if (reducedMotion) 0.4f else seconds
            flameShader.wind = drift
            smokeShader.clock = if (reducedMotion) 0.6f else seconds
            smokeShader.wind = drift
        }
        storm?.isVisible = !targeting
        storm?.transform?.setIdentity()?.translate(if (reducedMotion) 0f else sin(seconds * 0.31f) * 0.05f, 0f, 0f)
    }

    private fun FirePose?.visibleStrike() = this?.lightning?.takeIf { lightningAge < LightningVisibleTicks }

    private fun replace(old: Node?, next: Node?): Node? {
        old?.let {
            scene.removeNode(it)
            it.releaseDelayed(2)
        }
        return next
    }

    /** Ash darkens burned ground; fresh grass, then scrub, colour it as it comes back; felled ground shows bare earth. */
    private fun Scene.addScars(fire: FirePose) = addColorMesh("Burned and cleared ground") {
        generate { addSurface(terrain.fireScars(fire)) }
        shader = scarShader
    }

    /** Flame tongues are crossed triangles; the shader makes them lick and flicker. */
    private fun Scene.addFlames(fire: FirePose) = addColorMesh("Fire front") {
        isFrustumChecked = false // The shader moves the tips.
        generate { addSurface(terrain.fireFront(fire, compact)) }
        shader = flameShader
    }

    /** Smoke puffs rise and lean downwind in a loop; each burning tile sends up a few. */
    private fun Scene.addSmoke(fire: FirePose) = addColorMesh("Smoke") {
        isFrustumChecked = false
        generate {
            for ((order, tile) in fire.burning.withIndex()) {
                if (compact && order % 2 == 1) continue
                val puffs = if (compact) 3 else 4
                repeat(puffs) { index ->
                    val seed = tile.tile * 19 + index * 23
                    val wx = (tile.ground.x + 1f) * CellSize
                    val wz = (tile.ground.z + 1f) * CellSize
                    val base = terrain.heightAt(wx, wz).coerceAtLeast(0f) + 0.30f
                    color = Color((seed % 89) / 89f, 0.6f + tile.intensity / 10f, 0f, 1f)
                    icoSphere { radius = 0.11f + (seed % 4) * 0.03f; steps = 1; center.set(wx + (index % 2) * 0.05f, base, wz) }
                }
            }
        }
        shader = smokeShader
    }

    private fun Scene.addStumps(props: Set<Int>, felledTiles: Set<Int>) = addColorMesh("Fallen pines") {
        generate { addSurface(terrain.fallenPines(props, felledTiles)) }
        shader = KslBlinnPhongShader {
            color { vertexColor() }
            specularStrength(0.03f)
        }
    }

    /** Heavy, rainless cloud gathering where the storm will strike. */
    private fun Scene.addStorm(points: List<GroundPoint>) = addColorMesh("Dry storm") {
        generate {
            val first = points.first().let { cellPosition(it.x, it.z, it.elevationTwice) }
            val last = points.last().let { cellPosition(it.x, it.z, it.elevationTwice) }
            val crest = maxOf(first.y, last.y, 0f) + 1.34f
            val count = if (points.size == 1) 7 else if (compact) 12 else 16
            for (index in 0 until count) {
                val blend = index.toFloat() / (count - 1)
                val roll = sin(blend * PI).toFloat()
                val x = first.x + (last.x - first.x) * blend + (blend - 0.5f) * 0.72f
                val z = first.z + (last.z - first.z) * blend + sin(index * 2.42f) * 0.19f
                val base = crest + sin(index * 1.91f) * 0.07f
                color = Color(0.24f, 0.27f, 0.32f)
                icoSphere {
                    radius = 0.22f + roll * 0.07f
                    steps = 1
                    center.set(x, base, z + 0.11f)
                }
                if (index % 2 == 0) {
                    color = Color(0.34f, 0.36f, 0.41f)
                    icoSphere {
                        radius = 0.25f + roll * 0.12f + (index % 5) * 0.012f
                        steps = 1
                        center.set(x - 0.07f, base + 0.15f, z - 0.08f)
                    }
                }
            }
        }
        shader = KslBlinnPhongShader {
            color { vertexColor() }
            specularStrength(0.02f)
        }
    }

    private fun Scene.addBolt(point: GroundPoint) = addColorMesh("Lightning") {
        isFrustumChecked = false
        generate { addSurface(terrain.lightningBolt(point)) }
        shader = translucentSurface()
    }

    private companion object {
        const val LightningVisibleTicks = 12
        val StepX = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1)
        val StepZ = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)
    }
}

private class ScarShader : KslShader(KslProgram("Fire succession"), PipelineConfig(
    blendMode = BlendMode.BLEND_MULTIPLY_ALPHA, cullMethod = CullMethod.NO_CULLING, isWriteDepth = false,
)) {
    var light by uniform1f("uScarLight", 0.8f)

    init {
        program.apply {
            val ground = interStageFloat2("scarGround")
            val tint = interStageFloat4("scarTint")
            val mask = interStageFloat2("scarEdgeMask")
            val sunlight = uniformFloat1("uScarLight")
            vertexStage {
                main {
                    val p = vertexAttrib(VertexLayouts.Position.position)
                    ground.input set p.xz
                    tint.input set vertexAttrib(VertexLayouts.Color.color)
                    mask.input set vertexAttrib(VertexLayouts.Normal.normal).xy
                    outPosition set cameraData().viewProjMat * float4Value(p, 1f.const)
                }
            }
            fragmentStage {
                main {
                    val p = ground.output
                    val drift = sin(p.x * 19f.const + p.y * 17f.const) * 2.3f.const
                    val daub = float1Var(sin(p.x * 66f.const + p.y * 19f.const + drift) * cos(p.y * 51f.const - p.x * 17f.const), "daub")
                    val local = float2Var(fract(p / (2 * CellSize).const) - float2Value(0.5f, 0.5f), "tileLocal")
                    val edgeMask = round(mask.output)
                    val lowMask = edgeMask - floor(edgeMask * 0.5f.const) * 2f.const
                    val low = max(-local, float2Value(0f, 0f)) * lowMask
                    val high = max(local, float2Value(0f, 0f)) * step(float2Value(1.5f, 1.5f), edgeMask)
                    val edge = max(max(low.x, low.y), max(high.x, high.y))
                    val opacity = 1f.const - smoothStep(0.33f.const, 0.54f.const, edge + daub * 0.038f.const)
                    val tone = 0.70f.const + sunlight * 0.25f.const + daub * 0.14f.const
                    colorOutput(tint.output.rgb * tone, tint.output.a * opacity)
                }
            }
        }
    }
}

private class FlameShader : KslShader(KslProgram("Flame tongues"), PipelineConfig(
    blendMode = BlendMode.BLEND_ADDITIVE, cullMethod = CullMethod.NO_CULLING, isWriteDepth = false,
)) {
    var clock by uniform1f("uFireTime")
    var wind by uniform2f("uAshWind")

    init {
        program.apply {
            val camera = cameraData()
            val heat = interStageFloat3("flameHeat")
            val time = uniformFloat1("uFireTime")
            val wind = uniformFloat2("uAshWind")
            vertexStage {
                main {
                    val p = vertexAttrib(VertexLayouts.Position.position)
                    val data = vertexAttrib(VertexLayouts.Color.color)
                    val phase = data.r * 6.283f.const
                    val tip = data.g
                    val lick = sin(time * 9.5f.const + phase) * 0.035f.const * tip
                    val sway = cos(time * 7.1f.const + phase * 1.7f.const) * 0.028f.const * tip
                    val flare = float1Var(sin(time * 12.3f.const + phase * 2.3f.const) * 0.06f.const, "flare")
                    val age = float1Var(fract(time * 0.81f.const + data.r), "emberAge")
                    val spark = 1f.const - data.a
                    val travel = float1Var(age * spark, "emberTravel")
                    val height = p.y + tip * flare + travel * 0.57f.const
                    val fade = data.a + spark * (1f.const - age) * smoothStep(0f.const, 0.14f.const, age)
                    heat.input set float3Value(tip, data.b, fade)
                    outPosition set camera.viewProjMat * float4Value(
                        p.x + lick + travel * wind.x, height, p.z + sway + travel * wind.y, 1f.const,
                    )
                }
            }
            fragmentStage {
                main {
                    val tip = heat.output.x
                    val ember = float3Value(0.78f, 0.16f, 0.03f)
                    val amber = float3Value(1f, 0.55f, 0.10f)
                    val pale = float3Value(1f, 0.90f, 0.55f)
                    val color = mix(mix(ember, amber, smoothStep(0f.const, 0.45f.const, tip)), pale, smoothStep(0.55f.const, 1f.const, tip))
                    colorOutput(color * (1.05f.const + heat.output.y * 0.45f.const), (1f.const - tip * tip) * heat.output.z)
                }
            }
        }
    }
}

private class SmokeShader : KslShader(KslProgram("Rising smoke"), PipelineConfig(
    blendMode = BlendMode.BLEND_MULTIPLY_ALPHA, cullMethod = CullMethod.NO_CULLING, isWriteDepth = false,
)) {
    var clock by uniform1f("uSmokeTime")
    var wind by uniform2f("uSmokeWind")

    init {
        program.apply {
            val camera = cameraData()
            val fade = interStageFloat1("smokeFade")
            val time = uniformFloat1("uSmokeTime")
            val drift = uniformFloat2("uSmokeWind")
            vertexStage {
                main {
                    val p = vertexAttrib(VertexLayouts.Position.position)
                    val data = vertexAttrib(VertexLayouts.Color.color)
                    val age = float1Var(fract(time * 0.22f.const + data.r), "puffAge")
                    val climb = age * 1.9f.const
                    fade.input set (1f.const - age) * smoothStep(0f.const, 0.12f.const, age) * data.g
                    outPosition set camera.viewProjMat * float4Value(p.x + drift.x * age, p.y + climb, p.z + drift.y * age, 1f.const)
                }
            }
            fragmentStage { main { colorOutput(float3Value(0.24f, 0.22f, 0.21f), fade.output * 0.55f.const) } }
        }
    }
}
