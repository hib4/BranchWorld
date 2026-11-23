package id.branchworld.game

import androidx.compose.runtime.Composable
import branchworld.shared.generated.resources.Res
import branchworld.shared.generated.resources.atlas_earlier
import branchworld.shared.generated.resources.atlas_sandbox
import branchworld.shared.generated.resources.atlas_story
import branchworld.shared.generated.resources.day_afternoon
import branchworld.shared.generated.resources.day_dawn
import branchworld.shared.generated.resources.day_dusk
import branchworld.shared.generated.resources.day_label
import branchworld.shared.generated.resources.day_midday
import branchworld.shared.generated.resources.day_morning
import branchworld.shared.generated.resources.day_night
import branchworld.shared.generated.resources.code_shared_island
import branchworld.shared.generated.resources.earlier_rules_title
import branchworld.shared.generated.resources.fading_grove
import branchworld.shared.generated.resources.story_crossing
import branchworld.shared.generated.resources.story_crossing_premise
import branchworld.shared.generated.resources.story_ember
import branchworld.shared.generated.resources.story_ember_premise
import branchworld.shared.generated.resources.story_grove_premise
import id.branchworld.game.ecology.Chapter
import id.branchworld.game.ecology.DayLength
import id.branchworld.game.ecology.FireRulesVersion
import id.branchworld.game.ecology.RangeRulesVersion
import id.branchworld.game.save.AtlasEntry
import id.branchworld.game.save.PlayerProfile
import id.branchworld.game.terrain.TerrainKey
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The crafted chapters, in the order the journey opens them. */
internal enum class Story(val chapter: Chapter, val key: IslandSessionKey, val numeral: String) {
    FadingGrove(Chapter.FadingGrove, IslandSessionKey(IslandGallery[0]), "I"),
    LastCrossing(Chapter.LastCrossing, IslandSessionKey(IslandGallery[1], RangeRulesVersion, Chapter.LastCrossing), "II"),
    EmberSeason(Chapter.EmberSeason, IslandSessionKey(IslandGallery[2], FireRulesVersion, Chapter.EmberSeason), "III");

    val previous: Story? get() = entries.getOrNull(ordinal - 1)
    val next: Story? get() = entries.getOrNull(ordinal + 1)

    fun unlocked(profile: PlayerProfile) = previous?.let { profile.closed(it.chapter.code) } ?: true

    val title: StringResource get() = when (this) {
        FadingGrove -> Res.string.fading_grove
        LastCrossing -> Res.string.story_crossing
        EmberSeason -> Res.string.story_ember
    }

    val premise: StringResource get() = when (this) {
        FadingGrove -> Res.string.story_grove_premise
        LastCrossing -> Res.string.story_crossing_premise
        EmberSeason -> Res.string.story_ember_premise
    }

    companion object {
        fun of(chapter: Chapter?): Story? = entries.firstOrNull { it.chapter == chapter }
        fun of(key: IslandSessionKey): Story? = entries.firstOrNull { it.key == key.copy(sharedSlot = null) }
    }
}

internal fun AtlasEntry.key(): IslandSessionKey = IslandSessionKey(
    TerrainKey(seed, worldgen), rules, Chapter.entries.firstOrNull { it.code == chapter }?.takeIf { rules >= RangeRulesVersion },
    slot.takeIf(SharedWorldSlot::matches),
    slot.takeIf(LivingWorldSlot::matches),
    id.branchworld.game.ecology.WorldStart.entries.firstOrNull { it.code == start },
)

/** The name an island goes by in menus: its story, or the island itself in the sandbox. */
@Composable
internal fun sessionTitle(key: IslandSessionKey): String {
    Story.of(key)?.let { return stringResource(it.title) }
    val island = stringResource(islandTitle(key.terrain))
    return if (key.sandbox || key.story == null) island else stringResource(Res.string.earlier_rules_title, island)
}

internal fun sessionKind(key: IslandSessionKey): StringResource = when {
    key.isShared -> Res.string.code_shared_island
    Story.of(key) != null -> Res.string.atlas_story
    key.sandbox -> Res.string.atlas_sandbox
    else -> Res.string.atlas_earlier
}

internal fun dayOf(tick: Long) = (tick / DayLength + 1).toInt()

/** "Day 2 · late afternoon", from the displayed tick's place in the island's day. */
@Composable
internal fun dayLabel(tick: Long, dayPhase: Float?): String {
    val part = when (val phase = dayPhase ?: ((tick % DayLength).toFloat() / DayLength)) {
        in 0f..0.05f -> Res.string.day_dawn
        in 0.05f..0.18f -> Res.string.day_morning
        in 0.18f..0.30f -> Res.string.day_midday
        in 0.30f..0.45f -> Res.string.day_afternoon
        in 0.45f..0.56f -> Res.string.day_dusk
        else -> if (phase >= 0.95f) Res.string.day_dawn else Res.string.day_night
    }
    return stringResource(Res.string.day_label, dayOf(tick), stringResource(part))
}
