package id.branchworld.game.terrain

import id.branchworld.game.simulation.GridCell
import id.branchworld.game.simulation.SplitMix64
import id.branchworld.game.terrain.TerrainMap.Companion.MinCell
import id.branchworld.game.terrain.TerrainMap.Companion.Size
import id.branchworld.game.terrain.TerrainMap.Companion.cellIndex
import kotlin.math.abs

internal class TerrainNavigation(val terrain: TerrainMap) {
    private val component = largestComponent()
    private val componentOrder = IntArray(Size * Size) { -1 }.apply {
        component.forEachIndexed { order, index -> this[index] = order }
    }
    val spawn = component.minBy { index -> cell(index).let { abs(it.x) + abs(it.z) } }.let(::cell)
    val west = steeringTarget(left = true)
    val east = steeringTarget(left = false)

    fun reachableCells(): List<GridCell> = component.map(::cell)

    fun adjacent(cell: GridCell): List<GridCell> = buildList {
        neighbors(cellIndex(cell.x, cell.z)) { add(this@TerrainNavigation.cell(it)) }
    }

    fun contains(cell: GridCell): Boolean =
        TerrainMap.containsCell(cell.x, cell.z) && componentOrder[cellIndex(cell.x, cell.z)] >= 0

    fun pickDestination(random: SplitMix64, current: GridCell): GridCell {
        if (component.size < 2) return current
        val currentIndex = componentOrder[cellIndex(current.x, current.z)]
        val draw = random.nextBounded(component.size - 1)
        return cell(component[if (draw < currentIndex) draw else draw + 1])
    }

    fun distancesTo(target: GridCell): IntArray {
        require(contains(target))
        val distances = IntArray(Size * Size) { -1 }
        val queue = IntArray(distances.size)
        val targetIndex = cellIndex(target.x, target.z)
        queue[0] = targetIndex
        distances[targetIndex] = 0
        var read = 0
        var write = 1
        while (read < write) {
            val index = queue[read++]
            neighbors(index) { next ->
                if (distances[next] < 0) {
                    distances[next] = distances[index] + 1
                    queue[write++] = next
                }
            }
        }
        return distances
    }

    /** Like [distancesTo], but cells in [blocked] (cell indices) are never entered unless they are the target. */
    fun distancesAvoiding(target: GridCell, blocked: Set<Int>): IntArray {
        require(contains(target))
        val distances = IntArray(Size * Size) { -1 }
        val queue = IntArray(distances.size)
        val targetIndex = cellIndex(target.x, target.z)
        queue[0] = targetIndex
        distances[targetIndex] = 0
        var read = 0
        var write = 1
        while (read < write) {
            val index = queue[read++]
            neighbors(index) { next ->
                if (distances[next] < 0 && next !in blocked) {
                    distances[next] = distances[index] + 1
                    queue[write++] = next
                }
            }
        }
        return distances
    }

    fun step(from: GridCell, distances: IntArray): GridCell {
        val index = cellIndex(from.x, from.z)
        val distance = distances[index]
        if (distance == 0) return from
        check(distance > 0) { "Destination is disconnected from the actor" }
        neighbors(index) { next ->
            if (distances[next] == distance - 1) return cell(next)
        }
        error("No descending step in a connected distance field")
    }

    private fun steeringTarget(left: Boolean): GridCell? {
        val targetX = spawn.x + if (left) -10 else 10
        var best: GridCell? = null
        var bestDistance = Int.MAX_VALUE
        for (index in component) {
            val candidate = cell(index)
            if (left && candidate.x >= spawn.x || !left && candidate.x <= spawn.x) continue
            val distance = abs(candidate.x - targetX) + abs(candidate.z - spawn.z)
            if (distance < bestDistance) {
                best = candidate
                bestDistance = distance
            }
        }
        return best
    }

    private fun largestComponent(): IntArray {
        val visited = BooleanArray(Size * Size)
        val queue = IntArray(visited.size)
        var largest = IntArray(0)
        for (start in visited.indices) {
            if (visited[start] || cell(start).let { terrain.cellAt(it.x, it.z)?.walkable != true }) continue
            visited[start] = true
            queue[0] = start
            var read = 0
            var write = 1
            while (read < write) {
                neighbors(queue[read++]) { next ->
                    if (!visited[next]) {
                        visited[next] = true
                        queue[write++] = next
                    }
                }
            }
            if (write > largest.size) largest = queue.copyOf(write)
        }
        check(largest.isNotEmpty()) { "The generated island has no walkable cells" }
        largest.sort()
        return largest
    }

    // The fixed west/east/north/south order resolves equal-length movement paths.
    private inline fun neighbors(index: Int, visit: (Int) -> Unit) {
        val x = MinCell + index % Size
        val z = MinCell + index / Size
        if (terrain.canStep(x, z, x - 1, z)) visit(index - 1)
        if (terrain.canStep(x, z, x + 1, z)) visit(index + 1)
        if (terrain.canStep(x, z, x, z - 1)) visit(index - Size)
        if (terrain.canStep(x, z, x, z + 1)) visit(index + Size)
    }

    private fun cell(index: Int) = GridCell(MinCell + index % Size, MinCell + index / Size)
}
