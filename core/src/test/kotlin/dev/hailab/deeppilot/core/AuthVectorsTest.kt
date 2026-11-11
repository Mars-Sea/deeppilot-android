package dev.hailab.deeppilot.core

import dev.hailab.deeppilot.core.auth.AuthSigning
import dev.hailab.deeppilot.core.protocol.AuthChallenge
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec

/**
 * Cross-language contract for the protocol-v2 authentication proof.
 *
 * `protocol-fixtures/auth-vectors.json` is a copy of
 * `tests/fixtures/auth-vectors.json` in Mars-Sea/dsh-deeppilot, the same file
 * the TypeScript Host and the iOS app test against. It pins the canonical
 * string and the signature encoding, so this client cannot drift from the
 * other two without a failing test. Refresh the copy with
 * `scripts/sync-protocol-fixtures.sh`; never edit it by hand.
 */
class AuthVectorsTest {
    private class Case(val name: String, val fields: JsonObject, val canonical: String)

    private class Signer(val label: String, val publicKey: String, val signatures: JsonObject)

    private val vendored: ByteArray = checkNotNull(
        javaClass.getResourceAsStream("/protocol-fixtures/auth-vectors.json"),
    ) { "protocol-fixtures/auth-vectors.json is missing from the test resources" }.use { it.readBytes() }

    private val fixture = Json.parseToJsonElement(vendored.toString(Charsets.UTF_8)).jsonObject

    private val cases = fixture.getValue("cases").jsonArray.map {
        val item = it.jsonObject
        Case(
            name = item.getValue("name").jsonPrimitive.content,
            fields = item.getValue("fields").jsonObject,
            canonical = item.getValue("canonical").jsonPrimitive.content,
        )
    }

    private val signers = fixture.getValue("signers").jsonObject.map { (label, value) ->
        val signer = value.jsonObject
        Signer(label, signer.getValue("publicKey").jsonPrimitive.content, signer.getValue("signatures").jsonObject)
    }

    private fun canonical(fields: JsonObject): ByteArray {
        fun text(key: String) = fields.getValue(key).jsonPrimitive.content
        return AuthSigning.canonical(
            challenge = AuthChallenge(
                nonce = text("nonce"),
                audience = text("audience"),
                issuedAt = fields.getValue("issuedAt").jsonPrimitive.long,
                expiresAt = fields.getValue("expiresAt").jsonPrimitive.long,
            ),
            deviceId = text("deviceId"),
            deviceName = text("deviceName"),
            appVersion = text("appVersion"),
            resumeCursor = fields["resumeCursor"]?.jsonPrimitive?.longOrNull,
        )
    }

    /** The fixture carries P-256 public keys as an uncompressed X9.63 point: 0x04 || X || Y. */
    private fun publicKey(encoded: String): PublicKey {
        val raw = checkNotNull(Base64Url.decodeOrNull(encoded)) { "public key is not base64url" }
        check(raw.size == 65 && raw[0] == 4.toByte()) { "public key is not an uncompressed P-256 point" }
        val curve = AlgorithmParameters.getInstance("EC").run {
            init(ECGenParameterSpec("secp256r1"))
            getParameterSpec(ECParameterSpec::class.java)
        }
        val point = ECPoint(BigInteger(1, raw.copyOfRange(1, 33)), BigInteger(1, raw.copyOfRange(33, 65)))
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, curve))
    }

    /** Signatures are ASN.1 DER, the layout [dev.hailab.deeppilot.core.auth.DeviceSigner] must produce. */
    private fun isValid(signature: String, message: ByteArray, key: PublicKey): Boolean {
        val der = checkNotNull(Base64Url.decodeOrNull(signature)) { "signature is not base64url" }
        return Signature.getInstance("SHA256withECDSA").run {
            initVerify(key)
            update(message)
            verify(der)
        }
    }

    @Test
    fun canonicalStringMatchesTheSharedVectors() {
        assertTrue("fixture lost cases", cases.size >= 6)
        for (case in cases) {
            assertEquals(case.name, case.canonical, canonical(case.fields).toString(Charsets.UTF_8))
        }
    }

    @Test
    fun signaturesFromEverySignerVerifyOverTheKotlinCanonicalBytes() {
        assertEquals(setOf("node", "swift"), signers.map { it.label }.toSet())
        for (signer in signers) {
            val key = publicKey(signer.publicKey)
            for (case in cases) {
                val signature = signer.signatures[case.name]?.jsonPrimitive?.content
                assertNotNull("${signer.label} signature missing for ${case.name}", signature)
                assertTrue("${signer.label}/${case.name}", isValid(signature!!, canonical(case.fields), key))
            }
        }
    }

    /** A signature must not verify over another case's bytes, or the test above proves nothing. */
    @Test
    fun aSignatureDoesNotVerifyOverAnotherCase() {
        for (signer in signers) {
            val key = publicKey(signer.publicKey)
            for (case in cases) {
                val signature = signer.signatures.getValue(case.name).jsonPrimitive.content
                for (other in cases.filter { it.name != case.name }) {
                    assertFalse(
                        "${signer.label}/${case.name} verified over ${other.name}",
                        isValid(signature, canonical(other.fields), key),
                    )
                }
            }
        }
    }

    /**
     * Fails when the copy here differs from the plugin repository's file. Runs
     * when a dsh-deeppilot checkout is available: next to this repository, or
     * at `DSH_DEEPPILOT_DIR` (CI checks the plugin out and sets it).
     */
    @Test
    fun vendoredCopyMatchesThePluginRepository() {
        val root = System.getenv("DSH_DEEPPILOT_DIR")?.takeIf { it.isNotBlank() }?.let(::File)
            ?: File("../../dsh-deeppilot")
        val source = File(root, "tests/fixtures/auth-vectors.json")
        assumeTrue("no dsh-deeppilot checkout at ${root.absolutePath}", source.isFile)
        assertArrayEquals(
            "auth-vectors.json differs from ${source.path}; run scripts/sync-protocol-fixtures.sh",
            source.readBytes(),
            vendored,
        )
    }
}
