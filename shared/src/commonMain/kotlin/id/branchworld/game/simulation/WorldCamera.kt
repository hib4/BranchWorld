package id.branchworld.game.simulation

import id.branchworld.game.terrain.LandmarkKind

/** Viewport requests from the player interface. They never enter world commands or saves. */
data class WorldCamera(
    val focus: Focus? = null,
    val reducedMotion: Boolean = false,
    val opening: Boolean = false,
    val focusRequest: Int = 0,
    val targetSites: List<GroundPoint> = emptyList(),
    /** Rules v12: where the chosen tool would act. */
    val placement: PlacementPreview? = null,
    /** Rules v12: the creature the player picked out. */
    val selected: Long? = null,
) {
    sealed interface Focus {
        data class Region(val index: Int, val rain: Boolean = false) : Focus
        data class Creature(val id: Long) : Focus
        data class Landmark(val kind: LandmarkKind) : Focus
        data class WildlifeLocation(val ground: GroundPoint) : Focus
        data class EchoSite(val grounds: List<GroundPoint>, val sideDock: Boolean) : Focus
        /** A close, storybook framing of a settlement's homes, fields and stores. */
        data class Village(val grounds: List<GroundPoint>) : Focus
    }
}
