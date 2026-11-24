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
    // Surf and rain are the loudest recordings, so they sit low: the island should be a bed under the music.
    Sunlit(0.29f, 0.36f, 0f, 0.36f),
    Twilight(0.25f, 0.26f, 0f, 0f),
    LocalRain(0.24f, 0.33f, 0.17f, 0.14f),
    Rain(0.15f, 0.44f, 0.25f, 0f),
    Storm(0.16f, 0.63f, 0.30f, 0f),
    DryStorm(0.18f, 0.62f, 0f, 0.04f),
    Wildfire(0.14f, 0.45f, 0f, 0f, crackle = 0.72f);

    /** The volume a layer plays at: the mix above, scaled by [Master] so music can sit above the island. */
    fun volume(layer: AmbienceLayer): Float = Master * when (layer) {
        AmbienceLayer.Surf -> surf
        AmbienceLayer.Wind -> wind
        AmbienceLayer.Rain -> rain
        AmbienceLayer.Birds -> birds
        AmbienceLayer.Fire -> crackle
    }

    companion object {
        /** The whole soundscape's level (−2.5 dB). */
        const val Master = 0.75f
    }
}

/** One looping layer of the ambient soundscape. */
enum class AmbienceLayer(private val fileName: String) {
    Surf("coastal-surf.wav"), Wind("coastal-wind.wav"), Rain("coastal-rain.wav"), Birds("coastal-birds.wav"),
    Fire("ember-crackle.wav");

    fun assetUri(): String = Res.getUri("files/sounds/$fileName")
}

/** When a piece of music suits the island. */
enum class MusicMood { Day, Twilight }

/**
 * A piece of background music: Tom Feldmann's Free Wild Music Pack, CC BY 4.0 (credited in Settings and
 * docs/ASSETS.md). Each piece plays once, start to end; silence between pieces belongs to the island.
 */
enum class MusicTrack(private val fileName: String, val mood: MusicMood) {
    Plains("plains.mp3", MusicMood.Day),
    Village("village.mp3", MusicMood.Day),
    MysticalForest("mystical-forest.mp3", MusicMood.Twilight);

    fun assetUri(): String = Res.getUri("files/music/$fileName")
}
