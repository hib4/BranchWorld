package id.branchworld.game.domain

import id.branchworld.game.domain.session.PlaceOutcome
import id.branchworld.game.domain.session.WorldPlacement
import id.branchworld.game.platform.sound.WorldSound
import id.branchworld.simulation.ecology.life.PlacementCheck
import id.branchworld.simulation.projection.GroundPoint
import id.branchworld.simulation.projection.PlaceTool
import id.branchworld.simulation.projection.WorldPick
import kotlin.test.Test
import kotlin.test.assertEquals

class WorldInteractionTest {
    private class FakeHand(val placing: PlaceOutcome = PlaceOutcome.Placed, val erasing: PlaceOutcome = PlaceOutcome.Placed) : WorldPlacement {
        val calls = mutableListOf<String>()
        override fun placementCheck(tool: PlaceTool, ground: GroundPoint) = PlacementCheck.Ok
        override fun place(tool: PlaceTool, ground: GroundPoint): PlaceOutcome = placing.also { calls += "place $tool" }
        override fun erase(pick: WorldPick): PlaceOutcome = erasing.also { calls += "erase" }
    }

    private val ground = GroundPoint(1, 2, 3)

    @Test
    fun lookingSelectsWithoutTouchingTheWorld() {
        val hand = FakeHand()
        assertEquals(HandResult.Inspect(7), hand.useHand(PlaceTool.Inspect, WorldPick(ground, creatureId = 7)))
        assertEquals(emptyList(), hand.calls)
    }

    @Test
    fun animalsConfirmAndPlantsRustle() {
        assertEquals(HandResult.Done(WorldSound.UiConfirm), FakeHand().useHand(PlaceTool.Wolf, WorldPick(ground)))
        assertEquals(HandResult.Done(WorldSound.Grove), FakeHand().useHand(PlaceTool.Tree, WorldPick(ground)))
        assertEquals(HandResult.Done(WorldSound.UiSelect), FakeHand().useHand(PlaceTool.Eraser, WorldPick(ground, creatureId = 7)))
    }

    @Test
    fun waterAndTheShoreRefuseBeforeTheWorldIsAsked() {
        val hand = FakeHand()
        assertEquals(HandResult.Refused(PlacementCheck.Water), hand.useHand(PlaceTool.Rabbit, WorldPick(null, water = true)))
        assertEquals(HandResult.Refused(PlacementCheck.Unreachable), hand.useHand(PlaceTool.Rabbit, WorldPick(null)))
        assertEquals(emptyList(), hand.calls)
    }

    @Test
    fun thePastAsksForANewHistoryAndBusyWorldsWait() {
        val pick = WorldPick(ground)
        assertEquals(HandResult.NeedsNewHistory(pick), FakeHand(placing = PlaceOutcome.ViewingPast).useHand(PlaceTool.Deer, pick))
        assertEquals(HandResult.Busy, FakeHand(placing = PlaceOutcome.Busy).useHand(PlaceTool.Deer, pick))
        val refused = PlaceOutcome.Rejected(PlacementCheck.Occupied)
        assertEquals(HandResult.Refused(PlacementCheck.Occupied), FakeHand(placing = refused).useHand(PlaceTool.Deer, pick))
        assertEquals(HandResult.NothingToLift, FakeHand(erasing = refused).useHand(PlaceTool.Eraser, pick))
        assertEquals(HandResult.Ignored, FakeHand(erasing = refused).useHand(PlaceTool.Eraser, WorldPick(ground, creatureId = 3)))
    }
}
