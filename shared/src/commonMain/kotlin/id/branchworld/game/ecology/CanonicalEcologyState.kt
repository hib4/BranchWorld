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
        animal.wildlife?.let {
            writeLong(it.bornAt)
            writeCell(it.home)
            writeLong(it.prey ?: 0L)
            writeLong(it.huntUntil)
        }
    }
    environment?.let {
        writeInt(if (it.weather == Weather.Dry) 0 else 1)
        writeLong(it.nextChange)
        writeULong(it.randomState)
        it.recharge?.let(::writeInt)
        writeLong(ledger.introduced)
        patches.forEach { patch -> writeInt(if (patch.planted) 1 else 0) }
    }
    settlements?.let { world ->
        writeLong(world.gatheredFood)
        writeLong(world.gatheredWood)
        writeInt(world.sites.size)
        for (site in world.sites) {
            writeInt(site.id)
            writeLong(site.foundedAt)
            writeCell(site.center)
            writeCell(site.field)
            writeInt(site.lots.size)
            site.lots.forEach(::writeCell)
            writeInt(site.residents)
            writeInt(site.homes)
            writeInt(site.food)
            writeInt(site.wood)
            writeInt(site.stone)
            writeInt(site.stoneRemaining)
            writeInt(site.shortage)
            writeInt(site.surplus)
            writeInt(site.labor.farm)
            writeInt(site.labor.wood)
            writeInt(site.labor.stone)
            writeInt(site.foodBalance)
        }
        writeInt(world.events.size)
        for (event in world.events) {
            writeLong(event.tick)
            writeInt(event.siteId)
            writeInt(event.kind.code)
            writeInt(event.amount)
        }
    }
    wildlife?.let { history ->
        writeLong(history.catches)
        writeInt(history.events.size)
        for (event in history.events) {
            writeLong(event.tick)
            writeLong(event.id)
            writeInt(event.species.canonicalId)
            writeInt(event.kind.canonicalId)
            writeCell(event.cell)
        }
    }
    bytes()
}

internal fun EcologyState.canonicalHash(): String = canonicalBytes().canonicalHash()

private fun CanonicalWriter.writeOptionalCell(cell: GridCell?) {
    writeInt(if (cell == null) 0 else 1)
    if (cell != null) writeCell(cell)
}
