package id.branchworld.game.ecology

import id.branchworld.game.simulation.CanonicalWriter
import id.branchworld.game.simulation.GridCell
import id.branchworld.game.simulation.canonicalHash

/** All IDs, arrays, and optional keys have explicit little-endian encodings. */
internal fun EcologyState.canonicalBytes(): ByteArray = CanonicalWriter().run {
    writeInt(rulesVersion)
    writeLong(terrain.key.seed)
    writeInt(terrain.key.worldgenVersion)
    writeULong(terrain.digest)
    writeLong(tick)
    writeULong(randomState)
    writeLong(nextEntityId)
    writeLong(ledger.consumed)
    writeLong(ledger.regrown)
    writeLong(ledger.births)
    writeLong(ledger.deaths)
    writeInt(springs.size)
    springs.forEach(::writeCell)
    writeInt(patches.size)
    for (patch in patches) {
        writeInt(patch.id)
        writeCell(patch.cell)
        writeInt(patch.kind.canonicalId)
        writeInt(patch.capacity)
        writeInt(patch.regrowth)
        writeInt(patch.biomass)
    }
    writeInt(creatures.size)
    for (animal in creatures) {
        writeLong(animal.id)
        writeInt(animal.species.canonicalId)
        writeCell(animal.cell)
        writeInt(animal.food)
        writeInt(animal.water)
        writeInt(animal.energy)
        writeLong(animal.matureAt)
        writeLong(animal.breedAfter)
        writeInt(animal.depletedTicks)
        writeInt(animal.heading.canonicalId)
        writeInt(animal.action.canonicalId)
        val goal = animal.goal
        writeInt(goal?.need?.canonicalId ?: 0)
        if (goal != null) writeCell(goal.cell)
        writeOptionalCell(animal.waypoint)
    }
    environment?.let {
        writeInt(if (it.weather == Weather.Dry) 0 else 1)
        writeLong(it.nextChange)
        writeULong(it.randomState)
        writeLong(ledger.introduced)
        patches.forEach { patch -> writeInt(if (patch.planted) 1 else 0) }
    }
    bytes()
}

internal fun EcologyState.canonicalHash(): String = canonicalBytes().canonicalHash()

private fun CanonicalWriter.writeOptionalCell(cell: GridCell?) {
    writeInt(if (cell == null) 0 else 1)
    if (cell != null) writeCell(cell)
}
