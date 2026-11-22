package id.branchworld.game

import id.branchworld.game.ecology.Weather
import id.branchworld.game.simulation.ClimatePose
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorldSoundTest {
    @Test
    fun fireSoundsFollowTheDisplayedConditionsAndSoundSettings() {
        val clear = ClimatePose(1, true, Weather.Dry, 0.85f, 0.25f)
        val dryStorm = worldAmbience(enabled = true, active = true, clear, dryStorm = true)
        assertEquals(0f, dryStorm.volume(AmbienceLayer.Rain))
        assertTrue(dryStorm.volume(AmbienceLayer.Wind) > WorldAmbience.Sunlit.volume(AmbienceLayer.Wind))
        assertTrue(worldAmbience(true, true, clear, burning = true).volume(AmbienceLayer.Fire) > 0f)
        assertEquals(WorldAmbience.Silent, worldAmbience(false, true, clear, burning = true))
        assertEquals(WorldAmbience.Silent, worldAmbience(true, false, clear, burning = true))
        assertEquals(WorldAmbience.Rain, worldAmbience(true, true, clear.copy(weather = Weather.Rain), burning = true))
    }
}
