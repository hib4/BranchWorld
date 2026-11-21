package id.branchworld.game.terrain

import id.branchworld.game.simulation.GridCell
import id.branchworld.game.simulation.mix64
import id.branchworld.game.terrain.IslandGenerator.coordinateHash
import id.branchworld.game.terrain.IslandGenerator.noise
import id.branchworld.game.terrain.TerrainMap.Companion.MaxCell
import id.branchworld.game.terrain.TerrainMap.Companion.MaxCorner
import id.branchworld.game.terrain.TerrainMap.Companion.MinCell
import id.branchworld.game.terrain.TerrainMap.Companion.Size
import id.branchworld.game.terrain.TerrainMap.Companion.cellIndex
import id.branchworld.game.terrain.TerrainMap.Companion.containsCell
import id.branchworld.game.terrain.TerrainMap.Companion.cornerIndex
import kotlin.math.abs

/** Integer height fields, drainage, and habitat placement for version 2. */
internal object WatershedGenerator {
    fun generate(key: TerrainKey): TerrainMap {
        val landscape = IslandLandscape.entries[((key.seed % 3 + 3) % 3).toInt()]
        val seed = mix64(key.seed.toULong())
        for (attempt in 0uL..2uL) {
            build(key, landscape, mix64(seed + attempt), reliefVariation = true)?.let { return it }
        }
        // Each profile has a fixed relief envelope. Placement still varies with the requested seed.
        return checkNotNull(build(key, landscape, seed, reliefVariation = false)) {
            "Unable to form a connected watershed for ${key.seed}"
        }
    }

    private fun build(
        key: TerrainKey,
        landscape: IslandLandscape,
        seed: ULong,
        reliefVariation: Boolean,
    ): TerrainMap? {
        val relief = Relief(landscape, if (reliefVariation) seed else 0uL)
        val corners = IntArray((Size + 1) * (Size + 1))
        for (z in MinCell..MaxCorner) for (x in MinCell..MaxCorner) {
            corners[cornerIndex(x, z)] = relief.height(x, z)
        }
        val heights = IntArray(Area) { centerHeight(corners, x(it), z(it)) }
        val downstream = drainage(heights)
        val head = cellIndex(relief.guide(HeadZ), HeadZ)
        if (heights[head] < 12) return null
        val route = buildList {
            var index = head
            while (downstream[index] >= 0 && heights[index] > 0) {
                add(index)
                index = downstream[index]
            }
            add(index)
        }
        if (route.size < 24 || heights[route.last()] > 0) return null

        val basin = when (landscape) {
            IslandLandscape.Valley -> route.size * 42 / 100
            IslandLandscape.Tarn -> route.size * 32 / 100
            IslandLandscape.Marsh -> route.size * 67 / 100
        }
        val surfaces = IntArray(route.size)
        for ((order, index) in route.withIndex()) {
            val bedGrade = minOf(heights[index], (route.lastIndex - order) * 2)
            val spill = if (order == 0) bedGrade else minOf(surfaces[order - 1], bedGrade)
            surfaces[order] = if (order == route.lastIndex) 0 else spill.coerceAtLeast(2)
        }
        val basinRadius = when (landscape) {
            IslandLandscape.Valley -> 3
            IslandLandscape.Tarn -> 6
            IslandLandscape.Marsh -> 7
        }
        val basinFirst = (basin - basinRadius).coerceAtLeast(2)
        val basinLast = (basin + basinRadius).coerceAtMost(route.lastIndex - 3)
        for (order in basinFirst..basinLast) surfaces[order] = surfaces[basinLast]
        for (order in basinFirst - 1 downTo 0) {
            surfaces[order] = minOf(surfaces[order], surfaces[order + 1] + 3)
        }

        val water = arrayOfNulls<WaterCell>(Area)
        val retention = IntArray(Area)
        val bed = corners.copyOf()
        val carved = BooleanArray(bed.size)
        val territory = channelTerritory(route)
        for (index in territory.order) {
            val cx = x(index)
            val cz = z(index)
            val owner = territory.owner[index]
            val distance = territory.distance[index]
            retention[index] = (12 - 2 * distance).coerceAtLeast(0)
            if (heights[index] <= 0 || owner == route.lastIndex) continue
            val kind = when {
                landscape == IslandLandscape.Marsh && owner >= basinFirst -> WaterKind.Marsh
                owner in basinFirst..basinLast -> WaterKind.Lake
                else -> WaterKind.Stream
            }
            val width = channelWidth(landscape, kind, owner, basin, basinRadius, route.lastIndex)
            val jaggedness = noise(seed xor BankSalt, cx, cz, 4) / 57
            if (distance > (width + jaggedness).coerceAtLeast(1)) continue
            val next = if (distance == 0) route[owner + 1] else territory.parent[index]
            val surface = if (distance == 0) surfaces[owner] else water[next]?.surfaceTwice ?: continue
            if (distance > 1 && heights[index] > surface + 12 + width * 2) continue
            val depth = when (kind) {
                WaterKind.Stream -> if (distance == 0) 5 else 3
                WaterKind.Lake -> if (distance < 3) 7 else 4
                WaterKind.Marsh -> if (distance < 2) 4 else 2
            }
            water[index] = WaterCell(kind, surface, x(next), z(next))
            for (dz in 0..1) for (dx in 0..1) {
                val corner = cornerIndex(cx + dx, cz + dz)
                bed[corner] = minOf(bed[corner], (surface - depth) / 2)
                carved[corner] = true
            }
        }
        softenBanks(bed, carved)

        val cells = Array(Area) { index ->
            val x = x(index)
            val z = z(index)
            val a = bed[cornerIndex(x, z)]
            val b = bed[cornerIndex(x + 1, z)]
            val c = bed[cornerIndex(x, z + 1)]
            val d = bed[cornerIndex(x + 1, z + 1)]
            val low = minOf(a, b, c, d)
            val high = maxOf(a, b, c, d)
            val center = a + d
            val temperature = (216 - maxOf(center, 0) * 2 - abs(z) +
                noise(seed xor TemperatureSalt, x, z, 16) / 6).coerceIn(0, 255)
            val moisture = (105 + retention[index] * 5 - maxOf(center, 0) / 5 +
                noise(seed xor ForestSalt, x, z, 8) / 4).coerceIn(0, 255)
            val biome = when {
                water[index] != null -> Biome.Wetland
                high <= 0 -> Biome.Water
                low <= 0 || center <= 5 -> Biome.Beach
                center >= if (landscape == IslandLandscape.Tarn) 47 else 59 -> Biome.RockyUpland
                landscape == IslandLandscape.Marsh && retention[index] >= 6 && center < 15 -> Biome.Wetland
                retention[index] in 5..10 && center < 37 -> Biome.Meadow
                moisture > 118 || center > 39 -> Biome.PineForest
                else -> Biome.Meadow
            }
            TerrainCell(center, biome, moisture, temperature, low > 0, false,
                water[index] == null && low > 0 && high - low <= 8)
        }
        val props = placeProps(seed, cells, retention)
        val provisional = TerrainMap(key, bed, cells, props)
        if (cells.none(TerrainCell::walkable)) return null
        val reachable = TerrainNavigation(provisional).reachableCells()
        if (reachable.size < 350 || reachable.count { provisional.cellAt(it.x, it.z)?.biome == Biome.Meadow } < 75 ||
            reachable.count { provisional.cellAt(it.x, it.z)?.biome == Biome.PineForest } < 55) return null

        val bankCandidates = reachable.filter {
            val cell = checkNotNull(provisional.cellAt(it.x, it.z))
            cell.biome in BankBiomes && neighbors(cellIndex(it.x, it.z)).any { next -> water[next] != null }
        }
        val basinCell = route[basin]
        val first = bankCandidates.filter { bank ->
            reachable.count { abs(bank.x - it.x) + abs(bank.z - it.z) <= 5 &&
                provisional.cellAt(it.x, it.z)?.biome in BankBiomes } >= 12
        }.minByOrNull { steps(cellIndex(it.x, it.z), basinCell) } ?: return null
        val second = bankCandidates.filter { abs(first.x - it.x) + abs(first.z - it.z) >= 9 }
            .minByOrNull { steps(cellIndex(it.x, it.z), head) } ?: return null
        val estuary = route[(route.lastIndex - 3).coerceAtLeast(basinLast)]
        val landmarks = listOf(
            TerrainLandmark(LandmarkKind.Headwaters, x(head), z(head)),
            TerrainLandmark(LandmarkKind.Basin, x(basinCell), z(basinCell)),
            TerrainLandmark(LandmarkKind.Estuary, x(estuary), z(estuary)),
        )
        val banks = listOf(first, second).sortedBy { cellIndex(it.x, it.z) }.map { it.x to it.z }
        return TerrainMap(key, bed, cells, props, Watershed(landscape, water, retention, banks, landmarks))
    }

    private fun softenBanks(elevations: IntArray, carved: BooleanArray) {
        val initial = elevations.copyOf()
        for (z in MinCell + 1 until MaxCorner) for (x in MinCell + 1 until MaxCorner) {
            val index = cornerIndex(x, z)
            if (carved[index] || initial[index] <= 0) continue
            for (dz in -1..1) for (dx in -1..1) {
                val shore = cornerIndex(x + dx, z + dz)
                if (carved[shore]) elevations[index] = minOf(elevations[index], initial[shore] + 4 + abs(dx) + abs(dz))
            }
        }
    }

    private class ChannelTerritory {
        val owner = IntArray(Area) { -1 }
        val distance = IntArray(Area)
        val parent = IntArray(Area)
        val order = IntArray(Area)
    }

    /** Fill banks outward from the whole channel. Every wet tile points toward an existing outlet. */
    private fun channelTerritory(route: List<Int>): ChannelTerritory = ChannelTerritory().apply {
        for ((source, index) in route.withIndex()) {
            owner[index] = source
            order[source] = index
        }
        var end = route.size
        for (index in order.indices) {
            val cell = order[index]
            visitNeighbors(cell) { next ->
                if (owner[next] < 0) {
                    owner[next] = owner[cell]
                    distance[next] = distance[cell] + 1
                    parent[next] = cell
                    order[end++] = next
                }
            }
        }
    }

    private fun channelWidth(
        landscape: IslandLandscape,
        kind: WaterKind,
        order: Int,
        basin: Int,
        radius: Int,
        end: Int,
    ): Int = when (kind) {
        WaterKind.Stream -> if (order < 5) 1 else if (order >= end - 5) 3 else 2
        WaterKind.Lake -> {
            val widest = if (landscape == IslandLandscape.Tarn) 7 else 4
            (widest - abs(order - basin) * (widest - 1) / radius).coerceAtLeast(1)
        }
        WaterKind.Marsh -> (6 - abs(order - basin) / 3).coerceIn(2, 6)
    }

    private fun placeProps(seed: ULong, cells: Array<TerrainCell>, retention: IntArray): List<TerrainProp> {
        val ranked = cells.indices.filter {
            val cell = cells[it]
            cell.walkable && (cell.biome == Biome.PineForest && retention[it] <= 5 || cell.biome == Biome.RockyUpland)
        }.sortedWith(compareBy<Int> {
            coordinateHash(seed xor PropsSalt, x(it), z(it))
        }.thenBy { it })
        val placed = ArrayList<TerrainProp>()
        var pines = 0
        var rocks = 0
        for (index in ranked) {
            val cell = cells[index]
            if (!cell.walkable) continue
            val x = x(index)
            val z = z(index)
            val kind = if (cell.biome == Biome.PineForest) PropKind.Pine else PropKind.Rock
            if (kind == PropKind.Pine && pines >= 82 || kind == PropKind.Rock && rocks >= 37) continue
            val spacing = if (kind == PropKind.Pine) 3 else 4
            if (placed.any { abs(x - it.x) + abs(z - it.z) < spacing }) continue
            val draw = coordinateHash(seed xor VariationSalt, x, z)
            val size = if (kind == PropKind.Pine) 62 + (draw % 66uL).toInt() else 74 + (draw % 69uL).toInt()
            placed.add(TerrainProp(kind, x, z, size, ((draw shr 16) % 360uL).toInt()))
            cells[index] = cell.copy(occupied = true, walkable = false)
            if (kind == PropKind.Pine) pines++ else rocks++
        }
        return placed.sortedWith(compareBy(TerrainProp::z, TerrainProp::x))
    }

    /** Priority flood visits low spill levels first. Cell IDs resolve ties across targets. */
    private fun drainage(heights: IntArray): IntArray {
        val downstream = IntArray(Area) { Unvisited }
        val queue = FloodQueue()
        for (index in heights.indices) {
            if (x(index) in MinCell + 1 until MaxCell && z(index) in MinCell + 1 until MaxCell) continue
            downstream[index] = -1
            queue.add(heights[index], index)
        }
        while (queue.isNotEmpty()) {
            val next = queue.removeFirst()
            val index = next % Area
            val level = next / Area + TerrainMap.MinElevation * 2
            visitNeighbors(index) { neighbor ->
                if (downstream[neighbor] == Unvisited) {
                    downstream[neighbor] = index
                    queue.add(maxOf(heights[neighbor], level), neighbor)
                }
            }
        }
        return downstream
    }

    private class FloodQueue {
        private val heap = IntArray(Area)
        private var size = 0

        fun isNotEmpty() = size != 0

        fun add(height: Int, index: Int) {
            val value = (height - TerrainMap.MinElevation * 2) * Area + index
            var child = size++
            while (child > 0) {
                val parent = (child - 1) / 2
                if (heap[parent] <= value) break
                heap[child] = heap[parent]
                child = parent
            }
            heap[child] = value
        }

        fun removeFirst(): Int {
            val first = heap[0]
            val value = heap[--size]
            var parent = 0
            while (parent * 2 + 1 < size) {
                val left = parent * 2 + 1
                val right = left + 1
                val smallest = if (right < size && heap[right] < heap[left]) right else left
                if (heap[smallest] >= value) break
                heap[parent] = heap[smallest]
                parent = smallest
            }
            heap[parent] = value
            return first
        }
    }

    private class Relief(private val landscape: IslandLandscape, private val seed: ULong) {
        private val offset = noise(seed xor ReliefSalt, -9, 7, 16) / 24
        private val radius = noise(seed xor ReliefSalt, 13, 22, 16) / 28

        fun guide(z: Int): Int {
            val phase = (z + 24).coerceIn(0, 56)
            val s = when {
                phase < 17 -> -7 + phase * 8 / 17
                phase < 31 -> 1 + (phase - 17) * 6 / 14
                phase < 44 -> 7 - (phase - 31) * 9 / 13
                else -> -2 - (phase - 44) / 3
            }
            return offset + when (landscape) {
                IslandLandscape.Marsh -> -s - 4
                IslandLandscape.Tarn -> -s / 2 + 4
                IslandLandscape.Valley -> s
            }
        }

        fun height(x: Int, z: Int): Int {
            if (x == MinCell || x == MaxCorner || z == MinCell || z == MaxCorner) return TerrainMap.MinElevation
            val crag = noise(seed xor ReliefSalt, x, z, 8)
            val coarse = noise(seed xor CoastSalt, x, z, 16)
            val form = when (landscape) {
                IslandLandscape.Valley -> maxOf(
                    mound(x, z, -8, -4, 23 + radius / 2, 28, 34),
                    mound(x, z, 11, -4, 20, 25, 34),
                ) + ridge(x, z, -12, -10, 13, 16, 10)
                IslandLandscape.Tarn -> maxOf(
                    mound(x, z, -9, -6, 23, 25 + radius / 2, 39),
                    mound(x, z, 13, 3, 18, 24, 27),
                ) + ridge(x, z, -13, -13, 11, 10, 12) + ridge(x, z, 14, -10, 10, 12, 16)
                IslandLandscape.Marsh -> maxOf(
                    mound(x, z, -10, -2, 19 + radius / 2, 28, 26),
                    mound(x, z, 7, -12, 22, 17, 33),
                    mound(x, z, 19, 11, 11, 17, 22),
                    mound(x, z, 13, 20, 13, 12, 13),
                ) - ridge(x, z, 10, 15, 12, 15, 19)
            }
            val natural = form + coarse / 18 + crag / 29
            val distance = abs(x - guide(z))
            val grade = maxOf(0, (22 - z) * 2 / 3)
            val valley = grade + maxOf(0, distance - 1) * 2
            // A continuous apron around the headwaters lets the channel fall down the
            // island's long axis. Taper the apron back into the profile's coast and ridges.
            val apron = valley - maxOf(0, distance - 7) * 7 - maxOf(0, -20 - z) * 9 - maxOf(0, z - 16) * 4
            return minOf(maxOf(natural, apron), valley).coerceIn(TerrainMap.MinElevation, TerrainMap.MaxElevation)
        }

        private fun ridge(x: Int, z: Int, cx: Int, cz: Int, rx: Int, rz: Int, height: Int) =
            mound(x, z, cx, cz, rx, rz, height).coerceAtLeast(0)

        private fun mound(x: Int, z: Int, cx: Int, cz: Int, rx: Int, rz: Int, height: Int): Int {
            val dx = x - cx
            val dz = z - cz
            val radial = 1024 * dx * dx / (rx * rx) + 1024 * dz * dz / (rz * rz)
            return (1024 - radial) * height / 1024
        }
    }

    private fun centerHeight(corners: IntArray, x: Int, z: Int): Int =
        corners[cornerIndex(x, z)] + corners[cornerIndex(x + 1, z + 1)]

    private fun neighbors(index: Int) = buildList(4) { visitNeighbors(index) { add(it) } }

    private inline fun visitNeighbors(index: Int, visit: (Int) -> Unit) {
        val x = x(index)
        val z = z(index)
        if (containsCell(x - 1, z)) visit(index - 1)
        if (containsCell(x + 1, z)) visit(index + 1)
        if (containsCell(x, z - 1)) visit(index - Size)
        if (containsCell(x, z + 1)) visit(index + Size)
    }

    private fun steps(from: Int, to: Int): Int = abs(x(from) - x(to)) + abs(z(from) - z(to))
    private fun x(index: Int) = MinCell + index % Size
    private fun z(index: Int) = MinCell + index / Size

    private val BankBiomes = setOf(Biome.Meadow, Biome.PineForest)
    private const val HeadZ = -15
    private const val Area = Size * Size
    private const val Unvisited = -2
    private const val ReliefSalt = 0x916e69f4c8a5b733uL
    private const val CoastSalt = 0xadc0baa1482357f5uL
    private const val BankSalt = 0xd1748e7557f8b3f9uL
    private const val ForestSalt = 0x3794bef2555a07bduL
    private const val TemperatureSalt = 0x4c76323960ebc84duL
    private const val PropsSalt = 0x39761e04bf6e2e11uL
    private const val VariationSalt = 0xf5f92db9c7bdc635uL
}
