package id.branchworld.renderer.assets.gltf

import kotlin.math.acos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Poses a [GltfModel] from one of its clips at a moment in seconds: every node's local transform, starting from the
 * rest pose and overwritten by the clip's channels. Rotations take the shortest spherical path between keys, as the
 * glTF specification and Kool both do, so a backend driven by this matches the Kool animation frame for frame.
 */
internal class ClipSampler(private val model: GltfModel) {
    private val rest = model.nodes.map { it.transform }

    /** Local transforms for every node of the model at [time] seconds into [clip]. */
    fun pose(clip: GltfClip, time: Float): List<NodeTransform> {
        val pose = rest.toMutableList()
        val values = FloatArray(4)
        for (channel in clip.channels) {
            sample(channel, time, values)
            val current = pose[channel.node]
            pose[channel.node] = when (channel.path) {
                ChannelPath.Translation -> current.copy(tx = values[0], ty = values[1], tz = values[2])
                ChannelPath.Rotation -> current.copy(rx = values[0], ry = values[1], rz = values[2], rw = values[3])
                ChannelPath.Scale -> current.copy(sx = values[0], sy = values[1], sz = values[2])
            }
        }
        return pose
    }

    companion object {
        /** The channel's value at [time], clamped to its first and last keys. */
        fun sample(channel: GltfChannel, time: Float, out: FloatArray) {
            val times = channel.times
            val width = channel.width
            val v = channel.values
            if (times.size == 1 || time <= times.first()) {
                v.copyInto(out, 0, 0, width)
                return
            }
            if (time >= times.last()) {
                v.copyInto(out, 0, (times.size - 1) * width, times.size * width)
                return
            }
            var next = 1
            while (times[next] < time) next++
            val previous = next - 1
            if (channel.interpolation == Interpolation.Step) {
                v.copyInto(out, 0, previous * width, next * width)
                return
            }
            val t = (time - times[previous]) / (times[next] - times[previous])
            val a = previous * width
            val b = next * width
            if (channel.path == ChannelPath.Rotation) {
                slerp(v[a], v[a + 1], v[a + 2], v[a + 3], v[b], v[b + 1], v[b + 2], v[b + 3], t, out)
            } else {
                for (i in 0 until width) out[i] = v[a + i] + (v[b + i] - v[a + i]) * t
            }
        }

        private fun slerp(ax: Float, ay: Float, az: Float, aw: Float, bx0: Float, by0: Float, bz0: Float, bw0: Float, t: Float, out: FloatArray) {
            var bx = bx0; var by = by0; var bz = bz0; var bw = bw0
            var cos = ax * bx + ay * by + az * bz + aw * bw
            if (cos < 0f) { cos = -cos; bx = -bx; by = -by; bz = -bz; bw = -bw }
            val (wa, wb) = if (cos > 0.9995f) (1f - t) to t else {
                val angle = acos(cos.coerceAtMost(1f))
                val s = sin(angle)
                sin((1f - t) * angle) / s to sin(t * angle) / s
            }
            val x = ax * wa + bx * wb
            val y = ay * wa + by * wb
            val z = az * wa + bz * wb
            val w = aw * wa + bw * wb
            val length = sqrt(x * x + y * y + z * z + w * w).takeIf { it > 0f } ?: 1f
            out[0] = x / length; out[1] = y / length; out[2] = z / length; out[3] = w / length
        }
    }
}
