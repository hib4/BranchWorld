package id.branchworld.game.domain

import id.branchworld.simulation.core.CivilizationRulesVersion
import id.branchworld.simulation.core.FireRulesVersion
import id.branchworld.simulation.core.LifeRulesVersion
import id.branchworld.simulation.core.RangeRulesVersion
import id.branchworld.simulation.core.ShapingRulesVersion
import id.branchworld.simulation.core.VillageRulesVersion
import id.branchworld.simulation.core.WatershedWorldgenVersion
import id.branchworld.simulation.ecology.chapters.Chapter
import id.branchworld.simulation.ecology.chapters.IslandSeeds
import id.branchworld.simulation.ecology.life.WorldStart
import id.branchworld.simulation.terrain.TerrainKey

internal val SharedWorldSlot = Regex("shared-[0-9a-f]{8}\\.json")

/** A living world's own save slot, numbered from the profile. */
internal val LivingWorldSlot = Regex("world-[0-9]{1,9}\\.json")

internal val IslandGallery = IslandSeeds.map { TerrainKey(it, WatershedWorldgenVersion) }

internal data class IslandSessionKey(
    val terrain: TerrainKey,
    val rules: Int = if (terrain.worldgenVersion == WatershedWorldgenVersion) VillageRulesVersion else CivilizationRulesVersion,
    /** The crafted story of a rules-v10 history. The Fading Grove is implied by its seed under rules v8–v9. */
    val chapter: Chapter? = null,
    val sharedSlot: String? = null,
    /** A living world's own slot. */
    val worldSlot: String? = null,
    /** How a new living world begins. */
    val start: WorldStart? = null,
) {
    /** Rules v12: a freeform living world rather than a story or an earlier sandbox. */
    val living: Boolean get() = rules >= LifeRulesVersion

    val story: Chapter? get() = chapter ?: Chapter.FadingGrove.takeIf {
        rules in ShapingRulesVersion..VillageRulesVersion && terrain == IslandGallery.first()
    }

    val sandbox: Boolean get() = rules >= RangeRulesVersion && chapter == null
    val isShared: Boolean get() = sharedSlot != null

    /** The island's own save slot in the World Atlas. */
    val slot: String get() {
        sharedSlot?.let { return it }
        worldSlot?.let { return it }
        val seed = if (terrain.seed < 0) "n${-(terrain.seed + 1)}" else terrain.seed.toString()
        return when {
            living -> "world-$seed.json"
            chapter == Chapter.LastCrossing -> "story-last-crossing.json"
            chapter == Chapter.EmberSeason -> "story-ember-season.json"
            story == Chapter.FadingGrove && rules == VillageRulesVersion -> "story-fading-grove.json"
            // Rules-v11 sandboxes have their own slot, so islands shaped under earlier rules keep theirs.
            sandbox && rules >= FireRulesVersion -> "sandbox-$seed-w${terrain.worldgenVersion}-r$rules.json"
            sandbox -> "sandbox-$seed-w${terrain.worldgenVersion}.json"
            else -> "island-$seed-w${terrain.worldgenVersion}-r$rules.json"
        }
    }

    companion object {
        fun sandbox(seed: Long) = IslandSessionKey(TerrainKey(seed, WatershedWorldgenVersion), FireRulesVersion)

        fun living(seed: Long, slot: String, start: WorldStart) =
            IslandSessionKey(TerrainKey(seed, WatershedWorldgenVersion), LifeRulesVersion, worldSlot = slot, start = start)
    }
}
