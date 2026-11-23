package id.branchworld.renderer.lighting

import id.branchworld.renderer.geometry.Point3
import id.branchworld.renderer.geometry.Rgba
import id.branchworld.simulation.ecology.environment.Weather
import id.branchworld.simulation.projection.ClimatePose
import kotlin.math.abs

/**
 * How the island is lit for the shown weather and time of day: sun and fill light, their colours and direction, and
 * the haze over land and sea. Engine-free, so every renderer backend lights the island the same way.
 */
internal data class CoastalLight(
    val sun: Float, val twilight: Float, val rain: Boolean, val storm: Boolean = false,
    val dryStorm: Boolean = false, val flash: Float = 0f,
) {
    val haze: Rgba get() = if (storm) Rgba(0.22f, 0.34f, 0.40f)
    else if (rain) Rgba(0.27f, 0.40f, 0.45f)
    else if (dryStorm) Rgba(0.26f, 0.38f, 0.45f)
    else Rgba(0.31f, 0.53f, 0.58f).mix(Rgba(0.17f, 0.23f, 0.36f), 1f - sun)
    val lightColor: Rgba get() = if (rain) Rgba(0.68f, 0.79f, 0.89f)
    else if (dryStorm) Rgba(0.79f, 0.81f, 0.87f).mix(Rgba(1f, 0.79f, 0.58f), twilight * 0.5f)
    else Rgba(1f, 0.94f, 0.79f).mix(Rgba(1f, 0.57f, 0.30f), twilight * 0.8f)
    val fillColor: Rgba get() = if (rain) Rgba(0.72f, 0.86f, 1f) else Rgba(0.64f, 0.77f, 1f)
    val strength: Float get() = if (storm) 0.24f + 0.42f * sun
    else if (rain) 0.26f + 0.64f * sun
    else if (dryStorm) 0.23f + 0.75f * sun + 0.28f * twilight
    else 0.20f + 1.00f * sun + 0.46f * twilight
    val fill: Float get() = 0.40f + sun * 0.14f + (if (rain) 0.12f else 0f) + flash
    val direction: Point3 get() = Point3(-0.58f - twilight * 0.42f, -1.45f + twilight * 0.92f, -0.86f)
}

internal fun coastalLight(climate: ClimatePose?, opening: Boolean, dryStorm: Boolean = false, flash: Float = 0f): CoastalLight {
    if (climate == null) return CoastalLight(0.94f, 0f, false)
    val rain = climate.weather == Weather.Rain
    if (opening && climate.dayPhase == 0f && climate.isDaylight && !rain) return CoastalLight(0.96f, 0f, false)
    val phase = climate.dayPhase
    val twilight = (1f - abs(phase - 0.52f) / 0.12f).coerceIn(0f, 1f)
    val sun = if (phase <= 0.5f) maxOf(climate.daylight, 0.74f * (1f - phase)) else climate.daylight
    return CoastalLight(sun, twilight, rain, climate.isStorm, dryStorm = dryStorm && !rain, flash = flash)
}
