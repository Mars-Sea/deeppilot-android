package dev.hailab.deeppilot.core.trust

import dev.hailab.deeppilot.core.Base64Url
import java.security.MessageDigest
import java.security.cert.X509Certificate

/** `sha256:` + base64url(SHA-256(SubjectPublicKeyInfo DER)), 43 unpadded characters (§2.1). */
object SpkiFingerprint {
    private val PATTERN = Regex("^sha256:[A-Za-z0-9_-]{43}$")

    fun isValid(value: String): Boolean = PATTERN.matches(value)

    fun of(spkiDer: ByteArray): String =
        "sha256:" + Base64Url.encode(MessageDigest.getInstance("SHA-256").digest(spkiDer))

    /** `PublicKey.encoded` is the X.509 SubjectPublicKeyInfo DER. */
    fun of(certificate: X509Certificate): String = of(certificate.publicKey.encoded)
}
