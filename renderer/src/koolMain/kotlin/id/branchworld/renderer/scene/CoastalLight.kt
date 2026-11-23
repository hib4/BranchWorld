package id.branchworld.renderer.scene

import de.fabmax.kool.math.Vec4f
import de.fabmax.kool.modules.ksl.KslShader
import de.fabmax.kool.modules.ksl.blocks.cameraData
import de.fabmax.kool.modules.ksl.lang.*
import de.fabmax.kool.pipeline.CullMethod
import de.fabmax.kool.scene.VertexLayouts
import de.fabmax.kool.scene.vertexAttrib
import de.fabmax.kool.util.Color
import id.branchworld.renderer.camera.eye

internal class MeadowWind : KslShader("Meadow wind") {
    var time by uniform1f("uWindTime")
    var amplitude by uniform1f("uWindAmplitude", 0.022f)
    var tint by uniformColor("uMeadowTint", Color.WHITE)

    init {
        pipelineConfig = pipelineConfig.copy(cullMethod = CullMethod.NO_CULLING)
        program.apply {
            val color = interStageFloat3("bladeColor")
            val clock = uniformFloat1("uWindTime")
            val speed = uniformFloat1("uWindAmplitude")
            val palette = uniformFloat4("uMeadowTint")
            vertexStage {
                main {
                    val attributes = vertexAttrib(VertexLayouts.Color.color)
                    val position = vertexAttrib(VertexLayouts.Position.position)
                    val gust = sin(position.x * 2.96f.const + position.z * 3.81f.const + clock * 1.5f.const)
                    val bend = attributes.a * speed * gust
                    val leaning = position + float3Value(bend, 0f.const, bend * 0.54f.const)
                    color.input set attributes.rgb
                    outPosition set cameraData().viewProjMat * float4Value(leaning, 1f)
                }
            }
            fragmentStage { main { colorOutput(color.output * palette.rgb, 1f.const) } }
        }
    }
}

internal fun KslProgram.coastalHaze() {
    val air = uniformFloat4("uLandHaze")
    val camera = cameraData()
    fragmentStage {
        main {
            val position = getFloat3Port("worldPos").output
            val material = getFloat4Port("materialColor")
            val original = checkNotNull(material.input.input)
            val depth = smoothStep(5.2f.const, 14f.const, distance(camera.position, position))
            material.input(float4Value(mix(original.rgb, air.rgb, depth * air.a), original.a))
        }
    }
}

internal fun KslProgram.pineSway() {
    val wind = uniformFloat2("uPineWind")
    vertexStage {
        main {
            val port = getFloat3Port("worldPos")
            val world = checkNotNull(port.input.input)
            val localY = vertexAttrib(VertexLayouts.Position.position).y
            val crown = smoothStep(0.17f.const, 1.29f.const, localY)
            val shift = sin(world.x * 0.92f.const + world.z * 0.75f.const + wind.x * 1.53f.const) * crown * wind.y
            val offset = float3Value(shift, 0f.const, shift * 0.38f.const)
            port.input(world + offset)
        }
    }
}

/** Packs shore depth into the vertex alpha. The water itself is an opaque surface. */
internal class CoastalWater : KslShader("Coastal water") {
    var time by uniform1f("uCoastTime")
    var atmosphere by uniform4f("uCoastAtmosphere", Vec4f(0.96f, 0f, 0f, 0f))
    var haze by uniformColor("uCoastHaze", Color(0.31f, 0.53f, 0.58f))

    init {
        program.apply {
            // Retain named intermediate values. Kool 0.19 can duplicate declarations
            // when its expression pass hoists shared fragment expressions.
            optimizeExpressions = false
            val camera = cameraData()
            val world = interStageFloat3("coastPosition")
            val ground = interStageFloat4("coastGround")
            val weather = uniformFloat4("uCoastAtmosphere")
            val clock = uniformFloat1("uCoastTime")
            val air = uniformFloat4("uCoastHaze")
            vertexStage {
                main {
                    val position = vertexAttrib(VertexLayouts.Position.position)
                    world.input set position
                    ground.input set vertexAttrib(VertexLayouts.Color.color)
                    outPosition set camera.viewProjMat * float4Value(position, 1f)
                }
            }
            fragmentStage {
                main {
                    val p = world.output
                    val shore = ground.output.a
                    val overcast = weather.z
                    val light = weather.x
                    val dusk = weather.y
                    val distance = float1Var(distance(camera.position, p), "coastDistance")
                    val line = float1Var(sin(p.z * 18f.const + p.x * 8.3f.const + clock * 1.55f.const +
                        sin(p.x * 5.7f.const - p.z * 2.1f.const + clock * 0.41f.const) * 1.29f.const), "wave")
                    val crossWave = float1Var(sin(p.x * 12.1f.const - p.z * 8.7f.const - clock * 0.89f.const), "crossWave")
                    val detail = float1Var(1f.const - smoothStep(7f.const, 20f.const, distance), "waveDetail")
                    val swell = sin(p.x * 2.6f.const + p.z * 3.4f.const + clock * 0.89f.const)
                    val brush = swell * 0.011f.const + (line + crossWave) * 0.008f.const * detail
                    val lowLight = ground.output.rgb * float3Value(0.52f, 0.60f, 0.78f)
                    val water = float3Var(mix(lowLight, ground.output.rgb, light), "baseWater")
                    val color = float3Var(mix(water, water * float3Value(0.78f, 0.87f, 0.96f), overcast))
                    val ripple = float1Var(smoothStep(0.53f.const, 0.96f.const, line) *
                        smoothStep(-0.16f.const, 0.88f.const, crossWave), "ripple")
                    val foam = smoothStep(0.66f.const, 0.84f.const, shore) * (1f.const - smoothStep(0.95f.const, 1f.const, shore))
                    val wash = sin(shore * 38f.const - clock * 1.72f.const + swell * 0.89f.const)
                    val reflection = (ripple * 0.031f.const + smoothStep(0.44f.const, 0.98f.const, wash) * foam *
                        (0.065f.const + crossWave * 0.026f.const)) * detail
                    color += float3Value(0.64f, 0.89f, 0.85f) * (brush + reflection)
                    val horizon = float1Var(smoothStep(4.8f.const, 16f.const, distance), "horizon")
                    val eye = normalize(p - camera.position)
                    val sunPath = smoothStep(0.51f.const, 0.91f.const, dot(eye, float3Value(-0.64f, -0.24f, -0.71f)))
                    val duskReflection = (0.030f.const + ripple * 0.086f.const) * dusk * sunPath * detail * (1f.const - overcast)
                    color += float3Value(0.97f, 0.50f, 0.28f) * duskReflection
                    val rainGrid = float2Var(p.xz * 4.7f.const, "rainGrid")
                    val tile = float2Var(floor(rainGrid), "rainTile")
                    val phase = float1Var(fract(sin(dot(tile, float2Value(43.12f, 75.54f))) * 4376.19f.const +
                        clock * 1.62f.const), "ringPhase")
                    val drift = sin(dot(tile, float2Value(27.89f, 18.44f))) * 0.13f.const
                    val ring = abs(length(fract(rainGrid) - float2Value(0.5f.const + drift, 0.5f.const - drift)) - phase * 0.39f.const)
                    val splash = (1f.const - smoothStep(0.009f.const, 0.032f.const, ring)) * (1f.const - phase)
                    color += float3Value(0.55f, 0.74f, 0.78f) * (splash * overcast * detail * 0.13f.const)
                    colorOutput(mix(color, air.rgb, horizon * (0.42f.const + 0.24f.const * overcast)), 1f.const)
                }
            }
        }
    }
}
