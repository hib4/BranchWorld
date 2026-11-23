package id.branchworld.simulation.ecology

import id.branchworld.simulation.ecology.wildlife.WildlifeHistory

internal data class CreatureTurn(
    val animals: List<Creature>,
    val nextId: Long,
    val history: WildlifeHistory? = null,
)
