package id.branchworld.game.data

import id.branchworld.game.domain.IslandGallery
import id.branchworld.game.domain.IslandSessionKey
import id.branchworld.game.domain.session.IslandLoad
import id.branchworld.game.platform.storage.WorldSaveStore
import id.branchworld.simulation.core.canonicalHash
import id.branchworld.simulation.ecology.canonicalHash
import id.branchworld.simulation.legacy.canonicalHash
import id.branchworld.simulation.save.IncompatibleSave
import id.branchworld.simulation.save.TimelineSaveCodec
import id.branchworld.simulation.save.WorldCode
import id.branchworld.simulation.save.WorldCodeError
import id.branchworld.simulation.save.WorldCodeProblem
import id.branchworld.simulation.terrain.IslandGenerator
import id.branchworld.simulation.terrain.TerrainKey
import id.branchworld.simulation.timeline.Timeline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.time.TimeSource

/**
 * Opens, caches and keeps the player's worlds. Each world lives in its own named slot of a [WorldSaveStore]; the
 * repository restores it from there or generates a new island, and writes its whole history back on autosave.
 */
internal class WorldRepository {
    private val load = MutableStateFlow<IslandLoad>(IslandLoad.Empty)
    val state = load.asStateFlow()
    private val storage = MutableStateFlow<SaveStatus?>(null)
    val saveStatus = storage.asStateFlow()
    private val recent = linkedMapOf<IslandSessionKey, IslandLoad.Ready>()

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
                    start = key.start ?: id.branchworld.simulation.ecology.life.WorldStart.Empty)
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
        if (key.slot in unreadable) return null
        val checkpoint = island.checkpoint()
        val active = checkpoint.branches.first { it.id == checkpoint.activeBranchId }
        return try {
            val started = TimeSource.Monotonic.markNow()
            val bytes = withContext(Dispatchers.Default) { TimelineSaveCodec.encode(checkpoint, island.sharedOrigin) }
            if (!store.writeFile(key.slot, bytes)) return null
            println("BranchWorldSave autosaved slot=${key.slot} branch=${active.id} tick=${active.head.tick} branches=${checkpoint.branches.size} bytes=${bytes.size} saveMs=${started.elapsedNow().inWholeMilliseconds}")
            autosaved.value = active.head.tick
            key.atlasEntry(active.head.tick, checkpoint.branches.size, active.name)
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
        return key.atlasEntry(view.active.head, view.branches.size, view.active.name)
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
            WorldCodeImport.Open(key.atlasEntry(view.active.head, view.branches.size, view.active.name, story = key.story?.code ?: 0))
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
            key.atlasEntry(active.head.tick, checkpoint.branches.size, active.name)
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
}

/** The atlas line for a world. Shared-code imports carry only their story; other worlds record their chapter first. */
private fun IslandSessionKey.atlasEntry(
    head: Long, branches: Int, branchName: String, story: Int = chapter?.code ?: (this.story?.code ?: 0),
) = AtlasEntry(slot, terrain.seed, terrain.worldgenVersion, rules, story, head, branches, branchName, start = start?.code ?: 0)

/** What came of opening a shared world code. */
internal sealed interface WorldCodeImport {
    data class Open(val entry: AtlasEntry) : WorldCodeImport
    data class BadCode(val problem: WorldCodeProblem) : WorldCodeImport
    data object Occupied : WorldCodeImport
    data object Unwritable : WorldCodeImport
}

/** Why the open world could not be read or kept. */
internal sealed interface SaveStatus {
    data object Corrupt : SaveStatus
    data object Incompatible : SaveStatus
    data object Unreadable : SaveStatus
    data object Unwritable : SaveStatus
    data class Loaded(val tick: Long) : SaveStatus
}
