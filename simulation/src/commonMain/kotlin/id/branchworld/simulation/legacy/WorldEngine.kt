package id.branchworld.simulation.legacy

import id.branchworld.simulation.command.SetDestination
import id.branchworld.simulation.command.WorldCommands
import id.branchworld.simulation.core.GridCell
import id.branchworld.simulation.core.RulesVersion
import id.branchworld.simulation.core.SplitMix64
import id.branchworld.simulation.core.TerrainRulesVersion
import id.branchworld.simulation.core.mix64
import id.branchworld.simulation.terrain.TerrainIdentity
import id.branchworld.simulation.terrain.TerrainMap
import id.branchworld.simulation.terrain.TerrainNavigation

internal const val WorldTicksPerSecond = 20
private const val BunnyId = 1L
internal const val MinIslandX = -8
internal const val MaxIslandX = 7
private const val MinZ = 5
private const val SideLength = 16

/** The first island's walkable square (rules v1). */
internal fun GridCell.isOnIsland() = x in MinIslandX..MaxIslandX && z in MinZ until MinZ + SideLength

data class WorldState(
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

/** Single-owner simulation. All changes to authoritative state happen in [advance]. */
internal class WorldEngine(initial: WorldState, private val navigation: TerrainNavigation? = null) {
    private val inputs = WorldCommands<SetDestination>()
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

    fun submit(command: SetDestination): Boolean =
        command.entityId == state.bunnyId && isReachable(command.cell) && inputs.submit(state.tick, command)

    fun submitNext(cell: GridCell): Boolean =
        isReachable(cell) && inputs.submitNext(state.tick) { tick, sequence ->
            SetDestination(tick, sequence, state.bunnyId, cell)
        }

    fun acceptedCommands(): List<SetDestination> =
        inputs.accepted()

    fun advance(): WorldState {
        val previous = state
        check(previous.tick < Long.MAX_VALUE) { "World tick limit reached" }
        val tick = previous.tick + 1
        val commands = inputs.at(tick)
        var destination = previous.destination
        var randomState = previous.randomState

        if (commands.isNotEmpty()) {
            destination = commands.last().cell
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
