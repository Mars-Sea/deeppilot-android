package dev.hailab.deeppilot.core.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
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

/**
 * One WebSocket frame (PROTOCOL.md §1). A binary frame (§1.1) carries the same
 * header JSON plus raw trailing bytes in [body]; text frames leave it null.
 */
@Serializable
data class Envelope(
    val v: Int = PROTOCOL_VERSION,
    val type: String,
    val id: String? = null,
    @Serializable(with = LenientLongSerializer::class) val ts: Long? = null,
    /** Server push sequence (§7). Request responses carry none. */
    @Serializable(with = LenientLongSerializer::class) val seq: Long? = null,
    val payload: JsonElement? = null,
    /** Raw bytes after the header of a binary frame (§1.1). Never encoded. */
    @Transient val body: ByteArray? = null,
) {
    /**
     * The seq that may advance the replay cursor. Transient frames never do,
     * even if a misbehaving sender attached one (§7, streaming and plugin update).
     */
    val replaySeq: Long? get() = if (type in TRANSIENT_TYPES) null else seq

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
        /** Format byte of the only binary layout defined so far (§1.1). */
        const val BINARY_FORMAT: Int = 1

        /** Push frames that take no seq and never enter the replay ring. */
        val TRANSIENT_TYPES = setOf(FrameType.SESSION_DELTA, FrameType.PLUGIN_UPDATE_PROGRESS)

        fun parse(text: String): Envelope? = try {
            BridgeJson.decodeFromString(serializer(), text)
        } catch (_: Exception) {
            null
        }

        /**
         * `[format u8 = 1][header length u32 BE][header JSON][raw bytes]` (§1.1).
         * Null for an unknown format byte, an out-of-range length or a bad header.
         */
        fun parseBinary(bytes: ByteArray): Envelope? {
            if (bytes.size < 5 || bytes[0].toInt() and 0xff != BINARY_FORMAT) return null
            val length = ((bytes[1].toLong() and 0xff) shl 24) or ((bytes[2].toLong() and 0xff) shl 16) or
                ((bytes[3].toLong() and 0xff) shl 8) or (bytes[4].toLong() and 0xff)
            if (length > bytes.size - 5) return null
            val headerEnd = 5 + length.toInt()
            val header = parse(String(bytes, 5, length.toInt(), Charsets.UTF_8)) ?: return null
            return header.copy(body = bytes.copyOfRange(headerEnd, bytes.size))
        }

        /** Format byte of a deflated text frame (§1.2). */
        const val COMPRESSED_FORMAT: Int = 2
        const val DEFLATE_RAW = "deflate-raw"

        /**
         * `[format u8 = 2][deflate-raw of the whole envelope JSON]` (§1.2): the text
         * frame it stands for, or null when it is malformed or inflates past [maxBytes].
         */
        fun inflate(bytes: ByteArray, maxBytes: Int): String? {
            if (bytes.size < 2 || bytes[0].toInt() and 0xff != COMPRESSED_FORMAT) return null
            val inflater = java.util.zip.Inflater(true)
            return try {
                inflater.setInput(bytes, 1, bytes.size - 1)
                val out = java.io.ByteArrayOutputStream(minOf(maxBytes, bytes.size * 4))
                val buffer = ByteArray(16 * 1024)
                while (!inflater.finished()) {
                    val read = inflater.inflate(buffer)
                    if (read == 0 && (inflater.needsInput() || inflater.needsDictionary())) return null
                    if (out.size() + read > maxBytes) return null
                    out.write(buffer, 0, read)
                }
                String(out.toByteArray(), Charsets.UTF_8)
            } catch (_: java.util.zip.DataFormatException) {
                null
            } finally {
                inflater.end()
            }
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
    E_AUTH, E_FORBIDDEN, E_PROTOCOL, E_NOT_FOUND, E_BUSY, E_UNSUPPORTED, E_INTERNAL,
    /** A queued prompt can no longer steer: the turn ended or it left the queue. Not retryable. */
    E_STEER_UNAVAILABLE,
    /** The answer would exceed the frame size the client declared (attachment read-back). Not retryable. */
    E_TOO_LARGE;

    companion object {
        fun of(raw: String?): WireErrorCode? = entries.firstOrNull { it.name == raw }
    }
}
