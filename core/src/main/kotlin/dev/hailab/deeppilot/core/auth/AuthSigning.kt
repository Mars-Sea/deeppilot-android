package dev.hailab.deeppilot.core.auth

import dev.hailab.deeppilot.core.Base64Url
import dev.hailab.deeppilot.core.protocol.AuthChallenge

/**
 * Produces an ASN.1 DER ECDSA P-256/SHA-256 signature over [message].
 * On Android the private key lives in the Keystore and never leaves the device.
 */
fun interface DeviceSigner {
    fun sign(message: ByteArray): ByteArray
}

/** The exact bytes `c2s.auth.prove` signs (PROTOCOL.md §2.2). */
object AuthSigning {
    fun canonical(
        challenge: AuthChallenge,
        deviceId: String,
        deviceName: String,
        appVersion: String,
        resumeCursor: Long?,
    ): ByteArray {
        require(challenge.issuedAt >= 0 && challenge.expiresAt >= 0) { "timestamps must be non-negative" }
        require(resumeCursor == null || resumeCursor >= 0) { "cursor must be non-negative" }
        return listOf(
            "deeppilot-auth-v2",
            "device-id:${Base64Url.encode(deviceId)}",
            "nonce:${challenge.nonce}",
            "audience:${Base64Url.encode(challenge.audience)}",
            "issued-at:${challenge.issuedAt}",
            "expires-at:${challenge.expiresAt}",
            "device-name:${Base64Url.encode(deviceName)}",
            "app-version:${Base64Url.encode(appVersion)}",
            "resume-cursor:${resumeCursor?.toString() ?: "-"}",
        ).joinToString("\n").toByteArray(Charsets.UTF_8)
    }
}
