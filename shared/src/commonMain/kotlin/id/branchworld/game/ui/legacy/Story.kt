package id.branchworld.game.ui.legacy

import branchworld.shared.generated.resources.Res
import branchworld.shared.generated.resources.fading_grove
import branchworld.shared.generated.resources.story_crossing
import branchworld.shared.generated.resources.story_crossing_premise
import branchworld.shared.generated.resources.story_ember
import branchworld.shared.generated.resources.story_ember_premise
import branchworld.shared.generated.resources.story_grove_premise
import id.branchworld.game.domain.IslandGallery
import id.branchworld.game.domain.IslandSessionKey
import id.branchworld.game.data.PlayerProfile
import id.branchworld.simulation.core.FireRulesVersion
import id.branchworld.simulation.core.RangeRulesVersion
import id.branchworld.simulation.ecology.chapters.Chapter
import org.jetbrains.compose.resources.StringResource

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
