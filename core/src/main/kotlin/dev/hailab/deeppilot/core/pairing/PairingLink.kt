package dev.hailab.deeppilot.core.pairing

import dev.hailab.deeppilot.core.protocol.BridgeJson
import dev.hailab.deeppilot.core.protocol.PROTOCOL_VERSION
import dev.hailab.deeppilot.core.trust.SpkiFingerprint
import kotlinx.serialization.Serializable
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** Why a pairing string was refused; the UI maps each to its own message. */
enum class PairingError { INVALID_PAYLOAD, INVALID_HOST, INVALID_FINGERPRINT, INVALID_CODE, EXPIRED, UNSUPPORTED_VERSION }

class PairingException(val reason: PairingError) : Exception(reason.name)

/**
 * Pairing information (PROTOCOL.md §2.1). The QR code carries a JSON object; the
 * copyable text is the shorter `deeppilot://pair` link. Both go through the same
 * validation so the camera, the paste field and a deep link behave alike.
 */
data class PairingLink(
    /** `https://` or `wss://` origin. */
    val host: String,
    /** Single-use base64url pairing code. This is a credential: never persist or log it. */
    val code: String,
    /** SPKI pin for a self-signed LAN certificate; null for public-CA hosts. */
    val tlsFingerprint: String?,
    /** Stable host identity; optional in a pasted link, the pair response provides it. */
    val audience: String?,
    /** Absolute expiry in ms; absent in pasted links, where the host stays authoritative. */
    val expiresAt: Long?,
) {
    fun toUri(): String = buildString {
        append("$SCHEME://$AUTHORITY?h=").append(enc(host)).append("&c=").append(enc(code))
        tlsFingerprint?.let { append("&f=").append(enc(it)) }
        audience?.let { append("&a=").append(enc(it)) }
        expiresAt?.takeIf { it > 0 }?.let { append("&e=").append(it) }
    }

    override fun toString(): String = "PairingLink(host=$host, code=<redacted>, fingerprint=$tlsFingerprint)"

    companion object {
        const val SCHEME = "deeppilot"
        const val AUTHORITY = "pair"
        const val PAYLOAD_TYPE = "deeppilot-pairing"
        const val MAXIMUM_BYTES = 4096

        private val CODE_PATTERN = Regex("^[A-Za-z0-9_-]{32,128}$")
        val AUDIENCE_PATTERN = Regex("^deeppilot:[A-Za-z0-9_-]{22}$")

        /** Parses the link form or the QR JSON. Throws [PairingException]. */
        fun parse(value: String, nowMillis: Long = System.currentTimeMillis()): PairingLink {
            val trimmed = value.trim()
            if (trimmed.isEmpty() || trimmed.toByteArray(Charsets.UTF_8).size > MAXIMUM_BYTES) {
                throw PairingException(PairingError.INVALID_PAYLOAD)
            }
            return if (trimmed.lowercase().startsWith("$SCHEME://")) {
                parseLink(trimmed, nowMillis)
            } else {
                parseJson(trimmed, nowMillis)
            }
        }

        /** True when [uri] is the pairing deep link (not another app route). */
        fun isPairingUri(uri: String): Boolean = try {
            val parsed = URI(uri.trim())
            parsed.scheme.equals(SCHEME, ignoreCase = true) && parsed.host.equals(AUTHORITY, ignoreCase = true)
        } catch (_: Exception) {
            false
        }

        private fun parseLink(value: String, nowMillis: Long): PairingLink {
            if (!isPairingUri(value)) throw PairingException(PairingError.INVALID_PAYLOAD)
            val query = try {
                URI(value).rawQuery
            } catch (_: Exception) {
                null
            } ?: throw PairingException(PairingError.INVALID_PAYLOAD)
            val values = LinkedHashMap<String, String>()
            for (part in query.split('&')) {
                if (part.isEmpty()) continue
                val eq = part.indexOf('=')
                val name = dec(if (eq < 0) part else part.substring(0, eq))
                val v = if (eq < 0) "" else dec(part.substring(eq + 1))
                values.putIfAbsent(name, v)
            }
            val host = values["h"] ?: throw PairingException(PairingError.INVALID_PAYLOAD)
            val code = values["c"] ?: throw PairingException(PairingError.INVALID_PAYLOAD)
            return validate(
                host = host,
                code = code,
                tlsFingerprint = values["f"]?.trim()?.ifEmpty { null },
                audience = values["a"]?.trim()?.ifEmpty { null },
                expiresAt = values["e"]?.toLongOrNull(),
                nowMillis = nowMillis,
            )
        }

        @Serializable
        private data class QrPayload(
            val v: Int? = null,
            val type: String? = null,
            val host: String? = null,
            val code: String? = null,
            val expiresAt: Long? = null,
            val audience: String? = null,
            val tlsFingerprint: String? = null,
        )

        private fun parseJson(value: String, nowMillis: Long): PairingLink {
            val payload = try {
                BridgeJson.decodeFromString(QrPayload.serializer(), value)
            } catch (_: Exception) {
                throw PairingException(PairingError.INVALID_PAYLOAD)
            }
            if (payload.v != PROTOCOL_VERSION || payload.type != PAYLOAD_TYPE) {
                throw PairingException(PairingError.UNSUPPORTED_VERSION)
            }
            val host = payload.host ?: throw PairingException(PairingError.INVALID_PAYLOAD)
            val code = payload.code ?: throw PairingException(PairingError.INVALID_PAYLOAD)
            val audience = payload.audience ?: throw PairingException(PairingError.INVALID_PAYLOAD)
            val expiresAt = payload.expiresAt ?: throw PairingException(PairingError.INVALID_PAYLOAD)
            return validate(host, code, payload.tlsFingerprint, audience, expiresAt, nowMillis)
        }

        /** Field rules shared by both containers. */
        fun validate(
            host: String,
            code: String,
            tlsFingerprint: String?,
            audience: String?,
            expiresAt: Long?,
            nowMillis: Long = System.currentTimeMillis(),
        ): PairingLink {
            val trimmedHost = host.trim()
            // Only encrypted transports exist on the wire (§2.1).
            val uri = try {
                URI(trimmedHost)
            } catch (_: Exception) {
                throw PairingException(PairingError.INVALID_HOST)
            }
            val scheme = uri.scheme?.lowercase()
            if (scheme !in setOf("https", "wss") || uri.host.isNullOrEmpty() || uri.rawUserInfo != null) {
                throw PairingException(PairingError.INVALID_HOST)
            }
            if (tlsFingerprint != null && !SpkiFingerprint.isValid(tlsFingerprint)) {
                throw PairingException(PairingError.INVALID_FINGERPRINT)
            }
            if (code != code.trim() || !CODE_PATTERN.matches(code)) {
                throw PairingException(PairingError.INVALID_CODE)
            }
            if (audience != null && !AUDIENCE_PATTERN.matches(audience)) {
                throw PairingException(PairingError.INVALID_PAYLOAD)
            }
            if (expiresAt != null && expiresAt > 0 && expiresAt <= nowMillis) {
                throw PairingException(PairingError.EXPIRED)
            }
            return PairingLink(trimmedHost, code, tlsFingerprint, audience, expiresAt)
        }

        private fun enc(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

        private fun dec(value: String) = try {
            URLDecoder.decode(value, "UTF-8")
        } catch (_: IllegalArgumentException) {
            throw PairingException(PairingError.INVALID_PAYLOAD)
        }
    }
}
