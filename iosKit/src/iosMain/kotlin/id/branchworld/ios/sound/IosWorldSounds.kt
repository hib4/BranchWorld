@file:OptIn(ExperimentalForeignApi::class)

package id.branchworld.ios.sound

import id.branchworld.game.platform.sound.AmbienceGlide
import id.branchworld.game.platform.sound.AmbienceLayer
import id.branchworld.game.platform.sound.WorldAmbience
import id.branchworld.game.platform.sound.WorldSound
import id.branchworld.game.platform.sound.WorldSoundPlayer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionTypeEnded
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.setActive
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL

/**
 * The world's cues and soundscape on iOS with `AVAudioPlayer`, all on the main thread. The ambient audio category
 * mixes with the player's own music and follows the silent switch, as a calm game should. Layers fade with the shared
 * [AmbienceGlide], so the soundscape moves exactly as on Android and Desktop.
 */
internal class IosWorldSounds : WorldSoundPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val cues = mutableMapOf<WorldSound, AVAudioPlayer>()
    private val loops = mutableMapOf<AmbienceLayer, Loop>()
    private val unavailable = mutableSetOf<Any>()
    private var mix = WorldAmbience.Silent
    private var fading: Job? = null

    init {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryAmbient, error = null)
        session.setActive(true, error = null)
        // A call or another app's audio stops the loops; carry on when the interruption ends.
        NSNotificationCenter.defaultCenter.addObserverForName(AVAudioSessionInterruptionNotification, null,
            NSOperationQueue.mainQueue) { note ->
            val type = (note?.userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)?.unsignedLongValue
            if (type == AVAudioSessionInterruptionTypeEnded) {
                AVAudioSession.sharedInstance().setActive(true, error = null)
                for (loop in loops.values) if (loop.volume > 0f && !loop.player.playing) loop.player.play()
            }
        }
    }

    override fun play(sound: WorldSound) {
        scope.launch {
            val player = cues[sound] ?: open(sound.assetUri(), sound)?.also { cues[sound] = it } ?: return@launch
            player.stop()
            player.currentTime = 0.0
            if (player.play()) println("BranchWorldSound Played ${sound.name}")
        }
    }

    override fun setAmbience(mix: WorldAmbience) {
        scope.launch {
            if (this@IosWorldSounds.mix == mix) return@launch
            this@IosWorldSounds.mix = mix
            fading?.cancel()
            if (mix == WorldAmbience.Silent) {
                for (loop in loops.values) {
                    loop.player.stop()
                    loop.volume = 0f
                }
            } else fading = launch { glide() }
        }
    }

    private suspend fun glide() {
        while (true) {
            var moving = false
            for (layer in AmbienceLayer.entries) {
                val target = mix.volume(layer)
                if (target > 0f && layer !in loops) {
                    open(layer.assetUri(), layer)?.let { player ->
                        player.numberOfLoops = -1
                        player.volume = 0f
                        loops[layer] = Loop(player)
                    }
                }
                val loop = loops[layer] ?: continue
                loop.volume = AmbienceGlide.step(loop.volume, target)
                if (loop.volume != target) moving = true
                loop.player.volume = loop.volume
                if (loop.volume > 0f) {
                    if (!loop.player.playing && loop.player.play()) println("BranchWorldSound Ambience ${layer.name} started")
                } else if (loop.player.playing) loop.player.stop()
            }
            if (!moving) return
            delay(AmbienceGlide.StepMillis)
        }
    }

    /** A prepared player for a bundled sound, or null (logged once) when it cannot be read. */
    private fun open(uri: String, key: Any): AVAudioPlayer? {
        if (key in unavailable) return null
        val url = if (uri.contains("://")) NSURL.URLWithString(uri) else NSURL.fileURLWithPath(uri)
        val player = url?.let { AVAudioPlayer(contentsOfURL = it, error = null) }
        if (player == null) {
            unavailable += key
            println("BranchWorldSound Could not open $key at $uri")
            return null
        }
        player.prepareToPlay()
        return player
    }

    private class Loop(val player: AVAudioPlayer, var volume: Float = 0f)
}
