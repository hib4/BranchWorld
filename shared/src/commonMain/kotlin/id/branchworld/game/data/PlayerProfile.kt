package id.branchworld.game.data

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal const val ProfileFile = "profile.json"
internal const val ProfileVersion = 1
internal const val MaxProfileBytes = 512 * 1024
private const val MaxBookmarks = 64
private const val LivingRules = 12

/** Player-facing preferences. Presentation only: none of these touch authoritative simulation state. */
@Serializable
internal data class GameSettings(
    val worldSounds: Boolean = true,
    val interfaceSounds: Boolean = true,
    val reducedMotion: Boolean = false,
    /** Text scale in percent: 100, 115 or 130. */
    val textScale: Int = 100,
    val tickTools: Boolean = false,
    /** Developer mode: the crafted stories and earlier sandbox, hidden from players since living worlds. */
    val earlierStories: Boolean = false,
)

/** A history the player chose to close a chapter with. Outcome numbers come from that branch's real head state. */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
internal data class ChapterRecord(
    val chapter: Int,
    val slot: String,
    val branchId: Int,
    val branchName: String,
    val tick: Long,
    val people: Int,
    val departed: Int,
    val deer: Int,
    val homes: Int,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val burned: Int? = null,
)

/** One island in the World Atlas: its own save slot and a short summary of its current history. */
@Serializable
internal data class AtlasEntry(
    val slot: String,
    val seed: Long,
    val worldgen: Int,
    val rules: Int,
    val chapter: Int = 0,
    val tick: Long = 0,
    val branches: Int = 1,
    val branchName: String = "",
    /** Larger is more recent. A counter, not a wall-clock time. */
    val order: Long = 0,
    /** The name the player gave a living world. */
    val name: String = "",
    /** How a living world began: a [id.branchworld.simulation.ecology.life.WorldStart] code, 0 for other islands. */
    val start: Int = 0,
)

@Serializable
internal data class Bookmark(val slot: String, val branchId: Int, val tick: Long, val label: String)

@Serializable
internal data class PlayerProfile(
    val version: Int = ProfileVersion,
    val settings: GameSettings = GameSettings(),
    val chapters: List<ChapterRecord> = emptyList(),
    val atlas: List<AtlasEntry> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val hints: List<String> = emptyList(),
    val legacyImported: Boolean = false,
    /** The number the next living world's slot takes. */
    val nextWorld: Int = 1,
) {
    val lastPlayed: AtlasEntry? get() = atlas.maxByOrNull { it.order }

    fun closed(chapter: Int) = chapters.any { it.chapter == chapter }

    fun visit(entry: AtlasEntry): PlayerProfile {
        val order = (atlas.maxOfOrNull { it.order } ?: 0) + 1
        // An autosave summary carries no name; the one the player gave is kept.
        val name = entry.name.ifEmpty { atlas.firstOrNull { it.slot == entry.slot }?.name.orEmpty() }
        return copy(atlas = atlas.filterNot { it.slot == entry.slot } + entry.copy(order = order, name = name))
    }

    fun record(chapter: ChapterRecord) = copy(chapters = chapters.filterNot { it.chapter == chapter.chapter } + chapter)

    fun mark(bookmark: Bookmark): PlayerProfile {
        if (bookmarks.any { it.slot == bookmark.slot && it.branchId == bookmark.branchId && it.tick == bookmark.tick }) return this
        return copy(bookmarks = (bookmarks + bookmark).takeLast(MaxBookmarks))
    }

    fun unmark(bookmark: Bookmark) = copy(bookmarks = bookmarks - bookmark)

    fun seen(hint: String) = hint in hints

    fun see(hint: String) = if (seen(hint)) this else copy(hints = hints + hint)

    /** Living worlds the player has made, most recent first. */
    val worlds: List<AtlasEntry> get() = atlas.filter { it.rules >= LivingRules }.sortedByDescending { it.order }
}

internal object PlayerProfileCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(profile: PlayerProfile): ByteArray = json.encodeToString(profile).encodeToByteArray()

    /** A missing, unreadable or newer profile starts fresh; histories live in their own slots and are unaffected. */
    fun decode(bytes: ByteArray?): PlayerProfile? = try {
        bytes?.let { json.decodeFromString<PlayerProfile>(it.decodeToString()) }?.takeIf { it.version <= ProfileVersion }
    } catch (_: Exception) {
        null
    }
}
