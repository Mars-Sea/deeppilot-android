package dev.hailab.deeppilot.core.protocol

import kotlinx.serialization.Serializable

// Optional v2 extensions added after the first Android release: live assistant
// output, the running-session queue and plugin self-update. All of them are
// gated by welcome capabilities; older bridges never send these frames.

// ---------- live assistant output (assistantStream) ----------

/** `s2c.session.delta`: transient, no seq, not replayed, only for opened sessions. */
@Serializable
data class SessionDelta(
    val sessionId: String,
    val streamId: String,
    /** stream.start | message.delta | thinking.delta | stream.end */
    val kind: String,
    val text: String = "",
    @Serializable(with = LenientLongSerializer::class) val ts: Long = 0,
    val truncated: Boolean? = null,
    val outcome: StreamOutcome? = null,
)

/** How a stream ended: `committed` (with the host event) or `abandoned`. */
@Serializable
data class StreamOutcome(
    val kind: String,
    val eventType: String? = null,
    @Serializable(with = LenientLongSerializer::class) val seq: Long? = null,
)

// ---------- running-session queue (promptQueue) ----------

object SendMode {
    const val QUEUE = "queue"
    const val STEER = "steer"
}

@Serializable
data class QueueItem(
    val itemId: String,
    /** queued | steering */
    val placement: String = "queued",
    val text: String = "",
    val truncated: Boolean = false,
    val imageCount: Int = 0,
    val documentCount: Int = 0,
    val clientSendId: String? = null,
)

/** `s2c.session.queue`: the whole queue of one session; replaces what was there. */
@Serializable
data class SessionQueue(val sessionId: String, val items: List<QueueItem> = emptyList())

/** `c2s.session.queue.update`; [action] is remove or steer. */
@Serializable
data class QueueUpdate(val sessionId: String, val itemId: String, val action: String)

// ---------- plugin self-update (pluginUpdate) ----------

@Serializable
data class PluginUpdateJournal(
    val clientRequestId: String,
    val fromVersion: String = "",
    val toVersion: String = "",
    val phase: String = "",
    @Serializable(with = LenientLongSerializer::class) val startedAt: Long = 0,
    val message: String? = null,
)

@Serializable
data class PluginUpdateVersion(
    val version: String,
    val compatible: Boolean = false,
    val protocolVersion: Int? = null,
) {
    /** The host accepts it and it speaks this app's protocol; a missing declaration is not selectable. */
    val selectable: Boolean get() = compatible && protocolVersion == PROTOCOL_VERSION
}

@Serializable
data class PluginUpdateStatus(
    val currentVersion: String,
    val protocolVersion: Int,
    val latestVersion: String? = null,
    val updateAvailable: Boolean = false,
    val compatible: Boolean = false,
    val protocolCompatible: Boolean = false,
    val versions: List<PluginUpdateVersion> = emptyList(),
    @Serializable(with = LenientLongSerializer::class) val checkedAt: Long = 0,
    /** Set when the registry could not be read; offline is a normal state. */
    val reason: String? = null,
    val journal: PluginUpdateJournal? = null,
)

@Serializable
data class PluginUpdateApply(val version: String, val clientRequestId: String)

@Serializable
data class PluginUpdateAccepted(val clientRequestId: String = "", val accepted: Boolean = false)

@Serializable
data class PluginUpdateProgress(
    val clientRequestId: String,
    /** preparing | installing | verifying | restart-required | done | rolled-back | failed */
    val phase: String,
    val percent: Double? = null,
    val message: String? = null,
)
