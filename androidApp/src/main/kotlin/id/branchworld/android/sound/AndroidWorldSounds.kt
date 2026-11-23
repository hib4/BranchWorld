package id.branchworld.android.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.util.Log
import id.branchworld.game.platform.sound.AmbienceGlide
import id.branchworld.game.platform.sound.AmbienceLayer
import id.branchworld.game.platform.sound.WorldAmbience
import id.branchworld.game.platform.sound.WorldSound
import id.branchworld.game.platform.sound.WorldSoundPlayer

/** Cues through a [SoundPool] and the looping soundscape under audio focus; silent while the app is paused. */
internal class AndroidWorldSounds(private val context: Context) : WorldSoundPlayer {
    private var pool: SoundPool? = null
    private val samples = mutableMapOf<WorldSound, Int>()
    private val ready = mutableSetOf<Int>()
    private val pending = mutableSetOf<Int>()
    private val loops = mutableMapOf<AmbienceLayer, Loop>()
    private val handler = Handler(Looper.getMainLooper())
    private val manager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private var foreground = false
    private var mix = WorldAmbience.Silent
    private var focusGain = 0f
    private var requestedFocus = false
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes).setOnAudioFocusChangeListener({ focusChanged(it) }, handler).build()
    private val fade = Runnable(::updateLoops)

    @Synchronized
    override fun play(sound: WorldSound) {
        if (!foreground) return
        try {
            val player = pool ?: createPool().also { pool = it }
            val sample = samples.getOrPut(sound) {
                val path = sound.assetUri().removePrefix("file:///android_asset/")
                context.assets.openFd(path).use { player.load(it, 1) }.also { check(it > 0) }
            }
            if (sample in ready) play(player, sample) else pending.add(sample)
        } catch (error: Exception) {
            Log.w("BranchWorldSound", "Could not play ${sound.name}", error)
        }
    }

    @Synchronized
    override fun setAmbience(mix: WorldAmbience) {
        if (this.mix == mix && (requestedFocus || mix == WorldAmbience.Silent)) return
        this.mix = mix
        if (!foreground) return
        if (mix != WorldAmbience.Silent) {
            requestFocus()
            prepareLoops()
        }
        scheduleFade()
    }

    @Synchronized
    fun resume() {
        foreground = true
        if (mix == WorldAmbience.Silent) return
        requestFocus()
        prepareLoops()
        scheduleFade()
    }

    @Synchronized
    fun pause() {
        foreground = false
        handler.removeCallbacks(fade)
        releaseFocus()
        pool?.release()
        pool = null
        samples.clear()
        ready.clear()
        pending.clear()
        loops.clear()
    }

    private fun createPool() = SoundPool.Builder().setMaxStreams(6)
        .setAudioAttributes(attributes)
        .build().apply {
            setOnLoadCompleteListener { player, sample, status -> loaded(player, sample, status) }
        }

    @Synchronized
    private fun loaded(player: SoundPool, sample: Int, status: Int) {
        if (player !== pool) return
        val requested = pending.remove(sample)
        if (status == 0) {
            ready.add(sample)
            if (requested) play(player, sample)
            if (loops.values.any { it.sample == sample }) scheduleFade()
        } else {
            samples.values.remove(sample)
            Log.w("BranchWorldSound", "Could not load sample ($status)")
        }
    }

    private fun prepareLoops() {
        try {
            val player = pool ?: createPool().also { pool = it }
            for (layer in AmbienceLayer.entries) {
                if (layer in loops) continue
                val path = layer.assetUri().removePrefix("file:///android_asset/")
                val sample = context.assets.openFd(path).use { player.load(it, 0) }
                loops[layer] = Loop(sample)
            }
        } catch (error: Exception) {
            Log.w("BranchWorldSound", "Could not load the coastal soundscape", error)
        }
    }

    private fun requestFocus() {
        if (requestedFocus) return
        requestedFocus = manager?.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        focusGain = if (requestedFocus) 1f else 0f
    }

    private fun releaseFocus() {
        if (requestedFocus) manager?.abandonAudioFocusRequest(focus)
        requestedFocus = false
        focusGain = 0f
    }

    @Synchronized
    private fun focusChanged(change: Int) {
        if (!foreground || !requestedFocus) return
        focusGain = when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> 1f
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> 0.23f
            else -> 0f
        }
        scheduleFade()
    }

    private fun scheduleFade() {
        handler.removeCallbacks(fade)
        handler.post(fade)
    }

    @Synchronized
    private fun updateLoops() {
        val player = pool ?: return
        if (!foreground) return
        var moving = false
        for ((layer, loop) in loops) {
            val target = mix.volume(layer) * focusGain
            if (loop.sample !in ready) continue
            loop.volume = AmbienceGlide.step(loop.volume, target)
            if (loop.volume != target) moving = true
            if (loop.volume > 0f) {
                if (loop.stream == 0) {
                    loop.stream = player.play(loop.sample, loop.volume, loop.volume, 0, -1, 1f)
                    if (loop.stream != 0) Log.i("BranchWorldSound", "Ambience ${layer.name} started")
                } else player.setVolume(loop.stream, loop.volume, loop.volume)
            } else if (loop.stream != 0) {
                player.stop(loop.stream)
                loop.stream = 0
            }
        }
        if (moving) handler.postDelayed(fade, AmbienceGlide.StepMillis)
        else if (mix == WorldAmbience.Silent) releaseFocus()
    }

    private class Loop(val sample: Int, var stream: Int = 0, var volume: Float = 0f)

    private fun play(player: SoundPool, sample: Int) {
        if (player.play(sample, 1f, 1f, 1, 0, 1f) != 0) {
            Log.i("BranchWorldSound", "Played ${samples.entries.firstOrNull { it.value == sample }?.key?.name}")
        }
    }
}
