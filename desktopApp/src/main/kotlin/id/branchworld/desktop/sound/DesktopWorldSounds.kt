package id.branchworld.desktop.sound

import id.branchworld.game.platform.sound.AmbienceGlide
import id.branchworld.game.platform.sound.AmbienceLayer
import id.branchworld.game.platform.sound.MusicTrack
import id.branchworld.game.platform.sound.WorldAmbience
import id.branchworld.game.platform.sound.WorldSound
import id.branchworld.game.platform.sound.WorldSoundPlayer

import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit.MILLISECONDS
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.FloatControl
import kotlin.math.log10

/** Java Sound clips on one daemon audio thread; ambient layers fade through their master gain. */
internal class DesktopWorldSounds : WorldSoundPlayer {
    private val audio = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "branchworld-audio").apply { isDaemon = true }
    }
    private val clips = mutableMapOf<WorldSound, Clip>()
    private val loops = mutableMapOf<AmbienceLayer, Loop>()
    private val unavailable = mutableSetOf<AmbienceLayer>()
    private var mix = WorldAmbience.Silent
    private var update: ScheduledFuture<*>? = null
    private val music = DesktopMusic()
    private var duck = 1f

    override fun play(sound: WorldSound) {
        if (audio.isShutdown) return
        audio.execute {
            try {
                val clip = clips.getOrPut(sound) { openClip(sound.assetUri()) }
                clip.stop()
                clip.framePosition = 0
                clip.start()
                println("BranchWorldSound Played ${sound.name}")
            } catch (error: Exception) {
                println("BranchWorldSound Could not play ${sound.name}: ${error.message}")
            }
        }
    }

    override fun setAmbience(mix: WorldAmbience) {
        if (audio.isShutdown) return
        audio.execute {
            if (this.mix == mix) return@execute
            this.mix = mix
            update?.cancel(false)
            if (mix == WorldAmbience.Silent) {
                for (loop in loops.values) {
                    loop.clip.stop()
                    loop.volume = 0f
                }
            } else updateLoops()
        }
    }

    override fun playMusic(track: MusicTrack?, ended: () -> Unit) = music.play(track, ended)

    override fun duckAmbience(level: Float) {
        if (audio.isShutdown) return
        audio.execute {
            if (duck == level) return@execute
            duck = level
            println("BranchWorldSound Ambience level $level")
            if (mix != WorldAmbience.Silent) {
                update?.cancel(false)
                updateLoops()
            }
        }
    }

    private fun updateLoops() {
        var moving = false
        for (layer in AmbienceLayer.entries) {
            val target = mix.volume(layer) * duck
            if (target > 0f && layer !in loops && layer !in unavailable) {
                try {
                    val clip = openClip(layer.assetUri())
                    if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                        loops[layer] = Loop(clip, clip.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl)
                    } else {
                        clip.close()
                        unavailable.add(layer)
                    }
                } catch (error: Exception) {
                    unavailable.add(layer)
                    println("BranchWorldSound Could not open ambience ${layer.name}: ${error.message}")
                }
            }
            val loop = loops[layer] ?: continue
            loop.volume = AmbienceGlide.step(loop.volume, target)
            if (loop.volume != target) moving = true
            if (loop.volume > 0f) {
                loop.gain.value = (20f * log10(loop.volume)).coerceIn(loop.gain.minimum, loop.gain.maximum)
                if (!loop.clip.isRunning) {
                    loop.clip.loop(Clip.LOOP_CONTINUOUSLY)
                    println("BranchWorldSound Ambience ${layer.name} started")
                }
            } else loop.clip.stop()
        }
        if (moving && !audio.isShutdown) update = audio.schedule(::updateLoops, AmbienceGlide.StepMillis, MILLISECONDS)
    }

    private fun openClip(uri: String): Clip = AudioSystem.getAudioInputStream(URI(uri).toURL()).use { stream ->
        val clip = AudioSystem.getClip()
        try {
            clip.open(stream)
            clip
        } catch (error: Exception) {
            clip.close()
            throw error
        }
    }

    fun close() {
        music.close()
        if (audio.isShutdown) return
        audio.execute {
            update?.cancel(false)
            clips.values.forEach(Clip::close)
            loops.values.forEach { it.clip.close() }
        }
        audio.shutdown()
    }

    private class Loop(val clip: Clip, val gain: FloatControl, var volume: Float = 0f)
}
