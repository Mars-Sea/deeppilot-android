package dev.hailab.deeppilot.core.cache

import dev.hailab.deeppilot.core.protocol.MessageProjection
import dev.hailab.deeppilot.core.session.OpenSession
import dev.hailab.deeppilot.core.session.SessionReducer
import kotlinx.serialization.Serializable

/** The offline copy of one conversation (iOS `ConversationStore` rows plus `sessions_meta`). */
@Serializable
data class CachedConversation(
    val sessionId: String,
    val messages: List<MessageProjection>,
    /** Older rows exist on the host beyond the cached window. */
    val hasMore: Boolean,
)

/**
 * How much of each host is kept for offline reading. Sized like iOS so a
 * normal week of conversations still opens warm after a cold start.
 */
object ConversationRetention {
    const val MESSAGES_PER_SESSION = 1_000
    const val SESSIONS = 24

    /**
     * The cacheable copy of [state]: the newest rows only, with nothing left
     * "streaming" — a cached row is never live, and painting it as such
     * would show a turn that is not running.
     */
    fun snapshot(state: OpenSession): CachedConversation {
        val rows = SessionReducer.canonical(state.messages)
        val kept = rows.takeLast(MESSAGES_PER_SESSION)
        return CachedConversation(
            sessionId = state.sessionId,
            messages = kept.map { if (it.streaming) it.copy(streaming = false) else it },
            hasMore = state.hasMore || kept.size < rows.size,
        )
    }

    /** Moves [sessionId] to the most recent end; returns the new order and the ids pushed out. */
    fun touch(order: List<String>, sessionId: String): Pair<List<String>, List<String>> {
        val next = order.filterNot { it == sessionId } + sessionId
        val evicted = next.dropLast(SESSIONS)
        return next.takeLast(SESSIONS) to evicted
    }
}
