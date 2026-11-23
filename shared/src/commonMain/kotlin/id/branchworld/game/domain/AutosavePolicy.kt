package id.branchworld.game.domain

import id.branchworld.game.domain.session.OpenWorld
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/** A running world is kept at least this often, in ticks. */
internal const val AutosaveTicks = 600L

/** How long the world must stay quiet after a change before it is written, in milliseconds. */
internal const val AutosaveQuietMillis = 900L

/**
 * Moments worth keeping a world: when time stops, when a history is started or switched, when an intervention lands,
 * and every [AutosaveTicks] while it runs. The first value is the current state, not a change.
 */
internal fun OpenWorld.autosaveMoments(): Flow<Long> = merge(
    clock.playback.map { it.playing }.distinctUntilChanged().filter { !it }.map { 0L },
    timelineView.map { it.branches.size * 1_000_003L + it.branchId }.distinctUntilChanged(),
    interventions.map { it.applied.hashCode().toLong() }.distinctUntilChanged(),
    world.poses.map { it.tick / AutosaveTicks }.distinctUntilChanged(),
)
