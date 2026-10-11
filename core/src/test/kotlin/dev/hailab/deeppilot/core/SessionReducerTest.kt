package dev.hailab.deeppilot.core

import dev.hailab.deeppilot.core.protocol.BridgeJson
import dev.hailab.deeppilot.core.protocol.MessageProjection
import dev.hailab.deeppilot.core.protocol.SessionEvent
import dev.hailab.deeppilot.core.protocol.SessionSummary
import dev.hailab.deeppilot.core.protocol.SessionsDelta
import dev.hailab.deeppilot.core.session.NotificationDeduper
import dev.hailab.deeppilot.core.session.OpenSession
import dev.hailab.deeppilot.core.session.SessionList
import dev.hailab.deeppilot.core.session.SessionReducer
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionReducerTest {
    private fun json(text: String): JsonElement = BridgeJson.parseToJsonElement(text)

    private fun reduce(state: OpenSession, kind: String, seq: Long, data: String) =
        SessionReducer.reduce(state, SessionEvent("s", kind, seq, json(data)), nowMillis = 0)

    @Test
    fun deltasAccumulateIntoOneStreamingRowAndFinalReplacesIt() {
        var state = OpenSession("s")
        state = reduce(state, "turn.start", 1, "{}").state
        state = reduce(state, "message.delta", 2, """{"text":"Hel"}""").state
        state = reduce(state, "message.delta", 3, """{"text":"lo"}""").state
        assertEquals(1, state.messages.size)
        assertEquals("Hello", state.messages.single().text)
        assertTrue(state.messages.single().streaming)

        state = reduce(state, "message.final", 4, """{"seq":4,"role":"assistant","text":"Hello!","ts":5}""").state
        assertEquals(listOf(4L), state.messages.map { it.seq })
        assertEquals("Hello!", state.messages.single().text)
        assertFalse(state.messages.single().streaming)
    }

    @Test
    fun thinkingThenTextSplitsIntoTwoRows() {
        var state = OpenSession("s")
        state = reduce(state, "thinking.delta", 1, """{"text":"hmm"}""").state
        state = reduce(state, "message.delta", 2, """{"text":"answer"}""").state
        assertEquals(2, state.messages.size)
        assertEquals("hmm", state.messages[0].thinking)
        assertFalse(state.messages[0].streaming)
        assertEquals("answer", state.messages[1].text)
    }

    @Test
    fun toolEndMergesByCallIdNotBySeq() {
        var state = OpenSession("s")
        state = reduce(state, "tool.start", 10, """{"seq":10,"role":"tool","tool":{"name":"bash","state":"running","summary":"ls","callId":"a"},"ts":1}""").state
        state = reduce(state, "tool.start", 11, """{"seq":11,"role":"tool","tool":{"name":"bash","state":"running","summary":"pwd","callId":"b"},"ts":1}""").state
        state = reduce(state, "tool.end", 12, """{"ok":false,"callId":"b","seq":12,"summary":"failed"}""").state
        assertEquals(2, state.messages.size)
        assertEquals("running", state.messages[0].tool?.state)
        assertEquals("error", state.messages[1].tool?.state)
        assertEquals("failed", state.messages[1].tool?.summary)
    }

    @Test
    fun turnEndReportsCompletion() {
        var state = OpenSession("s", turnRunning = true)
        state = reduce(state, "message.final", 3, """{"seq":3,"role":"assistant","text":"done","ts":1}""").state
        val reduction = reduce(state, "turn.end", 4, """{"ok":true}""")
        assertFalse(reduction.state.turnRunning)
        assertNotNull(reduction.turnCompletion)
        assertEquals("done", reduction.turnCompletion!!.body)
    }

    @Test
    fun tailKeepsRowsOutsideItsWindowAndHistoryDedupes() {
        fun m(seq: Long) = MessageProjection(seq = seq, role = "user", text = "$seq")
        val merged = SessionReducer.mergeTail(listOf(m(1), m(5), m(9)), listOf(m(5), m(6)), oldestSeq = 5)
        assertEquals(listOf(1L, 5L, 6L, 9L), merged.map { it.seq })
        val (withHistory, inserted) = SessionReducer.mergeHistory(merged, listOf(m(0), m(1)))
        assertEquals(1, inserted)
        assertEquals(listOf(0L, 1L, 5L, 6L, 9L), withHistory.map { it.seq })
    }

    @Test
    fun sessionListAppliesDeltasNewestFirst() {
        fun s(id: String, ts: Long, title: String = id) = SessionSummary(id = id, title = title, lastActivityTs = ts)
        val list = SessionList.sorted(listOf(s("a", 1), s("b", 2), s("untitled", 3, title = "")))
        assertEquals(listOf("b", "a"), list.map { it.id })
        val next = SessionList.applyDelta(list, SessionsDelta(upserted = listOf(s("a", 5)), removedIds = listOf("b")))
        assertEquals(listOf("a"), next.map { it.id })
    }

    @Test
    fun deduperRemembersIds() {
        val deduper = NotificationDeduper(capacity = 2)
        assertTrue(deduper.firstSighting("n1"))
        assertFalse(deduper.firstSighting("n1"))
        deduper.firstSighting("n2")
        deduper.firstSighting("n3")
        assertTrue(deduper.firstSighting("n1"))
    }
}
