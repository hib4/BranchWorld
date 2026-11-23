package id.branchworld.simulation.core

class SplitMix64(initialState: ULong) {
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
