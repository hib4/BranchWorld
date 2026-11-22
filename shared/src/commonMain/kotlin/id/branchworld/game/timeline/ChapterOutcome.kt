package id.branchworld.game.timeline

import id.branchworld.game.ecology.Chapter
import id.branchworld.game.ecology.AshAge
import id.branchworld.game.ecology.EcologyState
import id.branchworld.game.ecology.Hardship
import id.branchworld.game.ecology.PlanOption
import id.branchworld.game.ecology.ScarKind
import id.branchworld.game.ecology.Species
import id.branchworld.game.ecology.StructureKind
import id.branchworld.game.ecology.rooted

/** What one history's head actually holds when a chapter closes. Every number is read from authoritative state. */
internal data class ChapterOutcome(
    val branch: BranchSummary,
    val tick: Long,
    val households: Int,
    val people: Int,
    val homes: Int,
    val settlements: Int,
    val departed: Map<Hardship, Int>,
    val well: Boolean,
    val groves: Int,
    val deer: Int,
    val rabbits: Int,
    val foxes: Int,
    val calves: Int,
    val refuges: Int,
    val growthPlan: PlanOption?,
    val waterPlan: PlanOption?,
    val keeperChose: Boolean,
    /** Rules v11: homes the fire took, pines lost to fire, pines felled, tiles still bare and the woods plan. */
    val homesBurned: Int = 0,
    val pinesBurned: Int = 0,
    val pinesFelled: Int = 0,
    val burnedGround: Int = 0,
    val regrowingGround: Int = 0,
    val fires: Int = 0,
    val emberPlan: PlanOption? = null,
) {
    companion object {
        fun of(branch: BranchSummary, state: EcologyState): ChapterOutcome {
            val world = state.settlements
            val village = world?.village
            val decided = village?.petitions.orEmpty()
            fun count(species: Species) = state.creatures.count { it.species == species }
            return ChapterOutcome(
                branch = branch,
                tick = state.tick,
                households = village?.households?.count { it.active } ?: 0,
                people = world?.sites?.sumOf { it.residents } ?: 0,
                homes = world?.sites?.sumOf { it.homes } ?: 0,
                settlements = world?.sites?.count { site -> village?.active(site.id)?.isNotEmpty() ?: (site.residents > 0) } ?: 0,
                departed = village?.households.orEmpty().mapNotNull { it.leftBecause }.groupingBy { it }.eachCount(),
                well = village?.structures?.any { it.kind == StructureKind.Well && it.complete } == true,
                groves = state.patches.count { it.rooted() },
                deer = count(Species.Deer),
                rabbits = count(Species.Rabbit),
                foxes = count(Species.Fox),
                calves = state.habitat?.herd?.calves ?: 0,
                refuges = state.habitat?.refuges?.size ?: 0,
                growthPlan = decided.firstOrNull { it.choice?.petition == id.branchworld.game.ecology.PetitionKind.Growth }?.choice,
                waterPlan = decided.firstOrNull { it.choice?.petition == id.branchworld.game.ecology.PetitionKind.Water }?.choice,
                keeperChose = decided.any { it.byKeeper },
                homesBurned = state.fire?.homesBurned ?: 0,
                pinesBurned = state.fire?.stands?.count { it.kind == ScarKind.Burned } ?: 0,
                pinesFelled = state.fire?.stands?.count { it.kind == ScarKind.Felled } ?: 0,
                burnedGround = state.fire?.scars?.count { it.kind == ScarKind.Burned } ?: 0,
                regrowingGround = state.fire?.scars?.count { it.kind == ScarKind.Burned && state.tick - it.at >= AshAge } ?: 0,
                fires = state.fire?.fires ?: 0,
                emberPlan = decided.firstOrNull { it.choice?.petition == id.branchworld.game.ecology.PetitionKind.Ember }?.choice,
            )
        }
    }
}

internal const val EmberClosingDelay = 3 * id.branchworld.game.ecology.DayLength

/** The crisis a story turns on has played out on this history, so the Keeper may close the chapter here. */
internal fun EcologyState.chapterCanClose(chapter: Chapter?): Boolean {
    val warned = shaping?.warningAt != null
    if (chapter == null || !warned || settlements?.village?.openPetition() != null) return false
    return when (chapter) {
        Chapter.FadingGrove -> tick >= FadingGroveRecovery
        // After the warning the herd has gone to the rains and come back: its year is complete.
        Chapter.LastCrossing -> (habitat?.herd?.crossings ?: 0) >= 3
        // The storm has broken, every fire is out and the land has had a season to show what comes back.
        Chapter.EmberSeason -> fire?.burning?.isEmpty() == true && fire.strikes.isEmpty() &&
            tick - checkNotNull(shaping.warningAt) >= EmberClosingDelay
    }
}
