package id.branchworld.game

import id.branchworld.game.ecology.EcologyState
import id.branchworld.game.ecology.PlacementCheck
import id.branchworld.game.ecology.PlantKind
import id.branchworld.game.ecology.Species
import id.branchworld.game.simulation.GridCell
import id.branchworld.game.simulation.PlaceTool
import id.branchworld.game.simulation.WorldPick
import id.branchworld.game.ecology.Intervention
import id.branchworld.game.ecology.PlanOption
import id.branchworld.game.ecology.InterventionResult
import id.branchworld.game.ecology.ShapingRulesVersion
import id.branchworld.game.ecology.canonicalHash
import id.branchworld.game.ecology.RangeRulesVersion
import id.branchworld.game.save.AtlasEntry
import id.branchworld.game.save.IncompatibleSave
import id.branchworld.game.save.SaveFileTooLarge
import id.branchworld.game.save.TimelineSaveCodec
import id.branchworld.game.save.WorldSaveStore
import id.branchworld.game.simulation.ClockSimulation
import id.branchworld.game.simulation.WorldClock
import id.branchworld.game.simulation.WorldRenderProjection
import id.branchworld.game.simulation.WorldRenderSource
import id.branchworld.game.simulation.renderProjection
import id.branchworld.game.simulation.project
import id.branchworld.game.simulation.GroundPoint
import id.branchworld.game.terrain.IslandGenerator
import id.branchworld.game.terrain.TerrainKey
import id.branchworld.game.terrain.TerrainMap
import id.branchworld.game.timeline.BranchSummary
import id.branchworld.game.timeline.HistoryMoment
import id.branchworld.game.timeline.InterventionMarker
import id.branchworld.game.timeline.SharedPast
import id.branchworld.game.timeline.Timeline
import id.branchworld.game.timeline.TimelinePair
import id.branchworld.game.timeline.WorldCode
import id.branchworld.game.timeline.WorldCodeError
import id.branchworld.game.timeline.WorldCodeProblem
import id.branchworld.game.timeline.commonTicks
import id.branchworld.game.timeline.inTreeOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.time.TimeSource

internal class IslandSession {
    private val load = MutableStateFlow<IslandLoad>(IslandLoad.Empty)
    val state = load.asStateFlow()
    private val storage = MutableStateFlow<SaveStatus?>(null)
    val saveStatus = storage.asStateFlow()
    private val recent = linkedMapOf<IslandSessionKey, IslandLoad.Ready>()

    fun dismissSaveStatus() {
        if (storage.value != SaveStatus.Busy) storage.value = null
    }

    fun acknowledgeRestore(tick: Long) {
        storage.value = SaveStatus.Loaded(tick)
    }

    suspend fun open(key: TerrainKey) = open(IslandSessionKey(key))

    /** Slots that failed to load this session. They are never autosaved over, so the player's history stays on disk. */
    private val unreadable = mutableSetOf<String>()

    suspend fun open(key: IslandSessionKey, store: WorldSaveStore? = null) {
        val previous = load.value
        if ((previous as? IslandLoad.Ready)?.key == key) return
        recent[key]?.let {
            publish(it)
            return
        }
        val loading = IslandLoad.Loading(key)
        if (previous is IslandLoad.Loading && previous.key == key) return
        load.value = loading
        try {
            val saved = store?.let { readSlot(it, key) }
            if (saved != null) {
                if (load.value === loading) publish(saved)
                return
            }
            if (key.sharedSlot != null) {
                if (load.value === loading) load.value = IslandLoad.Failed(key)
                return
            }
            val ready = withContext(Dispatchers.Default) {
                val started = TimeSource.Monotonic.markNow()
                val terrain = IslandGenerator.generate(key.terrain)
                val generationUs = started.elapsedNow().inWholeMicroseconds
                val timeline = Timeline.start(terrain, rulesVersion = key.rules, chapter = key.chapter,
                    start = key.start ?: id.branchworld.game.ecology.WorldStart.Empty)
                println("BranchWorldTerrain seed=${key.terrain.seed} version=${key.terrain.worldgenVersion} rules=${key.rules} hash=${terrain.digest.toString(16)} props=${terrain.propCount} generationUs=$generationUs readyUs=${started.elapsedNow().inWholeMicroseconds}")
                IslandLoad.Ready(terrain, timeline, worldSlot = key.worldSlot)
            }
            if (load.value === loading) publish(ready)
        } catch (error: CancellationException) {
            if (load.value === loading) load.value = if (previous is IslandLoad.Loading) IslandLoad.Empty else previous
            throw error
        } catch (error: Exception) {
            println("BranchWorldTerrain generationFailed key=$key ${error.stackTraceToString()}")
            if (load.value === loading) load.value = IslandLoad.Failed(key)
        }
    }

    /** The island's own history from its atlas slot, or null to begin a new one. */
    private suspend fun readSlot(store: WorldSaveStore, key: IslandSessionKey): IslandLoad.Ready? {
        val bytes = try {
            store.readFile(key.slot, TimelineSaveCodec.MaxBytes)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            println("BranchWorldSave slot unreadable ${key.slot}: ${error::class.simpleName}")
            unreadable.add(key.slot)
            storage.value = SaveStatus.Unreadable
            return null
        } ?: return null
        return try {
            val started = TimeSource.Monotonic.markNow()
            val world = withContext(Dispatchers.Default) { TimelineSaveCodec.restore(bytes) }
            val island = IslandLoad.Ready(world.terrain, world.timeline, world.sharedOrigin, key.worldSlot)
            check(island.key == key) { "Slot ${key.slot} holds ${island.key}" }
            println("BranchWorldSave opened slot=${key.slot} branch=${world.timeline.branchId} tick=${island.clock.state.tick} hash=${island.clock.state.canonicalHash()} restoreMs=${started.elapsedNow().inWholeMilliseconds}")
            island
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            println("BranchWorldSave slot rejected ${key.slot}: ${error::class.simpleName}")
            unreadable.add(key.slot)
            storage.value = if (error is IncompatibleSave) SaveStatus.Incompatible else SaveStatus.Corrupt
            null
        }
    }

    /**
     * Writes the open island's whole history to its own slot. Returns the atlas summary, or null when nothing was
     * written. A slot that failed to load is left untouched.
     */
    suspend fun autosave(store: WorldSaveStore): AtlasEntry? {
        val island = load.value as? IslandLoad.Ready ?: return null
        val key = island.key
        if (key.slot in unreadable || storage.value == SaveStatus.Busy) return null
        val checkpoint = island.checkpoint()
        val active = checkpoint.branches.first { it.id == checkpoint.activeBranchId }
        return try {
            val started = TimeSource.Monotonic.markNow()
            val bytes = withContext(Dispatchers.Default) { TimelineSaveCodec.encode(checkpoint, island.sharedOrigin) }
            if (!store.writeFile(key.slot, bytes)) return null
            println("BranchWorldSave autosaved slot=${key.slot} branch=${active.id} tick=${active.head.tick} branches=${checkpoint.branches.size} bytes=${bytes.size} saveMs=${started.elapsedNow().inWholeMilliseconds}")
            autosaved.value = active.head.tick
            AtlasEntry(key.slot, key.terrain.seed, key.terrain.worldgenVersion, key.rules, key.chapter?.code ?: (key.story?.code ?: 0),
                active.head.tick, checkpoint.branches.size, active.name, start = key.start?.code ?: 0)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            println("BranchWorldSave autosave failed: ${error::class.simpleName}")
            storage.value = SaveStatus.Unwritable
            null
        }
    }

    /** The open island's atlas summary without writing it, for platforms with no storage. */
    fun summary(): AtlasEntry? {
        val island = load.value as? IslandLoad.Ready ?: return null
        val key = island.key
        val view = island.timelineView.value
        return AtlasEntry(key.slot, key.terrain.seed, key.terrain.worldgenVersion, key.rules, key.chapter?.code ?: (key.story?.code ?: 0),
            view.active.head, view.branches.size, view.active.name, start = key.start?.code ?: 0)
    }

    suspend fun importCode(code: String, store: WorldSaveStore): WorldCodeImport {
        return try {
            val decoded = withContext(Dispatchers.Default) { WorldCode.decode(code) }
            val created = store.createFile(decoded.slot, decoded.save)
            val world = if (created) decoded.world else {
                val existing = store.readFile(decoded.slot, TimelineSaveCodec.MaxBytes) ?: return WorldCodeImport.Unwritable
                val restored = withContext(Dispatchers.Default) { TimelineSaveCodec.restore(existing) }
                if (restored.sharedOrigin != decoded.origin) return WorldCodeImport.Occupied
                restored
            }
            val island = IslandLoad.Ready(world.terrain, world.timeline, decoded.origin)
            val view = island.timelineView.value
            val key = island.key
            WorldCodeImport.Open(AtlasEntry(key.slot, key.terrain.seed, key.terrain.worldgenVersion, key.rules,
                key.story?.code ?: 0, view.active.head, view.branches.size, view.active.name, start = key.start?.code ?: 0))
        } catch (error: CancellationException) {
            throw error
        } catch (error: WorldCodeError) {
            WorldCodeImport.BadCode(error.problem)
        } catch (_: Exception) {
            WorldCodeImport.Unwritable
        }
    }

    private val autosaved = MutableStateFlow<Long?>(null)
    /** The head tick of the last successful autosave of the open island. */
    val lastAutosave = autosaved.asStateFlow()

    /**
     * Copies the original single save into its island's atlas slot once, leaving the original file untouched.
     * Returns the atlas entry for the imported island, or null when there was nothing usable to import.
     */
    suspend fun importLegacy(store: WorldSaveStore): AtlasEntry? {
        val bytes = try {
            store.read(TimelineSaveCodec.MaxBytes)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        } ?: return null
        return try {
            val world = withContext(Dispatchers.Default) { TimelineSaveCodec.restore(bytes) }
            val island = IslandLoad.Ready(world.terrain, world.timeline)
            val key = island.key
            val checkpoint = island.checkpoint()
            val active = checkpoint.branches.first { it.id == checkpoint.activeBranchId }
            if (store.readFile(key.slot, TimelineSaveCodec.MaxBytes) == null) {
                // Earlier schemas are re-encoded only by their own codec path; keep the original bytes exactly.
                if (!store.writeFile(key.slot, bytes)) return null
                println("BranchWorldSave imported legacy slot=${key.slot} tick=${active.head.tick} bytes=${bytes.size}")
            }
            AtlasEntry(key.slot, key.terrain.seed, key.terrain.worldgenVersion, key.rules, key.chapter?.code ?: (key.story?.code ?: 0),
                active.head.tick, checkpoint.branches.size, active.name, start = key.start?.code ?: 0)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            println("BranchWorldSave legacy import skipped: ${error::class.simpleName}")
            null
        }
    }

    private fun publish(island: IslandLoad.Ready) {
        val key = island.key
        recent.remove(key)
        recent[key] = island
        if (recent.size > IslandGallery.size * 2 + 1) recent.remove(recent.keys.first())
        load.value = island
    }

    suspend fun save(store: WorldSaveStore, replace: Boolean = false): Boolean {
        if (storage.value == SaveStatus.Busy) return false
        val island = load.value as? IslandLoad.Ready ?: return false
        if (island.key.sharedSlot != null) {
            if (autosave(store) == null) return false
            storage.value = SaveStatus.Saved(island.clock.state.tick)
            return true
        }
        val started = TimeSource.Monotonic.markNow()
        // Only the UI owner can capture the committed tick and pending queue together.
        val checkpoint = island.checkpoint()
        val world = island.clock.state
        storage.value = SaveStatus.Busy
        try {
            val bytes = withContext(Dispatchers.Default) { TimelineSaveCodec.encode(checkpoint) }
            storage.value = if (store.write(bytes, replace)) {
                println("BranchWorldSave saved seed=${world.terrain.key.seed} branch=${checkpoint.activeBranchId} tick=${world.tick} hash=${world.canonicalHash()} branches=${checkpoint.branches.size} bytes=${bytes.size} saveMs=${started.elapsedNow().inWholeMilliseconds}")
                SaveStatus.Saved(world.tick)
            } else SaveStatus.ConfirmReplace
        } catch (error: CancellationException) {
            storage.value = null
            throw error
        } catch (error: Exception) {
            println("BranchWorldSave write failed: ${error::class.simpleName}")
            storage.value = SaveStatus.Unwritable
        }
        return storage.value is SaveStatus.Saved
    }

    suspend fun restore(store: WorldSaveStore) {
        if (storage.value == SaveStatus.Busy) return
        storage.value = SaveStatus.Busy
        try {
            val started = TimeSource.Monotonic.markNow()
            val bytes = store.read(TimelineSaveCodec.MaxBytes)
            if (bytes == null) {
                storage.value = SaveStatus.Missing
                return
            }
            val world = withContext(Dispatchers.Default) {
                try {
                    TimelineSaveCodec.restore(bytes)
                } catch (error: IncompatibleSave) {
                    throw error
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    throw InvalidSave(error)
                }
            }
            val island = IslandLoad.Ready(world.terrain, world.timeline)
            publish(island)
            println("BranchWorldSave restored seed=${island.clock.seed} branch=${world.timeline.branchId} tick=${island.clock.state.tick} hash=${island.clock.state.canonicalHash()} restoreMs=${started.elapsedNow().inWholeMilliseconds}")
            storage.value = SaveStatus.Restored(island.key, island.clock.state.tick)
        } catch (error: CancellationException) {
            storage.value = null
            throw error
        } catch (_: IncompatibleSave) {
            storage.value = SaveStatus.Incompatible
        } catch (_: SaveFileTooLarge) {
            storage.value = SaveStatus.Corrupt
        } catch (_: InvalidSave) {
            storage.value = SaveStatus.Corrupt
        } catch (error: Exception) {
            println("BranchWorldSave read failed: ${error::class.simpleName}")
            storage.value = SaveStatus.Unreadable
        }
    }
}

internal sealed interface IslandLoad {
    data object Empty : IslandLoad
    data class Loading(val key: IslandSessionKey) : IslandLoad
    class Ready(
        terrain: TerrainMap,
        private val timeline: Timeline,
        val sharedOrigin: String? = null,
        worldSlot: String? = null,
    ) : IslandLoad {
        val key = IslandSessionKey(terrain.key, timeline.state.rulesVersion,
            timeline.chapter?.takeIf { timeline.state.rulesVersion >= RangeRulesVersion }, sharedOrigin?.let(WorldCode::slotForOrigin),
            worldSlot.takeIf { sharedOrigin == null }, timeline.state.life?.start)
        private val feedback = MutableStateFlow(InterventionFeedback(queued = timeline.queuedInterventions))
        val interventions = feedback.asStateFlow()
        private val timelineState = MutableStateFlow(timelineStatus())
        val timelineView = timelineState.asStateFlow()
        private val comparisonState = MutableStateFlow<ComparisonView?>(null)
        private var comparisonRevision = 0
        val comparison = comparisonState.asStateFlow()
        private val navigationAvailable get() = !timelineState.value.seeking && comparisonState.value == null
        private val display = MutableStateFlow(timeline.state.renderProjection(terrain).copy(branchId = timeline.branchId))
        private var liveFrame = false
        val clock = WorldClock(object : ClockSimulation<EcologyState> {
            override val state get() = timeline.state
            override val tick get() = state.tick
            override val seed get() = terrain.key.seed
            override fun canSteer(left: Boolean) = navigationAvailable && timeline.canSteer(left)
            override fun steer(left: Boolean) {
                if (navigationAvailable) timeline.steer(left)
                updateTimeline()
            }
            override fun advance() { liveFrame = advanceTimeline() }
            override fun project(previous: WorldRenderProjection?) =
                state.renderProjection(terrain).copy(branchId = timeline.branchId, interpolateCreatures = liveFrame).also {
                    liveFrame = false
                    if (comparisonState.value == null) display.value = it
                }
        })
        val world = WorldRenderSource(terrain, display.asStateFlow(), timeline.herdRoute().map { it.project(terrain) })

        fun inspectRegions() = timeline.inspectRegions()
        val chapter get() = timeline.chapter
        val canCloseChapter get() = !key.isShared && navigationAvailable && timeline.canCloseChapter
        fun outcomes() = timeline.outcomes()
        fun history(branchId: Int) = timeline.history(branchId)
        fun pastureRegion() = timeline.pastureRegion()

        /** Opens a bookmarked or recorded moment of any history. */
        suspend fun visit(branch: Int, tick: Long) {
            val summary = timelineState.value.branches.firstOrNull { it.id == branch } ?: return
            navigate(branch, tick.coerceIn(summary.start, summary.head))
        }
        fun checkpoint() = timeline.checkpoint()

        fun recommendedRainRegion() = timeline.recommendedRainRegion().coerceAtLeast(0)

        fun recommendedGroveRegion() = timeline.recommendedGroveRegion()

        fun shapePreview(kind: Intervention, region: Int): ShapeTarget? = timeline.preview(kind, region)?.let {
            ShapeTarget(it.sites.map { site -> site.project(world.terrain) },
                it.field?.project(world.terrain), it.bank?.project(world.terrain), it.blocked,
                // A firebreak is marked every other cell so the line reads without a ring on each one.
                it.cut.filterIndexed { index, _ -> index % 2 == 0 }.map { cell -> cell.project(world.terrain) }, it.cut.size / 2)
        }

        val warningTick get() = timeline.warningLocation()?.second
        val canReturnToWarning get() = timeline.warningLocation()?.let { (branch, tick) ->
            navigationAvailable && (branch != timeline.branchId || tick != timeline.state.tick)
        } == true

        suspend fun returnToWarning() {
            val (branch, tick) = timeline.warningLocation() ?: return
            navigate(branch, tick)
        }

        fun cancelShape() {
            if (navigationAvailable && timeline.cancelShape()) {
                feedback.value = feedback.value.copy(queued = timeline.queuedInterventions)
                updateTimeline()
            }
        }

        fun intervene(kind: Intervention, region: Int) {
            if (navigationAvailable && timeline.intervene(kind, region)) {
                feedback.value = feedback.value.copy(queued = timeline.queuedInterventions)
                updateTimeline()
            }
        }

        fun playTo(tick: Long) {
            if (navigationAvailable) clock.playTo(tick)
        }

        // ----- rules v12: the player's hand in a living world -----

        /** What [tool] would do at [ground] right now, without doing it. */
        fun placementCheck(tool: PlaceTool, ground: GroundPoint): PlacementCheck {
            val cell = GridCell(ground.x, ground.z)
            return when (tool) {
                PlaceTool.Rabbit -> timeline.creatureCheck(Species.Rabbit, cell)
                PlaceTool.Wolf -> timeline.creatureCheck(Species.Wolf, cell)
                PlaceTool.Deer -> timeline.creatureCheck(Species.Deer, cell)
                PlaceTool.Fox -> timeline.creatureCheck(Species.Fox, cell)
                PlaceTool.Tree -> timeline.plantCheck(PlantKind.Tree, cell)
                PlaceTool.Meadow -> timeline.plantCheck(PlantKind.Meadow, cell)
                PlaceTool.Inspect, PlaceTool.Eraser -> PlacementCheck.Ok
            }
        }

        /**
         * Sets down an animal or plants at [ground]. While paused the world takes one tick, so the new arrival appears
         * at once. Placing is only possible at the head of a history; the past is changed by starting a new one.
         */
        fun place(tool: PlaceTool, ground: GroundPoint): PlaceOutcome {
            if (!navigationAvailable) return PlaceOutcome.Busy
            if (!timeline.editsAllowed) return PlaceOutcome.ViewingPast
            val check = placementCheck(tool, ground)
            if (check != PlacementCheck.Ok) return PlaceOutcome.Rejected(check)
            val cell = GridCell(ground.x, ground.z)
            val done = when (tool) {
                PlaceTool.Rabbit -> timeline.place(Species.Rabbit, cell)
                PlaceTool.Wolf -> timeline.place(Species.Wolf, cell)
                PlaceTool.Deer -> timeline.place(Species.Deer, cell)
                PlaceTool.Fox -> timeline.place(Species.Fox, cell)
                PlaceTool.Tree -> timeline.plant(PlantKind.Tree, cell)
                PlaceTool.Meadow -> timeline.plant(PlantKind.Meadow, cell)
                PlaceTool.Inspect, PlaceTool.Eraser -> false
            }
            if (!done) return PlaceOutcome.Rejected(PlacementCheck.Occupied)
            afterHand()
            return PlaceOutcome.Placed
        }

        /** Lifts a creature out, or clears what the player planted at [ground]. */
        fun erase(pick: WorldPick): PlaceOutcome {
            if (!navigationAvailable) return PlaceOutcome.Busy
            if (!timeline.editsAllowed) return PlaceOutcome.ViewingPast
            val done = pick.creatureId?.let(timeline::remove)
                ?: pick.ground?.let { timeline.clearPlant(GridCell(it.x, it.z)) } ?: false
            if (!done) return PlaceOutcome.Rejected(PlacementCheck.Occupied)
            afterHand()
            return PlaceOutcome.Placed
        }

        private fun afterHand() {
            updateTimeline()
            clock.step()
        }

        /** Hollowmere's open petition, if the village is waiting for the Keeper. */
        fun openPetition() = timeline.openPetition()
        fun pendingEndorsement() = timeline.pendingEndorsement()

        fun endorse(option: PlanOption) {
            if (navigationAvailable && timeline.endorse(option)) {
                feedback.value = feedback.value.copy(queued = timeline.queuedInterventions)
                updateTimeline()
            }
        }

        fun cancelEndorsement() {
            if (navigationAvailable && timeline.cancelEndorsement()) {
                feedback.value = feedback.value.copy(queued = timeline.queuedInterventions)
                updateTimeline()
            }
        }

        fun villageChronicle(branch: Int) = timeline.villageChronicle(branch)

        suspend fun seek(tick: Long) = navigate(timeline.branchId, tick)

        fun markers(branch: Int) = timeline.interventionMarkers(branch, includePending = true)
        fun settlementEvents(branch: Int) = timeline.settlementEvents(branch)

        suspend fun seekEvent(tick: Long) = navigate(timeline.branchId, tick, inspectEvent = true)

        fun renameActive(name: String): Boolean {
            if (!navigationAvailable || !timeline.renameActive(name)) return false
            updateTimeline()
            return true
        }

        suspend fun switchBranch(id: Int) {
            timelineState.value.branches.firstOrNull { it.id == id }?.let { navigate(id, it.head) }
        }

        fun fork(name: String): Boolean {
            if (!navigationAvailable) return false
            clock.pause()
            if (!timeline.fork(name)) return false
            publishNavigation()
            return true
        }

        private fun advanceTimeline(): Boolean {
            if (!timeline.advance()) {
                clock.pause()
                timelineState.value = timelineStatus(failed = true)
                feedback.value = InterventionFeedback(timeline.queuedInterventions)
                return false
            }
            // A new petition is a turning point: hold time so the Keeper can answer it.
            if (timeline.openPetition()?.raisedAt == timeline.state.tick) clock.pause()
            val queued = timeline.queuedInterventions
            val applied = timeline.interventionResults
            if (applied.isNotEmpty() || queued != feedback.value.queued) {
                feedback.value = InterventionFeedback(queued, applied.ifEmpty { feedback.value.applied })
            }
            updateTimeline()
            return true
        }

        private suspend fun navigate(
            branch: Int, tick: Long, inspectEvent: Boolean = false, fromComparison: ComparisonView? = null,
        ) {
            if (fromComparison == null && !navigationAvailable || timelineState.value.seeking) return
            val request = (if (inspectEvent) timeline.eventReplay(branch, tick) else timeline.seek(branch, tick)) ?: return
            clock.pause()
            timelineState.value = timelineStatus(seeking = true)
            val started = TimeSource.Monotonic.markNow()
            try {
                val replayed = withContext(Dispatchers.Default) {
                    request.replay().also { if (inspectEvent) it.engine.advance() }
                }
                if (fromComparison != null && comparisonState.value !== fromComparison) {
                    timelineState.value = timelineStatus()
                    return
                }
                timeline.show(replayed)
                if (fromComparison != null) comparisonState.value = null
                publishNavigation()
                if (inspectEvent) {
                    feedback.value = feedback.value.copy(applied = replayed.engine.interventionResults)
                }
                println("BranchWorldTimeline seek branch=$branch tick=$tick hash=${timeline.state.canonicalHash()} seekMs=${started.elapsedNow().inWholeMilliseconds}")
            } catch (error: CancellationException) {
                timelineState.value = timelineStatus()
                throw error
            } catch (error: Exception) {
                println("BranchWorldTimeline seek failed: ${error::class.simpleName}")
                timelineState.value = timelineStatus(failed = true)
            }
        }

        private fun publishNavigation() {
            feedback.value = InterventionFeedback(timeline.queuedInterventions)
            timelineState.value = timelineStatus()
            clock.publish()
        }

        private fun updateTimeline() {
            timelineState.value = timelineStatus(failed = timelineState.value.failed)
        }

        private fun timelineStatus(seeking: Boolean = false, failed: Boolean = false) = TimelineView(
            timeline.branchId, timeline.state.tick, timeline.summaries(), timeline.editsAllowed && !seeking,
            timeline.canFork, timeline.commandLimitReached, seeking, failed,
        )

        fun openComparison() {
            val view = timelineState.value
            if (view.seeking || comparisonState.value != null) return
            val active = view.active
            val alternative = view.branches.inTreeOrder().firstOrNull { it.branch.id != active.id }?.branch ?: return
            clock.pause()
            if (active.parentId != null) selectComparison(active.parentId, active.id)
            else selectComparison(active.id, alternative.id)
        }

        fun closeComparison() {
            comparisonState.value = null
            display.value = clock.render.value
        }

        fun selectComparison(leftId: Int, rightId: Int) {
            if (leftId == rightId || timelineState.value.seeking) return
            val branches = timelineState.value.branches
            val left = branches.firstOrNull { it.id == leftId } ?: return
            val right = branches.firstOrNull { it.id == rightId } ?: return
            val range = commonTicks(left, right)
            val previous = comparisonState.value
            val current = previous?.tick ?: timeline.state.tick.takeIf { key.rules >= ShapingRulesVersion }
            comparisonState.value = ComparisonView(left, right, range,
                current?.takeIf { it in range } ?: range.last.takeUnless { range.isEmpty() },
                showRight = (previous?.visibleBranchId ?: timeline.branchId.takeIf { key.rules >= ShapingRulesVersion }) == rightId,
                echoEnabled = previous?.echoEnabled ?: true,
                sharedPast = timeline.sharedPast(leftId, rightId),
                moments = timeline.comparisonMoments(leftId, rightId, range),
                revision = ++comparisonRevision)
        }

        fun compareAt(tick: Long) {
            val prior = comparisonState.value ?: return
            if (tick !in prior.range || tick == prior.tick || timelineState.value.seeking) return
            comparisonState.value = prior.copy(tick = tick, pair = null, failed = false, event = null, outcomes = null,
                outcomeFailed = false, revision = ++comparisonRevision)
        }

        fun retryComparison() {
            val prior = comparisonState.value?.takeIf { it.failed } ?: return
            comparisonState.value = prior.copy(failed = false, revision = ++comparisonRevision)
        }

        suspend fun continueComparison() {
            val view = comparisonState.value ?: return
            val pair = view.pair ?: return
            navigate(view.visibleBranchId, pair.tick, fromComparison = view)
        }

        suspend fun revisitSharedPast() {
            val view = comparisonState.value ?: return
            val shared = view.sharedPast ?: return
            navigate(shared.branchId, shared.tick, fromComparison = view)
        }

        fun toggleEcho() {
            val prior = comparisonState.value?.takeUnless { timelineState.value.seeking } ?: return
            val next = prior.copy(echoEnabled = !prior.echoEnabled)
            comparisonState.value = next
            next.pair?.let { showPair(next, it) }
        }

        fun showComparisonSide(right: Boolean) {
            val prior = comparisonState.value?.takeUnless { timelineState.value.seeking } ?: return
            val next = prior.copy(showRight = right)
            comparisonState.value = next
            next.pair?.let { showPair(next, it) }
        }

        private fun showPair(view: ComparisonView, pair: TimelinePair) {
            val pose = if (view.showRight) pair.right.projection else pair.left.projection
            display.value = if (view.echoEnabled) pose else pose.copy(livingEcho = null)
        }

        suspend fun loadComparison(target: ComparisonTarget) {
            val prior = comparisonState.value
            if (prior?.target != target || prior.pair != null) return
            val request = timeline.compare(target.leftId, target.rightId, target.tick) ?: return
            try {
                val started = TimeSource.Monotonic.markNow()
                val pair = withContext(Dispatchers.Default) { request.replay() }
                val current = comparisonState.value?.takeIf { it.target == target } ?: return
                showPair(current, pair)
                comparisonState.value = current.copy(pair = pair)
                println("BranchWorldComparison tick=${pair.tick} left=${pair.left.hash} right=${pair.right.hash} compareMs=${started.elapsedNow().inWholeMilliseconds}")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                println("BranchWorldComparison failed: ${error::class.simpleName}")
                comparisonState.value?.takeIf { it.target == target }?.let { comparisonState.value = it.copy(failed = true) }
            }
        }

        fun inspectComparisonEvent(branchId: Int, marker: InterventionMarker) {
            val current = comparisonState.value ?: return
            if (timelineState.value.seeking) return
            val event = ComparisonEvent(branchId, marker.tick, marker.sequence, current.revision)
            if (current.event != event) {
                comparisonState.value = current.copy(event = event, outcomes = null, outcomeFailed = false)
            }
        }

        suspend fun loadComparisonEvent(event: ComparisonEvent) {
            val prior = comparisonState.value
            if (prior?.event != event || prior.outcomes != null) return
            val request = timeline.eventReplay(event.branchId, event.tick) ?: return
            val index = timeline.interventionMarkers(event.branchId, event.tick)
                .filter { it.tick == event.tick }.indexOfFirst { it.sequence == event.sequence }
            try {
                val outcomes = withContext(Dispatchers.Default) {
                    request.replay().engine.run {
                        advance()
                        listOfNotNull(interventionResults.getOrNull(index))
                    }
                }
                val current = comparisonState.value?.takeIf { it.event == event } ?: return
                comparisonState.value = current.copy(outcomes = outcomes)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                println("BranchWorldComparison event failed: ${error::class.simpleName}")
                comparisonState.value?.takeIf { it.event == event }?.let {
                    comparisonState.value = it.copy(outcomeFailed = true)
                }
            }
        }
    }
    data class Failed(val key: IslandSessionKey) : IslandLoad
}

/** What came of the player's hand in a living world. */
internal sealed interface PlaceOutcome {
    data object Placed : PlaceOutcome
    data class Rejected(val check: PlacementCheck) : PlaceOutcome
    /** The player is looking at the past; a new history must begin there first. */
    data object ViewingPast : PlaceOutcome
    data object Busy : PlaceOutcome
}

internal data class TimelineView(
    val branchId: Int,
    val tick: Long,
    val branches: List<BranchSummary>,
    val editsAllowed: Boolean,
    val canFork: Boolean,
    val commandLimitReached: Boolean,
    val seeking: Boolean,
    val failed: Boolean,
) {
    val active get() = branches.first { it.id == branchId }
}

internal data class ShapeTarget(
    val sites: List<GroundPoint>, val field: GroundPoint?, val bank: GroundPoint?,
    val blocked: id.branchworld.game.ecology.ShapeBlock?,
    /** Rules v11: marks along the firebreak line and its length in stretches (2-cell tiles). */
    val cutMarks: List<GroundPoint> = emptyList(),
    val cut: Int = 0,
) {
    val highlights get() = sites + listOfNotNull(field, bank) + cutMarks
}

internal data class InterventionFeedback(val queued: Int, val applied: List<InterventionResult> = emptyList())

internal data class ComparisonTarget(val leftId: Int, val rightId: Int, val tick: Long, val revision: Int)
internal data class ComparisonEvent(val branchId: Int, val tick: Long, val sequence: Long, val revision: Int)

internal data class ComparisonView(
    val left: BranchSummary,
    val right: BranchSummary,
    val range: LongRange,
    val tick: Long?,
    val showRight: Boolean = false,
    val pair: TimelinePair? = null,
    val failed: Boolean = false,
    val event: ComparisonEvent? = null,
    val outcomes: List<InterventionResult>? = null,
    val outcomeFailed: Boolean = false,
    val echoEnabled: Boolean = true,
    val sharedPast: SharedPast? = null,
    val moments: List<HistoryMoment> = emptyList(),
    val revision: Int,
) {
    val target get() = tick?.let { ComparisonTarget(left.id, right.id, it, revision) }
    val visibleBranchId get() = if (showRight) right.id else left.id
}

internal sealed interface WorldCodeImport {
    data class Open(val entry: AtlasEntry) : WorldCodeImport
    data class BadCode(val problem: WorldCodeProblem) : WorldCodeImport
    data object Occupied : WorldCodeImport
    data object Unwritable : WorldCodeImport
}

internal sealed interface SaveStatus {
    data object Busy : SaveStatus
    data object ConfirmReplace : SaveStatus
    data object Missing : SaveStatus
    data object Corrupt : SaveStatus
    data object Incompatible : SaveStatus
    data object Unreadable : SaveStatus
    data object Unwritable : SaveStatus
    data class Saved(val tick: Long) : SaveStatus
    data class Loaded(val tick: Long) : SaveStatus
    data class Restored(val key: IslandSessionKey, val tick: Long) : SaveStatus
}

private class InvalidSave(cause: Exception) : Exception(cause)
