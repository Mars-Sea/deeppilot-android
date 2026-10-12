package dev.hailab.deeppilot.core.session

import dev.hailab.deeppilot.core.protocol.MessageProjection

/**
 * History and tail projections carry a tool call and its result as two rows
 * (the call stays `running`). For display, fold each result into its call:
 * matched by `callId`, else the oldest call still open (§4), the same pairing
 * the live `tool.end` merge uses. A result with no open call stays a row.
 */
object ToolFolding {
    fun fold(messages: List<MessageProjection>): List<MessageProjection> {
        if (messages.none { it.isResult() }) return messages
        val out = ArrayList<MessageProjection>(messages.size)
        val open = ArrayList<Int>()
        for (message in messages) {
            val tool = message.tool
            if (message.role != "tool" || tool == null) {
                out += message
                continue
            }
            if (!message.isResult()) {
                if (tool.state == "running") open += out.size
                out += message
                continue
            }
            val slot = open.firstOrNull { !tool.callId.isNullOrEmpty() && out[it].tool?.callId == tool.callId }
                ?: open.firstOrNull()
            if (slot == null) {
                out += message
                continue
            }
            open.remove(slot)
            val call = out[slot]
            val callTool = call.tool ?: tool
            out[slot] = call.copy(
                streaming = false,
                tool = callTool.copy(
                    state = if (tool.state == "error") "error" else "done",
                    summary = callTool.summary.ifBlank { tool.summary },
                ),
                text = call.text?.takeIf { it.isNotBlank() } ?: message.text?.takeIf { it.isNotBlank() } ?: tool.summary,
            )
        }
        return out
    }

    private fun MessageProjection.isResult(): Boolean {
        val name = tool?.name?.lowercase() ?: return false
        return role == "tool" && (name == "result" || name == "tool_result")
    }
}
