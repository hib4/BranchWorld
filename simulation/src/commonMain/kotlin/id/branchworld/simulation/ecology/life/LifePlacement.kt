package id.branchworld.simulation.ecology.life

import id.branchworld.simulation.core.GridCell
import id.branchworld.simulation.ecology.Creature
import id.branchworld.simulation.ecology.EcologyState
import id.branchworld.simulation.ecology.ForageKind
import id.branchworld.simulation.ecology.ForagePatch
import id.branchworld.simulation.ecology.RegionalEcology
import id.branchworld.simulation.ecology.Species
import id.branchworld.simulation.ecology.cellId
import id.branchworld.simulation.ecology.fire.tileOf
import id.branchworld.simulation.ecology.terrainCell
import id.branchworld.simulation.ecology.wildlife.WildlifeLife
import id.branchworld.simulation.ecology.wildlife.later
import id.branchworld.simulation.terrain.Biome

/** What the player's hand may set down where, new creatures and plants, and the Wild start (rules v12). */
internal class LifePlacement(private val ground: LifeGround) {
    fun checkCreature(state: EcologyState, species: Species, cell: GridCell, queued: List<Species>): PlacementCheck {
        if (state.life == null) return PlacementCheck.NotLiving
        groundCheck(cell)?.let { return it }
        if (burning(state, cell)) return PlacementCheck.Burning
        val total = state.creatures.size + queued.size
        if (total >= IslandCreatureLimit) return PlacementCheck.IslandFull
        if (state.creatures.count { it.species == species } + queued.count { it == species } >= species.lifeLimit()) {
            return PlacementCheck.SpeciesFull
        }
        return PlacementCheck.Ok
    }

    fun checkPlant(state: EcologyState, kind: PlantKind, cell: GridCell, queued: List<GridCell>): PlacementCheck {
        if (state.life == null) return PlacementCheck.NotLiving
        groundCheck(cell)?.let { return it }
        if (ground.terrain.terrainCell(cell).biome !in PlantBiomes) return PlacementCheck.Unsuitable
        if (cell in state.springs || cell in queued || state.patches.any { it.cell == cell }) return PlacementCheck.Occupied
        if (burning(state, cell)) return PlacementCheck.Burning
        if (state.patches.count { it.planted } + queued.size >= MaxPlanted) return PlacementCheck.PlantsFull
        return PlacementCheck.Ok
    }

    private fun groundCheck(cell: GridCell): PlacementCheck? {
        val here = ground.terrain.cellAt(cell.x, cell.z) ?: return PlacementCheck.Unreachable
        if (here.biome == Biome.Water) return PlacementCheck.Water
        if (!ground.navigation.contains(cell)) return PlacementCheck.Unreachable
        return null
    }

    private fun burning(state: EcologyState, cell: GridCell) = state.fire?.burningAt(tileOf(cell)) != null

    fun plantPatch(kind: PlantKind, cell: GridCell): ForagePatch {
        val moisture = ground.terrain.terrainCell(cell).moisture
        return ForagePatch(cellId(cell), cell, if (kind == PlantKind.Tree) ForageKind.Browse else ForageKind.Meadow,
            160 + moisture / 3, 1 + moisture / 96, if (kind == PlantKind.Tree) RegionalEcology.SaplingBiomass else MeadowSeed,
            planted = true)
    }

    fun validPlanted(patch: ForagePatch, springs: List<GridCell>): Boolean {
        val here = ground.terrain.terrainCell(patch.cell)
        return patch.planted && here.biome in PlantBiomes && patch.cell !in springs &&
            patch.capacity == 160 + here.moisture / 3 && patch.regrowth == 1 + here.moisture / 96
    }

    fun creature(id: Long, species: Species, cell: GridCell, tick: Long, origin: Origin): Creature = Creature(
        id, species, cell, food = 720, water = 800, energy = 820, matureAt = tick,
        // Newcomers settle before they breed; a pack takes a season to raise its first pups.
        breedAfter = later(tick, if (origin == Origin.Seeded) 0 else if (species == Species.Wolf) WolfSettleTicks else SettleTicks),
        heading = Headings[lifeRoll(ground.seed, tick, id, RollHeading, 4)],
        // A hunter set down takes in its new ground before it hunts.
        wildlife = WildlifeLife(tick, cell, huntUntil = if (species.predator && origin == Origin.Placed) later(tick, HunterSettleTicks) else 0),
        life = CreatureLife(sexOf(id), origin),
    )

    /** The Wild start: rabbits on the meadows, deer in the woods and a pair of foxes on the ground.forest edge. */
    fun seedWild(state: EcologyState): EcologyState {
        ground.seed = ground.terrain.key.seed
        val animals = mutableListOf<Creature>()
        fun scatter(species: Species, count: Int, cells: List<GridCell>) {
            val near = ground.navigation.octileDistancesTo(ground.navigation.spawn)
            val ordered = cells.filter { near[cellId(it)] >= 0 }.sortedWith(compareBy<GridCell> {
                near[cellId(it)] + lifeRoll(ground.seed, 0, cellId(it).toLong(), RollSeed + species.canonicalId, 90)
            }.thenBy(::cellId))
            var placed = 0
            for (cell in ordered) {
                if (placed == count) break
                if (animals.any { chebyshev(it.cell, cell) < 3 }) continue
                animals.add(creature(animals.size + 1L, species, cell, 0, Origin.Seeded))
                placed++
            }
        }
        scatter(Species.Rabbit, 8, ground.meadow.sortedBy(::cellId))
        scatter(Species.Deer, 4, ground.forest)
        scatter(Species.Fox, 2, ground.edges)
        return state.copy(creatures = animals, nextEntityId = animals.size + 1L,
            life = checkNotNull(state.life).copy(placed = animals.size.toLong()))
    }
}
