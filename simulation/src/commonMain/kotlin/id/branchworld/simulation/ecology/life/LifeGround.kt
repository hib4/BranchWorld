package id.branchworld.simulation.ecology.life

import id.branchworld.simulation.core.GridCell
import id.branchworld.simulation.ecology.Creature
import id.branchworld.simulation.ecology.CreatureAction
import id.branchworld.simulation.ecology.ForageKind
import id.branchworld.simulation.ecology.ForagePatch
import id.branchworld.simulation.ecology.Goal
import id.branchworld.simulation.ecology.IslandEcology
import id.branchworld.simulation.ecology.PatchCellIndex
import id.branchworld.simulation.ecology.Species
import id.branchworld.simulation.ecology.cellId
import id.branchworld.simulation.ecology.environment.rooted
import id.branchworld.simulation.ecology.terrainCell
import id.branchworld.simulation.terrain.Biome
import kotlin.math.abs

/**
 * The ground a living world's animals know (rules v12): the island's reachable cells, its meadows, woods and edges, the
 * cover and warrens they hide in this tick, and how they move across it, with a small cache of shortest-path fields.
 */
internal class LifeGround(val island: IslandEcology) {
    val terrain = island.terrain
    val navigation = island.navigation
    val reachable = island.reachable.toHashSet()
    val forest = island.reachable.filter { terrain.terrainCell(it).biome == Biome.PineForest }
    val naturalCover = forest.toHashSet()
    val meadow = island.roamCells(Species.Rabbit).toHashSet()
    val edges = forest.filter { cell -> navigation.adjacent(cell).any { it !in naturalCover } }.ifEmpty { forest }
    val edgeSet = edges.toHashSet()
    val patchCells = PatchCellIndex()
    var plantedCover: List<GridCell> = emptyList()
    var bareCover: Set<GridCell> = emptySet()
    var cover: Set<GridCell> = naturalCover
    var warrenCells: Set<GridCell> = emptySet()
    val fields = LinkedHashMap<GridCell, IntArray>()
    var fieldBuilds = 0
    var seed = terrain.key.seed

    /** Ready the ground for one step of [seed]'s world: forage index, path-field budget and this tick's cover. */
    fun begin(seed: Long, patches: List<ForagePatch>, bare: Set<GridCell>, warrens: List<Warren>) {
        this.seed = seed
        fieldBuilds = 0
        patchCells.rebuild(patches)
        updateCover(patches, bare, warrens)
    }

    fun updateCover(patches: List<ForagePatch>, bare: Set<GridCell>, warrens: List<Warren>) {
        val rooted = patches.filter { it.rooted() && it.kind == ForageKind.Browse }.map { it.cell }
        warrenCells = warrens.mapTo(HashSet()) { it.cell }
        if (rooted == plantedCover && bare === bareCover) return
        plantedCover = rooted
        bareCover = bare
        cover = naturalCover.toHashSet().apply {
            removeAll(bare)
            for (cell in rooted) {
                add(cell)
                addAll(navigation.adjacent(cell))
            }
        }
    }

    /** A rabbit down its warren cannot be caught; cover only makes a catch harder. */
    fun refuge(animal: Creature) = animal.species == Species.Rabbit && animal.cell in warrenCells

    /** Where a rabbit can stop running: its warren or a thicket. */
    fun shelter(animal: Creature) = animal.species == Species.Rabbit && (animal.cell in warrenCells || animal.cell in cover)

    /** Whether something at [from] can see [to]: in range, not hidden deep in cover, and no thicket or ridge between. */
    fun sees(from: GridCell, to: GridCell, sight: Int): Boolean {
        val range = chebyshev(from, to)
        if (range > sight) return false
        if (range <= 2) return true
        if (to in cover && range > 4) return false
        val ends = maxOf(terrain.terrainCell(from).elevationTwice, terrain.terrainCell(to).elevationTwice)
        var x = from.x
        var z = from.z
        val dx = abs(to.x - x)
        val dz = -abs(to.z - z)
        val sx = if (x < to.x) 1 else -1
        val sz = if (z < to.z) 1 else -1
        var error = dx + dz
        var thicket = 0
        while (true) {
            val doubled = 2 * error
            if (doubled >= dz) { error += dz; x += sx }
            if (doubled <= dx) { error += dx; z += sz }
            if (x == to.x && z == to.z) return true
            val cell = GridCell(x, z)
            if (cell in cover && ++thicket >= 2) return false
            val ground = terrain.cellAt(x, z) ?: return false
            if (ground.elevationTwice > ends + RidgeTwice) return false
        }
    }

    fun denFor(a: GridCell, b: GridCell, packs: List<Pack>): GridCell {
        val mid = GridCell((a.x + b.x) / 2, (a.z + b.z) / 2)
        return edges.filter { cell -> packs.none { chebyshev(it.den, cell) < 14 } }
            .minWithOrNull(compareBy<GridCell> { octile(it, mid) }.thenBy(::cellId))
            ?: edges.minWithOrNull(compareBy<GridCell> { octile(it, mid) }.thenBy(::cellId)) ?: a
    }

    fun sprint(animal: Creature): Int {
        val gait = animal.species.gait
        return if (checkNotNull(animal.life).stamina > SprintFloor) gait.sprint else gait.walk
    }

    /**
     * Moves toward [goal] at [speed] centi-cells per tick: the stride fills until it covers the next step (141 on a
     * diagonal), so a sprinting rabbit crosses a cell every tick and a walking deer one about every third.
     * [moving] targets are steered toward directly; fixed ones follow a cached shortest-path field.
     */
    fun travel(animal: Creature, goal: Goal, action: CreatureAction, speed: Int, moving: Boolean): Creature {
        val life = checkNotNull(animal.life)
        if (goal.cell == animal.cell) return animal.copy(goal = goal, action = action, life = life.copy(stride = 0))
        val next = if (moving) steer(animal.cell, goal.cell) else follow(animal.cell, goal.cell)
        if (next == animal.cell) return animal.copy(goal = goal, action = action, life = life.copy(stride = 0))
        val cost = if (next.x != animal.cell.x && next.z != animal.cell.z) DiagonalStride else OrthogonalStride
        val stride = minOf(life.stride + speed, DiagonalStride)
        if (stride < cost) return animal.copy(goal = goal, action = action, heading = headingOf(animal.cell, next),
            life = life.copy(stride = stride))
        return animal.copy(cell = next, goal = goal, action = action, heading = headingOf(animal.cell, next),
            energy = (animal.energy - if (action == CreatureAction.Chasing || action == CreatureAction.Fleeing) 2 else 0).coerceAtLeast(0),
            life = life.copy(stride = stride - cost))
    }

    fun follow(from: GridCell, target: GridCell): GridCell {
        if (target !in reachable) return steer(from, target)
        val field = field(target) ?: return steer(from, target)
        return navigation.stepOctile(from, field)
    }

    /** One greedy step that closes on [target]; falls back to a path field when water or cliffs are in the way. */
    fun steer(from: GridCell, target: GridCell): GridCell {
        val here = octile(from, target)
        var best = from
        var bestScore = here
        for (next in navigation.adjacent8(from)) {
            val score = octile(next, target)
            if (score < bestScore) {
                best = next
                bestScore = score
            }
        }
        if (best != from || target !in reachable) return best
        val field = field(target)
        // Out of fresh path fields this tick: the cheaper four-way field still finds a way round.
        if (field == null) return navigation.step(from, island.terrainDistance(target))
        return navigation.stepOctile(from, field)
    }

    fun field(target: GridCell): IntArray? {
        fields[target]?.let {
            fields.remove(target)
            fields[target] = it
            return it
        }
        if (fieldBuilds >= FieldBudget) return null
        fieldBuilds++
        if (fields.size >= CachedFields) fields.remove(fields.keys.first())
        return navigation.octileDistancesTo(target).also { fields[target] = it }
    }
}
