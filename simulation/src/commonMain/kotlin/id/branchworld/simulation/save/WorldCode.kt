package id.branchworld.simulation.save

import id.branchworld.simulation.command.CallRainRegion
import id.branchworld.simulation.command.ClearPlant
import id.branchworld.simulation.command.CutFirebreak
import id.branchworld.simulation.command.EndorsePlan
import id.branchworld.simulation.command.PlaceCreature
import id.branchworld.simulation.command.PlacePlant
import id.branchworld.simulation.command.ProtectRegion
import id.branchworld.simulation.command.ReforestRegion
import id.branchworld.simulation.command.RemoveCreature
import id.branchworld.simulation.command.SetDestination
import id.branchworld.simulation.command.SupplementForage
import id.branchworld.simulation.command.WorldCommand
import id.branchworld.simulation.command.commandOrder
import id.branchworld.simulation.core.CivilizationRulesVersion
import id.branchworld.simulation.core.EnvironmentalRulesVersion
import id.branchworld.simulation.core.Fnv64
import id.branchworld.simulation.core.GridCell
import id.branchworld.simulation.core.LifeRulesVersion
import id.branchworld.simulation.core.WatershedRulesVersion
import id.branchworld.simulation.core.WatershedWorldgenVersion
import id.branchworld.simulation.core.canonicalHash
import id.branchworld.simulation.ecology.EcologyState
import id.branchworld.simulation.ecology.IslandEcology
import id.branchworld.simulation.ecology.Species
import id.branchworld.simulation.ecology.canonicalHash
import id.branchworld.simulation.ecology.chapters.Chapter
import id.branchworld.simulation.ecology.chapters.chapterOf
import id.branchworld.simulation.ecology.life.PlantKind
import id.branchworld.simulation.ecology.life.WorldStart
import id.branchworld.simulation.ecology.settlement.PlanOption
import id.branchworld.simulation.engine.EcologyEngine
import id.branchworld.simulation.legacy.canonicalHash
import id.branchworld.simulation.terrain.IslandGenerator
import id.branchworld.simulation.terrain.TerrainKey
import id.branchworld.simulation.terrain.TerrainMap
import id.branchworld.simulation.timeline.BranchCheckpoint
import id.branchworld.simulation.timeline.MaxBranches
import id.branchworld.simulation.timeline.RetainedSnapshots
import id.branchworld.simulation.timeline.SnapshotInterval
import id.branchworld.simulation.timeline.TimelineCheckpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

enum class WorldCodeProblem { Invalid, Checksum, Newer, Different, TooLarge, Unsupported }
class WorldCodeError(val problem: WorldCodeProblem) : Exception(problem.name)

data class DecodedWorldCode(
    val slot: String,
    val origin: String,
    val world: RestoredTimeline,
    val save: ByteArray,
)

object WorldCode {
    const val MaxLength = 800
    private const val MaxCodeCommands = 64
    private const val MaxReplayTicks = 120_000L
    private const val Version = 1
    private const val Prefix = "BW1-"
    private const val Alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val HeadMask = 0xffffffffffuL

    private data class Recipe(
        val parent: Int,
        val fork: Long,
        val head: Long,
        val commands: List<WorldCommand>,
    )

    fun encode(checkpoint: TimelineCheckpoint): String {
        val root = checkpoint.branches.first().start
        if (root.tick != 0L || checkpoint.branches.any { branch ->
            (branch.commands + branch.pending).any { it is SetDestination }
        }) fail(WorldCodeProblem.Unsupported)
        val recipes = checkpoint.branches.map { branch ->
            Recipe(branch.parentId ?: 0, branch.start.tick, branch.head.tick, branch.commands + branch.pending)
        }
        validateRecipes(recipes, checkpoint.activeBranchId, checkpoint.viewedTick)
        val key = root.terrain.key
        val rules = root.rulesVersion
        val chapter = chapterOf(rules, key.seed, root.habitat)
        val out = Writer()
        out.number(Version)
        out.number(key.worldgenVersion)
        out.number(rules)
        // A living world has no chapter; the same field carries how it began.
        out.number(root.life?.start?.code ?: chapter?.code ?: 0)
        out.varInt(((key.seed shl 1) xor (key.seed shr 63)).toULong())
        out.number(recipes.size)
        for (branch in recipes) {
            out.number(branch.parent)
            out.number(branch.fork)
            out.number(branch.head - branch.fork)
            out.number(branch.commands.size)
            var priorTick = branch.fork
            for (command in branch.commands) {
                out.number(command.tick - priorTick)
                out.number(command.sequence)
                out.command(command)
                priorTick = command.tick
            }
        }
        out.number(checkpoint.activeBranchId)
        out.number(checkpoint.viewedTick)
        out.fixed(heads(checkpoint.branches), 5)
        out.fixed(checksum(out.bytes()), 4)
        val code = format(out.bytes())
        if (code.length > MaxLength) fail(WorldCodeProblem.TooLarge)
        return code
    }

    suspend fun decode(code: String): DecodedWorldCode {
        val raw = parse(code)
        val body = raw.copyOf(raw.size - 4)
        val check = checksum(body)
        if (Reader(raw, body.size).fixed(4) != check) fail(WorldCodeProblem.Checksum)
        val input = Reader(body)
        val version = input.int()
        if (version > Version) fail(WorldCodeProblem.Newer)
        if (version != Version) fail(WorldCodeProblem.Invalid)
        val worldgen = input.int()
        val rules = input.int()
        if (worldgen > WatershedWorldgenVersion || rules > LifeRulesVersion) fail(WorldCodeProblem.Newer)
        if (!(worldgen == 1 && rules in EnvironmentalRulesVersion..CivilizationRulesVersion ||
                worldgen == WatershedWorldgenVersion && rules in WatershedRulesVersion..LifeRulesVersion)
        ) fail(WorldCodeProblem.Invalid)
        val chapterId = input.int()
        val living = rules >= LifeRulesVersion
        val start = if (living) WorldStart.entries.firstOrNull { it.code == chapterId } ?: fail(WorldCodeProblem.Newer) else WorldStart.Empty
        val chapter = if (living) null else Chapter.of(chapterId)
        if (!living && chapterId != 0 && chapter == null) fail(WorldCodeProblem.Newer)
        val zigzag = input.varInt()
        val seed = (zigzag shr 1).toLong() xor -(zigzag and 1uL).toLong()
        val branchCount = input.int()
        if (branchCount !in 1..MaxBranches) fail(WorldCodeProblem.TooLarge)
        var remainingCommands = MaxCodeCommands
        val recipes = List(branchCount) {
            val parent = input.int()
            val fork = input.long()
            val head = add(fork, input.long())
            val count = input.int()
            if (count > remainingCommands) fail(WorldCodeProblem.TooLarge)
            remainingCommands -= count
            var tick = fork
            val commands = List(count) {
                tick = add(tick, input.long())
                input.command(tick, input.long())
            }
            Recipe(parent, fork, head, commands)
        }
        val active = input.int()
        val viewed = input.long()
        val expectedHeads = input.fixed(5)
        if (!input.done) fail(WorldCodeProblem.Invalid)
        validateRecipes(recipes, active, viewed)
        val origin = format(raw)
        val slot = slotFor(check)
        try {
            val terrain = IslandGenerator.generate(TerrainKey(seed, worldgen))
            val checkpoint = rebuild(terrain, rules, chapter, recipes, active, viewed, start)
            if (heads(checkpoint.branches) != expectedHeads) fail(WorldCodeProblem.Different)
            val save = TimelineSaveCodec.encode(checkpoint, origin)
            return DecodedWorldCode(slot, origin, TimelineSaveCodec.restore(save), save)
        } catch (error: CancellationException) {
            throw error
        } catch (error: WorldCodeError) {
            throw error
        } catch (_: Exception) {
            fail(WorldCodeProblem.Different)
        }
    }

    /** Used when reopening an imported slot. The full canonical code also distinguishes slot checksum collisions. */
    fun slotForOrigin(origin: String): String {
        val raw = parse(origin)
        val stored = Reader(raw, raw.size - 4).fixed(4)
        if (checksum(raw.copyOf(raw.size - 4)) != stored) fail(WorldCodeProblem.Checksum)
        return slotFor(stored)
    }

    private fun slotFor(checksum: ULong) = "shared-${checksum.toString(16).padStart(8, '0')}.json"

    private fun validateRecipes(recipes: List<Recipe>, active: Int, viewed: Long) {
        if (recipes.size !in 1..MaxBranches || recipes.sumOf { it.commands.size } > MaxCodeCommands) {
            fail(WorldCodeProblem.TooLarge)
        }
        val selected = recipes.getOrNull(active - 1) ?: fail(WorldCodeProblem.Invalid)
        if (viewed !in selected.fork..selected.head) fail(WorldCodeProblem.Invalid)
        var cost = 0L
        for ((index, branch) in recipes.withIndex()) {
            if (branch.fork < 0 || branch.fork > branch.head) fail(WorldCodeProblem.Invalid)
            if (index == 0) {
                if (branch.parent != 0 || branch.fork != 0L) fail(WorldCodeProblem.Invalid)
            } else {
                if (branch.parent !in 1..index) fail(WorldCodeProblem.Invalid)
                val parent = recipes[branch.parent - 1]
                if (branch.fork !in parent.fork..parent.head) fail(WorldCodeProblem.Invalid)
            }
            val duration = branch.head - branch.fork
            if (duration > MaxReplayTicks - cost) fail(WorldCodeProblem.TooLarge)
            cost += duration
            if (branch.commands.any { it.tick <= branch.fork || it.sequence < 0 } ||
                branch.commands.zipWithNext().any { (first, next) -> commandOrder.compare(first, next) >= 0 }
            ) fail(WorldCodeProblem.Invalid)
        }
        // Opening a past cursor can replay from the branch's fork if its older snapshots were evicted.
        if (viewed != selected.head && viewed - selected.fork > MaxReplayTicks - cost) fail(WorldCodeProblem.TooLarge)
    }

    private suspend fun rebuild(
        terrain: TerrainMap,
        rules: Int,
        chapter: Chapter?,
        recipes: List<Recipe>,
        active: Int,
        viewed: Long,
        start: WorldStart,
    ): TimelineCheckpoint {
        val island = IslandEcology(terrain)
        val root = EcologyEngine(terrain, rulesVersion = rules, recordHistory = false, newChapter = chapter, newStart = start, island = island)
        if (root.story != chapter) fail(WorldCodeProblem.Invalid)
        val requiredForks = recipes.groupBy(Recipe::parent).mapValues { (_, children) ->
            children.mapTo(mutableSetOf(), Recipe::fork)
        }
        val forkStates = mutableMapOf<Pair<Int, Long>, EcologyState>()
        val context = currentCoroutineContext()
        val branches = recipes.mapIndexed { index, recipe ->
            val id = index + 1
            val start = if (id == 1) root.state else forkStates.getValue(recipe.parent to recipe.fork)
            val engine = EcologyEngine(terrain, start, recipe.commands, recordHistory = false, island = island)
            val forks = requiredForks[id].orEmpty()
            val snapshots = ArrayDeque<EcologyState>()
            if (start.tick in forks) forkStates[id to start.tick] = start
            while (engine.state.tick < recipe.head) {
                context.ensureActive()
                val state = engine.advance()
                if (state.tick in forks) forkStates[id to state.tick] = state
                if (state.tick % SnapshotInterval == 0L) {
                    snapshots.addLast(state)
                    if (snapshots.size > RetainedSnapshots) snapshots.removeFirst()
                }
            }
            BranchCheckpoint(id, if (id == 1) "Main" else "History $id", recipe.parent.takeIf { it != 0 },
                start, engine.state, snapshots.toList(), recipe.commands.filter { it.tick <= recipe.head }, engine.pendingCommands())
        }
        return TimelineCheckpoint(active, viewed, branches)
    }

    private fun heads(branches: List<BranchCheckpoint>): ULong = Fnv64().apply {
        for (branch in branches) {
            addInt(branch.id)
            addLong(branch.head.canonicalHash().toULong(16))
        }
    }.value and HeadMask

    private fun checksum(bytes: ByteArray): ULong {
        var hash = 0x811c9dc5u
        for (byte in bytes) hash = (hash xor byte.toUByte().toUInt()) * 0x01000193u
        return hash.toULong()
    }

    private fun format(bytes: ByteArray): String {
        val symbols = buildString {
            var bits = 0
            var count = 0
            for (byte in bytes) {
                bits = (bits shl 8) or byte.toUByte().toInt()
                count += 8
                while (count >= 5) {
                    count -= 5
                    append(Alphabet[(bits ushr count) and 31])
                }
            }
            if (count > 0) append(Alphabet[(bits shl (5 - count)) and 31])
        }
        return Prefix + symbols.chunked(5).joinToString("-")
    }

    private fun parse(source: String): ByteArray {
        if (source.length > MaxLength) fail(WorldCodeProblem.TooLarge)
        val code = source.trim().uppercase()
        if (!code.startsWith(Prefix)) {
            fail(if (code.startsWith("BW") && code.substringAfter("BW").substringBefore('-').toIntOrNull()?.let { it > 1 } == true)
                WorldCodeProblem.Newer else WorldCodeProblem.Invalid)
        }
        val out = Writer()
        var bits = 0
        var count = 0
        for (symbol in code.drop(Prefix.length)) {
            if (symbol == '-' || symbol.isWhitespace()) continue
            val index = when (symbol) {
                'O' -> 0
                'I', 'L' -> 1
                else -> Alphabet.indexOf(symbol)
            }
            if (index < 0) fail(WorldCodeProblem.Invalid)
            bits = (bits shl 5) or index
            count += 5
            if (count >= 8) {
                count -= 8
                out.byte(bits ushr count)
            }
        }
        val bytes = out.bytes()
        if (count > 4 || bits and ((1 shl count) - 1) != 0 || bytes.size <= 4) fail(WorldCodeProblem.Invalid)
        return bytes
    }

    private fun add(base: Long, delta: Long): Long {
        if (base > Long.MAX_VALUE - delta) fail(WorldCodeProblem.Invalid)
        return base + delta
    }

    private class Writer {
        private val data = mutableListOf<Byte>()
        fun bytes() = data.toByteArray()
        fun byte(value: Int) { data.add(value.toByte()) }
        fun number(value: Int) = number(value.toLong())
        fun number(value: Long) = varInt(value.toULong())
        fun varInt(value: ULong) {
            var rest = value
            while (rest >= 128uL) {
                byte(rest.toInt() or 128)
                rest = rest shr 7
            }
            byte(rest.toInt())
        }

        fun fixed(value: ULong, count: Int) {
            repeat(count) { byte((value shr (it * 8)).toInt()) }
        }

        fun command(command: WorldCommand) {
            when (command) {
                is ReforestRegion -> { byte(1); number(command.region) }
                is SupplementForage -> { byte(2); number(command.region) }
                is CallRainRegion -> { byte(3); number(command.region) }
                is ProtectRegion -> { byte(4); number(command.region) }
                is EndorsePlan -> { byte(5); number(command.petitionId); number(command.option.code) }
                is CutFirebreak -> { byte(6); number(command.region) }
                is SetDestination -> fail(WorldCodeProblem.Unsupported)
                is PlaceCreature -> { byte(7); number(command.species.canonicalId); cell(command.cell) }
                is RemoveCreature -> { byte(8); number(command.entityId) }
                is PlacePlant -> { byte(9); number(command.kind.code); cell(command.cell) }
                is ClearPlant -> { byte(10); cell(command.cell) }
            }
        }

        private fun cell(cell: GridCell) {
            number(cell.x - TerrainMap.MinCell)
            number(cell.z - TerrainMap.MinCell)
        }
    }

    private class Reader(private val data: ByteArray, private var cursor: Int = 0) {
        val done get() = cursor == data.size
        private fun byte() = data.getOrNull(cursor++)?.toUByte()?.toInt() ?: fail(WorldCodeProblem.Invalid)

        fun varInt(): ULong {
            var value = 0uL
            for (shift in 0..63 step 7) {
                val group = byte()
                if (shift == 63 && group > 1 || shift > 0 && group == 0) fail(WorldCodeProblem.Invalid)
                value = value or ((group and 127).toULong() shl shift)
                if (group < 128) return value
            }
            fail(WorldCodeProblem.Invalid)
        }

        fun long(): Long = varInt().takeIf { it <= Long.MAX_VALUE.toULong() }?.toLong() ?: fail(WorldCodeProblem.Invalid)
        fun int(): Int = long().takeIf { it <= Int.MAX_VALUE }?.toInt() ?: fail(WorldCodeProblem.Invalid)

        fun fixed(count: Int): ULong {
            var value = 0uL
            repeat(count) { value = value or (byte().toULong() shl (it * 8)) }
            return value
        }

        fun command(tick: Long, sequence: Long): WorldCommand = when (byte()) {
            1 -> ReforestRegion(tick, sequence, int())
            2 -> SupplementForage(tick, sequence, int())
            3 -> CallRainRegion(tick, sequence, int())
            4 -> ProtectRegion(tick, sequence, int())
            5 -> EndorsePlan(tick, sequence, int(), int().let { code ->
                PlanOption.entries.firstOrNull { it.code == code } ?: fail(WorldCodeProblem.Invalid)
            })
            6 -> CutFirebreak(tick, sequence, int())
            7 -> PlaceCreature(tick, sequence, int().let { code ->
                Species.entries.firstOrNull { it.canonicalId == code } ?: fail(WorldCodeProblem.Invalid)
            }, cell())
            8 -> RemoveCreature(tick, sequence, long())
            9 -> PlacePlant(tick, sequence, int().let { code ->
                PlantKind.entries.firstOrNull { it.code == code } ?: fail(WorldCodeProblem.Invalid)
            }, cell())
            10 -> ClearPlant(tick, sequence, cell())
            else -> fail(WorldCodeProblem.Invalid)
        }

        private fun cell() = GridCell(int() + TerrainMap.MinCell, int() + TerrainMap.MinCell)
    }

    private fun fail(problem: WorldCodeProblem): Nothing = throw WorldCodeError(problem)
}
