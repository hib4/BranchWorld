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
            site.welfare?.let {
                writeCell(it.bank)
                writeInt(it.water)
                writeInt(it.unmet)
                writeInt(it.cropYield)
                writeLong(it.firstShortage ?: 0L)
                writeLong(it.firstRecovery ?: 0L)
            }
        }
        writeInt(world.events.size)
        for (event in world.events) {
            writeLong(event.tick)
            writeInt(event.siteId)
            writeInt(event.kind.code)
            writeInt(event.amount)
        }
        world.village?.let(::writeVillage)
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
    shaping?.let {
        writeInt(if (it.spent) 1 else 0)
        writeLong(it.refilledSpell)
        writeLong(it.warningAt ?: 0L)
        writeLong(it.shower?.startedAt ?: 0L)
        it.shower?.let { shower -> writeInt(shower.region) }
        writeInt(it.pools.size)
        for (pool in it.pools) {
            writeInt(pool.region)
            writeInt(pool.retained)
        }
    }
    bytes()
}

internal fun EcologyState.canonicalHash(): String = canonicalBytes().canonicalHash()

private fun CanonicalWriter.writeOptionalCell(cell: GridCell?) {
    writeInt(if (cell == null) 0 else 1)
    if (cell != null) writeCell(cell)
}

/** Rules v9 village section. Absent in earlier rules, so their canonical bytes are unchanged. */
private fun CanonicalWriter.writeVillage(village: VillageState) {
    writeInt(village.nextId)
    writeInt(village.thirstTurns)
    writeInt(village.grazing)
    writeLong(village.lastGrazed ?: -1L)
    writeLong(village.lastFlood ?: -1L)
    writeInt(village.cleared.size)
    village.cleared.forEach(::writeInt)
    writeInt(village.households.size)
    for (household in village.households) {
        writeInt(household.id)
        writeInt(household.name)
        writeInt(household.siteId)
        writeInt(household.home)
        writeInt(household.members)
        writeInt(household.trade.code)
        writeLong(household.joinedAt)
        writeInt(household.hardship)
        writeInt(household.task.code)
        writeLong(household.leftAt ?: -1L)
        writeInt(household.leftBecause?.code ?: 0)
    }
    writeInt(village.structures.size)
    for (structure in village.structures) {
        writeInt(structure.id)
        writeInt(structure.siteId)
        writeInt(structure.kind.code)
        writeCell(structure.cell)
        writeLong(structure.startedAt)
        writeInt(structure.required)
        writeInt(structure.progress)
        writeInt(structure.level)
        writeLong(structure.completedAt ?: -1L)
    }
    writeInt(village.petitions.size)
    for (petition in village.petitions) {
        writeInt(petition.id)
        writeInt(petition.kind.code)
        writeInt(petition.siteId)
        writeLong(petition.raisedAt)
        writeLong(petition.deadline)
        writeInt(petition.options.size)
        petition.options.forEach { writeInt(it.code) }
        writeInt(petition.choice?.code ?: 0)
        writeLong(petition.decidedAt ?: -1L)
        writeInt(if (petition.byKeeper) 1 else 0)
    }
    writeInt(village.chronicle.size)
    for (event in village.chronicle) {
        writeLong(event.tick)
        writeInt(event.kind.code)
        writeInt(event.siteId)
        writeInt(event.subject)
        writeInt(event.detail)
    }
}
