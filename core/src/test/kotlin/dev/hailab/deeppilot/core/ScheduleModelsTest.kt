package dev.hailab.deeppilot.core

import dev.hailab.deeppilot.core.protocol.BridgeJson
import dev.hailab.deeppilot.core.protocol.ClientRequestId
import dev.hailab.deeppilot.core.protocol.ScheduleClock
import dev.hailab.deeppilot.core.protocol.ScheduleCreate
import dev.hailab.deeppilot.core.protocol.ScheduleExpected
import dev.hailab.deeppilot.core.protocol.ScheduleTask
import dev.hailab.deeppilot.core.protocol.ScheduleUpdated
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleModelsTest {
    @Test
    fun createSendsExactlyOneSnakeCaseSelector() {
        val create = ScheduleCreate("id-1", "s1", "Check", "Check the build", weekly = ScheduleClock("09:00:00", "Asia/Shanghai", listOf(2, 4)))
        val json = BridgeJson.encodeToJsonElement(ScheduleCreate.serializer(), create).jsonObject
        assertEquals(setOf("clientRequestId", "sessionId", "title", "prompt", "weekly"), json.keys)
        assertEquals(setOf("time", "time_zone", "weekdays"), json.getValue("weekly").jsonObject.keys)

        val after = BridgeJson.encodeToJsonElement(ScheduleCreate.serializer(), create.copy(weekly = null, afterSeconds = 600)).jsonObject
        assertEquals("600", after.getValue("after_seconds").toString())
    }

    @Test
    fun expectedCarriesTheLastReadRecord() {
        val task = BridgeJson.decodeFromString(
            ScheduleTask.serializer(),
            """{"id":"t1","kind":"daily","title":"A","prompt":"B","scheduledAt":"2026-10-13T01:00:00Z","state":"scheduled","deliveryMode":"queue","time":"09:00:00","timeZone":"Asia/Shanghai","future":1}""",
        )
        val json = BridgeJson.encodeToJsonElement(ScheduleExpected.serializer(), ScheduleExpected.of(task)).jsonObject
        assertEquals(setOf("id", "kind", "title", "prompt", "scheduledAt", "time", "timeZone"), json.keys)
    }

    @Test
    fun decodesDeleteAnswer() {
        val updated = BridgeJson.decodeFromString(ScheduleUpdated.serializer(), """{"clientRequestId":"x","sessionId":"s","deleted":true}""")
        assertTrue(updated.deleted)
        assertFalse(updated.replayed)
    }

    @Test
    fun requestIdsAreUnique() {
        val id = ClientRequestId.make(1730000000000)
        assertTrue(id.startsWith("1730000000000-"))
        assertEquals(36, id.substringAfter('-').length)
        assertFalse(id == ClientRequestId.make(1730000000000))
    }
}
