package dev.hailab.deeppilot.core.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** The only protocol version this client speaks (PROTOCOL.md: v2 is the sole baseline). */
const val PROTOCOL_VERSION = 2

/**
 * Shared JSON configuration. v2 adds optional fields over time, so unknown keys
 * are ignored (PROTOCOL.md §10), and absent optionals are not written as null.
 * Defaults are written: the bridge requires `v` on every request, and fields
 * such as `scopes` and `selected` must be sent even at their default value.
 */
val BridgeJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    coerceInputValues = true
}

/** One WebSocket text frame (PROTOCOL.md §1). */
@Serializable
data class Envelope(
    val v: Int = PROTOCOL_VERSION,
    val type: String,
    val id: String? = null,
    val ts: Long? = null,
    /** Server push sequence (§7). Request responses carry none. */
    val seq: Long? = null,
    val payload: JsonElement? = null,
) {
    /** Decodes [payload] as [T]; null when absent or malformed. */
    fun <T> decode(serializer: KSerializer<T>): T? {
        val element = payload ?: return null
        return try {
            BridgeJson.decodeFromJsonElement(serializer, element)
        } catch (_: Exception) {
            null
        }
    }

    fun encode(): String = BridgeJson.encodeToString(serializer(), this)

    companion object {
        fun parse(text: String): Envelope? = try {
            BridgeJson.decodeFromString(serializer(), text)
        } catch (_: Exception) {
            null
        }

        fun <T> request(
            type: String,
            serializer: KSerializer<T>,
            payload: T,
            id: String? = null,
            ts: Long = System.currentTimeMillis(),
        ): Envelope = Envelope(
            type = type,
            id = id,
            ts = ts,
            payload = BridgeJson.encodeToJsonElement(serializer, payload),
        )

        fun empty(type: String, id: String? = null, ts: Long = System.currentTimeMillis()): Envelope =
            Envelope(type = type, id = id, ts = ts, payload = JsonObject(emptyMap()))
    }
}

/** Wire error codes (PROTOCOL.md §9). */
enum class WireErrorCode {
    E_AUTH, E_FORBIDDEN, E_PROTOCOL, E_NOT_FOUND, E_BUSY, E_UNSUPPORTED, E_INTERNAL;

    companion object {
        fun of(raw: String?): WireErrorCode? = entries.firstOrNull { it.name == raw }
    }
}
