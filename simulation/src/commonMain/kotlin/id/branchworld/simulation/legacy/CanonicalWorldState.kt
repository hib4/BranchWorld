package id.branchworld.simulation.legacy

import id.branchworld.simulation.core.CanonicalWriter
import id.branchworld.simulation.core.canonicalHash

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

fun WorldState.canonicalHash(): String = canonicalBytes().canonicalHash()
