package dev.hailab.deeppilot.core

import dev.hailab.deeppilot.core.protocol.Envelope
import dev.hailab.deeppilot.core.protocol.SessionTail
import dev.hailab.deeppilot.core.protocol.SessionsDelta
import org.junit.Assert.assertEquals
import org.junit.Test

class LenientDecodingTest {
    @Test
    fun fractionalNumbersDecodeAsLongs() {
        val envelope = Envelope.parse(
            """{"v":2,"type":"s2c.session.tail","seq":12,"ts":1756000000000.75,"payload":{"sessionId":"s1","oldestSeq":3,"hasMore":false,
            "messages":[{"seq":3,"role":"user","text":"hi","ts":1.756000000001E12},{"seq":4.0,"role":"assistant","text":"yo","ts":1756000000002}]}}""",
        )!!
        assertEquals(1756000000000L, envelope.ts)
        val tail = envelope.decode(SessionTail.serializer())!!
        assertEquals(listOf(3L, 4L), tail.messages.map { it.seq })
        assertEquals(1756000000001L, tail.messages.first().ts)
    }

    @Test
    fun oneBadRowDoesNotBlankTheFrame() {
        val envelope = Envelope.parse(
            """{"v":2,"type":"s2c.session.tail","payload":{"sessionId":"s1","oldestSeq":1,"hasMore":true,
            "messages":[{"seq":1,"role":"user","text":"a"},{"role":"assistant","text":"no seq"},{"seq":2,"role":"assistant","text":"b"}]}}""",
        )!!
        val tail = envelope.decode(SessionTail.serializer())!!
        assertEquals(listOf(1L, 2L), tail.messages.map { it.seq })
    }

    @Test
    fun sessionRowsAreDecodedOneByOne() {
        val envelope = Envelope.parse(
            """{"v":2,"type":"s2c.sessions.delta","seq":5,"payload":{"upserted":[{"id":"s1","title":"t","lastActivityTs":17.5},{"title":"no id"}],"removedIds":["s9"]}}""",
        )!!
        val delta = envelope.decode(SessionsDelta.serializer())!!
        assertEquals(listOf("s1"), delta.upserted.map { it.id })
        assertEquals(17L, delta.upserted.single().lastActivityTs)
        assertEquals(listOf("s9"), delta.removedIds)
    }
}
