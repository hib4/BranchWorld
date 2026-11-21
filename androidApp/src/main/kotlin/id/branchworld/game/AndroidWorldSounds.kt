package id.branchworld.game

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log

internal class AndroidWorldSounds(private val context: Context) {
    private var pool: SoundPool? = null
    private val samples = mutableMapOf<WorldSound, Int>()
    private val ready = mutableSetOf<Int>()
    private val pending = mutableSetOf<Int>()

    @Synchronized
    fun play(sound: WorldSound) {
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
    fun close() {
        pool?.release()
        pool = null
        samples.clear()
        ready.clear()
        pending.clear()
    }

    private fun createPool() = SoundPool.Builder().setMaxStreams(2)
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
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
        } else {
            samples.values.remove(sample)
            Log.w("BranchWorldSound", "Could not load sample ($status)")
        }
    }

    private fun play(player: SoundPool, sample: Int) {
        if (player.play(sample, 1f, 1f, 1, 0, 1f) != 0) {
            Log.i("BranchWorldSound", "Played ${samples.entries.firstOrNull { it.value == sample }?.key?.name}")
        }
    }
}
