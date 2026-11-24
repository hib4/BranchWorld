package id.branchworld.game.platform.sound

/** Plays the world's cues, soundscape and music. Each platform owns its audio engine, focus and native resources. */
interface WorldSoundPlayer {
    fun play(sound: WorldSound)

    /** Fades every ambient layer toward [mix]. [WorldAmbience.Silent] fades the soundscape out. */
    fun setAmbience(mix: WorldAmbience)

    /**
     * Plays [track] once from its start, fading in, and fades out any piece already playing; null fades music out.
     * [ended] runs, on any thread, when [track] reaches its end by itself or cannot be played, so the music carries
     * on after a silence; it does not run when the piece was faded out on request.
     */
    fun playMusic(track: MusicTrack?, ended: () -> Unit = {}) {}

    /** Glides the whole soundscape to [level] of its mix (1 is full), so music can be heard over it. */
    fun duckAmbience(level: Float) {}
}
