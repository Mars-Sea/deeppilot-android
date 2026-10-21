package dev.hailab.deeppilot.data

import android.content.Context
import dev.hailab.deeppilot.core.drafts.Draft
import dev.hailab.deeppilot.core.drafts.DraftBook
import dev.hailab.deeppilot.core.protocol.BridgeJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer

/**
 * Per-conversation composer drafts of the active host (iOS `ComposerDraftStore`).
 * Typing is written behind a short debounce; a clear commits at once so a
 * killed app never brings back a prompt that was already sent.
 */
class DraftStore(context: Context, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("deeppilot.drafts", Context.MODE_PRIVATE)
    private val serializer = ListSerializer(Draft.serializer())
    private val lock = Any()
    private var profileId: String? = null
    private var book = DraftBook()
    private var pendingWrite: Job? = null

    /** Points the store at [profileId]'s drafts, writing out what the previous host had buffered. */
    fun bind(profileId: String?) = synchronized(lock) {
        if (profileId == this.profileId) return
        flushLocked()
        this.profileId = profileId
        book = DraftBook(profileId?.let(::load).orEmpty())
    }

    fun text(sessionId: String): String = synchronized(lock) { book.text(sessionId) }

    fun set(sessionId: String, text: String) = synchronized(lock) {
        if (!book.set(sessionId, text, System.currentTimeMillis())) return
        pendingWrite?.cancel()
        pendingWrite = scope.launch {
            delay(WRITE_DEBOUNCE_MILLIS)
            synchronized(lock) { writeLocked(commit = false) }
        }
    }

    fun clear(sessionId: String) = synchronized(lock) {
        if (!book.clear(sessionId)) return
        pendingWrite?.cancel()
        writeLocked(commit = true)
    }

    fun flush() = synchronized(lock) { flushLocked() }

    /** Forgets a deleted host's drafts. */
    fun remove(profileId: String) = synchronized(lock) {
        if (profileId == this.profileId) {
            pendingWrite?.cancel()
            book = DraftBook()
        }
        prefs.edit().remove(profileId).commit()
    }

    private fun flushLocked() {
        if (pendingWrite?.isActive == true) {
            pendingWrite?.cancel()
            writeLocked(commit = true)
        }
    }

    private fun writeLocked(commit: Boolean) {
        val id = profileId ?: return
        val rows = book.snapshot()
        val editor = prefs.edit()
        if (rows.isEmpty()) editor.remove(id) else editor.putString(id, BridgeJson.encodeToString(serializer, rows))
        if (commit) editor.commit() else editor.apply()
    }

    private fun load(profileId: String): List<Draft> {
        val raw = prefs.getString(profileId, null) ?: return emptyList()
        return runCatching { BridgeJson.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }

    private companion object {
        const val WRITE_DEBOUNCE_MILLIS = 400L
    }
}
