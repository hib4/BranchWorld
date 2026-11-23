package id.branchworld.simulation.terrain

import kotlinx.serialization.Serializable

@Serializable
data class TerrainIdentity(val key: TerrainKey, val digest: ULong)
