package id.branchworld.simulation.ecology.life

import id.branchworld.simulation.core.GridCell
import id.branchworld.simulation.ecology.Creature
import id.branchworld.simulation.ecology.ForagePatch
import id.branchworld.simulation.ecology.environment.DayLength
import id.branchworld.simulation.ecology.fire.FireView

/** One tick of a living world as the animals see it: the time of day, the forage, fire and who is where. */
internal class LifeStep(
    val tick: Long,
    val recharge: Int,
    val patches: MutableList<ForagePatch>,
    val springs: List<GridCell>,
    val fire: FireView?,
    val byId: Map<Long, Creature>,
    val packOf: Map<Long, Int>,
    val packs: Map<Int, Pack>,
    val warrens: List<Warren>,
    val thumpers: List<Creature>,
) {
    val phase = tick % DayLength
    val night = phase >= 660 || phase < 60
    val animals = byId.values.sortedBy { it.id }
}
