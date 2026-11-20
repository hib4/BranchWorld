package id.branchworld.game.ecology

import id.branchworld.game.simulation.SplitMix64
import id.branchworld.game.simulation.mix64
import kotlinx.serialization.Serializable

@Serializable
enum class Weather { Dry, Rain }

@Serializable
internal data class Environment(val weather: Weather, val nextChange: Long, val randomState: ULong) {
    fun at(tick: Long): Environment {
        if (tick != nextChange) return this
        val random = SplitMix64(randomState)
        val duration = 240 + random.nextBounded(121)
        return Environment(
            if (weather == Weather.Dry) Weather.Rain else Weather.Dry,
            minOf(tick, Long.MAX_VALUE - duration) + duration,
            random.state,
        )
    }

    companion object {
        fun start(seed: Long) = Environment(Weather.Dry, 300, mix64(seed.toULong() xor 0x6f8c97a25c3197d3uL))
    }
}

internal const val DayLength = 1200L
