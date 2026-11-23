package id.branchworld.renderer.scene

import de.fabmax.kool.math.Vec4f
import de.fabmax.kool.modules.ksl.KslShader
import de.fabmax.kool.modules.ksl.blocks.cameraData
import de.fabmax.kool.modules.ksl.lang.*
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.scene.VertexLayouts
import de.fabmax.kool.scene.addColorMesh
import de.fabmax.kool.scene.vertexAttrib
import de.fabmax.kool.util.Color
import id.branchworld.renderer.geometry.inlandWaterSurface
import id.branchworld.renderer.geometry.reedBanks
import id.branchworld.simulation.terrain.TerrainMap

/** Static water geometry shares elevation samples at every corner, including chunk borders. */
internal fun Scene.addInlandWater(terrain: TerrainMap, material: InlandWater) {
    addColorMesh("Watershed") {
        generate { addSurface(terrain.inlandWaterSurface()) }
        shader = material
    }
}

internal fun Scene.addReedBanks(terrain: TerrainMap, wind: MeadowWind, compact: Boolean) {
    addColorMesh("Rushes and reed seed heads") {
        generate { addSurface(terrain.reedBanks(compact)) }
        shader = wind
    }
}

internal class InlandWater : KslShader("Inland water") {
    var time by uniform1f("uRiverTime")
    var localShower by uniform4f("uRiverShower", Vec4f.ZERO)
    var recharge by uniform1f("uRiverRecharge", 0.25f)
    var atmosphere by uniform4f("uRiverAtmosphere", Vec4f(0.96f, 0f, 0f, 0f))
    var haze by uniformColor("uRiverHaze", Color(0.31f, 0.53f, 0.58f))

    init {
        program.apply {
            optimizeExpressions = false
            val camera = cameraData()
            val world = interStageFloat3("riverPosition")
            val data = interStageFloat4("riverGround")
            val clock = uniformFloat1("uRiverTime")
            val level = uniformFloat1("uRiverRecharge")
            val air = uniformFloat4("uRiverAtmosphere")
            val mist = uniformFloat4("uRiverHaze")
            val shower = uniformFloat4("uRiverShower")
            vertexStage {
                main {
                    val position = vertexAttrib(VertexLayouts.Position.position)
                    val height = float1Var(position.y + (level - 0.40f.const) * 0.12f.const *
                        smoothStep(0.025f.const, 0.19f.const, position.y), "surfaceLevel")
                    world.input set float3Value(position.x, height, position.z)
                    data.input set vertexAttrib(VertexLayouts.Color.color)
                    outPosition set camera.viewProjMat * float4Value(position.x, height, position.z, 1f.const)
                }
            }
            fragmentStage {
                main {
                    val p = world.output
                    val info = data.output
                    val bed = float1Var(info.a * 2.5f.const - 1f.const, "riverBed")
                    val depth = float1Var(p.y - bed, "riverDepth")
                    `if`(depth lt 0.004f.const) { discard() }
                    `if`(p.y lt 0.016f.const) { discard() }
                    val kind = info.b
                    val flow = float2Var(info.rg * 0.55f.const + float2Value(0.13f, 0.71f), "downstream")
                    val flowRate = float1Var(1.35f.const - smoothStep(0.10f.const, 0.72f.const, kind), "flowSpeed")
                    val longWave = float1Var(sin(dot(p.xz, flow) * 15f.const - clock * flowRate * 2.6f.const +
                        sin(p.x * 7.9f.const + p.z * 5.8f.const - clock * 0.77f.const) * 1.58f.const), "current")
                    val crossWave = float1Var(sin(p.x * 26f.const - p.z * 19f.const + clock * 1.25f.const), "riverGlints")
                    val deep = float3Var(mix(float3Value(0.12f, 0.43f, 0.45f), float3Value(0.24f, 0.42f, 0.30f), kind), "deepWater")
                    val lake = float1Var(1f.const - smoothStep(0.04f.const, 0.38f.const, abs(kind - 0.45f.const)), "lakeBasin")
                    deep set mix(deep, float3Value(0.12f, 0.35f, 0.47f), lake * 0.72f.const)
                    val shallow = float3Var(mix(float3Value(0.45f, 0.64f, 0.48f), float3Value(0.48f, 0.55f, 0.34f), kind), "shallowWater")
                    val base = float3Var(mix(shallow, deep, smoothStep(0.006f.const, 0.10f.const, depth)), "riverColor")
                    val light = float3Var(mix(float3Value(0.53f, 0.64f, 0.83f), float3Value(1.04f, 1.01f, 0.92f), air.x), "riverLight")
                    val sheen = float1Var(smoothStep(0.37f.const, 0.92f.const, longWave) *
                        smoothStep(-0.56f.const, 0.96f.const, crossWave), "sheen")
                    val wetEdge = float1Var(smoothStep(0.006f.const, 0.032f.const, depth) *
                        (1f.const - smoothStep(0.030f.const, 0.070f.const, depth)), "wetEdge")
                    val reflection = float1Var(sheen * 0.075f.const + wetEdge * 0.055f.const + longWave * 0.012f.const, "reflection")
                    val water = float3Var(base * light * (1f.const - air.z * 0.15f.const), "paintedWater")
                    water += float3Value(0.70f, 0.89f, 0.82f) * reflection
                    val local = p.xz - shower.xy
                    val rainEdge = float1Var(max(abs(local.x), abs(local.y)), "showerEdge")
                    val showerMask = float1Var((1f.const - smoothStep(1.05f.const, 1.57f.const, rainEdge)) * shower.z, "rainingHere")
                    val dropCell = p.xz * 5.3f.const
                    val dropCenter = fract(dropCell) - float2Value(0.5f, 0.5f)
                    val fallPhase = fract(clock * 1.8f.const + sin(dot(floor(dropCell), float2Value(2.31f, 6.51f))) * 8f.const)
                    val ring = (1f.const - smoothStep(0.008f.const, 0.066f.const, abs(length(dropCenter) - fallPhase * 0.42f.const))) *
                        (1f.const - fallPhase)
                    water += float3Value(0.63f, 0.90f, 0.92f) * (ring * showerMask * 0.21f.const)
                    water += float3Value(0.99f, 0.55f, 0.32f) * air.y * (sheen * 0.075f.const + 0.018f.const)
                    val coastDepth = float1Var(clamp((bed * 32f.const + 18f.const) / 18f.const, 0f.const, 1f.const), "tideDepth")
                    val coastBase = float3Var(float3Value(0.081f, 0.31f, 0.39f) +
                        float3Value(0.26f, 0.35f, 0.19f) * coastDepth, "coastBase")
                    val coastLight = float3Var(mix(float3Value(0.52f, 0.60f, 0.78f), float3Value(1f, 1f, 1f), air.x), "coastLight")
                    val overcast = float3Var(mix(float3Value(1f, 1f, 1f), float3Value(0.78f, 0.87f, 0.96f), air.z), "coastOvercast")
                    val tidal = float3Var(coastBase * coastLight * overcast, "tidalWater")
                    val shoreWave = float1Var(sin(p.z * 18f.const + p.x * 8.3f.const + clock * 1.55f.const +
                        sin(p.x * 5.7f.const - p.z * 2.1f.const + clock * 0.41f.const) * 1.29f.const), "shoreWave")
                    val seaCross = float1Var(sin(p.x * 12.1f.const - p.z * 8.7f.const - clock * 0.89f.const), "seaCross")
                    val coastRipple = float1Var(smoothStep(0.53f.const, 0.96f.const, shoreWave) *
                        smoothStep(-0.16f.const, 0.88f.const, seaCross), "coastRipple")
                    tidal += float3Value(0.64f, 0.89f, 0.85f) * ((shoreWave + seaCross) * 0.008f.const + coastRipple * 0.031f.const)
                    val inland = float1Var(smoothStep(0.018f.const, 0.17f.const, p.y), "inlandMix")
                    val horizon = float1Var(smoothStep(4.8f.const, 16f.const, distance(camera.position, p)), "inlandHorizon")
                    val color = float3Var(mix(tidal, water, inland), "estuaryColor")
                    colorOutput(mix(color, mist.rgb, horizon * (0.42f.const + 0.24f.const * air.z)), 1f.const)
                }
            }
        }
    }
}
