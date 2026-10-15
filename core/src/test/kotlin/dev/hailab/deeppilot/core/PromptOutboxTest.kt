package dev.hailab.deeppilot.core

import dev.hailab.deeppilot.core.delivery.OutboxFullException
import dev.hailab.deeppilot.core.delivery.PendingSend
import dev.hailab.deeppilot.core.delivery.PromptOutbox
import dev.hailab.deeppilot.core.protocol.BridgeJson
import dev.hailab.deeppilot.core.protocol.PromptReceipt
import dev.hailab.deeppilot.core.protocol.SendPrompt
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptOutboxTest {
    private fun pending(id: String) = PendingSend(id, "s1", "hi", 0)

    @Test
    fun receiptsSettleOrMarkRows() {
        val list = listOf(pending("a"), pending("b"), pending("c"))
        val after = PromptOutbox.apply(
            PromptOutbox.apply(PromptOutbox.apply(list, PromptReceipt("a", "accepted")), PromptReceipt("b", "rejected")),
            PromptReceipt("c", "notFound"),
        )
        assertEquals(listOf("b", "c"), after.map { it.clientSendId })
        assertEquals(PendingSend.Delivery.Rejected, after[0].delivery)
        assertEquals(PendingSend.Delivery.Unknown, after[1].delivery)
    }

    @Test
    fun refusesBeyondCapacity() {
        val full = (1..PromptOutbox.CAPACITY).map { pending("$it") }
        val refused = runCatching { PromptOutbox.add(full, pending("x")) }.exceptionOrNull()
        assertTrue(refused is OutboxFullException)
    }

    @Test
    fun sendIdOnlyOnTheWireWhenSet() {
        val plain = BridgeJson.encodeToJsonElement(SendPrompt.serializer(), SendPrompt("s1", "hi")).jsonObject
        assertFalse("clientSendId" in plain)
        val tracked = BridgeJson.encodeToJsonElement(SendPrompt.serializer(), SendPrompt("s1", "hi", clientSendId = "1-x")).jsonObject
        assertEquals("\"1-x\"", tracked.getValue("clientSendId").toString())
    }

    @Test
    fun pendingRowsRoundTrip() {
        val row = pending("a").copy(delivery = PendingSend.Delivery.Unknown, attachmentCount = 2)
        val json = BridgeJson.encodeToString(PendingSend.serializer(), row)
        assertEquals(row, BridgeJson.decodeFromString(PendingSend.serializer(), json))
    }
}
