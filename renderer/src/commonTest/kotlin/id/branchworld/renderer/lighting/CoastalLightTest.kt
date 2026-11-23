package id.branchworld.renderer.lighting

import id.branchworld.simulation.ecology.environment.Weather
import id.branchworld.simulation.projection.ClimatePose
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoastalLightTest {
    @Test
    fun lightReadsTheDisplayedClimateAndRewindDiscardsTheCurrentWeather() {
        val morning = ClimatePose(1, true, Weather.Dry, 0.50f, 0f)
        val rain = ClimatePose(1, true, Weather.Rain, 1f, 330f / 1200f)
        val dusk = ClimatePose(1, false, Weather.Dry, 0.125f, 645f / 1200f)
        val opening = coastalLight(morning, opening = true)
        val shower = coastalLight(rain, opening = false)
        val evening = coastalLight(dusk, opening = false)
        assertTrue(shower.rain && shower.haze != opening.haze)
        assertTrue(evening.twilight > 0.8f && evening.sun < 0.2f)
        assertTrue(evening.direction.y > opening.direction.y)
        assertEquals(opening, coastalLight(morning, opening = true))
        assertTrue(coastalLight(morning, opening = false).sun < opening.sun)
    }
}
