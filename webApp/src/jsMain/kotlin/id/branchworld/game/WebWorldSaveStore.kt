package id.branchworld.game

import id.branchworld.game.save.SaveFileName
import id.branchworld.game.save.SaveFileTooLarge
import id.branchworld.game.save.WorldSaveStore
import kotlinx.browser.window
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.khronos.webgl.Int8Array
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.Date
import kotlin.random.Random

/**
 * World saves in the browser's IndexedDB, one record per file. Each write is a single readwrite
 * transaction and returns only after the transaction commits, so a save is either whole or absent,
 * and two open tabs cannot both create the same world.
 *
 * Closing or reloading a tab saves on the way out, but browsers drop IndexedDB transactions that are
 * still in flight when the page unloads. Every replacing write is therefore journaled synchronously to
 * localStorage first and the journal entry is cleared once IndexedDB commits. A journal entry that is
 * still present is always the newest complete copy of that file, so reads prefer it.
 */
internal class WebWorldSaveStore : WorldSaveStore {
    private val opening = Mutex()
    private var database: dynamic = null

    override suspend fun read(maxBytes: Int): ByteArray? = load(SlotKey, maxBytes)

    override suspend fun write(bytes: ByteArray, replace: Boolean): Boolean = store(SlotKey, bytes, replace)

    override suspend fun readFile(name: String, maxBytes: Int): ByteArray? {
        require(SaveFileName.matches(name))
        return load(WorldsPrefix + name, maxBytes)
    }

    override suspend fun writeFile(name: String, bytes: ByteArray): Boolean {
        require(SaveFileName.matches(name))
        return store(WorldsPrefix + name, bytes, replace = true)
    }

    override suspend fun createFile(name: String, bytes: ByteArray): Boolean {
        require(SaveFileName.matches(name))
        return try {
            store(WorldsPrefix + name, bytes, replace = false)
        } catch (error: SaveStorageUnavailable) {
            false
        }
    }

    private suspend fun load(key: String, maxBytes: Int): ByteArray? {
        journaled(key)?.let { pending ->
            if (pending.size > maxBytes) throw SaveFileTooLarge()
            return pending
        }
        val db = open()
        val done = CompletableDeferred<dynamic>()
        val request = db.transaction(StoreName, "readonly").objectStore(StoreName).get(key)
        request.onsuccess = { done.complete(request.result) }
        request.onerror = { done.completeExceptionally(SaveStorageUnavailable(request.error?.message as? String)) }
        val value = done.await() ?: return null
        val stored = value.unsafeCast<Int8Array>()
        if (stored.length > maxBytes) throw SaveFileTooLarge()
        return Int8Array(stored.buffer, stored.byteOffset, stored.length).unsafeCast<ByteArray>().copyOf()
    }

    private suspend fun store(key: String, bytes: ByteArray, replace: Boolean): Boolean {
        // Before anything can suspend: the page may be unloading while this call runs.
        val token = if (replace) journal(key, bytes) else null
        val committed = commit(key, bytes, replace)
        if (committed && token != null) clearJournal(key, token)
        return committed
    }

    private suspend fun commit(key: String, bytes: ByteArray, replace: Boolean): Boolean {
        val db = open()
        val done = CompletableDeferred<Boolean>()
        val transaction = db.transaction(StoreName, "readwrite")
        val records = transaction.objectStore(StoreName)
        // ByteArray is an Int8Array on Kotlin/JS; store a copy that the caller can no longer change.
        val value = bytes.copyOf().unsafeCast<Int8Array>()
        val request = if (replace) records.put(value, key) else records.add(value, key)
        request.onerror = { event: dynamic ->
            if (request.error?.name == "ConstraintError") {
                // The record already exists; keep the transaction from reporting a failure.
                event.preventDefault()
                done.complete(false)
            }
        }
        transaction.oncomplete = { done.complete(true) }
        transaction.onabort = {
            done.completeExceptionally(SaveStorageUnavailable(transaction.error?.message as? String))
        }
        return done.await()
    }

    /** Writes the pending copy and returns its token, or null when localStorage is full or unavailable. */
    @OptIn(ExperimentalEncodingApi::class)
    private fun journal(key: String, bytes: ByteArray): String? = try {
        val token = "${Date.now().toLong()}-${(Random.nextDouble() * 1e9).toLong()}"
        window.localStorage.setItem(JournalPrefix + key, token + TokenSeparator + Base64.encode(bytes))
        token
    } catch (_: Throwable) {
        // A full or blocked localStorage only loses the unload guarantee; IndexedDB still saves.
        null
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun journaled(key: String): ByteArray? = try {
        window.localStorage.getItem(JournalPrefix + key)?.substringAfter(TokenSeparator)?.let(Base64::decode)
    } catch (_: Throwable) {
        null
    }

    /** Clears the pending copy unless a newer write has replaced it in the meantime. */
    private fun clearJournal(key: String, token: String) {
        try {
            val entry = window.localStorage.getItem(JournalPrefix + key) ?: return
            if (entry.substringBefore(TokenSeparator) == token) window.localStorage.removeItem(JournalPrefix + key)
        } catch (_: Throwable) {
        }
    }

    private suspend fun open(): dynamic = opening.withLock {
        val existing = database
        if (existing != null) return@withLock existing
        val factory = window.asDynamic().indexedDB ?: throw SaveStorageUnavailable("IndexedDB is not available")
        val done = CompletableDeferred<dynamic>()
        val request = try {
            factory.open(DatabaseName, DatabaseVersion)
        } catch (error: Throwable) {
            throw SaveStorageUnavailable(error.message)
        }
        request.onupgradeneeded = {
            val db = request.result
            if (!(db.objectStoreNames.contains(StoreName) as Boolean)) db.createObjectStore(StoreName)
        }
        request.onsuccess = { done.complete(request.result) }
        request.onerror = { done.completeExceptionally(SaveStorageUnavailable(request.error?.message as? String)) }
        request.onblocked = { done.completeExceptionally(SaveStorageUnavailable("Another BranchWorld tab is upgrading saves")) }
        val db = done.await()
        // Another tab with a newer schema asks us to step aside; reopen on the next access.
        db.onversionchange = { db.close(); database = null }
        database = db
        db
    }

    private companion object {
        const val DatabaseName = "branchworld"
        const val DatabaseVersion = 1
        const val StoreName = "files"
        const val SlotKey = "island-v3.json"
        const val WorldsPrefix = "worlds/"
        const val JournalPrefix = "branchworld-pending:"
        const val TokenSeparator = '|'
    }
}

/** The browser refused storage (private browsing, quota, or a blocked upgrade). */
internal class SaveStorageUnavailable(message: String?) : Exception(message ?: "Browser storage is unavailable")
