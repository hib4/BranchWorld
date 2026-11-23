package id.branchworld.simulation.core

import kotlinx.serialization.Serializable

/** One terrain cell. Every rule set addresses the island on this integer grid. */
@Serializable
data class GridCell(val x: Int, val z: Int)
