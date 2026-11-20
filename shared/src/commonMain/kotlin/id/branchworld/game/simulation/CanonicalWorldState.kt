package id.branchworld.game.simulation

/** Rules v2 appends terrain identity to the v1 layout. All integers are little-endian. */
internal fun WorldState.canonicalBytes(): ByteArray = CanonicalWriter().run {
    writeInt(rulesVersion)
    writeLong(seed)
    writeLong(tick)
    writeULong(randomState)
    writeLong(bunnyId)
    writeCell(bunnyCell)
    writeCell(destination)
    terrain?.let {
        writeInt(it.key.worldgenVersion)
        writeULong(it.digest)
    }
    bytes()
}

internal fun WorldState.canonicalHash(): String = canonicalBytes().canonicalHash()

internal fun ByteArray.canonicalHash(): String =
    Fnv64().apply { this@canonicalHash.forEach(::addByte) }.value.toString(16).padStart(16, '0')

internal class CanonicalWriter {
    private var buffer = ByteArray(64)
    private var offset = 0

    fun writeInt(value: Int) = writeBits(value.toUInt().toULong(), 4)
    fun writeLong(value: Long) = writeULong(value.toULong())
    fun writeULong(value: ULong) = writeBits(value, 8)
    fun writeCell(cell: GridCell) {
        writeInt(cell.x)
        writeInt(cell.z)
    }

    fun bytes(): ByteArray = buffer.copyOf(offset)

    private fun writeBits(value: ULong, width: Int) {
        if (offset + width > buffer.size) buffer = buffer.copyOf(buffer.size * 2)
        repeat(width) { byte -> buffer[offset++] = (value shr (8 * byte)).toByte() }
    }
}

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
