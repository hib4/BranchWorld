package id.branchworld.simulation.save

import id.branchworld.simulation.core.RangeRulesVersion
import id.branchworld.simulation.core.ShapingRulesVersion
import id.branchworld.simulation.core.VillageRulesVersion
import id.branchworld.simulation.core.WatershedWorldgenVersion
import id.branchworld.simulation.core.canonicalHash
import id.branchworld.simulation.ecology.Intervention
import id.branchworld.simulation.ecology.canonicalHash
import id.branchworld.simulation.ecology.chapters.Chapter
import id.branchworld.simulation.legacy.canonicalHash
import id.branchworld.simulation.terrain.IslandGenerator
import id.branchworld.simulation.terrain.TerrainKey
import id.branchworld.simulation.timeline.Timeline
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the exact saved bytes of earlier rules. New rules add optional sections and fields that must never appear in
 * these files, or older saves would fail their integrity check when re-encoded.
 */
class SaveShapeTest {
    private fun bytesHash(seed: Long, rules: Int, chapter: Chapter? = null, fork: Long, kind: Intervention, region: Int?, until: Long): String {
        val timeline = Timeline.start(IslandGenerator.generate(TerrainKey(seed, WatershedWorldgenVersion)), rules, chapter)
        while (timeline.state.tick < fork) timeline.advance()
        timeline.fork("Shape")
        timeline.intervene(kind, region ?: timeline.recommendedRainRegion())
        while (timeline.state.tick < until) timeline.advance()
        return TimelineSaveCodec.encode(timeline.checkpoint()).canonicalHash()
    }

    @Test
    fun earlierRulesKeepTheirSavedBytes() {
        val shapes = listOf(
            bytesHash(104729, ShapingRulesVersion, fork = 240, kind = Intervention.Rain, region = null, until = 900),
            bytesHash(104729, VillageRulesVersion, fork = 240, kind = Intervention.Rain, region = null, until = 1500),
            bytesHash(271828, RangeRulesVersion, Chapter.LastCrossing, fork = 960, kind = Intervention.Protect, region = 8, until = 2400),
            bytesHash(424242, RangeRulesVersion, fork = 600, kind = Intervention.Rain, region = null, until = 2400),
        )
        println("SAVE_SHAPES $shapes")
        assertEquals(PinnedShapes, shapes)
    }

    private companion object {
        val PinnedShapes = listOf("f8477f42311e1731", "e9ddd173f01eb0da", "5a774ebf0a5c3cf9", "04b8564ce3a03c0a")
    }
}
