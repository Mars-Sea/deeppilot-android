package dev.hailab.deeppilot.core.session

import dev.hailab.deeppilot.core.protocol.MessageProjection
import dev.hailab.deeppilot.core.protocol.SessionDelta

/**
 * One live assistant attempt built from `s2c.session.delta` (iOS
 * `TransientAssistantStream`). Reasoning and answer segments keep their arrival
 * order; nothing here is persisted or cached.
 */
data class TransientStream(
    val id: String,
    val timestamp: Long,
    val segments: List<Segment> = emptyList(),
    val truncated: Boolean = false,
) {
    data class Segment(val kind: String, val text: String)

    val text: String get() = segments.filter { it.kind == MESSAGE }.joinToString("") { it.text }
    val thinking: String get() = segments.filter { it.kind == THINKING }.joinToString("") { it.text }

    fun appending(text: String, kind: String): TransientStream {
        if (text.isEmpty()) return this
        val last = segments.lastOrNull()
        val next = if (last?.kind == kind) segments.dropLast(1) + last.copy(text = last.text + text) else segments + Segment(kind, text)
        return copy(segments = next)
    }

    /**
     * Rows for the timeline, one per segment. Their seq is only a local index for
     * this separate list and never reaches the session mirror or the cursor.
     */
    val presentations: List<MessageProjection>
        get() = segments.mapIndexed { index, segment ->
            MessageProjection(
                seq = index.toLong(),
                role = "assistant",
                text = segment.text.takeIf { segment.kind == MESSAGE },
                thinking = segment.text.takeIf { segment.kind == THINKING },
                streaming = index == segments.lastIndex,
                ts = timestamp,
                truncated = truncated,
            )
        }

    companion object {
        const val START = "stream.start"
        const val MESSAGE = "message.delta"
        const val THINKING = "thinking.delta"
        const val END = "stream.end"
    }
}

/**
 * Live streams of one opened session. The formal `message.final` replaces a
 * stream; `turn.end`, a cancel, a lost connection and an abandoned end drop them.
 * Retired ids stay remembered so a late delta cannot bring a row back.
 */
class TransientStreams {
    var streams: List<TransientStream> = emptyList()
        private set
    private val retired = HashSet<String>()

    /** Returns true when [streams] changed. */
    fun receive(delta: SessionDelta): Boolean {
        if (delta.streamId.isEmpty()) return false
        if (delta.kind == TransientStream.END) return finish(delta.streamId)
        if (delta.streamId in retired) return false
        if (delta.kind !in LIVE_KINDS) return false
        if (delta.kind != TransientStream.START && delta.text.isEmpty()) return false
        val index = streams.indexOfFirst { it.id == delta.streamId }
        var stream = if (index >= 0) streams[index] else TransientStream(delta.streamId, delta.ts)
        if (delta.kind != TransientStream.START) stream = stream.appending(delta.text, delta.kind)
        if (delta.truncated == true) stream = stream.copy(truncated = true)
        streams = if (index >= 0) streams.toMutableList().also { it[index] = stream } else streams + stream
        return true
    }

    /** Retires one stream, or all of them when [streamId] is null. Returns true when rows went away. */
    fun finish(streamId: String? = null): Boolean {
        if (streamId != null) {
            retired += streamId
            val before = streams.size
            streams = streams.filterNot { it.id == streamId }
            return streams.size != before
        }
        if (streams.isEmpty()) return false
        retired += streams.map { it.id }
        streams = emptyList()
        return true
    }

    /** Connection loss or a new session: forget everything, including retired ids. */
    fun clear(): Boolean {
        retired.clear()
        if (streams.isEmpty()) return false
        streams = emptyList()
        return true
    }

    private companion object {
        val LIVE_KINDS = setOf(TransientStream.START, TransientStream.MESSAGE, TransientStream.THINKING)
    }
}
