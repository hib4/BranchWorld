package id.branchworld.renderer.scene

import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.util.Color
import id.branchworld.renderer.geometry.Point3
import id.branchworld.renderer.geometry.Rgba

/** The shared engine-free values as Kool types. */
internal fun Rgba.kool() = Color(r, g, b, a)

internal fun Point3.kool() = Vec3f(x, y, z)
