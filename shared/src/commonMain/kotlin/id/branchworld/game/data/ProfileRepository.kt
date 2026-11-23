package id.branchworld.game.data

import id.branchworld.game.domain.IslandSessionKey
import id.branchworld.game.platform.storage.WorldSaveStore
import id.branchworld.simulation.core.LifeRulesVersion
import id.branchworld.simulation.core.WatershedWorldgenVersion
import id.branchworld.simulation.ecology.life.WorldStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull

/** The player's settings, atlas and marks, kept in `profile.json` beside their worlds. */
internal class ProfileRepository(private val store: WorldSaveStore?, private val worlds: WorldRepository) {
    private val current = MutableStateFlow<PlayerProfile?>(null)

    /** Null until [load] finishes. */
    val profile: StateFlow<PlayerProfile?> = current.asStateFlow()

    /**
     * Reads the profile once. An unreadable profile is set aside as `profile-unreadable.json` and a fresh one begins;
     * the original single save is imported into the atlas the first time, never rewritten. [beforePublish] sees the
     * profile before anyone observing [profile] does.
     */
    suspend fun load(beforePublish: (PlayerProfile) -> Unit = {}) {
        if (current.value != null) return
        var loaded = store?.let { files ->
            val bytes = try { files.readFile(ProfileFile, MaxProfileBytes) } catch (_: Exception) { null }
            PlayerProfileCodec.decode(bytes) ?: PlayerProfile().also {
                if (bytes != null) runCatching { files.writeFile(UnreadableProfileFile, bytes) }
            }
        } ?: PlayerProfile()
        if (store != null && !loaded.legacyImported) {
            val entry = worlds.importLegacy(store)
            loaded = (entry?.let(loaded::visit) ?: loaded).copy(legacyImported = true)
        }
        beforePublish(loaded)
        current.value = loaded
        store?.let { runCatching { it.writeFile(ProfileFile, PlayerProfileCodec.encode(loaded)) } }
    }

    fun update(change: (PlayerProfile) -> PlayerProfile) {
        current.value = current.value?.let(change)
    }

    /** Reserves the next living world's slot in the atlas under [name], and returns the world to open. */
    fun createWorld(seed: Long, start: WorldStart, name: String): IslandSessionKey {
        val number = current.value?.nextWorld ?: 1
        val slot = "world-$number.json"
        update {
            it.copy(nextWorld = number + 1).visit(AtlasEntry(slot, seed, WatershedWorldgenVersion, LifeRulesVersion, name = name, start = start.code))
        }
        return IslandSessionKey.living(seed, slot, start)
    }

    /** Writes every change after the first load, for as long as the caller keeps collecting. */
    suspend fun keepWritten() {
        val files = store ?: return
        current.filterNotNull().distinctUntilChanged().drop(1).conflate().collect { latest ->
            try {
                files.writeFile(ProfileFile, PlayerProfileCodec.encode(latest))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                println("BranchWorldProfile write failed: ${error::class.simpleName}")
            }
        }
    }

    private companion object {
        const val UnreadableProfileFile = "profile-unreadable.json"
    }
}
