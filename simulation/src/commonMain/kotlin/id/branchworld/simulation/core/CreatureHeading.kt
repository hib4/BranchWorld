package id.branchworld.simulation.core

import kotlinx.serialization.Serializable

@Serializable
enum class CreatureHeading(internal val canonicalId: Int) {
    PositiveX(1), NegativeX(2), PositiveZ(3), NegativeZ(4),
    /** Rules v12 diagonal headings. */
    PositiveXPositiveZ(5), NegativeXPositiveZ(6), PositiveXNegativeZ(7), NegativeXNegativeZ(8),
}
