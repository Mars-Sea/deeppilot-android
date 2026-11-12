package dev.hailab.deeppilot.core.bridge

import dev.hailab.deeppilot.core.Base64Url
import dev.hailab.deeppilot.core.auth.AuthSigning
import dev.hailab.deeppilot.core.auth.DeviceSigner
import dev.hailab.deeppilot.core.pairing.HostEndpoints
import dev.hailab.deeppilot.core.protocol.AuthChallenge
import dev.hailab.deeppilot.core.protocol.AuthProve
import dev.hailab.deeppilot.core.protocol.BridgeJson
import dev.hailab.deeppilot.core.protocol.Envelope
import dev.hailab.deeppilot.core.protocol.ErrorPayload
import dev.hailab.deeppilot.core.protocol.FrameType
import dev.hailab.deeppilot.core.protocol.HostRoutes
import dev.hailab.deeppilot.core.protocol.PROTOCOL_VERSION
import dev.hailab.deeppilot.core.protocol.Welcome
import dev.hailab.deeppilot.core.protocol.WireErrorCode
import dev.hailab.deeppilot.core.trust.BridgeTls
import dev.hailab.deeppilot.core.trust.PinnedTrustManager
import dev.hailab.deeppilot.core.trust.TrustRejection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.UUID
import kotlin.random.Random

/** Everything needed to reach one paired host. Nothing here is secret. */
data class HostCredentials(
    /** Local profile id; also names the Keystore key and the cursor slot. */
    val profileId: String,
    val host: String,
    val deviceId: String,
    val audience: String,
    val tlsFingerprint: String?,
    /** Addresses the host announced in its last welcome (`routeHints`); null before the first. */
    val routes: HostRoutes? = null,
)

/** Persists the replay cursor (§7) per profile. */
interface CursorStore {
    fun load(profileId: String): Long?
    fun save(profileId: String, cursor: Long)
}

sealed interface FailureReason {
    /** 4401 / 4500 or a bad welcome: the device must pair again. */
    data class AuthRejected(val closeCode: Int?) : FailureReason
    data object AudienceMismatch : FailureReason
    data object DeviceRevoked : FailureReason
    data class Untrusted(val rejection: TrustRejection) : FailureReason
    data object InvalidHost : FailureReason
    data class SigningFailed(val message: String?) : FailureReason
}

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data class Connecting(val attempt: Int) : ConnectionState
    data class Authenticated(val welcome: Welcome) : ConnectionState
    /** Terminal until [BridgeClient.forceReconnect] or a new [BridgeClient.start]. */
    data class Failed(val reason: FailureReason) : ConnectionState
}

class BridgeException(val code: WireErrorCode?, message: String?) : Exception(message ?: code?.name ?: "bridge error")
class NotConnectedException : Exception("not connected")

/**
 * The bridge WebSocket: challenge signing, replay cursor, heartbeat,
 * request/response correlation and reconnect with backoff (PROTOCOL.md §2, §7, §8).
 *
 * All mutable state is confined to one serial dispatcher; OkHttp callbacks hop
 * onto it and carry a connection epoch so a stale socket cannot touch state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BridgeClient(
    private val baseHttp: OkHttpClient,
    private val signerFor: (profileId: String) -> DeviceSigner,
    private val deviceName: () -> String,
    private val appVersion: String,
    private val cursorStore: CursorStore,
    parentScope: CoroutineScope,
    private val backoff: Backoff = Backoff(),
    private val pingIntervalMillis: Long = 25_000,
    /** Whether the LAN route is worth trying: true on Wi-Fi or Ethernet. */
    private val lanEligible: () -> Boolean = { true },
    /**
     * Sessions to keep receiving events for, besides the ones opened later
     * (`watchSessions`). Null leaves the connection on "every session".
     */
    private val watchSessions: () -> List<String>? = { null },
) {
    private val serial = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]) + serial)

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _frames = MutableSharedFlow<Envelope>(extraBufferCapacity = 1024)
    /** Every inbound frame after the client has handled it. */
    val frames: SharedFlow<Envelope> = _frames.asSharedFlow()

    @Volatile private var credentials: HostCredentials? = null
    private var trust: PinnedTrustManager? = null
    /** The routes of the current round and the one the newest socket uses. */
    private var candidates: List<Route> = emptyList()
    private var candidateIndex = 0
    private var route: Route? = null
    private var lanFailedAt: Long? = null
    private var networkKey: String? = null
    private var socket: WebSocket? = null
    /** When the current socket was opened, to tell a fresh handshake from a stuck one. */
    private var socketOpenedAt = 0L
    /** True between welcome and the loss of that socket; only then may callers send. */
    private var authenticated = false
    private var probing = false
    private var epoch = 0
    private var attempt = 0
    private var authRejected = false
    private var lastCursor: Long? = null
    private var reconnectJob: Job? = null
    private var pingJob: Job? = null
    private val pending = HashMap<String, CompletableDeferred<Envelope>>()
    private val progress = HashMap<String, (Envelope) -> Unit>()

    val currentCredentials: HostCredentials? get() = credentials

    /**
     * The diagnostic id of the newest socket (§2.0), sent as `at=` on the upgrade.
     * A new one per socket, so a retry is never confused with the attempt before it.
     */
    @Volatile var attemptId: String? = null
        private set

    /** The route of the authenticated connection, for diagnostics; null while not connected. */
    @Volatile var activeRoute: Route? = null
        private set

    /** Connects to [creds], replacing any current host. */
    fun start(creds: HostCredentials) {
        scope.launch {
            closeSocket(reconnect = false)
            credentials = creds
            lanFailedAt = null
            lastCursor = cursorStore.load(creds.profileId)
            authRejected = false
            attempt = 0
            openSocket()
        }
    }

    /** Disconnects and forgets the host. */
    fun stop() {
        scope.launch {
            closeSocket(reconnect = false)
            credentials = null
            _state.value = ConnectionState.Disconnected
        }
    }

    /**
     * Foreground or network wake-up. A socket that claims to be authenticated is
     * probed with one ping (a suspended process can miss the close); a pending
     * backoff is cut short.
     *
     * A handshake still in flight is left alone: the bridge counts every socket
     * closed before welcome as a failed authentication and blocks the phone for
     * 15 minutes after five of them (auth-rate-limit.ts). App start, the service
     * start and the network callback used to poke in a burst and abort each
     * other's handshakes, which is how the phone got locked out.
     */
    fun poke() {
        scope.launch {
            if (credentials == null || authRejected) return@launch
            attempt = 0
            when {
                authenticated -> probe()
                socket != null && System.nanoTime() - socketOpenedAt < HANDSHAKE_GRACE_NANOS -> Unit
                else -> {
                    closeSocket(reconnect = true)
                    openSocket()
                }
            }
        }
    }

    /**
     * The default network changed; [key] names the new one. Besides [poke], a new
     * network forgets the LAN failure, and a connection that is on the public
     * route moves to the LAN when one is now worth trying and nothing is in flight.
     */
    fun networkChanged(key: String?) {
        scope.launch {
            val creds = credentials
            // The first report only names the network the app started on.
            val changed = networkKey != null && key != networkKey
            networkKey = key
            if (!changed || creds == null || authRejected) return@launch
            lanFailedAt = null
            val current = route
            val lanFirst = RouteSelector.candidates(creds, lanEligible(), lanRecentlyFailed = false).first().kind == Route.Kind.Lan
            if (authenticated && current != null && current.kind != Route.Kind.Lan && lanFirst && pending.isEmpty()) {
                attempt = 0
                closeSocket(reconnect = true)
                openSocket()
            }
        }
        poke()
    }

    private fun probe() {
        if (probing) return
        probing = true
        val probeEpoch = epoch
        scope.launch {
            try {
                request(FrameType.PING, JsonObject(emptyMap()), timeoutMillis = 4_000)
            } catch (_: Exception) {
                if (epoch == probeEpoch && credentials != null) {
                    closeSocket(reconnect = true)
                    openSocket()
                }
            } finally {
                probing = false
            }
        }
    }

    /** Manual reconnect; also clears a previous auth failure. */
    fun forceReconnect() {
        scope.launch {
            if (credentials == null) return@launch
            authRejected = false
            attempt = 0
            closeSocket(reconnect = true)
            openSocket()
        }
    }

    /**
     * Fire-and-forget frame. Returns false until the connection is authenticated:
     * before welcome the bridge answers anything but proof and ping with an error.
     */
    suspend fun send(type: String, payload: JsonElement = JsonObject(emptyMap())): Boolean = withContext(serial) {
        authenticated && sendLocked(Envelope(type = type, ts = System.currentTimeMillis(), payload = payload))
    }

    suspend fun <T> send(type: String, serializer: KSerializer<T>, payload: T): Boolean =
        send(type, BridgeJson.encodeToJsonElement(serializer, payload))

    /** Request with a correlation id; returns the response envelope or throws [BridgeException]. */
    suspend fun request(type: String, payload: JsonElement = JsonObject(emptyMap()), timeoutMillis: Long = 12_000, onProgress: ((Envelope) -> Unit)? = null): Envelope {
        val id = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<Envelope>()
        withContext(serial) {
            if (!authenticated) throw NotConnectedException()
            pending[id] = deferred
            if (onProgress != null) progress[id] = onProgress
            if (!sendLocked(Envelope(type = type, id = id, ts = System.currentTimeMillis(), payload = payload))) {
                pending.remove(id)
                progress.remove(id)
                throw NotConnectedException()
            }
        }
        val response = try {
            withTimeout(timeoutMillis) { deferred.await() }
        } finally {
            withContext(kotlinx.coroutines.NonCancellable + serial) { pending.remove(id); progress.remove(id) }
        }
        if (response.type == FrameType.ERROR) {
            val error = response.decode(ErrorPayload.serializer())
            throw BridgeException(WireErrorCode.of(error?.code), error?.message)
        }
        return response
    }

    suspend fun <T> request(type: String, serializer: KSerializer<T>, payload: T, timeoutMillis: Long = 12_000): Envelope =
        request(type, BridgeJson.encodeToJsonElement(serializer, payload), timeoutMillis)

    // ---------- socket lifecycle (serial) ----------

    /** Opens the next socket: the first route of a new round, or the next one of this round. */
    private fun openSocket(nextInRound: Boolean = false) {
        val creds = credentials ?: return
        if (nextInRound) {
            candidateIndex += 1
        } else {
            val failedAt = lanFailedAt
            val recentlyFailed = failedAt != null &&
                System.nanoTime() - failedAt < RouteSelector.LAN_FAILURE_MEMORY_MILLIS * 1_000_000
            candidates = RouteSelector.candidates(creds, lanEligible(), recentlyFailed)
            candidateIndex = 0
        }
        val next = candidates.getOrNull(candidateIndex) ?: return
        val endpoints = HostEndpoints.of(next.url)
        if (endpoints == null) {
            if (candidateIndex < candidates.lastIndex) openSocket(nextInRound = true) else fail(FailureReason.InvalidHost)
            return
        }
        val timeout = if (next.quick) RouteSelector.LAN_CONNECT_TIMEOUT_MILLIS else 10_000L
        val (client, pinned) = BridgeTls.client(baseHttp, next.tlsFingerprint, timeout)
        route = next
        trust = pinned
        epoch += 1
        val socketEpoch = epoch
        _state.value = ConnectionState.Connecting(attempt)
        val tag = AttemptId.make()
        attemptId = tag
        val request = Request.Builder().url(AttemptId.handshakeUrl(endpoints.webSocket, tag)).build()
        socketOpenedAt = System.nanoTime()
        socket = client.newWebSocket(request, Listener(socketEpoch))
    }

    private fun closeSocket(reconnect: Boolean) {
        reconnectJob?.cancel()
        reconnectJob = null
        pingJob?.cancel()
        pingJob = null
        epoch += 1
        socket?.cancel()
        socket = null
        authenticated = false
        activeRoute = null
        failAllPending()
        if (!reconnect) attempt = 0
    }

    /** [atLeastMillis]: the bridge's Retry-After when it refused the upgrade. */
    private fun scheduleReconnect(atLeastMillis: Long = 0) {
        if (credentials == null || authRejected) return
        attempt += 1
        val delayMillis = maxOf(backoff.delayMillis(attempt), atLeastMillis)
        _state.value = ConnectionState.Connecting(attempt)
        reconnectJob?.cancel()
        val scheduledEpoch = epoch
        reconnectJob = scope.launch {
            delay(delayMillis)
            if (epoch == scheduledEpoch) openSocket()
        }
    }

    private fun fail(reason: FailureReason) {
        authRejected = true
        closeSocket(reconnect = false)
        _state.value = ConnectionState.Failed(reason)
    }

    private fun handleTransportLoss(
        socketEpoch: Int,
        closeCode: Int?,
        closeReason: String?,
        error: Throwable?,
        retryAfterMillis: Long = 0,
    ) {
        if (socketEpoch != epoch) return
        // onClosing and onClosed both report the same socket; only the first counts.
        epoch += 1
        pingJob?.cancel()
        socket = null
        val reached = authenticated
        authenticated = false
        activeRoute = null
        failAllPending()
        if (authRejected) return
        // A LAN route that never got as far as a welcome is remembered as unreachable.
        if (!reached && route?.kind == Route.Kind.Lan) lanFailedAt = System.nanoTime()
        when {
            closeCode == 4401 && closeReason == "device revoked" -> fail(FailureReason.DeviceRevoked)
            closeCode in AUTH_FAILURE_CODES -> fail(FailureReason.AuthRejected(closeCode))
            // Another route of this round is left: try it now, without backoff. Only the
            // last one may end the round, so a stale hint never asks the user to pair again.
            !reached && candidateIndex < candidates.lastIndex -> openSocket(nextInRound = true)
            error != null && trust?.lastRejection != null -> fail(FailureReason.Untrusted(trust!!.lastRejection!!))
            else -> scheduleReconnect(retryAfterMillis)
        }
    }

    private suspend fun handleFrame(socketEpoch: Int, text: String) {
        if (socketEpoch != epoch) return
        val envelope = Envelope.parse(text) ?: return
        val id = envelope.id
        if (envelope.type == FrameType.ERROR && id == null) {
            // Server shutting down (§8, E_INTERNAL): fail every waiter now. Other uncorrelated
            // errors answer a fire-and-forget frame (a session.open that found nothing) and
            // must not take unrelated requests, such as a prompt send, down with them.
            if (envelope.decode(ErrorPayload.serializer())?.code == WireErrorCode.E_INTERNAL.name) failAllPending()
        } else if (id != null && envelope.type in FrameType.CHUNK_TYPES) {
            deliverChunk(id, envelope)
            return
        } else if (id != null) {
            pending.remove(id)?.complete(envelope)
            progress.remove(id)
        }
        when (envelope.type) {
            FrameType.AUTH_CHALLENGE -> answerChallenge(envelope.decode(AuthChallenge.serializer()))
            FrameType.WELCOME -> {
                val welcome = envelope.decode(Welcome.serializer())
                if (welcome == null || welcome.protocolVersion != PROTOCOL_VERSION ||
                    welcome.deviceId != credentials?.deviceId
                ) {
                    fail(FailureReason.AuthRejected(null))
                    return
                }
                if (!welcome.resumed) replaceCursor(welcome.cursor)
                // Announced over an authenticated connection, so trusted; replaced on every welcome.
                if (welcome.capabilities.routeHints) {
                    credentials = credentials?.let { RouteSelector.adopt(it, welcome.routes) }
                }
                attempt = 0
                authenticated = true
                activeRoute = route
                if (route?.kind == Route.Kind.Lan) lanFailedAt = null
                _state.value = ConnectionState.Authenticated(welcome)
                startPing()
            }
        }
        envelope.replaySeq?.let { recordCursor(it) }
        _frames.emit(envelope)
    }

    /**
     * A binary frame (§1.1) only ever answers a request of ours that asked for
     * `binary: true`: it goes to that request's progress handler and nowhere else,
     * never to [frames] and never to the cursor. Anything else is dropped.
     */
    private suspend fun handleBinaryFrame(socketEpoch: Int, bytes: ByteArray) {
        if (socketEpoch != epoch || bytes.size > MAX_INBOUND_FRAME_BYTES) return
        // Format 2 (§1.2) is an ordinary text frame, deflated: any type, seq and replay included.
        if (bytes.isNotEmpty() && bytes[0].toInt() and 0xff == Envelope.COMPRESSED_FORMAT) {
            Envelope.inflate(bytes, MAX_INBOUND_FRAME_BYTES)?.let { handleFrame(socketEpoch, it) }
            return
        }
        val envelope = Envelope.parseBinary(bytes) ?: return
        val id = envelope.id ?: return
        if (envelope.type !in FrameType.CHUNK_TYPES || !progress.containsKey(id)) return
        deliverChunk(id, envelope)
    }

    private fun deliverChunk(id: String, envelope: Envelope) {
        try {
            progress[id]?.invoke(envelope)
        } catch (error: Exception) {
            pending.remove(id)?.completeExceptionally(error)
            progress.remove(id)
        }
    }

    private fun answerChallenge(challenge: AuthChallenge?) {
        val creds = credentials ?: return
        if (challenge == null) {
            fail(FailureReason.AuthRejected(null))
            return
        }
        if (challenge.audience != creds.audience) {
            fail(FailureReason.AudienceMismatch)
            return
        }
        val name = deviceName()
        val signature = try {
            val message = AuthSigning.canonical(challenge, creds.deviceId, name, appVersion, lastCursor)
            Base64Url.encode(signerFor(creds.profileId).sign(message))
        } catch (e: Exception) {
            fail(FailureReason.SigningFailed(e.message))
            return
        }
        val prove = AuthProve(
            nonce = challenge.nonce,
            audience = challenge.audience,
            issuedAt = challenge.issuedAt,
            expiresAt = challenge.expiresAt,
            deviceId = creds.deviceId,
            deviceName = name,
            appVersion = appVersion,
            resumeCursor = lastCursor,
            signature = signature,
            watchSessions = watchSessions()?.distinct()?.take(MAX_WATCHED_SESSIONS),
            acceptEncoding = listOf(Envelope.DEFLATE_RAW),
        )
        sendLocked(
            Envelope(
                type = FrameType.AUTH_PROVE,
                ts = System.currentTimeMillis(),
                payload = BridgeJson.encodeToJsonElement(AuthProve.serializer(), prove),
            ),
        )
    }

    private fun startPing() {
        pingJob?.cancel()
        val pingEpoch = epoch
        pingJob = scope.launch {
            while (isActive && epoch == pingEpoch) {
                delay(pingIntervalMillis)
                if (epoch == pingEpoch) {
                    sendLocked(Envelope.empty(FrameType.PING))
                }
            }
        }
    }

    private fun recordCursor(seq: Long) {
        val current = lastCursor
        if (seq < 0 || (current != null && seq <= current)) return
        lastCursor = seq
        credentials?.let { cursorStore.save(it.profileId, seq) }
    }

    private fun replaceCursor(seq: Long) {
        if (seq < 0) return
        lastCursor = seq
        credentials?.let { cursorStore.save(it.profileId, seq) }
    }

    private fun sendLocked(envelope: Envelope): Boolean = socket?.send(envelope.encode()) ?: false

    private fun failAllPending() {
        if (pending.isEmpty()) return
        val waiters = pending.values.toList()
        pending.clear()
        progress.clear()
        waiters.forEach { it.completeExceptionally(NotConnectedException()) }
    }

    private inner class Listener(private val socketEpoch: Int) : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            scope.launch { handleFrame(socketEpoch, text) }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            val raw = bytes.toByteArray()
            scope.launch { handleBinaryFrame(socketEpoch, raw) }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
            scope.launch { handleTransportLoss(socketEpoch, code, reason, null) }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            scope.launch { handleTransportLoss(socketEpoch, code, reason, null) }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            val retryAfter = response?.takeIf { it.code == 429 }?.header("Retry-After")?.toLongOrNull()
            val retryAfterMillis = retryAfter?.let { (it * 1000).coerceIn(0, MAX_RETRY_AFTER_MILLIS) } ?: 0
            scope.launch { handleTransportLoss(socketEpoch, null, null, t, retryAfterMillis) }
        }
    }

    companion object {
        /**
         * The biggest frame this client takes, declared to the bridge as
         * `maxFrameBytes`. OkHttp has no receive limit of its own, unlike iOS
         * (1 MiB by default), so the same 32 MiB ceiling as iOS is used.
         */
        const val MAX_INBOUND_FRAME_BYTES = 32 * 1024 * 1024

        /**
         * 4401 (bad proof) and 4500 (version mismatch) need the user. 4402 is only an
         * auth timeout (a slow network, a phone that dozed mid-handshake), so it retries.
         */
        private val AUTH_FAILURE_CODES = setOf(4401, 4500)
        /** The bridge takes at most this many `watchSessions` (§2.2). */
        const val MAX_WATCHED_SESSIONS = 16
        /** A handshake younger than this is not aborted by [poke]; connectTimeout is 10 s. */
        private const val HANDSHAKE_GRACE_NANOS = 15_000_000_000L
        /** The bridge blocks for up to 15 min; retry at least every minute so recovery is seen. */
        private const val MAX_RETRY_AFTER_MILLIS = 60_000L
    }
}

/** Exponential backoff with jitter: 1 s doubling to a 30 s ceiling (PRD M-7). */
class Backoff(private val random: Random = Random.Default) {
    fun delayMillis(attempt: Int): Long {
        val capped = attempt.coerceIn(1, 6)
        val base = 1L shl (capped - 1)
        val jittered = base * 1000 * (0.8 + random.nextDouble() * 0.4)
        return jittered.toLong().coerceAtMost(30_000)
    }
}
