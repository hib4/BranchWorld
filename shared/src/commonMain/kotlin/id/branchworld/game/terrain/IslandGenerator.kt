package id.branchworld.game.terrain

import id.branchworld.game.simulation.SplitMix64
import id.branchworld.game.simulation.mix64
import id.branchworld.game.terrain.TerrainMap.Companion.MaxCell
import id.branchworld.game.terrain.TerrainMap.Companion.MaxCorner
import id.branchworld.game.terrain.TerrainMap.Companion.MinCell
import id.branchworld.game.terrain.TerrainMap.Companion.Size
import id.branchworld.game.terrain.TerrainMap.Companion.cellIndex
import id.branchworld.game.terrain.TerrainMap.Companion.containsCell
import id.branchworld.game.terrain.TerrainMap.Companion.cornerIndex
import kotlin.math.abs

/** Worldgen v1 uses integer sampling and independent salts for each field. */
object IslandGenerator {
    fun generate(key: TerrainKey): TerrainMap = when (key.worldgenVersion) {
        1 -> legacy(key)
        WatershedWorldgenVersion -> WatershedGenerator.generate(key)
        else -> throw IllegalArgumentException("Unsupported world generation version ${key.worldgenVersion}")
    }

    private fun legacy(key: TerrainKey): TerrainMap {
        val seed = key.seed.toULong()
        val shape = IslandShape(SplitMix64(mix64(seed xor ShapeSalt)))
        val elevations = IntArray((Size + 1) * (Size + 1))
        for (z in MinCell..MaxCorner) {
            for (x in MinCell..MaxCorner) {
                elevations[cornerIndex(x, z)] = elevation(seed, shape, x, z)
            }
        }
        val cells = Array(Size * Size) { index ->
            val x = MinCell + index % Size
            val z = MinCell + index / Size
            val a = elevations[cornerIndex(x, z)]
            val b = elevations[cornerIndex(x + 1, z)]
            val c = elevations[cornerIndex(x, z + 1)]
            val d = elevations[cornerIndex(x + 1, z + 1)]
            val low = minOf(a, b, c, d)
            val high = maxOf(a, b, c, d)
            // The rendered diagonal joins a and d, so their average is the exact cell-center height.
            val centerTwice = a + d
            val inlandElevation = maxOf(0, centerTwice / 2)
            val moisture = (145 + noise(seed xor MoistureSalt, x, z, 16) * 3 / 4 - inlandElevation).coerceIn(0, 255)
            val temperature = (199 - inlandElevation * 3 - abs(z) * 2 +
                noise(seed xor TemperatureSalt, x, z, 16) / 4).coerceIn(0, 255)
            val biome = when {
                high <= 0 -> Biome.Water
                low <= 0 || centerTwice <= 7 -> Biome.Beach
                centerTwice >= 46 || temperature < 105 -> Biome.RockyUpland
                moisture >= 135 -> Biome.PineForest
                else -> Biome.Meadow
            }
            TerrainCell(centerTwice, biome, moisture, temperature, low > 0, false, low > 0 && high - low <= 8)
        }
        val props = placeProps(seed, cells)
        return TerrainMap(key, elevations, cells, props)
    }

    private fun elevation(seed: ULong, shape: IslandShape, x: Int, z: Int): Int {
        if (x == MinCell || x == MaxCorner || z == MinCell || z == MaxCorner) return TerrainMap.MinElevation
        val radial = shape.radiusSquared(x, z)
        val coast = noise(seed xor CoastSalt, x, z, 16) / 14
        val detail = noise(seed xor DetailSalt, x, z, 8) / 29
        return ((1024 - radial) * 32 / 1024 + coast + detail)
            .coerceIn(TerrainMap.MinElevation, TerrainMap.MaxElevation)
    }

    private fun placeProps(seed: ULong, cells: Array<TerrainCell>): List<TerrainProp> {
        val candidates = buildList {
            for (z in MinCell..MaxCell) {
                for (x in MinCell..MaxCell) {
                    val cell = cells[cellIndex(x, z)]
                    if (!cell.walkable || abs(x) + abs(z) < 5) continue
                    val kind = when (cell.biome) {
                        Biome.PineForest -> PropKind.Pine
                        Biome.RockyUpland -> PropKind.Rock
                        else -> continue
                    }
                    val rank = coordinateHash(seed xor PropSalt, x, z)
                    add(PropCandidate(x, z, kind, rank))
                }
            }
        }.sortedWith(compareBy(PropCandidate::rank, PropCandidate::z, PropCandidate::x))
        val props = mutableListOf<TerrainProp>()
        var pines = 0
        var rocks = 0
        for ((x, z, kind, rank) in candidates) {
            if (kind == PropKind.Pine && pines == 54 || kind == PropKind.Rock && rocks == 26) continue
            if (props.any { abs(x - it.x) + abs(z - it.z) < 4 }) continue
            val variation = mix64(rank)
            val scale = 70 + (variation % 51uL).toInt()
            val yaw = ((variation shr 16) % 360uL).toInt()
            props.add(TerrainProp(kind, x, z, scale, yaw))
            if (kind == PropKind.Pine) pines++ else rocks++
            for (dz in -1..1) {
                for (dx in -1..1) {
                    if (abs(dx) + abs(dz) > 1 || !containsCell(x + dx, z + dz)) continue
                    val index = cellIndex(x + dx, z + dz)
                    cells[index] = cells[index].copy(occupied = true, walkable = false)
                }
            }
        }
        return props.sortedWith(compareBy(TerrainProp::z, TerrainProp::x))
    }

    internal fun noise(seed: ULong, x: Int, z: Int, period: Int): Int {
        val latticeX = x shr period.countTrailingZeroBits()
        val latticeZ = z shr period.countTrailingZeroBits()
        val offsetX = x and (period - 1)
        val offsetZ = z and (period - 1)
        fun sample(i: Int, j: Int) = (coordinateHash(seed, i, j) shr 56).toInt() - 128
        val top = sample(latticeX, latticeZ) * (period - offsetX) + sample(latticeX + 1, latticeZ) * offsetX
        val bottom = sample(latticeX, latticeZ + 1) * (period - offsetX) + sample(latticeX + 1, latticeZ + 1) * offsetX
        return (top * (period - offsetZ) + bottom * offsetZ) / (period * period)
    }

    internal fun coordinateHash(seed: ULong, x: Int, z: Int): ULong =
        mix64(seed xor (x.toULong() * 0x9e3779b97f4a7c15uL) xor (z.toULong() * 0xd1b54a32d192ed03uL))

    private data class PropCandidate(val x: Int, val z: Int, val kind: PropKind, val rank: ULong)

    private class IslandShape(random: SplitMix64) {
        private val radiusX = 19 + random.nextBounded(7)
        private val radiusZ = 15 + random.nextBounded(8)
        private val centerX = random.nextBounded(7) - 3
        private val centerZ = random.nextBounded(7) - 3
        private val orientation = random.nextBounded(4)

        fun radiusSquared(x: Int, z: Int): Int {
            val dx = x - centerX
            val dz = z - centerZ
            val (u, v) = when (orientation) {
                0 -> dx to dz
                1 -> dz to dx
                2 -> (dx + dz) to (dx - dz)
                else -> (dx - dz) to (dx + dz)
            }
            val normalizer = if (orientation < 2) 1024 else 512
            return u * u * normalizer / (radiusX * radiusX) + v * v * normalizer / (radiusZ * radiusZ)
        }
    }

    private const val ShapeSalt = 0x243f6a8885a308d3uL
    private const val CoastSalt = 0x13198a2e03707344uL
    private const val DetailSalt = 0xa4093822299f31d0uL
    private const val MoistureSalt = 0x082efa98ec4e6c89uL
    private const val TemperatureSalt = 0x452821e638d01377uL
    private const val PropSalt = 0xbe5466cf34e90c6cuL
}
