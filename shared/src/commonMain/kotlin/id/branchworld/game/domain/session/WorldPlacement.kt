package id.branchworld.game.domain.session

import id.branchworld.simulation.core.GridCell
import id.branchworld.simulation.ecology.Species
import id.branchworld.simulation.ecology.life.PlacementCheck
import id.branchworld.simulation.ecology.life.PlantKind
import id.branchworld.simulation.projection.GroundPoint
import id.branchworld.simulation.projection.PlaceTool
import id.branchworld.simulation.projection.WorldPick

/** Rules v12: the player's hand in a living world. */
internal interface WorldPlacement {
    /** What [tool] would do at [ground] right now, without doing it. */
    fun placementCheck(tool: PlaceTool, ground: GroundPoint): PlacementCheck

    /**
     * Sets down an animal or plants at [ground]. While paused the world takes one tick, so the new arrival appears at
     * once. Placing is only possible at the head of a history; the past is changed by starting a new one.
     */
    fun place(tool: PlaceTool, ground: GroundPoint): PlaceOutcome

    /** Lifts a creature out, or clears what the player planted at [ground]. */
    fun erase(pick: WorldPick): PlaceOutcome
}

internal class WorldPlacementActions(private val session: WorldSessionCore) : WorldPlacement {
    private val timeline get() = session.timeline

    override fun placementCheck(tool: PlaceTool, ground: GroundPoint): PlacementCheck {
        val cell = GridCell(ground.x, ground.z)
        return when (tool) {
            PlaceTool.Rabbit -> timeline.creatureCheck(Species.Rabbit, cell)
            PlaceTool.Wolf -> timeline.creatureCheck(Species.Wolf, cell)
            PlaceTool.Deer -> timeline.creatureCheck(Species.Deer, cell)
            PlaceTool.Fox -> timeline.creatureCheck(Species.Fox, cell)
            PlaceTool.Tree -> timeline.plantCheck(PlantKind.Tree, cell)
            PlaceTool.Meadow -> timeline.plantCheck(PlantKind.Meadow, cell)
            PlaceTool.Inspect, PlaceTool.Eraser -> PlacementCheck.Ok
        }
    }

    override fun place(tool: PlaceTool, ground: GroundPoint): PlaceOutcome {
        if (!session.navigationAvailable) return PlaceOutcome.Busy
        if (!timeline.editsAllowed) return PlaceOutcome.ViewingPast
        val check = placementCheck(tool, ground)
        if (check != PlacementCheck.Ok) return PlaceOutcome.Rejected(check)
        val cell = GridCell(ground.x, ground.z)
        val done = when (tool) {
            PlaceTool.Rabbit -> timeline.place(Species.Rabbit, cell)
            PlaceTool.Wolf -> timeline.place(Species.Wolf, cell)
            PlaceTool.Deer -> timeline.place(Species.Deer, cell)
            PlaceTool.Fox -> timeline.place(Species.Fox, cell)
            PlaceTool.Tree -> timeline.plant(PlantKind.Tree, cell)
            PlaceTool.Meadow -> timeline.plant(PlantKind.Meadow, cell)
            PlaceTool.Inspect, PlaceTool.Eraser -> false
        }
        if (!done) return PlaceOutcome.Rejected(PlacementCheck.Occupied)
        afterHand()
        return PlaceOutcome.Placed
    }

    override fun erase(pick: WorldPick): PlaceOutcome {
        if (!session.navigationAvailable) return PlaceOutcome.Busy
        if (!timeline.editsAllowed) return PlaceOutcome.ViewingPast
        val done = pick.creatureId?.let(timeline::remove)
            ?: pick.ground?.let { timeline.clearPlant(GridCell(it.x, it.z)) } ?: false
        if (!done) return PlaceOutcome.Rejected(PlacementCheck.Occupied)
        afterHand()
        return PlaceOutcome.Placed
    }

    private fun afterHand() {
        session.updateTimeline()
        session.clock.step()
    }
}
