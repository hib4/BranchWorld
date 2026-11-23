package id.branchworld.simulation.projection

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** What lies under the player's finger or pointer in the 3D world. Presentation only until the interface turns it into a command. */
data class WorldPick(
    /** The ground cell hit, or null when the ray left the island. */
    val ground: GroundPoint?,
    /** The hit was on open water. */
    val water: Boolean = false,
    /** The creature drawn nearest the pointer, if one was close enough to mean it. */
    val creatureId: Long? = null,
)

/** The player's hand in a living world. */
enum class PlaceTool { Inspect, Rabbit, Wolf, Deer, Fox, Tree, Meadow, Eraser }

/** The ghost the renderer draws where a tool would act, and whether it can. */
data class PlacementPreview(val ground: GroundPoint, val tool: PlaceTool, val valid: Boolean)

/**
 * Taps and hovers on the 3D world, from the renderer's input adapters to the interface. The renderer only reports
 * where the pointer is; the interface decides what that means.
 */
class WorldPointer {
    private val hovering = MutableStateFlow<WorldPick?>(null)
    private val tapped = MutableSharedFlow<WorldPick>(extraBufferCapacity = 16)
    val hover: StateFlow<WorldPick?> = hovering.asStateFlow()
    val taps: SharedFlow<WorldPick> = tapped.asSharedFlow()

    fun hover(pick: WorldPick?) {
        hovering.value = pick
    }

    fun tap(pick: WorldPick): Boolean = tapped.tryEmit(pick)
}
