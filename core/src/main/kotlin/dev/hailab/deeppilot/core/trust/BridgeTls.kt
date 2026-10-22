package dev.hailab.deeppilot.core.trust

import okhttp3.OkHttpClient
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/** Why a pinned handshake was refused, so pairing can say what it saw. */
sealed class TrustRejection {
    data class Mismatch(val presented: String) : TrustRejection()
    data object UnsupportedKey : TrustRejection()
    data object EmptyChain : TrustRejection()
}

class PinnedTrustException(val rejection: TrustRejection) : CertificateException(rejection.toString())

/**
 * Trusts exactly one leaf public key (PROTOCOL.md §2.1). LAN hosts serve a
 * self-signed P-256 certificate whose SPKI fingerprint arrived inside the
 * pairing payload; the system trust store and the host name play no part.
 * There is no trust-on-first-use.
 */
class PinnedTrustManager(private val fingerprint: String) : X509TrustManager {
    init {
        require(SpkiFingerprint.isValid(fingerprint)) { "invalid fingerprint" }
    }

    @Volatile
    var lastRejection: TrustRejection? = null
        private set

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: reject(TrustRejection.EmptyChain)
        val key = leaf.publicKey
        if (key !is ECPublicKey || key.params.curve.field.fieldSize != 256) {
            reject(TrustRejection.UnsupportedKey)
        }
        val presented = SpkiFingerprint.of(leaf)
        if (presented != fingerprint) reject(TrustRejection.Mismatch(presented))
        lastRejection = null
    }

    private fun reject(rejection: TrustRejection): Nothing {
        lastRejection = rejection
        throw PinnedTrustException(rejection)
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("client certificates are not used")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

object BridgeTls {
    /**
     * An OkHttp client for one host. With a [fingerprint] only that SPKI passes;
     * without one the platform trust store decides (Funnel / public CA).
     */
    fun client(base: OkHttpClient, fingerprint: String?): Pair<OkHttpClient, PinnedTrustManager?> {
        val builder = base.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            // WebSocket-level pings fail a half-open socket (Wi-Fi gone, NAT expired)
            // within a minute instead of leaving it "connected" until the next poke.
            .pingInterval(30, TimeUnit.SECONDS)
        if (fingerprint == null) return builder.build() to null
        val trustManager = PinnedTrustManager(fingerprint)
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf(trustManager), null)
        val client = builder
            .sslSocketFactory(context.socketFactory, trustManager)
            // The pin identifies the host; a LAN IP never matches a certificate name.
            .hostnameVerifier { _, _ -> true }
            .build()
        return client to trustManager
    }
}
