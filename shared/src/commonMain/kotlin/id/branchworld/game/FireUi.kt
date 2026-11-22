package id.branchworld.game

import branchworld.shared.generated.resources.Res
import branchworld.shared.generated.resources.fire_note_ash
import branchworld.shared.generated.resources.fire_note_burning
import branchworld.shared.generated.resources.fire_note_dry_woods
import branchworld.shared.generated.resources.fire_note_meadow
import branchworld.shared.generated.resources.fire_note_scrub
import branchworld.shared.generated.resources.fire_note_storm
import branchworld.shared.generated.resources.fire_show_burned
import branchworld.shared.generated.resources.fire_show_fire
import branchworld.shared.generated.resources.fire_show_storm
import branchworld.shared.generated.resources.echo_dry_storm
import branchworld.shared.generated.resources.echo_pasture_request
import branchworld.shared.generated.resources.echo_warning
import id.branchworld.game.ecology.Chapter
import id.branchworld.game.ecology.Succession
import id.branchworld.game.simulation.FirePose
import id.branchworld.game.simulation.GroundPoint
import org.jetbrains.compose.resources.StringResource

/** The island's fire, as one line for the top card. Null when nothing about fire is worth saying. */
internal fun fireConcern(fire: FirePose?, ember: Boolean, warned: Boolean): StringResource? = when {
    fire == null -> null
    fire.burning.isNotEmpty() -> Res.string.fire_note_burning
    fire.storm.isNotEmpty() -> Res.string.fire_note_storm
    fire.scars.any { !it.felled && it.stage == Succession.Ash } -> Res.string.fire_note_ash
    fire.scars.any { !it.felled && it.stage == Succession.Meadow } -> Res.string.fire_note_meadow
    fire.scars.any { !it.felled && it.stage == Succession.Scrub } -> Res.string.fire_note_scrub
    ember && !warned -> Res.string.fire_note_dry_woods
    else -> null
}

/** What "show me" frames: the fire front, the gathering storm, or the burned ground. */
internal fun fireFocus(fire: FirePose?): Pair<StringResource, List<GroundPoint>>? = when {
    fire == null -> null
    fire.burning.isNotEmpty() -> Res.string.fire_show_fire to fire.burning.map { it.ground }
    fire.storm.isNotEmpty() -> Res.string.fire_show_storm to fire.storm
    fire.scars.any { !it.felled } -> Res.string.fire_show_burned to fire.scars.filter { !it.felled }.map { it.ground }
    else -> null
}

/** The name a chapter's turning point goes by on scrubbers, in the Chronicle and in the Living Echo. */
internal fun warningMoment(chapter: Chapter?): StringResource = when (chapter) {
    Chapter.LastCrossing -> Res.string.echo_pasture_request
    Chapter.EmberSeason -> Res.string.echo_dry_storm
    else -> Res.string.echo_warning
}
