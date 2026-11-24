package id.branchworld.game.domain

import id.branchworld.game.platform.sound.MusicGlide
import id.branchworld.game.platform.sound.MusicMood
import id.branchworld.game.platform.sound.MusicTrack
import id.branchworld.game.platform.sound.WorldAmbience
import id.branchworld.game.platform.sound.WorldSound
import id.branchworld.game.platform.sound.WorldSoundPlayer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class MusicPolicyTest {
    @Test
    fun weatherAndFireSilenceTheMusic() {
        assertEquals(MusicMood.Day, MusicPolicy.mood(isWorld = true, WorldAmbience.Sunlit))
        assertEquals(MusicMood.Twilight, MusicPolicy.mood(isWorld = true, WorldAmbience.Twilight))
        for (mix in listOf(WorldAmbience.Silent, WorldAmbience.LocalRain, WorldAmbience.Rain, WorldAmbience.Storm,
            WorldAmbience.DryStorm, WorldAmbience.Wildfire)) {
            assertNull(MusicPolicy.mood(isWorld = true, mix), "$mix")
        }
        assertEquals(MusicMood.Day, MusicPolicy.mood(isWorld = false, WorldAmbience.Silent))
    }

    @Test
    fun waitsAreShortOnTheTitleAndLongBetweenPieces() {
        val random = Random(7)
        assertEquals(MusicPolicy.TitleWait, MusicPolicy.wait(first = true, isWorld = false, random))
        repeat(200) {
            assertTrue(MusicPolicy.wait(first = true, isWorld = true, random) in MusicPolicy.FirstWorldWait)
            assertTrue(MusicPolicy.wait(first = false, isWorld = true, random) in MusicPolicy.Silence)
            assertTrue(MusicPolicy.wait(first = false, isWorld = false, random) in MusicPolicy.Silence)
        }
    }

    @Test
    fun aPieceNeverPlaysTwiceInARowAndFollowsTheMood() {
        val random = Random(11)
        var last: MusicTrack? = null
        repeat(300) {
            val mood = if (it % 3 == 0) MusicMood.Twilight else MusicMood.Day
            val next = MusicPolicy.nextTrack(mood, last, random)
            assertNotEquals(last, next)
            if (last?.mood != mood || MusicTrack.entries.count { track -> track.mood == mood } > 1) {
                assertEquals(mood, next.mood)
            }
            last = next
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun stormFadesThePieceAndLeavingSilencesMusic() = runTest {
        val player = RecordingPlayer()
        val mood = MutableStateFlow<MusicMood?>(MusicMood.Day)
        val music = launch { playMusic(player, isWorld = true, mood, Random(3)) }

        advanceTimeBy(MusicPolicy.FirstWorldWait.endInclusive + 1.seconds)
        runCurrent()
        val started = player.calls.single()
        assertTrue(started != null)
        // The island softens while a piece plays, and comes back when it stops.
        assertEquals(listOf(MusicGlide.Duck), player.ducks)

        mood.value = null
        runCurrent()
        assertEquals(listOf(started, null), player.calls)
        assertEquals(listOf(MusicGlide.Duck, 1f), player.ducks)

        // Calm again: after the silence, another piece starts, never the same one.
        mood.value = MusicMood.Day
        advanceTimeBy(MusicPolicy.Silence.endInclusive + 1.seconds)
        runCurrent()
        assertEquals(3, player.calls.size)
        assertNotEquals(started, player.calls.last())

        // The piece ends by itself; the next one waits out a silence first.
        player.finish()
        advanceTimeBy(MusicPolicy.Silence.start - 1.seconds)
        runCurrent()
        assertEquals(3, player.calls.size)
        assertEquals(listOf(MusicGlide.Duck, 1f, MusicGlide.Duck, 1f), player.ducks)

        music.cancel()
        runCurrent()
        assertNull(player.calls.last())
        assertEquals(1f, player.ducks.last())
    }

    private class RecordingPlayer : WorldSoundPlayer {
        val calls = mutableListOf<MusicTrack?>()
        val ducks = mutableListOf<Float>()
        private var ended: (() -> Unit)? = null

        override fun duckAmbience(level: Float) {
            ducks += level
        }

        override fun play(sound: WorldSound) = Unit
        override fun setAmbience(mix: WorldAmbience) = Unit
        override fun playMusic(track: MusicTrack?, ended: () -> Unit) {
            calls += track
            this.ended = ended
        }

        fun finish() = checkNotNull(ended).invoke()
    }
}
