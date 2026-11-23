package id.branchworld.game.platform.sound

/** Plays the world's cues and soundscape. Each platform owns its audio engine, focus and native resources. */
interface WorldSoundPlayer {
    fun play(sound: WorldSound)

    /** Fades every ambient layer toward [mix]. [WorldAmbience.Silent] fades the soundscape out. */
    fun setAmbience(mix: WorldAmbience)
}
