package dev.hailab.deeppilot.core.session

import dev.hailab.deeppilot.core.protocol.SessionSummary

/** Sessions of one workspace in the drawer; [workspace] is null for uncategorized ones. */
data class SessionGroup(val workspace: String?, val sessions: List<SessionSummary>) {
    val id: String get() = workspace ?: "__unassigned__"
}

/** Drawer grouping, the same policy as iOS `SessionSidebarSnapshot`. */
object SessionGroups {
    fun make(sessions: List<SessionSummary>): List<SessionGroup> {
        val newestFirst = compareByDescending<SessionSummary> { it.lastActivityTs }.thenBy { it.id }
        return sessions
            .groupBy { it.workspaceLabel?.trim().orEmpty() }
            .map { (workspace, members) -> SessionGroup(workspace.ifEmpty { null }, members.sortedWith(newestFirst)) }
            .sortedWith(
                compareByDescending<SessionGroup> { it.sessions.first().lastActivityTs }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.workspace.orEmpty() },
            )
    }
}

/** Compact relative time as in the iOS session rows ("刚刚", "5分", "3小时", …). */
object RelativeTime {
    enum class Unit { JustNow, Minutes, Hours, Days, Weeks, Months, Years }

    fun of(timestampMs: Long, nowMs: Long): Pair<Unit, Long> {
        val seconds = ((nowMs - timestampMs) / 1000).coerceAtLeast(0)
        return when {
            seconds < 60 -> Unit.JustNow to 0
            seconds < 3_600 -> Unit.Minutes to seconds / 60
            seconds < 86_400 -> Unit.Hours to seconds / 3_600
            seconds < 604_800 -> Unit.Days to seconds / 86_400
            seconds < 2_592_000 -> Unit.Weeks to seconds / 604_800
            seconds < 31_536_000 -> Unit.Months to seconds / 2_592_000
            else -> Unit.Years to seconds / 31_536_000
        }
    }
}
