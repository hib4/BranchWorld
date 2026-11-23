package id.branchworld.game.domain

import id.branchworld.game.domain.session.PlaceOutcome
import id.branchworld.game.domain.session.WorldPlacement
import id.branchworld.game.platform.sound.WorldSound
import id.branchworld.simulation.ecology.Species
import id.branchworld.simulation.ecology.life.PlacementCheck
import id.branchworld.simulation.projection.PlaceTool
import id.branchworld.simulation.projection.WorldPick

/** The animal a tool sets down, if it sets one down. */
internal val PlaceTool.species: Species? get() = when (this) {
    PlaceTool.Rabbit -> Species.Rabbit
    PlaceTool.Wolf -> Species.Wolf
    PlaceTool.Deer -> Species.Deer
    PlaceTool.Fox -> Species.Fox
    else -> null
}

/** Whether the tool puts something into the world (rather than looking or lifting out). */
internal val PlaceTool.places get() = this != PlaceTool.Inspect && this != PlaceTool.Eraser

/** What a tap with the player's hand came to. */
internal sealed interface HandResult {
    /** Looking: select the creature under the tap, or nothing. */
    data class Inspect(val creatureId: Long?) : HandResult

    /** The world changed; [sound] confirms it. */
    data class Done(val sound: WorldSound) : HandResult

    /** The player is looking at the past: a new history must begin at [pick] first. */
    data class NeedsNewHistory(val pick: WorldPick) : HandResult

    /** Nothing happened, for [check]'s reason. */
    data class Refused(val check: PlacementCheck) : HandResult

    /** A replay or comparison is running. */
    data object Busy : HandResult

    /** Lifting out found nothing the player planted there. */
    data object NothingToLift : HandResult

    /** Lifting out was refused for a creature; the card already explains why. */
    data object Ignored : HandResult
}

/** Applies a tap of [tool] at [pick] to the world. */
internal fun WorldPlacement.useHand(tool: PlaceTool, pick: WorldPick): HandResult {
    val ground = pick.ground
    return when {
        tool == PlaceTool.Inspect -> HandResult.Inspect(pick.creatureId)
        tool == PlaceTool.Eraser -> when (val outcome = erase(pick)) {
            PlaceOutcome.Placed -> HandResult.Done(WorldSound.UiSelect)
            PlaceOutcome.ViewingPast -> HandResult.NeedsNewHistory(pick)
            is PlaceOutcome.Rejected -> if (pick.creatureId == null) HandResult.NothingToLift else HandResult.Ignored
            PlaceOutcome.Busy -> HandResult.Ignored
        }
        ground == null -> HandResult.Refused(if (pick.water) PlacementCheck.Water else PlacementCheck.Unreachable)
        else -> when (val outcome = place(tool, ground)) {
            PlaceOutcome.Placed -> HandResult.Done(if (tool.species != null) WorldSound.UiConfirm else WorldSound.Grove)
            PlaceOutcome.ViewingPast -> HandResult.NeedsNewHistory(pick)
            PlaceOutcome.Busy -> HandResult.Busy
            is PlaceOutcome.Rejected -> HandResult.Refused(outcome.check)
        }
    }
}
