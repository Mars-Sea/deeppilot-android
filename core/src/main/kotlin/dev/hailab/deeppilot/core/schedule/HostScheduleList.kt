package dev.hailab.deeppilot.core.schedule

import dev.hailab.deeppilot.core.protocol.HostScheduleSnapshot
import dev.hailab.deeppilot.core.protocol.HostScheduleTask

/** Which schedule frames the UI uses. `schedulesHost` wins: on such hosts the old frames are only a mapping. */
enum class ScheduleMode {
    Unavailable, Legacy, Host;

    companion object {
        fun resolve(schedules: Boolean, schedulesHost: Boolean): ScheduleMode = when {
            schedulesHost -> Host
            schedules -> Legacy
            else -> Unavailable
        }
    }
}

/** List filter; on the wire `active` is omitted for all. */
enum class HostScheduleFilter(val active: Boolean?) {
    All(null), Active(true), Inactive(false);

    fun matches(task: HostScheduleTask): Boolean = active == null || task.isActive == active
}

/**
 * Paging state of one host-level task list (iOS `HostScheduleState`). Pages are
 * appended by id so a task the host inserted between two pages is not doubled.
 * When [sessionId] is set, only tasks with a fixed target on that session are kept,
 * the same rule the bridge uses when it maps the old per-session list.
 */
data class HostScheduleList(
    val tasks: List<HostScheduleTask> = emptyList(),
    val total: Int = 0,
    val nextOffset: Int = 0,
    val loaded: Boolean = false,
) {
    val canLoadMore: Boolean get() = nextOffset < total

    fun applying(page: HostScheduleSnapshot, reset: Boolean, requestedOffset: Int, sessionId: String? = null): HostScheduleList {
        val offset = page.offset ?: requestedOffset
        val count = page.shown ?: page.tasks.size
        val incoming = page.tasks.filter { sessionId == null || it.target.fixedSessionId == sessionId }
        val merged = if (reset) incoming else {
            val known = tasks.mapTo(HashSet()) { it.id }
            tasks + incoming.filter { known.add(it.id) }
        }
        return HostScheduleList(merged, page.total ?: (offset + count), offset + count, loaded = true)
    }

    /** A task the host returned after an edit or a run; a deleted one when [task] is null. */
    fun replacing(id: String, task: HostScheduleTask?): HostScheduleList {
        if (task == null) {
            if (tasks.none { it.id == id }) return this
            return copy(tasks = tasks.filterNot { it.id == id }, total = (total - 1).coerceAtLeast(0))
        }
        val index = tasks.indexOfFirst { it.id == id }
        return if (index < 0) this else copy(tasks = tasks.toMutableList().also { it[index] = task })
    }

    companion object {
        const val PAGE_SIZE = 50
    }
}
