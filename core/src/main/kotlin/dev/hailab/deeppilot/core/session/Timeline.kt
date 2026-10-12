package dev.hailab.deeppilot.core.session

import dev.hailab.deeppilot.core.protocol.MessageProjection

/** One row of the rendered conversation, the same grouping as iOS `ConversationListItem`. */
sealed interface TimelineItem {
    val key: String

    data class Message(val message: MessageProjection) : TimelineItem {
        override val key get() = "message-${message.seq}"
    }

    /** A run of thinking and tool rows between two real messages, shown as one collapsed row. */
    data class Activity(val entries: List<MessageProjection>) : TimelineItem {
        override val key get() = "activity-${entries.first().seq}"
    }

    /** Host-injected context, collapsed into one quiet row for the whole session. */
    data class SystemContext(val messages: List<MessageProjection>) : TimelineItem {
        override val key get() = "system-context"
    }
}

object Timeline {
    private val reminder = Regex("<system-reminder>[\\s\\S]*?</system-reminder>")
    private val openReminder = Regex("<system-reminder>[\\s\\S]*$")

    /** Text with host-injected `<system-reminder>` blocks removed; null when nothing is left. */
    fun displayText(message: MessageProjection): String? {
        var text = message.text ?: return null
        if (text.contains("<system-reminder>")) {
            text = text.replace(reminder, "").replace(openReminder, "")
        }
        return text.trim().ifEmpty { null }
    }

    /** True for an activity entry that shows reasoning rather than a tool. */
    fun isThinking(entry: MessageProjection): Boolean =
        entry.role != "tool" && !entry.thinking.isNullOrBlank()

    fun group(messages: List<MessageProjection>): List<TimelineItem> {
        val items = ArrayList<TimelineItem>()
        val pending = ArrayList<MessageProjection>()
        var contextIndex = -1

        fun flush() {
            if (pending.isEmpty()) return
            items += TimelineItem.Activity(pending.toList())
            pending.clear()
        }

        for (message in ToolFolding.fold(messages)) {
            val text = displayText(message)
            val hasAttachments = !message.attachments.isNullOrEmpty()
            if ((message.role == "user" || message.role == "system") && text == null && !hasAttachments) continue
            when {
                message.role == "system" -> {
                    flush()
                    if (contextIndex < 0) {
                        contextIndex = items.size
                        items += TimelineItem.SystemContext(listOf(message))
                    } else {
                        val current = items[contextIndex] as TimelineItem.SystemContext
                        items[contextIndex] = TimelineItem.SystemContext(current.messages + message)
                    }
                }
                message.role == "tool" && message.tool != null -> {
                    if (hasAttachments) {
                        // The collapsed activity shows text only; images get their own row.
                        flush()
                        items += TimelineItem.Message(message.copy(streaming = false))
                    } else {
                        pending += message
                    }
                }
                message.role == "assistant" -> {
                    if (!message.thinking.isNullOrBlank()) {
                        pending += message.copy(
                            text = null,
                            attachments = null,
                            streaming = if (text != null) false else message.streaming,
                        )
                    }
                    if (text != null || hasAttachments) {
                        flush()
                        items += TimelineItem.Message(message.copy(thinking = null))
                    }
                }
                else -> {
                    flush()
                    items += TimelineItem.Message(message)
                }
            }
        }
        flush()
        return items
    }
}
