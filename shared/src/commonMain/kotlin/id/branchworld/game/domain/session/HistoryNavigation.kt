package id.branchworld.game.domain.session

import id.branchworld.simulation.ecology.settlement.SettlementEvent
import id.branchworld.simulation.projection.InterventionMarker
import id.branchworld.simulation.timeline.HistoryBrowser

/** Moving through a world's histories: seeking, switching and starting branches. */
internal interface HistoryNavigation {
    /** Opens a bookmarked or recorded moment of any history. */
    suspend fun visit(branch: Int, tick: Long)
    suspend fun seek(tick: Long)

    /** Replays to just before [tick] and applies its commands, so their results can be read. */
    suspend fun seekEvent(tick: Long)
    suspend fun switchBranch(id: Int)

    /** Starts a new history from the moment on view. */
    fun fork(name: String): Boolean
    fun renameActive(name: String): Boolean
    fun markers(branch: Int): List<InterventionMarker>
    fun settlementEvents(branch: Int): List<SettlementEvent>
    fun history(branchId: Int): HistoryBrowser
}

internal class HistoryNavigator(private val session: WorldSessionCore) : HistoryNavigation {
    private val timeline get() = session.timeline

    override suspend fun visit(branch: Int, tick: Long) {
        val summary = session.timelineState.value.branches.firstOrNull { it.id == branch } ?: return
        session.navigate(branch, tick.coerceIn(summary.start, summary.head))
    }

    override suspend fun seek(tick: Long) = session.navigate(timeline.branchId, tick)

    override suspend fun seekEvent(tick: Long) = session.navigate(timeline.branchId, tick, inspectEvent = true)

    override suspend fun switchBranch(id: Int) {
        session.timelineState.value.branches.firstOrNull { it.id == id }?.let { session.navigate(id, it.head) }
    }

    override fun fork(name: String): Boolean {
        if (!session.navigationAvailable) return false
        session.clock.pause()
        if (!timeline.fork(name)) return false
        session.publishNavigation()
        return true
    }

    override fun renameActive(name: String): Boolean {
        if (!session.navigationAvailable || !timeline.renameActive(name)) return false
        session.updateTimeline()
        return true
    }

    override fun markers(branch: Int) = timeline.interventionMarkers(branch, includePending = true)

    override fun settlementEvents(branch: Int) = timeline.settlementEvents(branch)

    override fun history(branchId: Int) = timeline.history(branchId)
}
