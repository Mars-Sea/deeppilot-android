package dev.hailab.deeppilot.core

import dev.hailab.deeppilot.core.auth.AuthSigning
import dev.hailab.deeppilot.core.auth.DeviceSigner
import dev.hailab.deeppilot.core.bridge.BridgeClient
import dev.hailab.deeppilot.core.bridge.ConnectionState
import dev.hailab.deeppilot.core.bridge.CursorStore
import dev.hailab.deeppilot.core.bridge.FailureReason
import dev.hailab.deeppilot.core.bridge.HostCredentials
import dev.hailab.deeppilot.core.bridge.NotConnectedException
import dev.hailab.deeppilot.core.pairing.PairingClient
import dev.hailab.deeppilot.core.pairing.PairingFailure
import dev.hailab.deeppilot.core.pairing.PairingLink
import dev.hailab.deeppilot.core.protocol.AuthChallenge
import dev.hailab.deeppilot.core.protocol.AuthProve
import dev.hailab.deeppilot.core.protocol.BridgeJson
import dev.hailab.deeppilot.core.protocol.Envelope
import dev.hailab.deeppilot.core.protocol.FrameType
import dev.hailab.deeppilot.core.protocol.SessionsSnapshot
import dev.hailab.deeppilot.core.protocol.Welcome
import dev.hailab.deeppilot.core.trust.SpkiFingerprint
import dev.hailab.deeppilot.core.trust.TrustRejection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class BridgeClientTest {
    private val audience = "deeppilot:" + "a".repeat(22)
    private val deviceId = "device-1"
    private val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val signer = DeviceSigner { message ->
        Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair.private)
            update(message)
            sign()
        }
    }
    private val serverCert = HeldCertificate.Builder().ecdsa256().addSubjectAlternativeName("localhost").build()
    private val server = MockWebServer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val received = CopyOnWriteArrayList<Envelope>()

    @Before
    fun setUp() {
        server.useHttps(HandshakeCertificates.Builder().heldCertificate(serverCert).build().sslSocketFactory(), false)
        server.start()
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.shutdown()
    }

    private val cursors = object : CursorStore {
        val values = ConcurrentHashMap<String, Long>()
        override fun load(profileId: String) = values[profileId]
        override fun save(profileId: String, cursor: Long) {
            values[profileId] = cursor
        }
    }

    /** A minimal bridge: challenge, verify the proof, welcome, answer sessions.list. */
    private fun bridge(closeWith: Int? = null, challengeDelayMillis: Long = 0) = object : WebSocketListener() {
        val challenge = AuthChallenge("bm9uY2U", audience, System.currentTimeMillis(), System.currentTimeMillis() + 30_000)

        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (challengeDelayMillis > 0) Thread.sleep(challengeDelayMillis)
            webSocket.send(
                Envelope(
                    type = FrameType.AUTH_CHALLENGE,
                    payload = BridgeJson.encodeToJsonElement(AuthChallenge.serializer(), challenge),
                ).encode(),
            )
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val envelope = Envelope.parse(text)!!
            received += envelope
            when (envelope.type) {
                FrameType.AUTH_PROVE -> {
                    if (closeWith != null) {
                        webSocket.close(closeWith, "bad proof")
                        return
                    }
                    val prove = envelope.decode(AuthProve.serializer())!!
                    val message = AuthSigning.canonical(challenge, prove.deviceId, prove.deviceName, prove.appVersion, prove.resumeCursor)
                    val ok = Signature.getInstance("SHA256withECDSA").run {
                        initVerify(keyPair.public)
                        update(message)
                        verify(Base64Url.decodeOrNull(prove.signature))
                    }
                    if (!ok) {
                        webSocket.close(4401, "bad signature")
                        return
                    }
                    val welcome = Welcome(protocolVersion = 2, deviceId = prove.deviceId, cursor = 7, resumed = false)
                    webSocket.send(
                        Envelope(type = FrameType.WELCOME, payload = BridgeJson.encodeToJsonElement(Welcome.serializer(), welcome)).encode(),
                    )
                }
                FrameType.SESSIONS_LIST -> webSocket.send(
                    Envelope(
                        type = FrameType.SESSIONS_SNAPSHOT,
                        id = envelope.id,
                        payload = BridgeJson.parseToJsonElement("""{"full":true,"sessions":[{"id":"s1","title":"t","status":"idle","lastActivityTs":1}]}"""),
                    ).encode(),
                )
            }
        }
    }

    private fun client() = BridgeClient(
        baseHttp = OkHttpClient(),
        signerFor = { signer },
        deviceName = { "Pixel" },
        appVersion = "0.1.0",
        cursorStore = cursors,
        parentScope = scope,
    )

    private fun credentials(fingerprint: String? = SpkiFingerprint.of(serverCert.certificate)) = HostCredentials(
        profileId = "p1",
        host = "https://localhost:${server.port}",
        deviceId = deviceId,
        audience = audience,
        tlsFingerprint = fingerprint,
    )

    @Test
    fun authenticatesOverPinnedTlsAndCorrelatesRequests() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(bridge()))
        val client = client()
        client.start(credentials())
        val state = withTimeout(10_000) { client.state.first { it is ConnectionState.Authenticated } }
        assertEquals(7L, cursors.values["p1"])
        assertEquals(deviceId, (state as ConnectionState.Authenticated).welcome.deviceId)

        val response = client.request(FrameType.SESSIONS_LIST, JsonObject(emptyMap()))
        val snapshot = response.decode(SessionsSnapshot.serializer())!!
        assertEquals("s1", snapshot.sessions.single().id)
        val prove = received.first { it.type == FrameType.AUTH_PROVE }.decode(AuthProve.serializer())!!
        assertEquals(null, prove.resumeCursor)
        client.stop()
    }

    @Test
    fun fingerprintMismatchIsTerminal() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(bridge()))
        val client = client()
        client.start(credentials(fingerprint = "sha256:" + "x".repeat(43)))
        val state = withTimeout(10_000) { client.state.first { it is ConnectionState.Failed } }
        val reason = (state as ConnectionState.Failed).reason
        assertTrue(reason is FailureReason.Untrusted && reason.rejection is TrustRejection.Mismatch)
        client.stop()
    }

    @Test
    fun authCloseCodeStopsReconnecting() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(bridge(closeWith = 4401)))
        val client = client()
        client.start(credentials())
        val state = withTimeout(10_000) { client.state.first { it is ConnectionState.Failed } }
        assertEquals(FailureReason.AuthRejected(4401), (state as ConnectionState.Failed).reason)
        client.stop()
    }

    @Test
    fun authTimeoutRetriesInsteadOfFailing() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(bridge(closeWith = 4402)))
        server.enqueue(MockResponse().withWebSocketUpgrade(bridge()))
        val client = client()
        client.start(credentials())
        withTimeout(10_000) { client.state.first { it is ConnectionState.Authenticated } }
        assertEquals(2, server.requestCount)
        client.stop()
    }

    @Test
    fun pokeLeavesAHandshakeInFlightAlone() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(bridge(challengeDelayMillis = 1_000)))
        val client = client()
        client.start(credentials())
        withTimeout(5_000) { client.state.first { it is ConnectionState.Connecting } }
        repeat(3) { client.poke() }
        withTimeout(10_000) { client.state.first { it is ConnectionState.Authenticated } }
        assertEquals(1, server.requestCount)
        client.stop()
    }

    @Test
    fun requestsWaitForWelcome() = runBlocking {
        val client = client()
        val refused = runCatching { client.request(FrameType.SESSIONS_LIST) }.exceptionOrNull()
        assertTrue(refused is NotConnectedException)
        assertEquals(false, client.send(FrameType.SESSIONS_LIST))
    }

    @Test
    fun pairingChecksAudienceAndStatus() = runBlocking {
        val fingerprint = SpkiFingerprint.of(serverCert.certificate)
        val link = PairingLink("https://localhost:${server.port}", "C".repeat(32), fingerprint, audience, null)
        val publicKey = Base64Url.encode((keyPair.public as ECPublicKey).let { byteArrayOf(4) + it.w.affineX.toFixed() + it.w.affineY.toFixed() })

        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"ok":true,"v":2,"deviceId":"d1","audience":"$audience","tlsFingerprint":"$fingerprint"}"""))
        val paired = PairingClient(OkHttpClient()).pair(link, publicKey, "Pixel", "0.1.0")
        assertEquals("d1", paired.deviceId)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"publicKey\":\"$publicKey\""))

        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"ok":true,"v":2,"deviceId":"d1","audience":"deeppilot:${"z".repeat(22)}"}"""))
        assertFailure<PairingFailure.AudienceMismatch> { PairingClient(OkHttpClient()).pair(link, publicKey, "Pixel", "0.1.0") }

        server.enqueue(MockResponse().setResponseCode(401))
        assertFailure<PairingFailure.CodeRejected> { PairingClient(OkHttpClient()).pair(link, publicKey, "Pixel", "0.1.0") }
    }

    private suspend inline fun <reified T> assertFailure(block: suspend () -> Unit) {
        try {
            block()
            throw AssertionError("expected ${T::class.simpleName}")
        } catch (e: PairingFailure) {
            assertTrue("got $e", e is T)
        }
    }

    private fun java.math.BigInteger.toFixed(): ByteArray {
        val raw = toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
        return ByteArray(32 - raw.size) + raw
    }
}
