package id.branchworld.simulation.ecology.chapters

import id.branchworld.simulation.core.RangeRulesVersion
import id.branchworld.simulation.core.ShapingRulesVersion
import id.branchworld.simulation.core.VillageRulesVersion
import id.branchworld.simulation.ecology.range.HabitatState

/** A crafted world-story whose rules the engine applies. The Fading Grove is implied by rules v8–v9 on its seed. */
enum class Chapter(val code: Int, val seed: Long) {
    FadingGrove(1, FadingGroveSeed),
    LastCrossing(2, CrossingSeed),
    /** Rules v11: a dry storm over the pinewoods of Heron Reach. */
    EmberSeason(3, EmberSeed);

    internal companion object {
        fun of(code: Int): Chapter? = entries.firstOrNull { it.code == code }
    }
}

internal const val CrossingSeed = 271828L

/** The chapter a history belongs to. Sandbox histories on rules v10 have none. */
internal fun chapterOf(rulesVersion: Int, seed: Long, habitat: HabitatState?): Chapter? = when {
    rulesVersion in ShapingRulesVersion..VillageRulesVersion -> Chapter.FadingGrove.takeIf { seed == FadingGroveSeed }
    rulesVersion >= RangeRulesVersion -> habitat?.chapter?.let(Chapter::of)
    else -> null
}

internal const val FadingGroveSeed = 104729L

internal const val EmberSeed = 424242L

val IslandSeeds = listOf(FadingGroveSeed, CrossingSeed, EmberSeed)
