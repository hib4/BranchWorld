package id.branchworld.game.domain.session

import id.branchworld.game.domain.IslandSessionKey
import id.branchworld.simulation.terrain.TerrainMap
import id.branchworld.simulation.timeline.Timeline

/** Where opening a world stands. */
internal sealed interface IslandLoad {
    data object Empty : IslandLoad
    data class Loading(val key: IslandSessionKey) : IslandLoad
    data class Failed(val key: IslandSessionKey) : IslandLoad

    /**
     * An open world. Each role lives in its own class over one shared [WorldSessionCore]; this type joins them so
     * the interface can hand one object to the panels that need several roles.
     */
    class Ready private constructor(core: WorldSessionCore) : IslandLoad,
        OpenWorld by core,
        WorldPlacement by WorldPlacementActions(core),
        HistoryNavigation by HistoryNavigator(core),
        BranchComparing by BranchComparisons(core),
        ChapterControls by LegacyChapterControls(core) {

        constructor(terrain: TerrainMap, timeline: Timeline, sharedOrigin: String? = null, worldSlot: String? = null) :
            this(WorldSessionCore(terrain, timeline, sharedOrigin, worldSlot))
    }
}
