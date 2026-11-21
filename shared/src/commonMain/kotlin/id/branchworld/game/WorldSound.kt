package id.branchworld.game

import branchworld.shared.generated.resources.Res
import id.branchworld.game.ecology.Weather
import id.branchworld.game.simulation.ClimatePose

enum class WorldSound(private val fileName: String) {
    Fork("fork.wav"),
    Save("save.wav"),
    Grove("grove.wav");

    fun assetUri(): String = Res.getUri("files/sounds/$fileName")
}

/** A presentation request. Platform players own fades, audio focus, and native resources. */
enum class WorldAmbience(val surf: Float, val wind: Float, val rain: Float, val birds: Float) {
    Silent(0f, 0f, 0f, 0f),
    Sunlit(0.48f, 0.36f, 0f, 0.36f),
    Twilight(0.42f, 0.26f, 0f, 0f),
    Rain(0.25f, 0.44f, 0.68f, 0f),
    Storm(0.27f, 0.63f, 0.83f, 0f);

    fun volume(layer: AmbienceLayer): Float = when (layer) {
        AmbienceLayer.Surf -> surf
        AmbienceLayer.Wind -> wind
        AmbienceLayer.Rain -> rain
        AmbienceLayer.Birds -> birds
    }
}

enum class AmbienceLayer(private val fileName: String) {
    Surf("coastal-surf.wav"), Wind("coastal-wind.wav"), Rain("coastal-rain.wav"), Birds("coastal-birds.wav");

    fun assetUri(): String = Res.getUri("files/sounds/$fileName")
}

internal fun worldAmbience(enabled: Boolean, active: Boolean, climate: ClimatePose?): WorldAmbience = when {
    !enabled || !active || climate == null -> WorldAmbience.Silent
    climate.isStorm -> WorldAmbience.Storm
    climate.weather == Weather.Rain -> WorldAmbience.Rain
    climate.dayPhase >= 0.46f && climate.dayPhase < 0.96f -> WorldAmbience.Twilight
    else -> WorldAmbience.Sunlit
}
