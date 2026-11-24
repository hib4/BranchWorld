package id.branchworld.web.sound

import id.branchworld.game.platform.sound.AmbienceLayer
import id.branchworld.game.platform.sound.MusicGlide
import id.branchworld.game.platform.sound.MusicTrack
import id.branchworld.game.platform.sound.WorldAmbience
import id.branchworld.game.platform.sound.WorldSound
import id.branchworld.game.platform.sound.WorldSoundPlayer
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import org.w3c.dom.events.Event
import kotlin.js.Promise

/**
 * WebAudio playback mirroring the desktop mix: one-shot cues and looping ambience layers that glide to
 * their target volumes. Browsers keep audio muted until the player interacts, so the context resumes on
 * the first pointer or key press and any ambience requested before then starts at that moment.
 */
internal class WebWorldSounds : WorldSoundPlayer {
    private val scope = MainScope()
    private val context: dynamic = createAudioContext()
    private val buffers = mutableMapOf<String, Deferred<dynamic>>()
    private val loops = mutableMapOf<AmbienceLayer, Loop>()
    private val unavailable = mutableSetOf<AmbienceLayer>()
    private var mix = WorldAmbience.Silent
    private var piece: Piece? = null
    private var duck = 1f
    private var waitingForPlayer: Piece? = null

    init {
        if (context != null) {
            val unlock: (Event) -> Unit = {
                if (context.state == "suspended") context.resume()
                waitingForPlayer?.let { waiting ->
                    waitingForPlayer = null
                    if (waiting === piece) start(waiting)
                }
            }
            for (type in listOf("pointerdown", "keydown", "touchend")) document.addEventListener(type, unlock)
        }
    }

    override fun play(sound: WorldSound) {
        if (context == null) return
        scope.launch {
            try {
                val source = context.createBufferSource()
                source.buffer = buffer(sound.assetUri())
                source.connect(context.destination)
                source.start()
            } catch (error: Throwable) {
                console.warn("BranchWorld could not play ${sound.name}: ${error.message}")
            }
        }
    }

    override fun setAmbience(mix: WorldAmbience) {
        if (context == null || this.mix == mix) return
        this.mix = mix
        scope.launch {
            for (layer in AmbienceLayer.entries) {
                val volume = mix.volume(layer) * duck
                val loop = loops[layer] ?: if (volume > 0f && layer !in unavailable) start(layer) else null
                // The same glide as the desktop mixer, which moves 17% of the way every 42 ms.
                loop?.gain?.gain?.setTargetAtTime(volume, context.currentTime, GlideSeconds)
            }
        }
    }

    override fun duckAmbience(level: Float) {
        if (context == null || duck == level) return
        duck = level
        console.log("BranchWorldSound Ambience level $level")
        for ((layer, loop) in loops) loop.gain.gain.setTargetAtTime(mix.volume(layer) * duck, context.currentTime, GlideSeconds)
    }

    /**
     * Music streams through a media element routed into WebAudio, so a piece is never decoded whole into memory and
     * its gain fades in every browser, Safari included.
     */
    override fun playMusic(track: MusicTrack?, ended: () -> Unit) {
        if (context == null) return
        piece?.let(::leave)
        piece = null
        waitingForPlayer = null
        if (track == null) return
        try {
            val element: dynamic = js("new Audio()")
            element.preload = "auto"
            element.src = track.assetUri()
            val source = context.createMediaElementSource(element)
            val gain = context.createGain()
            gain.gain.value = 0
            source.connect(gain)
            gain.connect(context.destination)
            val next = Piece(track, element, gain, ended)
            element.onended = {
                if (piece === next) {
                    piece = null
                    console.log("BranchWorldSound Music ${track.name} ended")
                    ended()
                }
            }
            element.onerror = {
                if (piece === next) {
                    piece = null
                    console.warn("BranchWorld could not play music ${track.name}")
                    ended()
                }
            }
            piece = next
            start(next)
        } catch (error: Throwable) {
            console.warn("BranchWorld could not play music ${track.name}: ${error.message}")
            ended()
        }
    }

    private fun start(next: Piece) {
        val now = context.currentTime
        next.gain.gain.setValueAtTime(0, now)
        next.gain.gain.linearRampToValueAtTime(MusicGlide.Volume, now + MusicGlide.FadeInMillis / 1000.0)
        val playing = next.element.play()
        (playing as? Promise<dynamic>)?.then({ _: dynamic -> console.log("BranchWorldSound Music ${next.track.name} started") },
            { _: dynamic -> waitingForPlayer = next }) // Autoplay waits for the first tap or key press.
    }

    private fun leave(gone: Piece) {
        val now = context.currentTime
        gone.gain.gain.cancelScheduledValues(now)
        gone.gain.gain.setValueAtTime(gone.gain.gain.value, now)
        gone.gain.gain.linearRampToValueAtTime(0, now + MusicGlide.FadeOutMillis / 1000.0)
        gone.element.onended = null
        window.setTimeout({
            gone.element.pause()
            gone.gain.disconnect()
            console.log("BranchWorldSound Music ${gone.track.name} faded")
        }, MusicGlide.FadeOutMillis.toInt())
    }

    private suspend fun start(layer: AmbienceLayer): Loop? = try {
        val gain = context.createGain()
        gain.gain.value = 0
        gain.connect(context.destination)
        val source = context.createBufferSource()
        source.buffer = buffer(layer.assetUri())
        source.loop = true
        source.connect(gain)
        source.start()
        Loop(source, gain).also { loops[layer] = it }
    } catch (error: Throwable) {
        unavailable += layer
        console.warn("BranchWorld could not open ambience ${layer.name}: ${error.message}")
        null
    }

    private suspend fun buffer(uri: String): dynamic = buffers.getOrPut(uri) {
        scope.async {
            val response: dynamic = window.asDynamic().fetch(uri).unsafeCast<Promise<dynamic>>().await()
            check(response.ok == true) { "HTTP ${response.status} for $uri" }
            val bytes = response.arrayBuffer().unsafeCast<Promise<dynamic>>().await()
            decode(bytes).await()
        }
    }.await()

    private fun decode(bytes: dynamic): Deferred<dynamic> {
        val done = CompletableDeferred<dynamic>()
        val promise = context.decodeAudioData(bytes, { decoded: dynamic -> done.complete(decoded) },
            { error: dynamic -> done.completeExceptionally(IllegalStateException("Could not decode audio: $error")) })
        // Current browsers also return a promise; failures already reach the error callback.
        (promise as? Promise<dynamic>)?.catch { }
        return done
    }

    private class Loop(val source: dynamic, val gain: dynamic)

    private class Piece(val track: MusicTrack, val element: dynamic, val gain: dynamic, val ended: () -> Unit)

    private companion object {
        const val GlideSeconds = 0.23

        fun createAudioContext(): dynamic {
            val type = window.asDynamic().AudioContext ?: window.asDynamic().webkitAudioContext ?: return null
            return try {
                js("new type()")
            } catch (_: Throwable) {
                null
            }
        }
    }
}
