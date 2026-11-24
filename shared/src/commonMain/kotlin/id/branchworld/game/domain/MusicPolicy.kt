package id.branchworld.game.domain

import id.branchworld.game.platform.sound.MusicGlide
import id.branchworld.game.platform.sound.MusicMood
import id.branchworld.game.platform.sound.MusicTrack
import id.branchworld.game.platform.sound.WorldAmbience
import id.branchworld.game.platform.sound.WorldSoundPlayer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * When BranchWorld plays music: a piece now and then, with minutes of the island's own sound in between, and none
 * while rain, storm or fire hold the soundscape. Presentation only: music never reads or changes the world's rules.
 */
internal object MusicPolicy {
    val TitleWait: Duration = 5.seconds
    val FirstWorldWait: ClosedRange<Duration> = 20.seconds..40.seconds
    val Silence: ClosedRange<Duration> = 120.seconds..300.seconds

    /** The music the moment suits, or null when the weather or a fire should be heard alone. */
    fun mood(isWorld: Boolean, mix: WorldAmbience): MusicMood? = when {
        !isWorld -> MusicMood.Day
        mix == WorldAmbience.Sunlit -> MusicMood.Day
        mix == WorldAmbience.Twilight -> MusicMood.Twilight
        else -> null
    }

    /** How long to wait before the next piece. */
    fun wait(first: Boolean, isWorld: Boolean, random: Random): Duration = when {
        first && !isWorld -> TitleWait
        first -> random.between(FirstWorldWait)
        else -> random.between(Silence)
    }

    /** A piece for [mood], never the one that just played. */
    fun nextTrack(mood: MusicMood, last: MusicTrack?, random: Random): MusicTrack {
        val others = MusicTrack.entries.filter { it != last }.ifEmpty { MusicTrack.entries }
        val pool = others.filter { it.mood == mood }.ifEmpty { others }
        return pool[random.nextInt(pool.size)]
    }

    private fun Random.between(range: ClosedRange<Duration>): Duration =
        range.start + (range.endInclusive - range.start) * nextDouble()
}

/**
 * Plays music on [sounds] for as long as the caller keeps this running, following [mood]. Leaving (cancellation)
 * fades the music out.
 */
internal suspend fun playMusic(
    sounds: WorldSoundPlayer,
    isWorld: Boolean,
    mood: Flow<MusicMood?>,
    random: Random = Random.Default,
) {
    var last: MusicTrack? = null
    var first = true
    try {
        while (true) {
            delay(MusicPolicy.wait(first, isWorld, random))
            first = false
            val wanted = mood.filterNotNull().first()
            val track = MusicPolicy.nextTrack(wanted, last, random).also { last = it }
            val ended = CompletableDeferred<Unit>()
            sounds.duckAmbience(MusicGlide.Duck)
            sounds.playMusic(track) { ended.complete(Unit) }
            coroutineScope {
                val unsuited = async { mood.first { it == null } }
                select {
                    ended.onAwait {}
                    unsuited.onAwait { sounds.playMusic(null) }
                }
                unsuited.cancel()
            }
            sounds.duckAmbience(1f)
        }
    } finally {
        sounds.playMusic(null)
        sounds.duckAmbience(1f)
    }
}
