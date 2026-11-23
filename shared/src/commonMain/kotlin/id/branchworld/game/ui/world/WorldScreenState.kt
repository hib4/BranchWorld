package id.branchworld.game.ui.world

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import id.branchworld.game.platform.photo.PhotoResult
import id.branchworld.game.ui.legacy.Story
import id.branchworld.simulation.projection.GroundPoint
import id.branchworld.simulation.projection.PlaceTool
import id.branchworld.simulation.projection.WorldCamera
import id.branchworld.simulation.projection.WorldPick

/** The side panels an open world can show. */
internal enum class WorldPanel { Inspect, Grove, Village, Timeline, Diagnostics, Settings, Moments }

/** Full-screen layers over an open world. */
internal enum class WorldOverlay { Pause, Photo, Closing }

/**
 * What the interface around an open world is doing: its panel and overlay, the player's hand, the camera's focus and
 * short-lived notes. The panel, overlay, tool and selection survive configuration changes; the rest is momentary.
 */
@Stable
internal class WorldScreenState(
    panel: WorldPanel? = null,
    overlay: WorldOverlay? = null,
    tool: PlaceTool = PlaceTool.Rabbit,
    selectedId: Long? = null,
    announcedPlanting: String? = null,
) {
    var panel by mutableStateOf(panel)
        private set
    var overlay by mutableStateOf(overlay)
    var tool by mutableStateOf(tool)
    var selectedId by mutableStateOf(selectedId)

    /** The last planting the player was told about, so it is announced once. */
    var announcedPlanting by mutableStateOf(announcedPlanting)

    /** Trees just planted in an earlier chapter's grove, shown for a few seconds. */
    var plantingNotice by mutableStateOf<Int?>(null)

    var focus by mutableStateOf<WorldCamera.Focus?>(null)
    var focusRequest by mutableIntStateOf(0)
        private set
    var shapeSites by mutableStateOf<List<GroundPoint>>(emptyList())
    var savingPhoto by mutableStateOf(false)
    var photoResult by mutableStateOf<PhotoResult?>(null)

    /** A chapter the player has just closed, and the history they closed it on. */
    var closed by mutableStateOf<Pair<Story, String>?>(null)

    /** Start the clock once the island is ready (a world begun from the menus). */
    var autoStart by mutableStateOf(false)
    var handNote by mutableStateOf<String?>(null)
        private set
    var handNoteAt by mutableIntStateOf(0)
        private set

    /** A tap on a past moment that would need a new history first. */
    var pastPick by mutableStateOf<WorldPick?>(null)

    fun requestFocus(target: WorldCamera.Focus) {
        focus = target
        focusRequest++
    }

    /** Opens [destination], or closes the panel. Following a creature survives closing its inspector. */
    fun showPanel(destination: WorldPanel?) {
        if (destination != panel || destination == null) {
            val keepFollowing = panel == WorldPanel.Inspect && destination == null && focus is WorldCamera.Focus.Creature
            if (!keepFollowing) focus = null
            shapeSites = emptyList()
            panel = destination
        }
    }

    fun note(text: String) {
        handNote = text
        handNoteAt++
    }

    fun clearNote() {
        handNote = null
    }

    companion object {
        val Saver = listSaver<WorldScreenState, Any?>(
            save = { listOf(it.panel?.name, it.overlay?.name, it.tool.name, it.selectedId, it.announcedPlanting) },
            restore = {
                WorldScreenState(
                    (it[0] as String?)?.let(WorldPanel::valueOf),
                    (it[1] as String?)?.let(WorldOverlay::valueOf),
                    PlaceTool.valueOf(it[2] as String),
                    it[3] as Long?,
                    it[4] as String?,
                )
            },
        )
    }
}
