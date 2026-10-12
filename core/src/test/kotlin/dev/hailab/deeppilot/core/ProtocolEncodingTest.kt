package dev.hailab.deeppilot.core

import dev.hailab.deeppilot.core.protocol.BridgeJson
import dev.hailab.deeppilot.core.protocol.DefaultScopes
import dev.hailab.deeppilot.core.protocol.Envelope
import dev.hailab.deeppilot.core.protocol.PairRequest
import dev.hailab.deeppilot.core.protocol.QuestionAnswer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The bridge rejects requests without `v` (400 "protocol v2 required"). */
class ProtocolEncodingTest {
    @Test
    fun pairRequestCarriesVersionAndScopes() {
        val json = BridgeJson.parseToJsonElement(
            BridgeJson.encodeToString(PairRequest.serializer(), PairRequest(code = "c", publicKey = "k", deviceName = "d", appVersion = "1")),
        ).jsonObject
        assertEquals(2, json.getValue("v").jsonPrimitive.int)
        assertEquals(DefaultScopes.size, json.getValue("scopes").jsonArray.size)
    }

    @Test
    fun envelopeCarriesVersionAndOmitsNulls() {
        val json = BridgeJson.parseToJsonElement(Envelope(type = "c2s.ping").encode()).jsonObject
        assertEquals(2, json.getValue("v").jsonPrimitive.int)
        assertFalse("id" in json)
        assertFalse("payload" in json)
    }

    @Test
    fun questionAnswerAlwaysSendsSelected() {
        val json = BridgeJson.encodeToJsonElement(QuestionAnswer.serializer(), QuestionAnswer(id = "q")).jsonObject
        assertEquals(JsonArray(emptyList()), json.getValue("selected"))
        assertFalse("custom" in json)
    }
}
