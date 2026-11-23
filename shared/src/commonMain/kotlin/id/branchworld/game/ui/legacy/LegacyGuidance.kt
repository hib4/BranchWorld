package id.branchworld.game.ui.legacy

import branchworld.shared.generated.resources.Res
import branchworld.shared.generated.resources.grove_dry_note
import branchworld.shared.generated.resources.guide_close
import branchworld.shared.generated.resources.guide_look
import branchworld.shared.generated.resources.guide_petition
import branchworld.shared.generated.resources.guide_rewind
import branchworld.shared.generated.resources.guide_warning
import branchworld.shared.generated.resources.island_opening
import branchworld.shared.generated.resources.shape_rain_arrived
import branchworld.shared.generated.resources.shape_recovered
import branchworld.shared.generated.resources.shape_shortage
import branchworld.shared.generated.resources.shape_warning
import branchworld.shared.generated.resources.valley_clear_note
import branchworld.shared.generated.resources.valley_low_note
import branchworld.shared.generated.resources.valley_refill_note
import branchworld.shared.generated.resources.valley_storm_note
import branchworld.shared.generated.resources.world_dry_note
import branchworld.shared.generated.resources.world_rain_note
import id.branchworld.game.data.ChapterRecord
import id.branchworld.game.data.PlayerProfile
import id.branchworld.game.domain.session.IslandLoad
import id.branchworld.game.domain.session.TimelineView
import id.branchworld.simulation.ecology.EcologyState
import id.branchworld.simulation.ecology.chapters.Chapter
import id.branchworld.simulation.ecology.environment.Season
import id.branchworld.simulation.ecology.environment.Weather
import id.branchworld.simulation.projection.WorldRenderProjection
import id.branchworld.simulation.timeline.ChapterOutcome
import org.jetbrains.compose.resources.StringResource

/** The one line a chapter's board shows about what is happening now, most urgent first. */
internal fun chapterConcern(chapter: Chapter?, state: EcologyState, pose: WorldRenderProjection?): StringResource {
    val climate = pose?.climate
    val watershed = climate?.watershed
    val isHollowmere = chapter == Chapter.FadingGrove
    val shaping = pose?.shaping
    val village = shaping?.welfare
    val tick = pose?.tick
    val fireNote = fireConcern(pose?.fire, chapter == Chapter.EmberSeason, shaping?.warningAt != null)
    return when {
        fireNote != null -> fireNote
        chapter == Chapter.LastCrossing -> crossingConcern(state) ?: Res.string.island_opening
        isHollowmere && village != null && village.unmet > 0 -> Res.string.shape_shortage
        isHollowmere && tick != null && village?.firstRecovery?.let { tick - it <= 90 } == true -> Res.string.shape_recovered
        shaping?.shower != null -> Res.string.shape_rain_arrived
        isHollowmere && tick != null && shaping?.warningAt?.let { tick - it <= 60 } == true -> Res.string.shape_warning
        watershed?.season == Season.Wet && climate.weather == Weather.Rain -> Res.string.valley_storm_note
        watershed != null && climate.weather == Weather.Rain -> Res.string.valley_refill_note
        watershed != null && watershed.recharge < 52 -> Res.string.valley_low_note
        watershed != null -> Res.string.valley_clear_note
        climate?.weather == Weather.Rain -> Res.string.world_rain_note
        climate?.weather == Weather.Dry && isHollowmere -> Res.string.grove_dry_note
        climate?.weather == Weather.Dry -> Res.string.world_dry_note
        else -> Res.string.island_opening
    }
}

/** The one first-time explanation the current moment calls for, if the player has not seen it yet. */
internal fun guideFor(profile: PlayerProfile, island: IslandLoad.Ready, view: TimelineView?, tick: Long?): Pair<String, StringResource>? {
    val state = island.clock.state
    val warning = state.shaping?.warningAt
    val now = tick ?: state.tick
    val candidates = buildList {
        add("look" to Res.string.guide_look)
        if (warning != null && now - warning in 0..240) add("warning" to Res.string.guide_warning)
        if (island.openPetition() != null) add("petition" to Res.string.guide_petition)
        if (island.canCloseChapter && island.chapter?.let { profile.closed(it.code) } == false) add("close" to Res.string.guide_close)
        val active = view?.branches?.firstOrNull { it.id == view.branchId }
        if (warning != null && view?.branches?.size == 1 && (active?.interventionCount ?: 0) > 0 && now > warning + 300) {
            add("rewind" to Res.string.guide_rewind)
        }
    }
    return candidates.firstOrNull { !profile.seen(it.first) }
}

/** The profile's record of a chapter closed on [slot]. */
internal fun ChapterOutcome.record(story: Story, slot: String) = ChapterRecord(
    story.chapter.code, slot, branch.id, branch.name, tick, people, departed.values.sum(), deer, homes,
    burned = homesBurned.takeIf { story.chapter == Chapter.EmberSeason },
)
