package dev.hailab.deeppilot.core

import dev.hailab.deeppilot.core.cache.ConversationRetention
import dev.hailab.deeppilot.core.protocol.MessageProjection
import dev.hailab.deeppilot.core.session.OpenSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationRetentionTest {
    @Test
    fun snapshotKeepsNewestRowsAndMarksOlderOnes() {
        val rows = (1L..(ConversationRetention.MESSAGES_PER_SESSION + 5L)).map { MessageProjection(it, "user", "m$it") }
        val cached = ConversationRetention.snapshot(OpenSession("s", rows.shuffled()))
        assertEquals(ConversationRetention.MESSAGES_PER_SESSION, cached.messages.size)
        assertEquals(6L, cached.messages.first().seq)
        assertTrue(cached.hasMore)
    }

    @Test
    fun snapshotNeverStoresStreamingRows() {
        val state = OpenSession("s", listOf(MessageProjection(1, "assistant", "partial", streaming = true)))
        val cached = ConversationRetention.snapshot(state)
        assertFalse(cached.messages.single().streaming)
        assertFalse(cached.hasMore)
    }

    @Test
    fun touchEvictsLeastRecentPastTheCap() {
        var order = emptyList<String>()
        var evicted = emptyList<String>()
        for (i in 0..ConversationRetention.SESSIONS) {
            val result = ConversationRetention.touch(order, "s$i")
            order = result.first
            evicted = result.second
        }
        assertEquals(listOf("s0"), evicted)
        assertEquals(ConversationRetention.SESSIONS, order.size)
        assertEquals("s1", ConversationRetention.touch(order, "s1").first.last())
    }
}
