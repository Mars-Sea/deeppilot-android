package dev.hailab.deeppilot.core.pairing

import dev.hailab.deeppilot.core.protocol.BridgeJson
import dev.hailab.deeppilot.core.protocol.PairRequest
import dev.hailab.deeppilot.core.protocol.PairResponse
import dev.hailab.deeppilot.core.trust.BridgeTls
import dev.hailab.deeppilot.core.trust.TrustRejection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** What a successful pairing leaves behind; none of it is secret. */
data class PairedHost(
    val host: String,
    val deviceId: String,
    val audience: String,
    val tlsFingerprint: String?,
)

sealed class PairingFailure : Exception() {
    /** 401: the code is invalid, expired or already used. */
    data object CodeRejected : PairingFailure()
    /** 429 */
    data object RateLimited : PairingFailure()
    /** 409: the host's device registry is full. */
    data object RegistryFull : PairingFailure()
    data class HttpStatus(val status: Int) : PairingFailure()
    data object AudienceMismatch : PairingFailure()
    data object FingerprintMismatch : PairingFailure()
    data object MalformedResponse : PairingFailure()
    data class Untrusted(val rejection: TrustRejection) : PairingFailure()
    data class Network(override val cause: IOException) : PairingFailure()
}

/** `POST /phone/pair` (PROTOCOL.md §2.1). */
class PairingClient(private val base: OkHttpClient) {
    suspend fun pair(
        link: PairingLink,
        publicKeyX963: String,
        deviceName: String,
        appVersion: String,
    ): PairedHost = withContext(Dispatchers.IO) {
        val endpoints = HostEndpoints.of(link.host) ?: throw PairingException(PairingError.INVALID_HOST)
        val (client, trust) = BridgeTls.client(base, link.tlsFingerprint)
        val body = BridgeJson.encodeToString(
            PairRequest.serializer(),
            PairRequest(code = link.code, publicKey = publicKeyX963, deviceName = deviceName, appVersion = appVersion),
        ).toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(endpoints.pair).post(body).build()
        val (status, text) = try {
            client.newCall(request).execute().use { it.code to it.body?.string().orEmpty() }
        } catch (e: IOException) {
            trust?.lastRejection?.let { throw PairingFailure.Untrusted(it) }
            throw PairingFailure.Network(e)
        }
        when (status) {
            200, 201 -> Unit
            401 -> throw PairingFailure.CodeRejected
            409 -> throw PairingFailure.RegistryFull
            429 -> throw PairingFailure.RateLimited
            else -> throw PairingFailure.HttpStatus(status)
        }
        val response = try {
            BridgeJson.decodeFromString(PairResponse.serializer(), text)
        } catch (_: Exception) {
            throw PairingFailure.MalformedResponse
        }
        val deviceId = response.deviceId
        val audience = response.audience
        if (!response.ok || deviceId.isNullOrEmpty() || audience == null ||
            !PairingLink.AUDIENCE_PATTERN.matches(audience)
        ) {
            throw PairingFailure.MalformedResponse
        }
        if (link.audience != null && link.audience != audience) throw PairingFailure.AudienceMismatch
        if (link.tlsFingerprint != null && response.tlsFingerprint != null &&
            response.tlsFingerprint != link.tlsFingerprint
        ) {
            throw PairingFailure.FingerprintMismatch
        }
        PairedHost(link.host, deviceId, audience, link.tlsFingerprint)
    }
}
