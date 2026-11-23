package id.branchworld.renderer.stage

import id.branchworld.renderer.WorldStage
import id.branchworld.renderer.geometry.SurfaceMesh
import id.branchworld.renderer.geometry.contactShadows
import id.branchworld.renderer.geometry.fallenPines
import id.branchworld.renderer.geometry.fireFront
import id.branchworld.renderer.geometry.fireScars
import id.branchworld.renderer.geometry.lightningBolt
import id.branchworld.renderer.geometry.livingEcho
import id.branchworld.renderer.geometry.rainDrop
import id.branchworld.renderer.geometry.rainVeil
import id.branchworld.renderer.geometry.smokePuffs
import id.branchworld.renderer.geometry.stormClouds
import id.branchworld.renderer.lighting.CoastalLight
import id.branchworld.renderer.motion.WildlifeBodyScale
import id.branchworld.simulation.ecology.fire.Succession
import id.branchworld.simulation.projection.FirePose
import id.branchworld.simulation.projection.LivingEchoProjection
import id.branchworld.simulation.projection.WorldRenderProjection
import id.branchworld.simulation.terrain.TerrainMap
import kotlin.math.sin

/** A mesh slot on the stage: one entity whose mesh is replaced whenever what it shows changes. */
private class Slot(private val stage: WorldStage, ids: StageIds, material: Int) {
    val mesh = ids.take()
    val entity = ids.take().also { stage.addEntity(it, -1, mesh, material) }
    private var empty = true

    fun show(surface: SurfaceMesh?, texCoords: FloatArray = FloatArray(0)) {
        if (surface == null || surface.indices.isEmpty()) {
            if (!empty) stage.define(mesh, EmptySurface)
            empty = true
        } else {
            stage.define(mesh, surface, texCoords)
            empty = false
        }
    }

    private companion object {
        val EmptySurface = SurfaceMesh(FloatArray(0), FloatArray(0), FloatArray(0), IntArray(0))
    }
}

/**
 * Weather and comparison on the stage: the rain veil, soft shadows under the animals and the echo of the other history,
 * from the same shared geometry the Kool renderer draws.
 */
internal class StageAtmosphere(private val stage: WorldStage, ids: StageIds, private val terrain: TerrainMap) {
    private val translucent = ids.take().also { stage.defineMaterial(it, StageShading.Translucent, -1, 1f, 1f, 1f, 1f, 0) }
    private val rain = Slot(stage, ids, translucent).also { it.show(rainVeil(compact = true)) }
    private val shadows = Slot(stage, ids, translucent)
    private val echo = Slot(stage, ids, translucent)
    private var shownContacts: Any? = null
    private var shownEcho: LivingEchoProjection? = null

    fun show(world: WorldRenderProjection, light: CoastalLight?, seconds: Float, reducedMotion: Boolean, batch: TransformBatch) {
        val raining = light?.rain == true && !reducedMotion
        if (raining) batch.put(rain.entity, 0f, rainDrop(seconds, light?.storm == true), 0f, TransformBatch.Identity, 1f, 1f, 1f, visible = true)
        else batch.hide(rain.entity)
        val contacts = world.creatures.map { Triple(it.ground, it.species, it.juvenile) }
        if (contacts != shownContacts) {
            shownContacts = contacts
            shadows.show(terrain.contactShadows(contacts, world.wildlife != null, WildlifeBodyScale))
        }
        val next = world.livingEcho?.takeIf { it.sites.isNotEmpty() }
        if (next != shownEcho) {
            shownEcho = next
            echo.show(next?.let { terrain.livingEcho(it) })
        }
    }
}

/**
 * Fire on the stage (rules v11 onward, and every living world): soft-edged scars that recover through ash, meadow and
 * scrub; flames and rising embers; smoke drifting downwind; stumps and fallen trunks; the gathering storm and its
 * lightning. Shapes come from the shared geometry; the flames, smoke and scars move in the stage's shaders.
 */
internal class StageFire(private val stage: WorldStage, private val ids: StageIds, private val terrain: TerrainMap) {
    private val scarMaterial = ids.take().also { stage.defineMaterial(it, StageShading.Scar, -1, 1f, 1f, 1f, 1f, 0) }
    private val flameMaterial = ids.take()
    private val smokeMaterial = ids.take()
    private val plain = ids.take().also { stage.defineMaterial(it, StageShading.Plain, -1, 1f, 1f, 1f, 1f, 0) }
    private val translucent = ids.take().also { stage.defineMaterial(it, StageShading.Translucent, -1, 1f, 1f, 1f, 1f, 0) }
    private var wind = -1
    private val scars = Slot(stage, ids, scarMaterial)
    private val flames: Slot
    private val smoke: Slot
    private val stumps = Slot(stage, ids, plain)
    private val storm = Slot(stage, ids, plain)
    private val bolt = Slot(stage, ids, translucent)
    private var shown: FirePose? = null

    init {
        windFrom(0)
        flames = Slot(stage, ids, flameMaterial)
        smoke = Slot(stage, ids, smokeMaterial)
    }

    /** The flame and smoke materials carry the wind's direction (eighths from east toward +z) for their drift. */
    private fun windFrom(direction: Int) {
        if (direction == wind) return
        wind = direction
        stage.defineMaterial(flameMaterial, StageShading.Flame, -1, 1f, 1f, 1f, 1f, direction)
        stage.defineMaterial(smokeMaterial, StageShading.Smoke, -1, 1f, 1f, 1f, 1f, direction)
    }

    fun show(fire: FirePose?, seconds: Float, reducedMotion: Boolean, targeting: Boolean, batch: TransformBatch) {
        val previous = shown
        if (previous !== fire) {
            val burning = fire?.burning.orEmpty()
            if (fire?.scars != previous?.scars || burning.map { it.tile } != previous?.burning.orEmpty().map { it.tile }) {
                val surface = fire?.takeIf { it.scars.isNotEmpty() }?.let { terrain.fireScars(it) }
                // The edge mask rides in the normals for Kool; here it moves to the texture coordinates.
                scars.show(surface, surface?.let { s -> FloatArray(s.vertexCount * 2) { i -> s.normals[(i / 2) * 3 + i % 2] } } ?: FloatArray(0))
            }
            if (burning != previous?.burning.orEmpty()) {
                val active = fire?.takeIf { burning.isNotEmpty() }
                if (active != null) windFrom(active.wind)
                flames.show(active?.let { terrain.fireFront(it, compact = true) })
                smoke.show(active?.let { terrain.smokePuffs(it, compact = true) })
            }
            if (fire?.stands != previous?.stands) {
                val downed = fire?.stands.orEmpty().filterValues { it == Succession.Ash || it == Succession.Meadow }
                stumps.show(downed.takeIf { it.isNotEmpty() }?.let {
                    terrain.fallenPines(it.keys, fire?.scars.orEmpty().filter { scar -> scar.felled }.map { scar -> scar.tile }.toSet())
                })
            }
            if (fire?.storm.orEmpty() != previous?.storm.orEmpty()) {
                storm.show(fire?.storm?.takeIf { it.isNotEmpty() }?.let { stormClouds(it, compact = true) })
            }
            val strike = fire?.lightning?.takeIf { fire.lightningAge < LightningVisibleTicks }
            val previousStrike = previous?.lightning?.takeIf { previous.lightningAge < LightningVisibleTicks }
            if (strike != previousStrike) bolt.show(strike?.let { terrain.lightningBolt(it) })
            shown = fire
        }
        // The storm steps aside while the player chooses a place, and drifts a little otherwise.
        if (targeting) batch.hide(storm.entity)
        else batch.put(storm.entity, if (reducedMotion) 0f else sin(seconds * 0.31f) * 0.05f, 0f, 0f, TransformBatch.Identity, 1f, 1f, 1f, visible = true)
    }

    private companion object {
        const val LightningVisibleTicks = 12
    }
}
