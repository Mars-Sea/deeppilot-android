package dev.hailab.deeppilot.core.session

import dev.hailab.deeppilot.core.protocol.SessionSummary
import dev.hailab.deeppilot.core.protocol.SessionsDelta

/** Session list mirror: snapshot replaces, delta upserts and removes (PROTOCOL.md §3). */
object SessionList {
    fun applyDelta(current: List<SessionSummary>, delta: SessionsDelta): List<SessionSummary> {
        val byId = LinkedHashMap<String, SessionSummary>()
        current.forEach { byId[it.id] = it }
        delta.removedIds.forEach { byId.remove(it) }
        delta.upserted.forEach { byId[it.id] = it }
        return sorted(byId.values)
    }

    /**
     * Newest activity first. Sessions that never got a title stay out of the
     * list (PRD M-12) unless they are running or waiting on the user.
     */
    fun sorted(sessions: Collection<SessionSummary>): List<SessionSummary> =
        sessions
            .filter { !it.archived }
            .filter { it.title.isNotBlank() || it.status == "running" || it.pendingApproval || it.pendingQuestion }
            .sortedByDescending { it.lastActivityTs }
}

/** Recently shown notification ids; the same id arrives live, on replay and by push (§6). */
class NotificationDeduper(private val capacity: Int = 256) {
    private val seen = object : LinkedHashMap<String, Unit>(capacity, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?) = size > capacity
    }

    /** True the first time [notificationId] is offered. */
    @Synchronized
    fun firstSighting(notificationId: String): Boolean {
        if (seen.containsKey(notificationId)) return false
        seen[notificationId] = Unit
        return true
    }
}
