package id.branchworld.renderer.geometry

/** A column-major 4×4 matrix for baking model transforms into vertex arrays. */
internal class Mat4(val m: FloatArray = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)) {
    operator fun times(o: Mat4): Mat4 {
        val r = FloatArray(16)
        for (col in 0 until 4) for (row in 0 until 4) {
            var sum = 0f
            for (k in 0 until 4) sum += m[k * 4 + row] * o.m[col * 4 + k]
            r[col * 4 + row] = sum
        }
        return Mat4(r)
    }

    fun point(p: Point3) = Point3(
        m[0] * p.x + m[4] * p.y + m[8] * p.z + m[12],
        m[1] * p.x + m[5] * p.y + m[9] * p.z + m[13],
        m[2] * p.x + m[6] * p.y + m[10] * p.z + m[14],
    )

    /** Directions transform without translation; renormalised for non-uniform scale. */
    fun direction(d: Point3) = Point3(
        m[0] * d.x + m[4] * d.y + m[8] * d.z,
        m[1] * d.x + m[5] * d.y + m[9] * d.z,
        m[2] * d.x + m[6] * d.y + m[10] * d.z,
    ).norm()

    companion object {
        /** Translation × rotation (quaternion x, y, z, w) × scale. */
        fun trs(tx: Float, ty: Float, tz: Float, qx: Float, qy: Float, qz: Float, qw: Float, sx: Float, sy: Float, sz: Float): Mat4 {
            val xx = qx * qx; val yy = qy * qy; val zz = qz * qz
            val xy = qx * qy; val xz = qx * qz; val yz = qy * qz
            val wx = qw * qx; val wy = qw * qy; val wz = qw * qz
            return Mat4(floatArrayOf(
                (1 - 2 * (yy + zz)) * sx, (2 * (xy + wz)) * sx, (2 * (xz - wy)) * sx, 0f,
                (2 * (xy - wz)) * sy, (1 - 2 * (xx + zz)) * sy, (2 * (yz + wx)) * sy, 0f,
                (2 * (xz + wy)) * sz, (2 * (yz - wx)) * sz, (1 - 2 * (xx + yy)) * sz, 0f,
                tx, ty, tz, 1f,
            ))
        }
    }
}

/** A quaternion (x, y, z, w) for [degrees] about the vertical axis. */
internal fun yawQuaternion(degrees: Float): FloatArray {
    val half = degrees * 0.0087266462f
    return floatArrayOf(0f, kotlin.math.sin(half), 0f, kotlin.math.cos(half))
}

/** a × b for quaternions (x, y, z, w). */
internal fun quaternionProduct(a: FloatArray, b: FloatArray) = floatArrayOf(
    a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
    a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
    a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
    a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2],
)

/** A quaternion (x, y, z, w) for [degrees] about the X axis. */
internal fun pitchQuaternion(degrees: Float): FloatArray {
    val half = degrees * 0.0087266462f
    return floatArrayOf(kotlin.math.sin(half), 0f, 0f, kotlin.math.cos(half))
}
