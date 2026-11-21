package id.branchworld.game.simulation

import id.branchworld.game.terrain.LandmarkKind

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
        data class Landmark(val kind: LandmarkKind) : Focus
        data class WildlifeLocation(val ground: GroundPoint) : Focus
    }
}
