package id.branchworld.game

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
internal class WebWorldSounds {
    private val scope = MainScope()
    private val context: dynamic = createAudioContext()
    private val buffers = mutableMapOf<String, Deferred<dynamic>>()
    private val loops = mutableMapOf<AmbienceLayer, Loop>()
    private val unavailable = mutableSetOf<AmbienceLayer>()
    private var mix = WorldAmbience.Silent

    init {
        if (context != null) {
            val unlock: (Event) -> Unit = { if (context.state == "suspended") context.resume() }
            for (type in listOf("pointerdown", "keydown", "touchend")) document.addEventListener(type, unlock)
        }
    }

    fun play(sound: WorldSound) {
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

    fun setAmbience(target: WorldAmbience) {
        if (context == null || mix == target) return
        mix = target
        scope.launch {
            for (layer in AmbienceLayer.entries) {
                val volume = target.volume(layer)
                val loop = loops[layer] ?: if (volume > 0f && layer !in unavailable) start(layer) else null
                // The same glide as the desktop mixer, which moves 17% of the way every 42 ms.
                loop?.gain?.gain?.setTargetAtTime(volume, context.currentTime, GlideSeconds)
            }
        }
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
