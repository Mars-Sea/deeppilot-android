package dev.hailab.deeppilot.core

import dev.hailab.deeppilot.core.protocol.MessageAttachment
import dev.hailab.deeppilot.core.protocol.MessageProjection
import dev.hailab.deeppilot.core.protocol.SessionSummary
import dev.hailab.deeppilot.core.protocol.ToolInfo
import dev.hailab.deeppilot.core.session.RelativeTime
import dev.hailab.deeppilot.core.session.SessionGroups
import dev.hailab.deeppilot.core.session.Timeline
import dev.hailab.deeppilot.core.session.TimelineItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineTest {
    private fun user(seq: Long, text: String) = MessageProjection(seq = seq, role = "user", text = text)
    private fun tool(seq: Long, name: String, state: String = "running") =
        MessageProjection(seq = seq, role = "tool", tool = ToolInfo(name = name, state = state))

    @Test
    fun thinkingAndToolsBetweenMessagesBecomeOneActivity() {
        val items = Timeline.group(
            listOf(
                user(1, "hi"),
                MessageProjection(seq = 2, role = "assistant", thinking = "plan"),
                tool(3, "bash"),
                tool(4, "result", "ok"),
                MessageProjection(seq = 5, role = "assistant", text = "done", thinking = "check"),
            ),
        )
        assertEquals(3, items.size)
        val activity = items[1] as TimelineItem.Activity
        // plan, folded bash, and the thinking split off the final answer
        assertEquals(listOf(2L, 3L, 5L), activity.entries.map { it.seq })
        assertEquals("done", activity.entries[1].tool?.state)
        val answer = (items[2] as TimelineItem.Message).message
        assertEquals("done", answer.text)
        assertNull(answer.thinking)
    }

    @Test
    fun systemRowsCollapseIntoOneContextRowAndRemindersAreHidden() {
        val items = Timeline.group(
            listOf(
                MessageProjection(seq = 1, role = "system", text = "job a"),
                user(2, "<system-reminder>x</system-reminder>"),
                user(3, "real<system-reminder>x</system-reminder>"),
                MessageProjection(seq = 4, role = "system", text = "job b"),
            ),
        )
        assertEquals(2, items.size)
        assertEquals(2, (items[0] as TimelineItem.SystemContext).messages.size)
        assertEquals("real", Timeline.displayText((items[1] as TimelineItem.Message).message))
    }

    @Test
    fun toolResultWithImagesStaysItsOwnRow() {
        val shot = MessageProjection(
            seq = 2,
            role = "tool",
            tool = ToolInfo(name = "browser", state = "ok"),
            attachments = listOf(MessageAttachment(kind = "image")),
        )
        val items = Timeline.group(listOf(tool(1, "read"), shot))
        assertTrue(items[0] is TimelineItem.Activity)
        assertTrue(items[1] is TimelineItem.Message)
    }

    @Test
    fun sessionsGroupByWorkspaceNewestFirst() {
        val groups = SessionGroups.make(
            listOf(
                SessionSummary(id = "a", workspaceLabel = "web", lastActivityTs = 10),
                SessionSummary(id = "b", workspaceLabel = " ", lastActivityTs = 30),
                SessionSummary(id = "c", workspaceLabel = "web", lastActivityTs = 20),
            ),
        )
        assertEquals(listOf(null, "web"), groups.map { it.workspace })
        assertEquals(listOf("c", "a"), groups[1].sessions.map { it.id })
    }

    @Test
    fun relativeTimeBuckets() {
        assertEquals(RelativeTime.Unit.JustNow to 0L, RelativeTime.of(0, 59_000))
        assertEquals(RelativeTime.Unit.Minutes to 5L, RelativeTime.of(0, 300_000))
        assertEquals(RelativeTime.Unit.Days to 2L, RelativeTime.of(0, 2 * 86_400_000L))
    }
}
