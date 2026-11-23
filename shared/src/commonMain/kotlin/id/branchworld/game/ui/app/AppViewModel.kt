package id.branchworld.game.ui.app

import androidx.lifecycle.ViewModel
import id.branchworld.game.data.ProfileRepository
import id.branchworld.game.data.WorldCodeImport
import id.branchworld.game.data.WorldRepository
import id.branchworld.game.domain.IslandSessionKey
import id.branchworld.game.platform.storage.WorldSaveStore

/** Outlives configuration changes: the open worlds, the player's profile, and keeping both. */
internal class AppViewModel(private val store: WorldSaveStore?) : ViewModel() {
    val worlds = WorldRepository()
    val profiles = ProfileRepository(store, worlds)

    /** Worlds are kept between sessions only when the platform lends storage. */
    val keepsWorlds get() = store != null

    suspend fun open(key: IslandSessionKey) = worlds.open(key, store)

    /** Writes the open world and records it in the atlas; without storage, only records it. */
    suspend fun autosave() {
        val entry = store?.let { worlds.autosave(it) } ?: if (store == null) worlds.summary() else null
        entry?.let { visited -> profiles.update { it.visit(visited) } }
    }

    /** Opens a shared world code, when there is storage to keep the world in. */
    val importCode: (suspend (String) -> WorldCodeImport)? = store?.let { files -> { code -> worlds.importCode(code, files) } }
}
