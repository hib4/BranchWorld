package id.branchworld.renderer

import platform.Foundation.NSData
import platform.UIKit.UIView

/**
 * The iOS 3D view, implemented in Swift with RealityKit (Swift-only, so out of Kotlin's reach) and driven entirely from
 * shared Kotlin: Kotlin builds every mesh, chooses every material and moves every entity each frame; the stage only
 * turns that data into RealityKit entities. All arrays are little-endian: Float32 positions, normals (xyz) and colours
 * (rgba), texture coordinates (uv) and Int32 indices.
 */
interface WorldStage {
    /** The view the stage draws into; Compose hosts it beneath the interface. */
    fun makeView(): UIView

    /** Removes every entity, mesh, material and texture: a different world is about to be shown. */
    fun clearScene()

    /** A triangle mesh. [colors] or [texCoords] may be empty. Redefining an id replaces the mesh everywhere it is used. */
    fun defineMesh(mesh: Int, positions: NSData, normals: NSData, colors: NSData, texCoords: NSData, indices: NSData)

    /** A PNG or JPEG image for textured materials. */
    fun defineTexture(texture: Int, image: NSData)

    /**
     * A material: [shading] is one of [StageShading]'s codes; [texture] is -1 for vertex colours; [r], [g], [b], [a]
     * tint the colour; [variant] selects a coat or other detail within the shading.
     */
    fun defineMaterial(material: Int, shading: Int, texture: Int, r: Float, g: Float, b: Float, a: Float, variant: Int)

    /** An entity under [parent] (-1 for the scene root), drawing [mesh] with [material], or an empty group when [mesh] is -1. */
    fun addEntity(entity: Int, parent: Int, mesh: Int, material: Int)

    fun removeEntity(entity: Int)

    /**
     * Local transforms, 48 bytes per entity: Int32 entity id, then Float32 translation xyz, rotation quaternion xyzw,
     * scale xyz and visibility (0 hides).
     */
    fun setTransforms(records: NSData)

    fun setCamera(eyeX: Float, eyeY: Float, eyeZ: Float, targetX: Float, targetY: Float, targetZ: Float, fovDegrees: Float)

    /** The eight lighting texels every shader reads (RGBA bytes, see `lightingTexels`) and the clear colour behind the world. */
    fun setLighting(texels: NSData, backgroundR: Float, backgroundG: Float, backgroundB: Float)

    /** The current frame as PNG bytes, or null when no frame is available. */
    fun snapshot(done: (NSData?) -> Unit)

    /** Calls [frames] on the main thread before every frame the stage draws, so Kotlin can update the scene. */
    fun startFrames(frames: StageFrames)
}

/** Receives the stage's frames: [frame] runs once per drawn frame with a monotonic time in nanoseconds. */
interface StageFrames {
    fun frame(nanos: Long)
}
