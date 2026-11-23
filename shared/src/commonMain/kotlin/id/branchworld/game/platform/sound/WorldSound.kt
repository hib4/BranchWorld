package id.branchworld.game.platform.sound

import branchworld.shared.generated.resources.Res

/** A one-shot cue. */
enum class WorldSound(private val fileName: String) {
    Fork("fork.wav"),
    Save("save.wav"),
    Grove("grove.wav"),
    RainChime("spring-shower.wav"),
    Thunder("ember-thunder.wav"),
    UiSelect("ui-select.wav"),
    UiConfirm("ui-confirm.wav"),
    UiBack("ui-back.wav"),
    UiOpen("ui-open.wav");

    fun assetUri(): String = Res.getUri("files/sounds/$fileName")
}

/** A presentation request. Platform players own fades, audio focus, and native resources. */
enum class WorldAmbience(val surf: Float, val wind: Float, val rain: Float, val birds: Float, val crackle: Float = 0f) {
    Silent(0f, 0f, 0f, 0f),
    Sunlit(0.48f, 0.36f, 0f, 0.36f),
    Twilight(0.42f, 0.26f, 0f, 0f),
    LocalRain(0.40f, 0.33f, 0.48f, 0.14f),
    Rain(0.25f, 0.44f, 0.68f, 0f),
    Storm(0.27f, 0.63f, 0.83f, 0f),
    DryStorm(0.30f, 0.62f, 0f, 0.04f),
    Wildfire(0.24f, 0.45f, 0f, 0f, crackle = 0.72f);

    fun volume(layer: AmbienceLayer): Float = when (layer) {
        AmbienceLayer.Surf -> surf
        AmbienceLayer.Wind -> wind
        AmbienceLayer.Rain -> rain
        AmbienceLayer.Birds -> birds
        AmbienceLayer.Fire -> crackle
    }
}

/** One looping layer of the ambient soundscape. */
enum class AmbienceLayer(private val fileName: String) {
    Surf("coastal-surf.wav"), Wind("coastal-wind.wav"), Rain("coastal-rain.wav"), Birds("coastal-birds.wav"),
    Fire("ember-crackle.wav");

    fun assetUri(): String = Res.getUri("files/sounds/$fileName")
}
