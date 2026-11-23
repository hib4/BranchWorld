package id.branchworld.renderer.diagnostics

import de.fabmax.kool.pipeline.backend.stats.BackendStats
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Logs frame times (p50/p95) and draw counts over ten seconds, after two seconds of warm-up, for each [kind] and mode. */
internal class FrameTiming(private val kind: String, private val measureDraws: Boolean = false) {
    private var start: TimeMark? = null
    private var previous: TimeMark? = null
    private val samples = ArrayList<Double>()
    private var reported = false
    private var minimumDraws = Int.MAX_VALUE
    private var maximumDraws = 0

    fun reset() {
        start = null
        previous = null
        samples.clear()
        reported = false
        minimumDraws = Int.MAX_VALUE
        maximumDraws = 0
    }

    fun frame(mode: String? = null) {
        if (reported) return
        val first = start ?: TimeSource.Monotonic.markNow().also { start = it }
        val prior = previous
        previous = TimeSource.Monotonic.markNow()
        val elapsed = first.elapsedNow().inWholeMilliseconds
        if (prior == null || elapsed < 2_000) return
        samples += prior.elapsedNow().inWholeMicroseconds / 1000.0
        if (measureDraws) {
            minimumDraws = minOf(minimumDraws, BackendStats.numDrawCommands)
            maximumDraws = maxOf(maximumDraws, BackendStats.numDrawCommands)
        }
        if (elapsed < 12_000) return
        samples.sort()
        val label = if (mode == null) kind else "$kind $mode"
        println("BranchWorldRenderer $label frames=${samples.size} p50Ms=${samples[samples.lastIndex / 2]} p95Ms=${samples[(samples.lastIndex * 95) / 100]}")
        if (measureDraws) {
            println("BranchWorldRenderer drawCommands=$minimumDraws..$maximumDraws buffers=${BackendStats.totalBufferSize} textures=${BackendStats.totalTextureSize}")
        }
        reported = true
    }
}
