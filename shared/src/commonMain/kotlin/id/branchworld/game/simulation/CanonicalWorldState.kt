package id.branchworld.game.simulation

/** Rules v2 appends terrain identity to the v1 layout. All integers are little-endian. */
internal fun WorldState.canonicalBytes(): ByteArray {
    val bytes = ByteArray(if (terrain == null) 52 else 64)
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
    terrain?.let {
        writeInt(it.key.worldgenVersion)
        write(it.digest, 8)
    }
    return bytes
}

internal fun WorldState.canonicalHash(): String =
    Fnv64().apply { canonicalBytes().forEach(::addByte) }.value.toString(16).padStart(16, '0')

internal class Fnv64 {
    var value = 0xcbf29ce484222325uL
        private set

    fun addByte(byte: Byte) {
        value = (value xor byte.toUByte().toULong()) * 0x100000001b3uL
    }

    fun addInt(value: Int) = addBits(value.toUInt().toULong(), 4)
    fun addLong(value: ULong) = addBits(value, 8)

    private fun addBits(value: ULong, width: Int) {
        repeat(width) { offset -> addByte((value shr (offset * 8)).toByte()) }
    }
}
