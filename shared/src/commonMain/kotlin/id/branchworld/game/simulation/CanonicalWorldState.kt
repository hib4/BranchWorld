package id.branchworld.game.simulation

/** Rules v1 layout. Integers store their exact little-endian bit patterns. */
internal fun WorldState.canonicalBytes(): ByteArray {
    val bytes = ByteArray(52)
    var offset = 0

    fun write(value: ULong, width: Int) {
        repeat(width) { byte ->
            bytes[offset++] = (value shr (8 * byte)).toByte()
        }
    }

    fun writeInt(value: Int) = write(value.toUInt().toULong(), 4)
    fun writeCell(cell: GridCell) {
        writeInt(cell.x)
        writeInt(cell.z)
    }

    writeInt(rulesVersion)
    write(seed.toULong(), 8)
    write(tick.toULong(), 8)
    write(randomState, 8)
    write(bunnyId.toULong(), 8)
    writeCell(bunnyCell)
    writeCell(destination)
    return bytes
}

internal fun WorldState.canonicalHash(): String {
    var hash = 0xcbf29ce484222325uL
    for (byte in canonicalBytes()) {
        hash = (hash xor byte.toUByte().toULong()) * 0x100000001b3uL
    }
    return hash.toString(16).padStart(16, '0')
}
