package id.branchworld.game.timeline

import id.branchworld.game.ecology.EmberSeed
import id.branchworld.game.ecology.FireRulesVersion
import id.branchworld.game.ecology.Intervention
import id.branchworld.game.ecology.Chapter
import id.branchworld.game.ecology.RegionCount
import id.branchworld.game.ecology.canonicalHash
import id.branchworld.game.simulation.ReforestRegion
import id.branchworld.game.simulation.SetDestination
import id.branchworld.game.terrain.IslandGenerator
import id.branchworld.game.terrain.TerrainKey
import id.branchworld.game.terrain.WatershedWorldgenVersion
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WorldCodeTest {
    private val terrain = IslandGenerator.generate(TerrainKey(EmberSeed, WatershedWorldgenVersion))

    @Test
    fun rebuildsForksWithTheirOwnHashesAndPendingEdits() = runBlocking {
        val timeline = Timeline.start(terrain, FireRulesVersion, Chapter.EmberSeason)
        repeat(500) { timeline.advance() }
        val branchPoint = timeline.state.tick
        val rainRegion = (0 until RegionCount).first { timeline.preview(Intervention.Rain, it)?.blocked == null }
        repeat(20) { timeline.advance() }
        assertTrue(timeline.fork("A player name"))
        assertTrue(timeline.intervene(Intervention.Rain, rainRegion))
        repeat(20) { timeline.advance() }
        val second = timeline.branchId
        timeline.show(checkNotNull(timeline.seek(1, branchPoint + 12)).replay())
        assertTrue(timeline.fork("Another player name"))
        assertTrue(timeline.intervene(Intervention.Rain, rainRegion))
        timeline.show(checkNotNull(timeline.seek(second, branchPoint + 25)).replay())

        val original = timeline.checkpoint()
        val code = WorldCode.encode(original)
        assertEquals("BW1-0410P-0Z4WG-SG600-0H020-00C80-GA020-800C0-03004-000G2-00300-18T15-R8FH3-GFC1J-AM30", code)
        val decoded = WorldCode.decode(code.lowercase())
        val rebuilt = decoded.world.timeline.checkpoint()

        assertTrue(Regex("BW1-(?:[0-9A-HJKMNP-TV-Z]{5}-)*[0-9A-HJKMNP-TV-Z]{1,5}").matches(code))
        assertEquals(original.activeBranchId, rebuilt.activeBranchId)
        assertEquals(original.viewedTick, rebuilt.viewedTick)
        assertEquals(original.branches.map { it.head.canonicalHash() }, rebuilt.branches.map { it.head.canonicalHash() })
        assertEquals(original.branches.map { it.commands to it.pending }, rebuilt.branches.map { it.commands to it.pending })
        assertEquals(listOf("Main", "History 2", "History 3"), rebuilt.branches.map { it.name })
        assertEquals(timeline.state.canonicalHash(), decoded.world.timeline.state.canonicalHash())
        assertEquals(code, WorldCode.encode(rebuilt))
    }

    @Test
    fun rejectsEverySingleSymbolCorruption() = runBlocking {
        val code = WorldCode.encode(Timeline.start(terrain, FireRulesVersion, Chapter.EmberSeason).checkpoint())
        for (index in 4 until code.length) {
            if (code[index] == '-') continue
            val replacement = if (code[index] == 'A') 'B' else 'A'
            assertFailsWith<WorldCodeError>("at $index") {
                WorldCode.decode(code.replaceRange(index, index + 1, replacement.toString()))
            }
        }
    }

    @Test
    fun oldRulesAndExtremeSeedsStillRebuild() = runBlocking {
        for (rules in 5..10) {
            val key = TerrainKey(Long.MIN_VALUE, if (rules == 5) 1 else WatershedWorldgenVersion)
            val original = Timeline.start(IslandGenerator.generate(key), rules)
            repeat(20) { original.advance() }
            val decoded = WorldCode.decode(WorldCode.encode(original.checkpoint())).world
            assertEquals(key, decoded.terrain.key)
            assertEquals(rules, decoded.timeline.state.rulesVersion)
            assertEquals(original.state.canonicalHash(), decoded.timeline.state.canonicalHash())
        }
    }

    @Test
    fun rejectsHistoriesOutsideTheSharingBudget() = runBlocking {
        val checkpoint = Timeline.start(terrain, FireRulesVersion, Chapter.EmberSeason).checkpoint()
        val branch = checkpoint.branches.single()
        val expensive = branch.copy(head = branch.head.copy(tick = 120_001))
        val manyCommands = branch.copy(pending = List(65) { ReforestRegion(1, it.toLong(), 0) })
        val steering = branch.copy(pending = listOf(SetDestination(1, 0, 1, branch.head.creatures.first().cell)))
        val longText = assertFailsWith<WorldCodeError> { WorldCode.decode("W".repeat(WorldCode.MaxLength + 1)) }
        assertEquals(WorldCodeProblem.TooLarge, longText.problem)
        for (tooLarge in listOf(expensive, manyCommands)) {
            val issue = assertFailsWith<WorldCodeError> { WorldCode.encode(checkpoint.copy(branches = listOf(tooLarge))) }
            assertEquals(WorldCodeProblem.TooLarge, issue.problem)
        }
        val unsupported = assertFailsWith<WorldCodeError> { WorldCode.encode(checkpoint.copy(branches = listOf(steering))) }
        assertEquals(WorldCodeProblem.Unsupported, unsupported.problem)
    }
}
