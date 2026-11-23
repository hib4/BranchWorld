package id.branchworld.game.domain

import id.branchworld.game.platform.sound.WorldAmbience
import id.branchworld.simulation.ecology.environment.Weather
import id.branchworld.simulation.projection.ClimatePose

/** The soundscape a moment of the world asks for. Weather outranks fire, and fire outranks the time of day. */
internal fun worldAmbience(
    enabled: Boolean, active: Boolean, climate: ClimatePose?, calledRain: Boolean = false,
    dryStorm: Boolean = false, burning: Boolean = false,
): WorldAmbience = when {
    !enabled || !active || climate == null -> WorldAmbience.Silent
    climate.isStorm -> WorldAmbience.Storm
    climate.weather == Weather.Rain -> WorldAmbience.Rain
    burning -> WorldAmbience.Wildfire
    calledRain -> WorldAmbience.LocalRain
    dryStorm -> WorldAmbience.DryStorm
    climate.dayPhase >= 0.46f && climate.dayPhase < 0.96f -> WorldAmbience.Twilight
    else -> WorldAmbience.Sunlit
}
