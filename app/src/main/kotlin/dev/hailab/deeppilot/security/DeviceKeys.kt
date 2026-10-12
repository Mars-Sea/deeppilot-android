package dev.hailab.deeppilot.security

import android.content.Context
import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dev.hailab.deeppilot.core.Base64Url
import dev.hailab.deeppilot.core.auth.DeviceSigner
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

/**
 * Per-host P-256 signing keys in the Android Keystore (StrongBox when the
 * device has one). The private key never leaves the secure hardware; the
 * host only ever sees the public key (PROTOCOL.md §2.1).
 */
class DeviceKeys(private val context: Context) {
    private val keyStore: KeyStore get() = KeyStore.getInstance(PROVIDER).apply { load(null) }

    /** Generates the key for [profileId] and returns the 65-byte X9.63 public key, base64url. */
    fun create(profileId: String): String {
        val alias = alias(profileId)
        val hasStrongBox = context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        val pair = try {
            generate(alias, strongBox = hasStrongBox)
        } catch (e: Exception) {
            if (!hasStrongBox) throw e
            generate(alias, strongBox = false)
        }
        val point = (pair.public as ECPublicKey).w
        return Base64Url.encode(byteArrayOf(0x04) + point.affineX.fixed32() + point.affineY.fixed32())
    }

    fun signer(profileId: String): DeviceSigner = DeviceSigner { message ->
        val key = keyStore.getKey(alias(profileId), null) as? PrivateKey
            ?: error("device key missing; pair again")
        Signature.getInstance("SHA256withECDSA").run {
            initSign(key)
            update(message)
            sign()
        }
    }

    fun delete(profileId: String) {
        runCatching { keyStore.deleteEntry(alias(profileId)) }
    }

    private fun generate(alias: String, strongBox: Boolean) =
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, PROVIDER).run {
            initialize(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setIsStrongBoxBacked(strongBox)
                    .build(),
            )
            generateKeyPair()
        }

    private fun BigInteger.fixed32(): ByteArray {
        val raw = toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
        require(raw.size <= 32)
        return ByteArray(32 - raw.size) + raw
    }

    private fun alias(profileId: String) = "deeppilot.device.$profileId"

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
    }
}
