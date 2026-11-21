package id.branchworld.game.ecology

import id.branchworld.game.simulation.SplitMix64
import id.branchworld.game.simulation.mix64
import id.branchworld.game.simulation.GridCell
import id.branchworld.game.terrain.TerrainMap
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
enum class Weather { Dry, Rain }

enum class Season { Dry, Wet }

@Serializable
@OptIn(ExperimentalSerializationApi::class)
internal data class Environment(
    val weather: Weather,
    val nextChange: Long,
    val randomState: ULong,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val recharge: Int? = null,
) {
    fun at(tick: Long): Environment {
        val season = seasonAt(tick)
        val forecast = if (tick == nextChange) {
            val random = SplitMix64(randomState)
            val next = if (weather == Weather.Dry) Weather.Rain else Weather.Dry
            val duration = when {
                recharge == null -> 240 + random.nextBounded(121)
                next == Weather.Rain && season == Season.Wet -> 240 + random.nextBounded(121)
                next == Weather.Rain -> 180 + random.nextBounded(81)
                season == Season.Wet -> 160 + random.nextBounded(81)
                else -> 300 + random.nextBounded(61)
            }
            copy(weather = next, nextChange = minOf(tick, Long.MAX_VALUE - duration) + duration, randomState = random.state)
        } else this
        if (recharge == null || tick % 10L != 0L) return forecast
        val change = when {
            forecast.weather == Weather.Rain -> if (season == Season.Wet) 9 else 5
            season == Season.Dry -> -2
            else -> -1
        }
        return forecast.copy(recharge = (recharge + change).coerceIn(0, 255))
    }

    companion object {
        fun start(seed: Long, watershed: Boolean = false, firstRain: Long = 300) = Environment(
            Weather.Dry, firstRain, mix64(seed.toULong() xor 0x6f8c97a25c3197d3uL), if (watershed) 64 else null,
        )
    }
}

internal const val DayLength = 1200L

internal fun seasonAt(tick: Long) = if (tick / DayLength % 2L == 0L) Season.Dry else Season.Wet

internal fun TerrainMap.soilMoisture(cell: GridCell, recharge: Int): Int {
    val base = terrainCell(cell).moisture
    val retention = checkNotNull(watershed).retentionAt(cell.x, cell.z)
    return (base + retention * 2 + (recharge - 96) * (28 - retention) / 42).coerceIn(0, 255)
}

internal fun growthForSoil(moisture: Int, regrowth: Int): Int = when {
    moisture < 100 -> regrowth - 1
    moisture >= 153 -> regrowth * 2
    else -> regrowth
}

internal fun cropPerWorker(moisture: Int): Int = when {
    moisture < 125 -> 1
    moisture < 161 -> 2
    else -> 3
}
