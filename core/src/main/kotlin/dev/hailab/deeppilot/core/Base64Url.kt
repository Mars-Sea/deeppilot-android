package dev.hailab.deeppilot.core

import java.util.Base64

/** Unpadded base64url, the encoding every protocol field uses (PROTOCOL.md §2). */
object Base64Url {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(bytes: ByteArray): String = encoder.encodeToString(bytes)

    fun encode(text: String): String = encode(text.toByteArray(Charsets.UTF_8))

    /** Returns null for anything that is not valid base64url. */
    fun decodeOrNull(text: String): ByteArray? = try {
        decoder.decode(text)
    } catch (_: IllegalArgumentException) {
        null
    }
}
