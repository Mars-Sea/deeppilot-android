package dev.hailab.deeppilot.core.update

import dev.hailab.deeppilot.core.protocol.PROTOCOL_VERSION
import dev.hailab.deeppilot.core.protocol.PluginUpdateJournal
import dev.hailab.deeppilot.core.protocol.PluginUpdateProgress
import dev.hailab.deeppilot.core.protocol.PluginUpdateStatus

/**
 * Plugin self-update bookkeeping (iOS `PluginUpdateController`), without the
 * transport. A dropped connection is expected while the host swaps the plugin,
 * so an unanswered apply never counts as failed: only the host's journal, read
 * back with `c2s.plugin.update.check` after reconnecting, says how it ended.
 */
data class PluginUpdateState(
    val status: PluginUpdateStatus? = null,
    val progress: PluginUpdateProgress? = null,
    val requestId: String? = null,
    val applying: Boolean = false,
    /** True once a check answered on the current connection. */
    val fresh: Boolean = false,
    val error: Error? = null,
    /** Bumped by every progress frame, so a check that started earlier cannot roll it back. */
    val revision: Int = 0,
) {
    enum class Error { CheckFailed, ApplyRejected, ApplyUnknown }

    fun selectable(version: String): Boolean =
        status?.currentVersion != version && status?.versions?.any { it.version == version && it.selectable } == true

    /** A check answered. [startedAtRevision] is [revision] when the check was sent. */
    fun checked(value: PluginUpdateStatus, startedAtRevision: Int): PluginUpdateState {
        if (value.protocolVersion != PROTOCOL_VERSION) return failedCheck()
        var next = copy(status = value, fresh = true, error = null)
        if (startedAtRevision != revision) return next
        val journal: PluginUpdateJournal? = value.journal
        if (journal != null) {
            if (applying && requestId != null && journal.clientRequestId != requestId) return next.copy(error = Error.ApplyUnknown)
            val current = progress
            if (journal.clientRequestId == requestId && current != null && isTerminal(current.phase) && !isTerminal(journal.phase)) return next
            next = next.copy(
                requestId = journal.clientRequestId,
                progress = PluginUpdateProgress(journal.clientRequestId, journal.phase, null, journal.message),
                applying = !isTerminal(journal.phase),
            )
        } else if (applying) {
            next = next.copy(progress = PluginUpdateProgress(requestId.orEmpty(), UNKNOWN, null, null), error = Error.ApplyUnknown)
        }
        return next
    }

    fun failedCheck(): PluginUpdateState = copy(error = Error.CheckFailed, fresh = false)

    fun startedApply(requestId: String): PluginUpdateState = copy(
        requestId = requestId,
        applying = true,
        error = null,
        revision = revision + 1,
        progress = PluginUpdateProgress(requestId, "preparing"),
    )

    /**
     * The apply request failed. A clear refusal releases the lock; a timeout or a
     * drop may still have been accepted, so the lock stays until a check settles it.
     */
    fun applyFailed(requestId: String, rejected: Boolean): PluginUpdateState {
        if (this.requestId != requestId || isTerminal(progress?.phase.orEmpty())) return this
        return if (rejected) copy(applying = false, progress = null, requestId = null, error = Error.ApplyRejected)
        else copy(progress = PluginUpdateProgress(requestId, UNKNOWN), error = Error.ApplyUnknown)
    }

    fun received(value: PluginUpdateProgress): PluginUpdateState {
        if (value.clientRequestId != requestId) return this
        val current = progress
        if (current != null && isTerminal(current.phase) && !isTerminal(value.phase)) return this
        val terminal = isTerminal(value.phase)
        return copy(
            progress = value,
            revision = revision + 1,
            applying = if (terminal) false else applying,
            error = if (terminal) null else error,
        )
    }

    /** The connection or its permissions changed: an earlier check no longer allows an apply. */
    fun reconnected(): PluginUpdateState = copy(fresh = false)

    companion object {
        const val UNKNOWN = "unknown"
        private val TERMINAL = setOf("done", "rolled-back", "failed", "restart-required")
        fun isTerminal(phase: String): Boolean = phase in TERMINAL
        val KNOWN_PHASES = listOf("preparing", "installing", "verifying", "restart-required", "done", "rolled-back", "failed")
    }
}
