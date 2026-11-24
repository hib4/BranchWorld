package id.branchworld.desktop.sound

import fr.delthas.javamp3.Sound
import id.branchworld.game.platform.sound.MusicGlide
import id.branchworld.game.platform.sound.MusicTrack
import java.io.BufferedInputStream
import java.net.URI
import java.util.concurrent.Executors
import javax.sound.sampled.AudioSystem

/**
 * Streams one piece of music at a time through its own line. Java Sound reads only PCM, so JavaMP3 (MIT) decodes
 * the MP3 as it plays; fades scale the samples, which works on every output line.
 */
internal class DesktopMusic {
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "branchworld-music").apply { isDaemon = true }
    }

    @Volatile
    private var current: Piece? = null

    fun play(track: MusicTrack?, ended: () -> Unit) {
        if (worker.isShutdown) return
        current?.fadeOut()
        current = null
        if (track == null) return
        val piece = Piece(track, ended)
        current = piece
        // One worker: a new piece starts once the previous one has faded out.
        worker.execute(piece::stream)
    }

    fun close() {
        current?.stop()
        worker.shutdown()
    }

    private class Piece(private val track: MusicTrack, private val ended: () -> Unit) {
        @Volatile
        private var leaving = false

        @Volatile
        private var stopped = false

        fun fadeOut() {
            leaving = true
        }

        fun stop() {
            stopped = true
        }

        fun stream() {
            if (leaving || stopped) return
            try {
                Sound(BufferedInputStream(URI(track.assetUri()).toURL().openStream())).use { sound ->
                    val format = sound.audioFormat
                    val line = AudioSystem.getSourceDataLine(format)
                    line.open(format)
                    line.start()
                    println("BranchWorldSound Music ${track.name} started")
                    val bytesPerMilli = format.frameRate * format.frameSize / 1000f
                    val buffer = ByteArray(4096)
                    var playedMillis = 0L
                    var leftAt = -1L
                    var leftFrom = 0f
                    var volume = 0f
                    var finished = false
                    while (!stopped) {
                        val read = sound.read(buffer)
                        if (read <= 0) {
                            finished = true
                            break
                        }
                        if (leaving && leftAt < 0) {
                            leftAt = playedMillis
                            leftFrom = volume
                        }
                        volume = if (leftAt >= 0) {
                            MusicGlide.ramp(leftFrom, 0f, playedMillis - leftAt, MusicGlide.FadeOutMillis)
                        } else {
                            MusicGlide.ramp(0f, MusicGlide.Volume, playedMillis, MusicGlide.FadeInMillis)
                        }
                        scale(buffer, read, volume)
                        line.write(buffer, 0, read)
                        playedMillis += (read / bytesPerMilli).toLong()
                        if (leftAt >= 0 && volume == 0f) break
                    }
                    if (finished) line.drain() else line.flush()
                    line.close()
                    if (finished && !leaving && !stopped) {
                        println("BranchWorldSound Music ${track.name} ended")
                        ended()
                    } else println("BranchWorldSound Music ${track.name} faded")
                }
            } catch (error: Exception) {
                println("BranchWorldSound Could not play music ${track.name}: ${error.message}")
                if (!leaving && !stopped) ended()
            }
        }

        /** Scales 16-bit little-endian samples in place. */
        private fun scale(buffer: ByteArray, length: Int, volume: Float) {
            var i = 0
            while (i + 1 < length) {
                val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
                val scaled = (sample * volume).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                buffer[i] = scaled.toByte()
                buffer[i + 1] = (scaled shr 8).toByte()
                i += 2
            }
        }
    }
}
