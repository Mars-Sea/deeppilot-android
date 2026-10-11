package dev.hailab.deeppilot.core

import dev.hailab.deeppilot.core.protocol.MessageProjection
import dev.hailab.deeppilot.core.protocol.ToolInfo
import dev.hailab.deeppilot.core.session.ToolFolding
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolFoldingTest {
    private fun call(seq: Long, name: String, callId: String? = null) =
        MessageProjection(seq = seq, role = "tool", tool = ToolInfo(name = name, state = "running", summary = "$name args", callId = callId))

    private fun result(seq: Long, state: String = "ok", callId: String? = null) =
        MessageProjection(seq = seq, role = "tool", text = "out$seq", tool = ToolInfo(name = "result", state = state, summary = "sum$seq", callId = callId))

    @Test
    fun foldsResultsIntoOldestOpenCall() {
        val folded = ToolFolding.fold(listOf(call(1, "read"), call(2, "bash"), result(3), result(4, "error")))
        assertEquals(listOf(1L, 2L), folded.map { it.seq })
        assertEquals("done", folded[0].tool?.state)
        assertEquals("read args", folded[0].tool?.summary)
        assertEquals("out3", folded[0].text)
        assertEquals("error", folded[1].tool?.state)
    }

    @Test
    fun matchesByCallIdFirst() {
        val folded = ToolFolding.fold(listOf(call(1, "a", "x"), call(2, "b", "y"), result(3, callId = "y")))
        assertEquals("running", folded[0].tool?.state)
        assertEquals("done", folded[1].tool?.state)
    }

    @Test
    fun keepsOrphanResultAndOtherRows() {
        val text = MessageProjection(seq = 1, role = "assistant", text = "hi")
        val folded = ToolFolding.fold(listOf(text, result(2)))
        assertEquals(listOf(1L, 2L), folded.map { it.seq })
    }
}
