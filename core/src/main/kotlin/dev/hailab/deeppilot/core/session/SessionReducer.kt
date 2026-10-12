package dev.hailab.deeppilot.core.session

import dev.hailab.deeppilot.core.protocol.BridgeJson
import dev.hailab.deeppilot.core.protocol.DeltaBody
import dev.hailab.deeppilot.core.protocol.MessageProjection
import dev.hailab.deeppilot.core.protocol.SessionEvent
import dev.hailab.deeppilot.core.protocol.ToolEndBody
import dev.hailab.deeppilot.core.protocol.ToolInfo
import dev.hailab.deeppilot.core.protocol.TurnEndBody
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement

/** One open conversation as the detail screen renders it. */
data class OpenSession(
    val sessionId: String,
    val messages: List<MessageProjection> = emptyList(),
    val hasMore: Boolean = false,
    val loadingHistory: Boolean = false,
    val turnRunning: Boolean = false,
    /** True once the first tail arrived. */
    val loaded: Boolean = false,
)

data class TurnCompletion(val seq: Long, val succeeded: Boolean, val body: String)

data class Reduction(val state: OpenSession, val turnCompletion: TurnCompletion? = null)

/**
 * Applies `s2c.session.event`, tail and history frames to [OpenSession]
 * (PROTOCOL.md §4). Ported from the iOS SessionFrameReducer so both apps
 * merge streaming text, thinking and tool results the same way.
 */
object SessionReducer {
    fun reduce(state: OpenSession, event: SessionEvent, nowMillis: Long = System.currentTimeMillis()): Reduction {
        var messages = state.messages.toMutableList()
        var running = state.turnRunning
        var completion: TurnCompletion? = null

        when (event.kind) {
            "turn.start" -> running = true

            "message.start", "message.final" -> {
                if (event.kind == "message.start") running = true
                decode(event.data, MessageProjection.serializer())?.let { incoming ->
                    var message = incoming
                    var skip = false
                    if (event.kind == "message.final") {
                        message = message.copy(streaming = false)
                        if (message.role != "user") {
                            messages.removeAll { it.role == "assistant" && it.streaming }
                            skip = message.text.isNullOrEmpty() && message.thinking.isNullOrEmpty()
                        }
                    }
                    if (!skip) upsert(messages, message)
                }
            }

            "message.delta" -> {
                running = true
                appendDelta(messages, event.seq, decode(event.data, DeltaBody.serializer())?.text, nowMillis)
            }

            "thinking.delta" -> {
                running = true
                appendThinking(messages, event.seq, decode(event.data, DeltaBody.serializer())?.text, nowMillis)
            }

            "tool.start" -> {
                running = true
                decode(event.data, MessageProjection.serializer())?.let {
                    closeStreaming(messages)
                    upsert(messages, it.copy(streaming = true))
                }
            }

            "tool.end" -> decode(event.data, ToolEndBody.serializer())?.let { mergeToolEnd(messages, it, event.seq) }

            "turn.end" -> {
                running = false
                closeStreaming(messages)
                val succeeded = decode(event.data, TurnEndBody.serializer())?.ok != false
                val body = messages.lastOrNull { it.role == "assistant" && !it.text.isNullOrEmpty() }?.text.orEmpty()
                completion = TurnCompletion(event.seq, succeeded, body)
            }
        }
        return Reduction(state.copy(messages = messages, turnRunning = running), completion)
    }

    /** Installs a `s2c.session.tail`, keeping rows outside its seq window. */
    fun mergeTail(existing: List<MessageProjection>, tail: List<MessageProjection>, oldestSeq: Long): List<MessageProjection> {
        val canonicalTail = canonical(tail)
        val newest = canonicalTail.lastOrNull()?.seq ?: return canonical(existing)
        val outside = canonical(existing).filter { it.seq < oldestSeq || it.seq > newest }
        return canonical(outside + canonicalTail)
    }

    /** Prepends a `s2c.history.page`; returns the merged list and how many rows were new. */
    fun mergeHistory(existing: List<MessageProjection>, page: List<MessageProjection>): Pair<List<MessageProjection>, Int> {
        val current = canonical(existing)
        val known = current.mapTo(HashSet()) { it.seq }
        val older = canonical(page).filter { it.seq !in known }
        return canonical(older + current) to older.size
    }

    /** Unique by seq (last write wins), ascending. */
    fun canonical(messages: List<MessageProjection>): List<MessageProjection> {
        val bySeq = LinkedHashMap<Long, MessageProjection>(messages.size)
        for (message in messages) bySeq[message.seq] = message
        return bySeq.values.sortedBy { it.seq }
    }

    private fun appendDelta(messages: MutableList<MessageProjection>, seq: Long, text: String?, now: Long) {
        if (text.isNullOrEmpty()) return
        val index = messages.indexOfLast { it.role == "assistant" && it.streaming }
        if (index >= 0) {
            val row = messages[index]
            if (index == messages.lastIndex && row.thinking.isNullOrEmpty()) {
                messages[index] = row.copy(text = row.text.orEmpty() + text)
                return
            }
            messages[index] = row.copy(streaming = false)
        }
        messages.add(MessageProjection(seq = seq, role = "assistant", text = text, streaming = true, ts = now))
    }

    private fun appendThinking(messages: MutableList<MessageProjection>, seq: Long, text: String?, now: Long) {
        if (text.isNullOrEmpty()) return
        val index = messages.indexOfLast { it.role == "assistant" && it.streaming }
        if (index >= 0) {
            val row = messages[index]
            if (index == messages.lastIndex && row.text.isNullOrEmpty()) {
                messages[index] = row.copy(thinking = row.thinking.orEmpty() + text)
                return
            }
            messages[index] = row.copy(streaming = false)
        }
        messages.add(MessageProjection(seq = seq, role = "assistant", thinking = text, streaming = true, ts = now))
    }

    private fun closeStreaming(messages: MutableList<MessageProjection>) {
        val index = messages.indexOfLast { it.role == "assistant" && it.streaming }
        if (index >= 0) messages[index] = messages[index].copy(streaming = false)
    }

    private fun mergeToolEnd(messages: MutableList<MessageProjection>, body: ToolEndBody, eventSeq: Long) {
        val callId = body.callId
        // Match by callId; the result's own seq never identifies the call row (§4).
        val index = if (!callId.isNullOrEmpty()) {
            messages.indexOfFirst { it.role == "tool" && it.tool?.callId == callId }
        } else {
            messages.indexOfFirst { it.role == "tool" && it.tool?.state?.lowercase() in setOf("running", "pending") }
        }
        val state = if (body.ok == false) "error" else "done"
        if (index < 0) {
            if (body.attachments.isNullOrEmpty()) return
            upsert(
                messages,
                MessageProjection(
                    seq = body.seq ?: eventSeq,
                    role = "tool",
                    tool = ToolInfo(name = "result", state = state, summary = body.summary.orEmpty(), callId = callId),
                    attachments = body.attachments,
                    ts = body.ts ?: 0,
                ),
            )
            return
        }
        val row = messages[index]
        val tool = (row.tool ?: ToolInfo(name = "tool", state = state)).let { t ->
            t.copy(state = state, summary = body.summary ?: t.summary)
        }
        messages[index] = row.copy(
            tool = tool,
            streaming = false,
            attachments = body.attachments ?: row.attachments,
        )
    }

    private fun upsert(messages: MutableList<MessageProjection>, message: MessageProjection) {
        val index = messages.indexOfFirst { it.seq == message.seq }
        when {
            index >= 0 -> {
                messages[index] = message
                if (messages.drop(index + 1).any { it.seq == message.seq }) {
                    val canonical = canonical(messages)
                    messages.clear()
                    messages.addAll(canonical)
                }
            }
            (messages.lastOrNull()?.seq ?: -1) <= message.seq -> messages.add(message)
            else -> {
                messages.add(message)
                messages.sortBy { it.seq }
            }
        }
    }

    private fun <T> decode(element: JsonElement?, serializer: KSerializer<T>): T? = element?.let {
        try {
            BridgeJson.decodeFromJsonElement(serializer, it)
        } catch (_: Exception) {
            null
        }
    }
}
