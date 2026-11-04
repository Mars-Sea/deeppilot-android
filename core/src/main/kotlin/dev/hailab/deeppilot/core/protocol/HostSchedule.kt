package dev.hailab.deeppilot.core.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// Host-level schedules (optional v2 extension, capability `schedulesHost`,
// DSH 0.2.2-alpha.3 and later). Tasks are no longer bound to one session; they
// carry a delivery target and a status. The bridge keeps serving the
// per-session frames on such hosts, but the app uses these when it can.
// timing and target are unions keyed by `kind`; they are kept flat here so an
// unknown kind still decodes and the row can show it instead of failing the page.

object HostTimingKind {
    const val AFTER = "after"
    const val AT = "at"
    const val EVERY = "every"
    const val DAILY = "daily"
    const val WEEKLY = "weekly"
    const val CRON = "cron"
    const val RRULE = "rrule"
}

object HostTargetKind {
    const val SESSION = "session"
    const val NEW_ONCE = "new_once"
    const val NEW = "new"
}

object HostTaskStatus {
    const val ACTIVE = "active"
    const val PAUSED = "paused"
    const val ENDED = "ended"
}

/** `{date, time, time_zone}` form of an `at` timing. */
@Serializable
data class HostScheduleDateTime(
    val date: String,
    val time: String,
    @SerialName("time_zone") val timeZone: String,
)

@Serializable
data class HostScheduleTiming(
    val kind: String,
    @SerialName("after_seconds") val afterSeconds: Double? = null,
    /** An RFC 3339 string or a [HostScheduleDateTime] object. */
    val at: JsonElement? = null,
    @SerialName("every_seconds") val everySeconds: Double? = null,
    val daily: ScheduleClock? = null,
    val weekly: ScheduleClock? = null,
    val cron: ScheduleCron? = null,
    val rrule: String? = null,
) {
    /** The RFC 3339 instant of an `at` timing, when it is the string form. */
    val atInstant: String? get() = (at as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    val atDateTime: HostScheduleDateTime?
        get() = (at as? JsonObject)?.let { runCatching { BridgeJson.decodeFromJsonElement(HostScheduleDateTime.serializer(), it) }.getOrNull() }

    companion object {
        fun after(seconds: Long) = HostScheduleTiming(HostTimingKind.AFTER, afterSeconds = seconds.toDouble())
        fun every(seconds: Long) = HostScheduleTiming(HostTimingKind.EVERY, everySeconds = seconds.toDouble())
        fun at(instant: String) = HostScheduleTiming(HostTimingKind.AT, at = JsonPrimitive(instant))
        fun daily(time: String, timeZone: String) = HostScheduleTiming(HostTimingKind.DAILY, daily = ScheduleClock(time, timeZone))
        fun weekly(time: String, timeZone: String, weekdays: List<Int>) =
            HostScheduleTiming(HostTimingKind.WEEKLY, weekly = ScheduleClock(time, timeZone, weekdays))
        fun cron(expression: String, timeZone: String) = HostScheduleTiming(HostTimingKind.CRON, cron = ScheduleCron(expression, timeZone))
        fun rrule(value: String) = HostScheduleTiming(HostTimingKind.RRULE, rrule = value)
    }
}

/** Workspace and agent preset for a target that creates sessions. */
@Serializable
data class HostScheduleCreateOptions(val cwd: String? = null, val agentPreset: String? = null)

@Serializable
data class HostScheduleTarget(
    val kind: String,
    /** Fixed session for `session`; the host-reserved one for `new_once`. */
    val sessionId: String? = null,
    val create: HostScheduleCreateOptions? = null,
) {
    /** Only a fixed-session target filters a session's own list (as the old-frame mapping does). */
    val fixedSessionId: String? get() = sessionId.takeIf { kind == HostTargetKind.SESSION }

    companion object {
        fun session(sessionId: String) = HostScheduleTarget(HostTargetKind.SESSION, sessionId)
        fun newOnce() = HostScheduleTarget(HostTargetKind.NEW_ONCE)
        fun new() = HostScheduleTarget(HostTargetKind.NEW)
    }
}

@Serializable
data class HostScheduleDelivery(
    val scheduledAt: String? = null,
    val deliveredAt: String? = null,
    val messageId: String? = null,
    val targetSessionId: String? = null,
)

/** `schedule.host.task`. Everything after status is optional; never rely on it. */
@Serializable
data class HostScheduleTask(
    val id: String,
    val title: String = "",
    val prompt: String = "",
    val timing: HostScheduleTiming,
    val target: HostScheduleTarget = HostScheduleTarget(HostTargetKind.NEW_ONCE),
    /** active | paused | ended; anything else is shown as unknown. */
    val status: String = "",
    val scheduledAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val executionActive: Boolean? = null,
    val deliveryFloorEndsAt: String? = null,
    val lastDelivery: HostScheduleDelivery? = null,
) {
    val isActive: Boolean get() = status == HostTaskStatus.ACTIVE
}

@Serializable
data class HostScheduleListRequest(val active: Boolean? = null, val limit: Int? = null, val offset: Int? = null)

/** Answer to `c2s.schedule.host.list`; [shown] is this page, [total] the filtered count. */
@Serializable
data class HostScheduleSnapshot(
    @Serializable(with = HostTaskListSerializer::class) val tasks: List<HostScheduleTask> = emptyList(),
    val shown: Int? = null,
    val total: Int? = null,
    val limit: Int? = null,
    val offset: Int? = null,
)

@Serializable
data class HostScheduleViewRequest(val id: String)

@Serializable
data class HostScheduleTaskPayload(val task: HostScheduleTask)

@Serializable
data class HostScheduleCreate(
    val clientRequestId: String,
    val title: String,
    val prompt: String,
    val timing: HostScheduleTiming,
    /** Omitted: the host default (create a session on first delivery and reuse it). */
    val target: HostScheduleTarget? = null,
)

/** At least one field; [active] false pauses, true resumes. */
@Serializable
data class HostSchedulePatch(
    val title: String? = null,
    val prompt: String? = null,
    val timing: HostScheduleTiming? = null,
    val target: HostScheduleTarget? = null,
    val active: Boolean? = null,
) {
    val isEmpty: Boolean get() = title == null && prompt == null && timing == null && target == null && active == null
}

@Serializable
data class HostScheduleEdit(
    val clientRequestId: String,
    val id: String,
    /** Must equal the last-read updatedAt; otherwise the host answers E_BUSY (conflict). */
    val expectedUpdatedAt: String? = null,
    /** update | delete */
    val action: String,
    val patch: HostSchedulePatch? = null,
)

@Serializable
data class HostScheduleRun(val clientRequestId: String, val id: String)

@Serializable
data class HostScheduleUpdated(
    val clientRequestId: String = "",
    val task: HostScheduleTask? = null,
    val deleted: Boolean = false,
    val replayed: Boolean = false,
)

@Serializable
data class HostScheduleLimits(val newSessionIntervalSeconds: Double? = null)

/** History on the host model: sessionId is optional and ignored. */
@Serializable
data class HostScheduleHistoryRequest(val id: String, val limit: Int = 20, val before: String? = null)

/** Drops rows that fail to decode instead of losing the whole page. */
object HostTaskListSerializer : SkippingListSerializer<HostScheduleTask>(HostScheduleTask.serializer())
