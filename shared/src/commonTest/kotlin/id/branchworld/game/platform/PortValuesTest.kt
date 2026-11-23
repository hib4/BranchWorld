package id.branchworld.game.platform

import id.branchworld.game.platform.photo.PhotoMeta
import id.branchworld.game.platform.sound.AmbienceGlide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PortValuesTest {
    @Test
    fun ambientLayersGlideSeventeenPercentAndSnapWhenClose() {
        assertEquals(0.17f, AmbienceGlide.step(0f, 1f))
        assertEquals(0.5f, AmbienceGlide.step(0.495f, 0.5f))
        var volume = 0f
        var steps = 0
        while (volume != 0.6f) {
            volume = AmbienceGlide.step(volume, 0.6f)
            steps++
        }
        assertTrue(steps in 20..30, "glide took $steps steps")
        assertEquals(0f, AmbienceGlide.step(0.004f, 0f))
    }

    @Test
    fun photoNamesCarryTheWorldAndMoment() {
        assertEquals("branchworld-104729-360.png", PhotoMeta(104729, 360).fileName())
        assertEquals("branchworld-7-0-abc.png", PhotoMeta(7, 0).fileName("abc"))
    }
}
