package id.branchworld.game.ui.world

import androidx.compose.runtime.Composable
import branchworld.shared.generated.resources.Res
import branchworld.shared.generated.resources.atlas_earlier
import branchworld.shared.generated.resources.atlas_sandbox
import branchworld.shared.generated.resources.atlas_story
import branchworld.shared.generated.resources.code_shared_island
import branchworld.shared.generated.resources.day_afternoon
import branchworld.shared.generated.resources.day_dawn
import branchworld.shared.generated.resources.day_dusk
import branchworld.shared.generated.resources.day_label
import branchworld.shared.generated.resources.day_midday
import branchworld.shared.generated.resources.day_morning
import branchworld.shared.generated.resources.day_night
import branchworld.shared.generated.resources.earlier_rules_title
import id.branchworld.game.domain.IslandSessionKey
import id.branchworld.game.data.PlayerProfile
import id.branchworld.game.ui.legacy.Story
import id.branchworld.simulation.ecology.environment.DayLength
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

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

/** A living world's own name, or its island's. */
@Composable
internal fun worldTitle(key: IslandSessionKey, profile: PlayerProfile?): String =
    profile?.atlas?.firstOrNull { it.slot == key.slot }?.name?.takeIf { it.isNotBlank() }
        ?: stringResource(islandTitle(key.terrain))
