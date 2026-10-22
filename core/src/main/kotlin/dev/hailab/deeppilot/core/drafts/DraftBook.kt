package dev.hailab.deeppilot.core.drafts

import kotlinx.serialization.Serializable

@Serializable
data class Draft(val sessionId: String, val text: String, val updatedAt: Long)

/**
 * Composer drafts of one host, one per conversation (iOS `ComposerDraftStore`).
 * Bounded to the newest [MAX_DRAFTS] conversations and [MAX_CHARACTERS] each,
 * because a draft that is never sent is never deleted by anything else.
 */
class DraftBook(initial: List<Draft> = emptyList()) {
    /** Least recently touched first. */
    private val drafts = LinkedHashMap<String, Draft>()

    init {
        initial.filter { it.text.isNotEmpty() }.sortedBy { it.updatedAt }.forEach { drafts[it.sessionId] = it }
        trim()
    }

    fun text(sessionId: String): String = drafts[sessionId]?.text.orEmpty()

    /** Records [text]; returns whether anything changed. Empty text removes the draft. */
    fun set(sessionId: String, text: String, now: Long): Boolean {
        val clamped = text.take(MAX_CHARACTERS)
        if (clamped == text(sessionId)) return false
        drafts.remove(sessionId)
        if (clamped.isNotEmpty()) drafts[sessionId] = Draft(sessionId, clamped, now)
        trim()
        return true
    }

    fun clear(sessionId: String): Boolean = drafts.remove(sessionId) != null

    fun snapshot(): List<Draft> = drafts.values.toList()

    private fun trim() {
        while (drafts.size > MAX_DRAFTS) drafts.remove(drafts.keys.first())
    }

    companion object {
        const val MAX_DRAFTS = 50
        const val MAX_CHARACTERS = 4000
    }
}
