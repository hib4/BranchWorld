package id.branchworld.game

import id.branchworld.game.ecology.Chapter
import id.branchworld.game.ecology.Intervention
import id.branchworld.game.ecology.PlanOption
import id.branchworld.game.ecology.RangeRulesVersion
import id.branchworld.game.save.AtlasEntry
import id.branchworld.game.save.ChapterRecord
import id.branchworld.game.save.PlayerProfile
import id.branchworld.game.save.PlayerProfileCodec
import id.branchworld.game.save.TimelineSaveCodec
import id.branchworld.game.terrain.IslandGenerator
import id.branchworld.game.timeline.ChapterOutcome
import id.branchworld.game.timeline.Timeline
import java.io.File
import kotlin.test.Test
import kotlinx.coroutines.runBlocking

/**
 * Builds real histories for capture runs: The Fading Grove played past its recovery with rain and grove branches,
 * and The Last Crossing played through the herd's year with three answers to the village. Runs only when
 * build/verification/atlas-08/fixture.txt names an output folder.
 */
class AtlasFixtureBuilder {
    @Test
    fun buildFixtures(): Unit = runBlocking {
        val root = File(System.getProperty("user.dir")).parentFile
        val request = File(root, "build/verification/atlas-08/fixture.txt").takeIf { it.exists() } ?: return@runBlocking
        val folder = File(request.readText().trim()).also { File(it, "worlds").mkdirs() }
        fun Timeline.advanceTo(tick: Long) { while (state.tick < tick) check(advance()) }

        val grove = Timeline.start(IslandGenerator.generate(Story.FadingGrove.key.terrain), Story.FadingGrove.key.rules)
        grove.advanceTo(240)
        check(grove.fork("Rain at Hollowmere"))
        check(grove.intervene(Intervention.Rain, grove.recommendedRainRegion()))
        grove.advanceTo(1000)
        val rainId = grove.branchId
        grove.show(checkNotNull(grove.seek(1, 240)).replay())
        check(grove.fork("A grove for wildlife"))
        check(grove.intervene(Intervention.Reforest, 9))
        grove.advanceTo(1000)
        grove.show(checkNotNull(grove.seek(rainId, 1000)).replay())
        val groveCheckpoint = grove.checkpoint()
        File(folder, "worlds/story-fading-grove.json").writeBytes(TimelineSaveCodec.encode(groveCheckpoint))
        val ending = grove.outcomes().first { it.branch.id == rainId }

        val crossing = Timeline.start(IslandGenerator.generate(Story.LastCrossing.key.terrain), RangeRulesVersion, Chapter.LastCrossing)
        while (crossing.state.shaping?.warningAt == null) check(crossing.advance())
        val warning = crossing.state.tick
        check(crossing.fork("A refuge for the herd"))
        check(crossing.intervene(Intervention.Protect, checkNotNull(crossing.pastureRegion())))
        crossing.advanceTo(7200)
        val refugeId = crossing.branchId
        crossing.show(checkNotNull(crossing.seek(1, warning)).replay())
        check(crossing.fork("Build within"))
        check(crossing.endorse(PlanOption.BuildWithin))
        crossing.advanceTo(7200)
        crossing.show(checkNotNull(crossing.seek(1, warning)).replay())
        crossing.advanceTo(7200)
        crossing.show(checkNotNull(crossing.seek(refugeId, 7200)).replay())
        val crossingCheckpoint = crossing.checkpoint()
        File(folder, "worlds/story-last-crossing.json").writeBytes(TimelineSaveCodec.encode(crossingCheckpoint))

        fun entry(key: IslandSessionKey, timeline: Timeline, order: Long): AtlasEntry {
            val active = timeline.checkpoint().branches.first { it.id == timeline.branchId }
            return AtlasEntry(key.slot, key.terrain.seed, key.terrain.worldgenVersion, key.rules, key.story?.code ?: 0,
                active.head.tick, timeline.checkpoint().branches.size, active.name, order)
        }
        val profile = PlayerProfile(
            chapters = listOf(ending.record(Story.FadingGrove.key.slot)),
            atlas = listOf(entry(Story.FadingGrove.key, grove, 1), entry(Story.LastCrossing.key, crossing, 2)),
            legacyImported = true,
        )
        File(folder, "worlds/profile.json").writeBytes(PlayerProfileCodec.encode(profile))
        println("ATLAS_FIXTURE grove=${grove.outcomes().map { it.branch.name to it.people }} crossing=${crossing.outcomes().map { Triple(it.branch.name, it.deer, it.people) }}")
    }

    private fun ChapterOutcome.record(slot: String) =
        ChapterRecord(Chapter.FadingGrove.code, slot, branch.id, branch.name, tick, people, departed.values.sum(), deer, homes)
}
