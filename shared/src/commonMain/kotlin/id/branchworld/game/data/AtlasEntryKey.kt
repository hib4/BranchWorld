package id.branchworld.game.data

import id.branchworld.game.domain.IslandSessionKey
import id.branchworld.game.domain.LivingWorldSlot
import id.branchworld.game.domain.SharedWorldSlot
import id.branchworld.simulation.core.RangeRulesVersion
import id.branchworld.simulation.ecology.chapters.Chapter
import id.branchworld.simulation.ecology.life.WorldStart
import id.branchworld.simulation.terrain.TerrainKey

/** The world an atlas line refers to. */
internal fun AtlasEntry.key(): IslandSessionKey = IslandSessionKey(
    TerrainKey(seed, worldgen), rules, Chapter.entries.firstOrNull { it.code == chapter }?.takeIf { rules >= RangeRulesVersion },
    slot.takeIf(SharedWorldSlot::matches),
    slot.takeIf(LivingWorldSlot::matches),
    WorldStart.entries.firstOrNull { it.code == start },
)
