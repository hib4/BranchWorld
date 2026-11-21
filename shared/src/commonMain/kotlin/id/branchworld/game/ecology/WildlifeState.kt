package id.branchworld.game.ecology

import id.branchworld.game.simulation.GridCell
import kotlinx.serialization.Serializable

private const val RecentWildlifeEvents = 32

@Serializable
internal data class WildlifeLife(
    val bornAt: Long,
    val home: GridCell,
    val prey: Long? = null,
    val huntUntil: Long = 0,
)

@Serializable
enum class WildlifeMilestone(internal val canonicalId: Int) {
    Birth(1), NewRange(2), Caught(3), Scarcity(4), OldAge(5),
}

@Serializable
internal data class WildlifeEvent(
    val tick: Long,
    val id: Long,
    val species: Species,
    val kind: WildlifeMilestone,
    val cell: GridCell,
)

@Serializable
internal data class WildlifeHistory(
    val catches: Long = 0,
    val events: List<WildlifeEvent> = emptyList(),
) {
    fun record(newEvents: List<WildlifeEvent>): WildlifeHistory {
        if (newEvents.isEmpty()) return this
        return copy(
            catches = catches + newEvents.count { it.kind == WildlifeMilestone.Caught },
            events = (events + newEvents.sortedBy { it.id }).takeLast(RecentWildlifeEvents),
        )
    }

    fun validate(state: EcologyState, island: IslandEcology) {
        require(catches in 0..state.ledger.deaths)
        require(events.size <= RecentWildlifeEvents)
        require(events.zipWithNext().all { (first, next) ->
            first.tick < next.tick || (first.tick == next.tick && first.id < next.id)
        })
        require(events.all {
            it.tick in 1..state.tick && it.id in 1 until state.nextEntityId && island.navigation.contains(it.cell) &&
                (it.kind != WildlifeMilestone.Birth || it.id >= WildlifeFounderCount + 1L) &&
                (it.kind != WildlifeMilestone.Caught || it.species == Species.Rabbit)
        })
        require(events.count { it.kind == WildlifeMilestone.Caught }.toLong() <= catches)
        val living = state.creatures.associateBy { it.id }
        require(events.all { event ->
            val animal = living[event.id]
            animal == null || (animal.species == event.species &&
                event.kind in listOf(WildlifeMilestone.Birth, WildlifeMilestone.NewRange))
        })
    }
}

internal const val WildlifeFounderCount = 9

internal fun Species.populationLimit() = if (this == Species.Fox) 4 else MaxPerSpecies

internal fun Species.maturityTicks() = when (this) {
    Species.Rabbit -> MaturityTicks
    Species.Deer -> 360L
    Species.Fox -> 420L
}

internal fun Creature.lifespan(): Long = when (species) {
    Species.Rabbit -> 9 * DayLength
    Species.Deer -> 15 * DayLength
    Species.Fox -> 13 * DayLength
} + (id % 7) * 520
