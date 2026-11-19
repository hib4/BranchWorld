package id.branchworld.game.simulation

import id.branchworld.game.terrain.TerrainKey
import id.branchworld.game.terrain.TerrainMap
import id.branchworld.game.terrain.TerrainNavigation

internal const val RulesVersion = 1
internal const val TerrainRulesVersion = 2
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
    val terrain: TerrainIdentity? = null,
) {
    companion object {
        fun start(seed: Long, rulesVersion: Int = RulesVersion): WorldState {
            require(rulesVersion == RulesVersion) { "Unsupported rules version $rulesVersion" }
            val origin = GridCell(0, 10)
            return WorldState(rulesVersion, seed, 0, seed.toULong(), BunnyId, origin, origin)
        }
    }
}

internal data class TerrainIdentity(val key: TerrainKey, val digest: ULong)

internal data class SetDestination(
    val tick: Long,
    val sequence: Long,
    val entityId: Long,
    val cell: GridCell,
)

/** Single-owner simulation. All changes to authoritative state happen in [advance]. */
internal class WorldEngine(initial: WorldState, private val navigation: TerrainNavigation? = null) {
    private val pending = mutableMapOf<Long, MutableMap<Long, SetDestination>>()
    private val history = mutableListOf<SetDestination>()
    private var routedDestination: GridCell? = null
    private var distances = IntArray(0)

    var state: WorldState = initial
        private set

    init {
        val terrain = navigation?.terrain
        require(initial.rulesVersion == if (terrain == null) RulesVersion else TerrainRulesVersion)
        require(initial.terrain == terrain?.let { TerrainIdentity(it.key, it.digest) })
        require(terrain == null || initial.seed == terrain.key.seed)
        require(initial.tick >= 0 && initial.bunnyId == BunnyId)
        require(isReachable(initial.bunnyCell) && isReachable(initial.destination))
    }

    fun submit(command: SetDestination): Boolean {
        if (command.tick <= state.tick || command.sequence < 0 ||
            command.entityId != state.bunnyId || !isReachable(command.cell)
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
            destination = navigation?.pickDestination(random, previous.bunnyCell) ?: legacyDestination(random, previous.bunnyCell)
            randomState = random.state
        }

        val cell = previous.bunnyCell
        val nextCell = when {
            navigation != null -> terrainStep(cell, destination)
            cell.x != destination.x -> cell.copy(x = cell.x + destination.x.compareTo(cell.x))
            cell.z != destination.z -> cell.copy(z = cell.z + destination.z.compareTo(cell.z))
            else -> cell
        }
        return previous.copy(tick = tick, randomState = randomState, bunnyCell = nextCell, destination = destination)
            .also { state = it }
    }

    fun steeringTarget(left: Boolean): GridCell? {
        val nav = navigation ?: return GridCell(if (left) MinIslandX else MaxIslandX, 12)
        return if (left) nav.west else nav.east
    }

    private fun isReachable(cell: GridCell) = navigation?.contains(cell) ?: cell.isOnIsland()

    private fun terrainStep(cell: GridCell, target: GridCell): GridCell {
        val nav = checkNotNull(navigation)
        if (routedDestination != target) {
            distances = nav.distancesTo(target)
            routedDestination = target
        }
        return nav.step(cell, distances)
    }

    private fun legacyDestination(random: SplitMix64, current: GridCell): GridCell {
        do {
            val index = (random.next() and 0xffuL).toInt()
            val destination = GridCell(MinIslandX + index % SideLength, MinZ + index / SideLength)
            if (destination != current) return destination
        } while (true)
    }

    companion object {
        fun onTerrain(terrain: TerrainMap, checkpoint: WorldState? = null): WorldEngine {
            val navigation = TerrainNavigation(terrain)
            val initial = checkpoint ?: WorldState(
                rulesVersion = TerrainRulesVersion,
                seed = terrain.key.seed,
                tick = 0,
                randomState = mix64(terrain.key.seed.toULong() xor 0x4525af08a0f2db2fuL),
                bunnyId = BunnyId,
                bunnyCell = navigation.spawn,
                destination = navigation.spawn,
                terrain = TerrainIdentity(terrain.key, terrain.digest),
            )
            return WorldEngine(initial, navigation)
        }
    }
}

internal class SplitMix64(initialState: ULong) {
    var state: ULong = initialState
        private set

    fun next(): ULong {
        // The increment and mixer intentionally wrap modulo 2^64 on every target.
        state += 0x9e3779b97f4a7c15uL
        return mix64(state)
    }

    fun nextBounded(bound: Int): Int {
        require(bound > 0)
        val size = bound.toULong()
        val threshold = (0uL - size) % size
        while (true) {
            val draw = next()
            if (draw >= threshold) return (draw % size).toInt()
        }
    }
}

internal fun mix64(value: ULong): ULong {
    val first = (value xor (value shr 30)) * 0xbf58476d1ce4e5b9uL
    val second = (first xor (first shr 27)) * 0x94d049bb133111ebuL
    return second xor (second shr 31)
}
