package id.branchworld.game.domain.session

import id.branchworld.simulation.command.EndorsePlan
import id.branchworld.simulation.ecology.Intervention
import id.branchworld.simulation.ecology.RegionReadout
import id.branchworld.simulation.ecology.chapters.Chapter
import id.branchworld.simulation.ecology.settlement.Petition
import id.branchworld.simulation.ecology.settlement.PlanOption
import id.branchworld.simulation.projection.project
import id.branchworld.simulation.timeline.ChapterOutcome

/**
 * The earlier crafted chapters and sandboxes (rules v8–v11): shaping regions, the village's petitions and closing a
 * chapter. Living worlds (rules v12) never use these.
 */
internal interface ChapterControls {
    val chapter: Chapter?
    val canCloseChapter: Boolean
    val warningTick: Long?
    val canReturnToWarning: Boolean

    fun inspectRegions(): List<RegionReadout>
    fun outcomes(): List<ChapterOutcome>
    fun pastureRegion(): Int?
    fun recommendedRainRegion(): Int
    fun recommendedGroveRegion(): Int
    fun shapePreview(kind: Intervention, region: Int): ShapeTarget?
    suspend fun returnToWarning()
    fun cancelShape()
    fun intervene(kind: Intervention, region: Int)
    fun playTo(tick: Long)

    /** Hollowmere's open petition, if the village is waiting for an answer. */
    fun openPetition(): Petition?
    fun pendingEndorsement(): EndorsePlan?
    fun endorse(option: PlanOption)
    fun cancelEndorsement()
}

internal class LegacyChapterControls(private val session: WorldSessionCore) : ChapterControls {
    private val timeline get() = session.timeline

    override val chapter get() = timeline.chapter
    override val canCloseChapter get() = !session.key.isShared && session.navigationAvailable && timeline.canCloseChapter
    override val warningTick get() = timeline.warningLocation()?.second
    override val canReturnToWarning get() = timeline.warningLocation()?.let { (branch, tick) ->
        session.navigationAvailable && (branch != timeline.branchId || tick != timeline.state.tick)
    } == true

    override fun inspectRegions() = timeline.inspectRegions()
    override fun outcomes() = timeline.outcomes()
    override fun pastureRegion() = timeline.pastureRegion()
    override fun recommendedRainRegion() = timeline.recommendedRainRegion().coerceAtLeast(0)
    override fun recommendedGroveRegion() = timeline.recommendedGroveRegion()

    override fun shapePreview(kind: Intervention, region: Int): ShapeTarget? = timeline.preview(kind, region)?.let {
        val terrain = session.world.terrain
        ShapeTarget(it.sites.map { site -> site.project(terrain) },
            it.field?.project(terrain), it.bank?.project(terrain), it.blocked,
            // A firebreak is marked every other cell so the line reads without a ring on each one.
            it.cut.filterIndexed { index, _ -> index % 2 == 0 }.map { cell -> cell.project(terrain) }, it.cut.size / 2)
    }

    override suspend fun returnToWarning() {
        val (branch, tick) = timeline.warningLocation() ?: return
        session.navigate(branch, tick)
    }

    override fun cancelShape() {
        if (session.navigationAvailable && timeline.cancelShape()) session.queueChanged()
    }

    override fun intervene(kind: Intervention, region: Int) {
        if (session.navigationAvailable && timeline.intervene(kind, region)) session.queueChanged()
    }

    override fun playTo(tick: Long) {
        if (session.navigationAvailable) session.clock.playTo(tick)
    }

    override fun openPetition() = timeline.openPetition()
    override fun pendingEndorsement() = timeline.pendingEndorsement()

    override fun endorse(option: PlanOption) {
        if (session.navigationAvailable && timeline.endorse(option)) session.queueChanged()
    }

    override fun cancelEndorsement() {
        if (session.navigationAvailable && timeline.cancelEndorsement()) session.queueChanged()
    }
}
