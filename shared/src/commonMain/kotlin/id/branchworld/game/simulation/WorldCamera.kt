package id.branchworld.game.simulation

/** Viewport requests from the player interface. They never enter world commands or saves. */
data class WorldCamera(
    val focus: Focus? = null,
    val reducedMotion: Boolean = false,
    val opening: Boolean = false,
    val focusRequest: Int = 0,
) {
    sealed interface Focus {
        data class Region(val index: Int) : Focus
        data class Creature(val id: Long) : Focus
    }
}
