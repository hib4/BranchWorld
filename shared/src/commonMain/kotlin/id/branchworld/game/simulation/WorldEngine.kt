package id.branchworld.game.simulation

internal const val RulesVersion = 1
internal const val WorldTicksPerSecond = 20
private const val BunnyId = 1L
internal const val MinIslandX = -8
internal const val MaxIslandX = 7
private const val MinZ = 5
private const val SideLength = 16

internal data class GridCell(val x: Int, val z: Int) {
    fun isOnIsland() = x in MinIslandX..MaxIslandX && z in MinZ until MinZ + SideLength
}

internal data class WorldState(
    val rulesVersion: Int,
    val seed: Long,
    val tick: Long,
    val randomState: ULong,
    val bunnyId: Long,
    val bunnyCell: GridCell,
    val destination: GridCell,
) {
    companion object {
        fun start(seed: Long, rulesVersion: Int = RulesVersion): WorldState {
            require(rulesVersion == RulesVersion) { "Unsupported rules version $rulesVersion" }
            val origin = GridCell(0, 10)
            return WorldState(rulesVersion, seed, 0, seed.toULong(), BunnyId, origin, origin)
        }
    }
}

internal data class SetDestination(
    val tick: Long,
    val sequence: Long,
    val entityId: Long,
    val cell: GridCell,
)

/** Single-owner simulation. All changes to authoritative state happen in [advance]. */
internal class WorldEngine(initial: WorldState) {
    private val pending = mutableMapOf<Long, MutableMap<Long, SetDestination>>()
    private val history = mutableListOf<SetDestination>()

    var state: WorldState = initial
        private set

    init {
        require(initial.rulesVersion == RulesVersion) { "Unsupported rules version ${initial.rulesVersion}" }
        require(initial.tick >= 0 && initial.bunnyId == BunnyId)
        require(initial.bunnyCell.isOnIsland() && initial.destination.isOnIsland())
    }

    fun submit(command: SetDestination): Boolean {
        if (command.tick <= state.tick || command.sequence < 0 ||
            command.entityId != state.bunnyId || !command.cell.isOnIsland()
        ) return false

        val atTick = pending.getOrPut(command.tick, ::mutableMapOf)
        if (command.sequence in atTick) return false
        atTick[command.sequence] = command
        history.add(command)
        return true
    }

    fun submitNext(cell: GridCell): Boolean {
        if (state.tick == Long.MAX_VALUE) return false
        val tick = state.tick + 1
        val lastSequence = pending[tick]?.keys?.maxOrNull() ?: -1L
        if (lastSequence == Long.MAX_VALUE) return false
        return submit(SetDestination(tick, lastSequence + 1, state.bunnyId, cell))
    }

    fun acceptedCommands(): List<SetDestination> =
        history.sortedWith(compareBy(SetDestination::tick, SetDestination::sequence))

    fun advance(): WorldState {
        val previous = state
        check(previous.tick < Long.MAX_VALUE) { "World tick limit reached" }
        val tick = previous.tick + 1
        val commands = pending.remove(tick)
        var destination = previous.destination
        var randomState = previous.randomState

        if (commands != null) {
            for (sequence in commands.keys.sorted()) {
                destination = commands.getValue(sequence).cell
            }
        } else if (previous.bunnyCell == destination) {
            val random = SplitMix64(randomState)
            // Draw from a 16 × 16 grid and skip the bunny's current cell.
            do {
                val index = (random.next() and 0xffuL).toInt()
                destination = GridCell(MinIslandX + index % SideLength, MinZ + index / SideLength)
            } while (destination == previous.bunnyCell)
            randomState = random.state
        }

        val cell = previous.bunnyCell
        val nextCell = when {
            cell.x != destination.x -> cell.copy(x = cell.x + destination.x.compareTo(cell.x))
            cell.z != destination.z -> cell.copy(z = cell.z + destination.z.compareTo(cell.z))
            else -> cell
        }
        return previous.copy(tick = tick, randomState = randomState, bunnyCell = nextCell, destination = destination)
            .also { state = it }
    }
}

internal class SplitMix64(initialState: ULong) {
    var state: ULong = initialState
        private set

    fun next(): ULong {
        // The increment and mixer intentionally wrap modulo 2^64 on every target.
        state += 0x9e3779b97f4a7c15uL
        var bits = state
        bits = (bits xor (bits shr 30)) * 0xbf58476d1ce4e5b9uL
        bits = (bits xor (bits shr 27)) * 0x94d049bb133111ebuL
        return bits xor (bits shr 31)
    }
}
