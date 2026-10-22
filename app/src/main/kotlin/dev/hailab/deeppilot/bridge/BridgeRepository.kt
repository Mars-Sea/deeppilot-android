package dev.hailab.deeppilot.bridge

import android.graphics.Bitmap
import dev.hailab.deeppilot.attachments.AttachmentImages
import dev.hailab.deeppilot.core.attachments.PromptAttachment
import dev.hailab.deeppilot.core.attachments.promptDocuments
import dev.hailab.deeppilot.core.attachments.promptImages
import dev.hailab.deeppilot.core.bridge.BridgeClient
import dev.hailab.deeppilot.core.bridge.BridgeException
import dev.hailab.deeppilot.core.bridge.ConnectionState
import dev.hailab.deeppilot.core.bridge.HostCredentials
import dev.hailab.deeppilot.core.bridge.NotConnectedException
import dev.hailab.deeppilot.core.cache.ConversationRetention
import dev.hailab.deeppilot.core.delivery.PendingSend
import dev.hailab.deeppilot.core.hosts.HostProfile
import dev.hailab.deeppilot.core.hosts.HostRoster
import dev.hailab.deeppilot.core.delivery.PromptOutbox
import dev.hailab.deeppilot.core.pairing.PairingClient
import dev.hailab.deeppilot.core.pairing.PairingLink
import dev.hailab.deeppilot.core.protocol.ApprovalRespond
import dev.hailab.deeppilot.core.protocol.ArchivedSessionsSnapshot
import dev.hailab.deeppilot.core.protocol.AttachmentData
import dev.hailab.deeppilot.core.protocol.AttachmentRequest
import dev.hailab.deeppilot.core.protocol.ClientRequestId
import dev.hailab.deeppilot.core.protocol.DirectoryListing
import dev.hailab.deeppilot.core.protocol.DirectoryPicked
import dev.hailab.deeppilot.core.protocol.DirectoryRequest
import dev.hailab.deeppilot.core.protocol.Envelope
import dev.hailab.deeppilot.core.protocol.FrameType
import dev.hailab.deeppilot.core.protocol.HistoryPage
import dev.hailab.deeppilot.core.protocol.HistoryRequest
import dev.hailab.deeppilot.core.protocol.ModelSelection
import dev.hailab.deeppilot.core.protocol.Notify
import dev.hailab.deeppilot.core.protocol.NotifyCategory
import dev.hailab.deeppilot.core.protocol.PendingApproval
import dev.hailab.deeppilot.core.protocol.PendingCleared
import dev.hailab.deeppilot.core.protocol.PendingQuestion
import dev.hailab.deeppilot.core.protocol.PendingSnapshot
import dev.hailab.deeppilot.core.protocol.PromptDeliveryQuery
import dev.hailab.deeppilot.core.protocol.PromptReceipt
import dev.hailab.deeppilot.core.protocol.QuestionAnswer
import dev.hailab.deeppilot.core.protocol.QuestionRespond
import dev.hailab.deeppilot.core.protocol.ScheduleCreate
import dev.hailab.deeppilot.core.protocol.ScheduleDelete
import dev.hailab.deeppilot.core.protocol.ScheduleExpected
import dev.hailab.deeppilot.core.protocol.ScheduleHistory
import dev.hailab.deeppilot.core.protocol.ScheduleHistoryPayload
import dev.hailab.deeppilot.core.protocol.ScheduleHistoryRequest
import dev.hailab.deeppilot.core.protocol.ScheduleListRequest
import dev.hailab.deeppilot.core.protocol.ScheduleSnapshot
import dev.hailab.deeppilot.core.protocol.ScheduleTask
import dev.hailab.deeppilot.core.protocol.ScheduleUpdate
import dev.hailab.deeppilot.core.protocol.ScheduleUpdated
import dev.hailab.deeppilot.core.protocol.SelectModelRequest
import dev.hailab.deeppilot.core.protocol.SendPrompt
import dev.hailab.deeppilot.core.protocol.SessionCreate
import dev.hailab.deeppilot.core.protocol.SessionCreated
import dev.hailab.deeppilot.core.protocol.SessionEvent
import dev.hailab.deeppilot.core.protocol.SessionFork
import dev.hailab.deeppilot.core.protocol.SessionForked
import dev.hailab.deeppilot.core.protocol.SessionModelSelected
import dev.hailab.deeppilot.core.protocol.SessionModels
import dev.hailab.deeppilot.core.protocol.SessionModelsRequest
import dev.hailab.deeppilot.core.protocol.SessionOpen
import dev.hailab.deeppilot.core.protocol.SessionRef
import dev.hailab.deeppilot.core.protocol.SessionRename
import dev.hailab.deeppilot.core.protocol.SessionRenamed
import dev.hailab.deeppilot.core.protocol.SessionSummary
import dev.hailab.deeppilot.core.protocol.SessionTail
import dev.hailab.deeppilot.core.protocol.SessionsDelta
import dev.hailab.deeppilot.core.protocol.SessionsSnapshot
import dev.hailab.deeppilot.core.protocol.Welcome
import dev.hailab.deeppilot.core.protocol.WelcomeCapabilities
import dev.hailab.deeppilot.core.protocol.WireErrorCode
import dev.hailab.deeppilot.core.protocol.Workspace
import dev.hailab.deeppilot.core.protocol.WorkspaceCreate
import dev.hailab.deeppilot.core.protocol.WorkspaceCreated
import dev.hailab.deeppilot.core.protocol.WorkspacesSnapshot
import dev.hailab.deeppilot.core.session.NotificationDeduper
import dev.hailab.deeppilot.core.session.OpenSession
import dev.hailab.deeppilot.core.session.SessionList
import dev.hailab.deeppilot.core.session.SessionReducer
import dev.hailab.deeppilot.data.ConversationCache
import dev.hailab.deeppilot.data.DeviceIdentity
import dev.hailab.deeppilot.data.DraftStore
import dev.hailab.deeppilot.data.InstanceStore
import dev.hailab.deeppilot.data.PromptOutboxStore
import dev.hailab.deeppilot.notify.Notifier
import dev.hailab.deeppilot.security.DeviceKeys
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * App-level state mirrored from the paired bridge: session list, the open
 * session, pending approvals and questions. Screens read the flows; the
 * foreground service only keeps the process (and so the socket) alive.
 */
class BridgeRepository(
    private val client: BridgeClient,
    private val store: InstanceStore,
    private val keys: DeviceKeys,
    private val notifier: Notifier,
    private val visibility: AppVisibility,
    private val http: OkHttpClient,
    private val identity: DeviceIdentity,
    private val appVersion: String,
    private val scope: CoroutineScope,
    private val images: AttachmentImages,
    private val outbox: PromptOutboxStore,
    private val drafts: DraftStore,
    private val cache: ConversationCache,
) {
    val connection: StateFlow<ConnectionState> = client.state

    private val _host = MutableStateFlow(store.loadHost())
    val host: StateFlow<HostCredentials?> = _host.asStateFlow()

    private val _capabilities = MutableStateFlow<WelcomeCapabilities?>(null)
    val capabilities: StateFlow<WelcomeCapabilities?> = _capabilities.asStateFlow()

    private val _sessions = MutableStateFlow<List<SessionSummary>>(emptyList())
    val sessions: StateFlow<List<SessionSummary>> = _sessions.asStateFlow()

    private val _approvals = MutableStateFlow<List<PendingApproval>>(emptyList())
    val approvals: StateFlow<List<PendingApproval>> = _approvals.asStateFlow()

    private val _questions = MutableStateFlow<List<PendingQuestion>>(emptyList())
    val questions: StateFlow<List<PendingQuestion>> = _questions.asStateFlow()

    private val _openSession = MutableStateFlow<OpenSession?>(null)
    val openSession: StateFlow<OpenSession?> = _openSession.asStateFlow()

    private val _schedules = MutableStateFlow<ScheduleState?>(null)
    /** Automation tasks of the session whose task list was last requested. */
    val schedules: StateFlow<ScheduleState?> = _schedules.asStateFlow()

    private val _pendingSends = MutableStateFlow(_host.value?.let { outbox.load(it.profileId) }.orEmpty())
    /** Prompts whose delivery the host has not confirmed (§10), persisted per host. */
    val pendingSends: StateFlow<List<PendingSend>> = _pendingSends.asStateFlow()
    private val outboxLock = Any()
    /** Sends still waiting for their own ack; reconciliation leaves them alone. */
    private val inFlightSends = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var reconciling = false

    private val _roster = MutableStateFlow(store.loadRoster())
    /** Every paired computer; [host] is the one connected (iOS `InstanceManager`). */
    val roster: StateFlow<HostRoster> = _roster.asStateFlow()

    /** Debounced offline-cache writes, per conversation, so streaming does not write per token. */
    private val conversationSaves = ConcurrentHashMap<String, Job>()
    @Volatile private var sessionsSave: Job? = null

    private val deduper = NotificationDeduper()

    fun start() {
        drafts.bind(_host.value?.profileId)
        _host.value?.let { restoreSessions(it.profileId) }

        // Re-sync on every welcome frame, not on the connection StateFlow: a quick
        // reconnect with an identical welcome is conflated away there, and the new
        // socket then never re-opened the conversation on screen.
        scope.launch { client.frames.collect { handle(it) } }
        _host.value?.let { client.start(it) }
    }

    // ---------- hosts ----------

    /**
     * Pairs with the host in [link]. A new host joins the list and is connected
     * only when no other host is; [replacing] re-pairs that host in place, keeping
     * its name and position. Throws PairingFailure / PairingException.
     */
    suspend fun pair(link: PairingLink, name: String = "", replacing: String? = null) {
        val profileId = UUID.randomUUID().toString()
        val publicKey = withContext(Dispatchers.IO) { keys.create(profileId) }
        val paired = try {
            PairingClient(http).pair(link, publicKey, identity.deviceName, appVersion)
        } catch (e: Exception) {
            keys.delete(profileId)
            throw e
        }
        val roster = _roster.value
        // Pairing the same computer again (same audience) re-pairs it instead of listing it twice.
        val old = replacing?.let { id -> roster.profiles.firstOrNull { it.id == id } }
            ?: roster.profiles.firstOrNull { it.audience == paired.audience }
        val profile = HostProfile(
            id = profileId,
            name = HostRoster.displayName(name.ifBlank { old?.name.orEmpty() }),
            host = paired.host,
            deviceId = paired.deviceId,
            audience = paired.audience,
            tlsFingerprint = paired.tlsFingerprint,
        )
        if (old == null) {
            val next = roster.adding(profile)
            if (roster.activeId == null) switchTo(next) else commitRoster(next)
            return
        }
        // The old pairing is dead or about to be: drop its key and local data, keep its slot.
        val next = roster.copy(
            profiles = roster.profiles.map { if (it.id == old.id) profile else it },
            activeId = roster.activeId?.let { if (it == old.id) profile.id else it },
            launchId = roster.launchId?.let { if (it == old.id) profile.id else it },
        )
        if (roster.activeId == old.id) switchTo(next) else commitRoster(next)
        discardLocalData(old.id)
    }

    /** Connects to another paired host; everything shown switches with it. */
    fun selectHost(id: String) {
        if (id == _host.value?.profileId) return
        val next = _roster.value.selecting(id)
        if (next.activeId == id) switchTo(next)
    }

    fun renameHost(id: String, name: String) = commitRoster(_roster.value.renaming(id, name))

    fun setLaunchHost(id: String) = commitRoster(_roster.value.settingLaunch(id))

    /**
     * Forgets a host: the device is revoked on it when it is connected and supports
     * that, then its key and everything cached for it are deleted.
     */
    suspend fun deleteHost(id: String) {
        val roster = _roster.value
        if (roster.profiles.none { it.id == id }) return
        val active = id == _host.value?.profileId
        if (active && connection.value is ConnectionState.Authenticated && _capabilities.value?.deviceRevoke == true) {
            runCatching { client.request(FrameType.DEVICE_REVOKE, timeoutMillis = 5_000) }
        }
        val next = roster.removing(id)
        if (active) switchTo(next) else commitRoster(next)
        discardLocalData(id)
    }

    /** Forgets the connected host. */
    suspend fun unpair() {
        _host.value?.let { deleteHost(it.profileId) }
    }

    private fun discardLocalData(profileId: String) {
        keys.delete(profileId)
        store.clearCursor(profileId)
        outbox.clear(profileId)
        drafts.remove(profileId)
        scope.launch {
            cache.remove(profileId)
            images.clear(profileId)
        }
    }

    private fun commitRoster(next: HostRoster) {
        store.saveRoster(next)
        _roster.value = next
    }

    /** Disconnects, clears what belonged to the previous host and connects [next]'s active host. */
    private fun switchTo(next: HostRoster) {
        sessionsSave?.cancel()
        conversationSaves.values.forEach { it.cancel() }
        conversationSaves.clear()
        visibility.setViewingSession(null)
        _capabilities.value = null
        _sessions.value = emptyList()
        _approvals.value = emptyList()
        _questions.value = emptyList()
        _openSession.value = null
        _schedules.value = null
        notifier.cancelAll()
        commitRoster(next)
        val credentials = next.active?.credentials()
        _host.value = credentials
        drafts.bind(credentials?.profileId)
        _pendingSends.value = credentials?.let { outbox.load(it.profileId) }.orEmpty()
        // start() closes the previous socket itself; a separate stop() could land after it.
        if (credentials == null) {
            client.stop()
        } else {
            restoreSessions(credentials.profileId)
            client.start(credentials)
        }
    }

    // ---------- drafts and offline cache ----------

    /** The unsent composer text of [sessionId] on the connected host. */
    fun draft(sessionId: String): String = drafts.text(sessionId)

    fun saveDraft(sessionId: String, text: String) = drafts.set(sessionId, text)

    /** Shows the cached session list until the host sends its own. */
    private fun restoreSessions(profileId: String) {
        scope.launch {
            val cached = cache.loadSessions(profileId)
            if (cached.isEmpty()) return@launch
            _sessions.update { current -> if (current.isEmpty() && _host.value?.profileId == profileId) cached else current }
        }
    }

    /** Paints a conversation from the cache while the host's tail is on its way, or offline. */
    private fun restoreConversation(profileId: String, sessionId: String) {
        scope.launch {
            val cached = cache.loadConversation(profileId, sessionId) ?: return@launch
            _openSession.update { open ->
                if (open == null || open.sessionId != sessionId || open.messages.isNotEmpty() || _host.value?.profileId != profileId) open
                else open.copy(messages = cached.messages, hasMore = cached.hasMore, loaded = true)
            }
        }
    }

    private fun persistSessions() {
        val profileId = _host.value?.profileId ?: return
        sessionsSave?.cancel()
        sessionsSave = scope.launch {
            delay(CACHE_WRITE_DELAY_MILLIS)
            if (_host.value?.profileId == profileId) cache.saveSessions(profileId, _sessions.value)
        }
    }

    private fun persistConversation(sessionId: String) {
        val profileId = _host.value?.profileId ?: return
        conversationSaves.remove(sessionId)?.cancel()
        conversationSaves[sessionId] = scope.launch {
            delay(CACHE_WRITE_DELAY_MILLIS)
            val open = _openSession.value?.takeIf { it.sessionId == sessionId } ?: return@launch
            if (_host.value?.profileId != profileId || open.messages.isEmpty()) return@launch
            cache.saveConversation(profileId, ConversationRetention.snapshot(open))
        }
    }

    fun reconnect() = client.forceReconnect()

    // ---------- reads ----------

    /** Re-requests everything the screens show; runs after every (re)authentication and resync. */
    fun refresh() {
        scope.launch {
            client.send(FrameType.SESSIONS_LIST)
            if (_capabilities.value?.pendingSnapshot == true) client.send(FrameType.PENDING_LIST)
            _openSession.value?.let { client.send(FrameType.SESSION_OPEN, SessionOpen.serializer(), SessionOpen(it.sessionId, TAIL_COUNT)) }
        }
        _schedules.value?.let { loadSchedules(it.sessionId) }
    }

    fun openSession(sessionId: String) {
        visibility.setViewingSession(sessionId)
        notifier.cancelSession(sessionId)
        if (_openSession.value?.sessionId != sessionId) {
            val running = _sessions.value.firstOrNull { it.id == sessionId }?.status == "running"
            _openSession.value = OpenSession(sessionId, turnRunning = running)
            _host.value?.let { restoreConversation(it.profileId, sessionId) }
        }
        scope.launch { client.send(FrameType.SESSION_OPEN, SessionOpen.serializer(), SessionOpen(sessionId, TAIL_COUNT)) }
    }

    fun closeSession(sessionId: String) {
        if (visibility.viewingSession.value == sessionId) visibility.setViewingSession(null)
        if (_openSession.value?.sessionId == sessionId) _openSession.value = null
        scope.launch { client.send(FrameType.SESSION_CLOSE, SessionRef.serializer(), SessionRef(sessionId)) }
    }

    fun loadOlder() {
        val current = _openSession.value ?: return
        if (!current.hasMore || current.loadingHistory) return
        val oldest = current.messages.firstOrNull()?.seq ?: return
        _openSession.update { it?.copy(loadingHistory = true) }
        scope.launch {
            // §4 requires an id; the page itself is merged by handle() as it passes.
            val loaded = runCatching {
                client.request(
                    FrameType.SESSION_HISTORY,
                    HistoryRequest.serializer(),
                    HistoryRequest(current.sessionId, beforeSeq = oldest),
                    timeoutMillis = 20_000,
                )
            }.isSuccess
            if (!loaded) _openSession.update { if (it?.sessionId == current.sessionId) it.copy(loadingHistory = false) else it }
        }
    }

    // ---------- writes (throw on failure so the screen can say so) ----------

    /**
     * Sends a prompt. With `promptDelivery` the send gets a stable identity that is
     * saved before the request; an ack that never arrives leaves the row "unknown"
     * and throws [PromptUnconfirmedException] rather than inviting a duplicate resend.
     */
    suspend fun sendPrompt(sessionId: String, text: String, attachments: List<PromptAttachment> = emptyList()) {
        requireAuthenticated()
        val timeout = PromptAttachment.sendTimeoutMillis(attachments)
        val payload = SendPrompt(sessionId, text, images = attachments.promptImages(), documents = attachments.promptDocuments())
        val profileId = _host.value?.profileId
        if (_capabilities.value?.promptDelivery != true || profileId == null) {
            client.request(FrameType.SESSION_SEND_PROMPT, SendPrompt.serializer(), payload, timeoutMillis = timeout)
            return
        }
        val pending = PendingSend(ClientRequestId.make(), sessionId, text, attachments.size)
        val id = pending.clientSendId
        commitOutbox(profileId, required = true) { PromptOutbox.add(it, pending) }
        inFlightSends += id
        try {
            val reply = try {
                client.request(FrameType.SESSION_SEND_PROMPT, SendPrompt.serializer(), payload.copy(clientSendId = id), timeoutMillis = timeout)
            } catch (e: Exception) {
                if (e is BridgeException && e.code?.let { it in DEFINITE_REJECTIONS } == true) {
                    commitOutbox(profileId) { PromptOutbox.remove(it, id) }
                    throw e
                }
                commitOutbox(profileId) { PromptOutbox.mark(it, id, PendingSend.Delivery.Unknown) }
                if (e is CancellationException && e !is TimeoutCancellationException) throw e
                throw PromptUnconfirmedException()
            }
            val receipt = reply.decode(PromptReceipt.serializer())?.takeIf { it.clientSendId == id }
            when (receipt?.status) {
                "accepted" -> commitOutbox(profileId) { PromptOutbox.remove(it, id) }
                "rejected" -> {
                    commitOutbox(profileId) { PromptOutbox.remove(it, id) }
                    throw PromptRejectedException()
                }
                else -> {
                    commitOutbox(profileId) { PromptOutbox.mark(it, id, PendingSend.Delivery.Unknown) }
                    throw PromptUnconfirmedException()
                }
            }
        } finally {
            inFlightSends -= id
        }
    }

    /**
     * Asks the host about every unconfirmed send not waiting on its own ack.
     * Accepted rows clear; nothing is ever resent.
     */
    fun reconcileDeliveries() {
        val profileId = _host.value?.profileId ?: return
        if (_capabilities.value?.promptDelivery != true || connection.value !is ConnectionState.Authenticated || reconciling) return
        reconciling = true
        scope.launch {
            try {
                val rows = _pendingSends.value.filter { it.delivery != PendingSend.Delivery.Rejected && it.clientSendId !in inFlightSends }
                for (row in rows) {
                    val receipt = runCatching {
                        client.request(
                            FrameType.SESSION_DELIVERY,
                            PromptDeliveryQuery.serializer(),
                            PromptDeliveryQuery(row.sessionId, row.clientSendId),
                            timeoutMillis = 3_000,
                        ).decode(PromptReceipt.serializer())
                    }.getOrNull() ?: return@launch
                    if (receipt.clientSendId != row.clientSendId || _host.value?.profileId != profileId) return@launch
                    commitOutbox(profileId) { PromptOutbox.apply(it, receipt) }
                }
            } finally {
                reconciling = false
            }
        }
    }

    /** Retires an unconfirmed or rejected marker the user has acknowledged. */
    fun dismissPendingSend(clientSendId: String) {
        val profileId = _host.value?.profileId ?: return
        if (clientSendId in inFlightSends) return
        commitOutbox(profileId) { PromptOutbox.remove(it, clientSendId) }
    }

    /** Applies [change] and persists it; only a [required] write fails the caller. */
    private fun commitOutbox(profileId: String, required: Boolean = false, change: (List<PendingSend>) -> List<PendingSend>) {
        synchronized(outboxLock) {
            val next = change(_pendingSends.value)
            if (!outbox.save(profileId, next) && required) throw OutboxSaveException()
            _pendingSends.value = next
        }
    }

    /** The host's model catalog for [sessionId] (§4); never built into the app. */
    suspend fun loadModels(sessionId: String): SessionModels {
        requireAuthenticated()
        if (_capabilities.value?.models != true) throw UnsupportedFeatureException()
        val reply = client.request(FrameType.SESSION_MODELS, SessionModelsRequest.serializer(), SessionModelsRequest(sessionId))
        return reply.decode(SessionModels.serializer())?.takeIf { it.sessionId == sessionId } ?: throw InvalidResponseException()
    }

    /** Switches the session's route; returns what the host applied. */
    suspend fun selectModel(sessionId: String, selection: ModelSelection): ModelSelection {
        requireAuthenticated()
        if (_capabilities.value?.models != true) throw UnsupportedFeatureException()
        val reply = client.request(
            FrameType.SESSION_SELECT_MODEL,
            SelectModelRequest.serializer(),
            SelectModelRequest(sessionId, selection.provider, selection.model, selection.reasoningEffort?.takeIf { it.isNotEmpty() }),
        )
        return reply.decode(SessionModelSelected.serializer())?.takeIf { it.sessionId == sessionId }?.selected
            ?: throw InvalidResponseException()
    }

    /** One persisted conversation image, from the cache or read back from the host (§5). */
    suspend fun attachmentImage(sessionId: String, attachmentId: String): Bitmap? {
        val profileId = _host.value?.profileId ?: return null
        return images.image(profileId, sessionId, attachmentId) {
            if (connection.value !is ConnectionState.Authenticated) return@image null
            val reply = client.request(
                FrameType.SESSION_ATTACHMENT,
                AttachmentRequest.serializer(),
                AttachmentRequest(sessionId, attachmentId),
                timeoutMillis = 30_000,
            )
            reply.decode(AttachmentData.serializer())?.data?.takeIf { it.isNotEmpty() }
        }
    }

    /** A folder listing on the host; [path] null starts at the host user's home (§4b). */
    suspend fun listDirectory(path: String?): DirectoryListing {
        requireAuthenticated()
        val reply = client.request(FrameType.DIRECTORY_LIST, DirectoryRequest.serializer(), DirectoryRequest(path?.takeIf { it.isNotEmpty() }))
        return reply.decode(DirectoryListing.serializer()) ?: throw InvalidResponseException()
    }

    /** Opens the folder picker on the computer; null when the user cancels there. */
    suspend fun pickDirectoryOnComputer(): String? {
        requireAuthenticated()
        // Someone has to walk to the computer and choose, so allow minutes, not seconds.
        val reply = client.request(FrameType.DIRECTORY_PICK, timeoutMillis = 5 * 60_000)
        return (reply.decode(DirectoryPicked.serializer()) ?: throw InvalidResponseException()).path
    }

    /** Adopts a host folder as a project; adopting the same path twice returns the existing one. */
    suspend fun createWorkspace(path: String): Workspace {
        requireAuthenticated()
        if (_capabilities.value?.projectSelection != true) throw UnsupportedFeatureException()
        val reply = client.request(FrameType.WORKSPACE_CREATE, WorkspaceCreate.serializer(), WorkspaceCreate(path))
        return reply.decode(WorkspaceCreated.serializer())?.workspace ?: throw InvalidResponseException()
    }

    suspend fun cancelTurn(sessionId: String) {
        requireAuthenticated()
        client.request(FrameType.SESSION_CANCEL, SessionRef.serializer(), SessionRef(sessionId))
    }

    suspend fun respondApproval(requestId: String, allow: Boolean) {
        requireAuthenticated()
        client.request(
            FrameType.APPROVAL_RESPOND,
            ApprovalRespond.serializer(),
            ApprovalRespond(requestId, if (allow) "allow" else "deny"),
        )
        clearPending(requestId)
    }

    suspend fun respondQuestion(requestId: String, answers: List<QuestionAnswer>) {
        requireAuthenticated()
        val clean = answers.map { answer -> answer.copy(custom = answer.custom?.takeIf { it.isNotBlank() }) }
        client.request(FrameType.QUESTION_RESPOND, QuestionRespond.serializer(), QuestionRespond(requestId, clean))
        clearPending(requestId)
    }

    /** Projects on the host for the new-session picker (§4b). */
    suspend fun loadWorkspaces(): List<Workspace> {
        requireAuthenticated()
        return client.request(FrameType.WORKSPACES_LIST).decode(WorkspacesSnapshot.serializer())?.workspaces.orEmpty()
    }

    /** Creates an empty session in [workspaceId]; returns its id. */
    suspend fun createSession(workspaceId: String): String? {
        requireAuthenticated()
        val reply = client.request(FrameType.SESSION_CREATE, SessionCreate.serializer(), SessionCreate(workspaceId))
        client.send(FrameType.SESSIONS_LIST)
        return reply.decode(SessionCreated.serializer())?.sessionId
    }

    suspend fun renameSession(sessionId: String, title: String) {
        requireAuthenticated()
        val reply = client.request(FrameType.SESSION_RENAME, SessionRename.serializer(), SessionRename(sessionId, title))
        // The host may normalize the name; show what it accepted.
        val accepted = reply.decode(SessionRenamed.serializer())?.title?.takeIf { it.isNotEmpty() } ?: title
        _sessions.update { list -> list.map { if (it.id == sessionId) it.copy(title = accepted) else it } }
        persistSessions()
    }

    suspend fun archiveSession(sessionId: String) {
        requireAuthenticated()
        client.request(FrameType.SESSION_ARCHIVE, SessionRef.serializer(), SessionRef(sessionId))
        _sessions.update { list -> list.filterNot { it.id == sessionId } }
        persistSessions()
    }

    /** Archived rows, fetched on demand for the archived screen (§4c). */
    suspend fun loadArchivedSessions(): List<SessionSummary> {
        requireAuthenticated()
        if (_capabilities.value?.sessionRestore != true) throw UnsupportedFeatureException()
        val reply = client.request(FrameType.SESSIONS_ARCHIVED)
        return reply.decode(ArchivedSessionsSnapshot.serializer())?.sessions ?: throw InvalidResponseException()
    }

    /** Restores an archived session; the bridge then re-sends it in a sessions delta. */
    suspend fun unarchiveSession(sessionId: String) {
        requireAuthenticated()
        if (_capabilities.value?.sessionRestore != true) throw UnsupportedFeatureException()
        client.request(FrameType.SESSION_UNARCHIVE, SessionRef.serializer(), SessionRef(sessionId))
    }

    // ---------- session fork and schedules (optional v2 extensions) ----------

    /** Branches [sessionId] at the message boundary [atSeq]; returns the new session's id. */
    suspend fun forkSession(sessionId: String, atSeq: Long?): String {
        requireAuthenticated()
        if (_capabilities.value?.sessionFork != true) throw UnsupportedFeatureException()
        val reply = client.request(
            FrameType.SESSION_FORK,
            SessionFork.serializer(),
            SessionFork(ClientRequestId.make(), sessionId, atSeq),
            timeoutMillis = 30_000,
        )
        val forked = reply.decode(SessionForked.serializer())
            ?.takeIf { it.sourceSessionId == sessionId && it.sessionId.isNotEmpty() }
            ?: throw InvalidResponseException()
        client.send(FrameType.SESSIONS_LIST)
        return forked.sessionId
    }

    /** Reloads the automation tasks of [sessionId] into [schedules]. */
    fun loadSchedules(sessionId: String) {
        if (_capabilities.value?.schedules != true || connection.value !is ConnectionState.Authenticated) return
        _schedules.update { current ->
            if (current?.sessionId == sessionId) current.copy(loading = true, error = null) else ScheduleState(sessionId, loading = true)
        }
        scope.launch {
            var tasks: List<ScheduleTask>? = null
            val error = runCatching {
                val reply = client.request(FrameType.SCHEDULE_LIST, ScheduleListRequest.serializer(), ScheduleListRequest(sessionId))
                tasks = reply.decode(ScheduleSnapshot.serializer())?.takeIf { it.sessionId == sessionId }?.tasks
                    ?: throw InvalidResponseException()
            }.exceptionOrNull()
            _schedules.update { current ->
                if (current?.sessionId != sessionId) current
                else current.copy(tasks = tasks ?: current.tasks, loading = false, error = error)
            }
        }
    }

    suspend fun loadScheduleHistory(sessionId: String, taskId: String): ScheduleHistory {
        requireSchedules()
        val reply = client.request(
            FrameType.SCHEDULE_HISTORY,
            ScheduleHistoryRequest.serializer(),
            ScheduleHistoryRequest(sessionId, taskId),
        )
        return reply.decode(ScheduleHistoryPayload.serializer())
            ?.takeIf { it.sessionId == sessionId && it.history.id == taskId }?.history
            ?: throw InvalidResponseException()
    }

    /** Creates a task from [request]; its idempotency key and session are filled in here. */
    suspend fun createSchedule(sessionId: String, request: ScheduleCreate) {
        requireSchedules()
        val body = request.copy(clientRequestId = ClientRequestId.make(), sessionId = sessionId)
        val reply = client.request(FrameType.SCHEDULE_CREATE, ScheduleCreate.serializer(), body)
        val task = reply.decode(ScheduleUpdated.serializer())
            ?.takeIf { it.clientRequestId == body.clientRequestId && it.sessionId == sessionId }?.task
            ?: throw InvalidResponseException()
        replaceSchedule(sessionId, task)
    }

    /** Renames or rewords [task]; the host refuses the edit if [task] is stale. */
    suspend fun updateSchedule(sessionId: String, task: ScheduleTask, title: String?, prompt: String?) {
        requireSchedules()
        val body = ScheduleUpdate(ClientRequestId.make(), sessionId, task.id, ScheduleExpected.of(task), title, prompt)
        val reply = client.request(FrameType.SCHEDULE_UPDATE, ScheduleUpdate.serializer(), body)
        val updated = reply.decode(ScheduleUpdated.serializer())
            ?.takeIf { it.clientRequestId == body.clientRequestId && it.sessionId == sessionId }?.task
            ?: throw InvalidResponseException()
        replaceSchedule(sessionId, updated)
    }

    suspend fun deleteSchedule(sessionId: String, taskId: String) {
        requireSchedules()
        val body = ScheduleDelete(ClientRequestId.make(), sessionId, taskId)
        val reply = client.request(FrameType.SCHEDULE_DELETE, ScheduleDelete.serializer(), body)
        reply.decode(ScheduleUpdated.serializer())
            ?.takeIf { it.clientRequestId == body.clientRequestId && it.sessionId == sessionId && it.deleted }
            ?: throw InvalidResponseException()
        _schedules.update { current ->
            if (current?.sessionId != sessionId) current else current.copy(tasks = current.tasks.filterNot { it.id == taskId })
        }
    }

    private fun replaceSchedule(sessionId: String, task: ScheduleTask) {
        _schedules.update { current ->
            if (current?.sessionId != sessionId) current
            else current.copy(tasks = (current.tasks.filterNot { it.id == task.id } + task).sortedBy { it.scheduledAt })
        }
    }

    private fun requireSchedules() {
        requireAuthenticated()
        if (_capabilities.value?.schedules != true) throw UnsupportedFeatureException()
    }

    private fun requireAuthenticated() {
        if (connection.value !is ConnectionState.Authenticated) throw NotConnectedException()
    }

    // ---------- frames ----------

    private fun handle(envelope: Envelope) {
        when (envelope.type) {
            FrameType.WELCOME -> {
                _capabilities.value = envelope.decode(Welcome.serializer())?.capabilities
                refresh()
                reconcileDeliveries()
            }
            FrameType.RESYNC -> refresh()
            FrameType.SCHEDULE_CHANGED -> _schedules.value?.let { loadSchedules(it.sessionId) }
            FrameType.SESSION_RENAMED -> envelope.decode(SessionRenamed.serializer())?.let { renamed ->
                val id = renamed.sessionId ?: return@let
                val title = renamed.title ?: return@let
                _sessions.update { list -> list.map { if (it.id == id) it.copy(title = title) else it } }
                persistSessions()
            }
            FrameType.SESSION_ARCHIVED -> envelope.decode(SessionRef.serializer())?.let { archived ->
                _sessions.update { list -> list.filterNot { it.id == archived.sessionId } }
                persistSessions()
            }
            FrameType.SESSIONS_SNAPSHOT -> envelope.decode(SessionsSnapshot.serializer())?.let { snapshot ->
                _sessions.update { current ->
                    if (snapshot.full) SessionList.sorted(snapshot.sessions)
                    else SessionList.applyDelta(current, SessionsDelta(upserted = snapshot.sessions))
                }
                syncTurnRunning()
                persistSessions()
            }
            FrameType.SESSIONS_DELTA -> envelope.decode(SessionsDelta.serializer())?.let { delta ->
                _sessions.update { SessionList.applyDelta(it, delta) }
                syncTurnRunning()
                persistSessions()
            }
            FrameType.SESSION_TAIL -> envelope.decode(SessionTail.serializer())?.let { tail ->
                _openSession.update { open ->
                    if (open == null || open.sessionId != tail.sessionId) return@update open
                    open.copy(
                        messages = SessionReducer.mergeTail(open.messages, tail.messages, tail.oldestSeq),
                        hasMore = tail.hasMore,
                        loaded = true,
                    )
                }
                persistConversation(tail.sessionId)
            }
            FrameType.HISTORY_PAGE -> envelope.decode(HistoryPage.serializer())?.let { page ->
                _openSession.update { open ->
                    if (open == null || open.sessionId != page.sessionId) return@update open
                    open.copy(
                        messages = SessionReducer.mergeHistory(open.messages, page.messages).first,
                        hasMore = page.hasMore,
                        loadingHistory = false,
                    )
                }
                persistConversation(page.sessionId)
            }
            FrameType.SESSION_EVENT -> envelope.decode(SessionEvent.serializer())?.let { event ->
                _openSession.update { open ->
                    if (open == null || open.sessionId != event.sessionId) open
                    else SessionReducer.reduce(open, event).state
                }
                persistConversation(event.sessionId)
            }
            FrameType.PENDING_APPROVAL -> envelope.decode(PendingApproval.serializer())?.let { approval ->
                _approvals.update { list -> list.filterNot { it.requestId == approval.requestId } + approval }
                if (!notifiesEverything()) {
                    fallbackNotify("approval:${approval.requestId}", NotifyCategory.APPROVAL_REQUIRED, approval.sessionId, approval.toolName, approval.summary)
                }
            }
            FrameType.PENDING_QUESTION -> envelope.decode(PendingQuestion.serializer())?.let { question ->
                _questions.update { list -> list.filterNot { it.requestId == question.requestId } + question }
                if (!notifiesEverything()) {
                    fallbackNotify(
                        "question:${question.requestId}",
                        NotifyCategory.QUESTION_ASKED,
                        question.sessionId,
                        "",
                        question.questions.firstOrNull()?.question.orEmpty(),
                    )
                }
            }
            FrameType.PENDING_SNAPSHOT -> envelope.decode(PendingSnapshot.serializer())?.let { snapshot ->
                _approvals.value = snapshot.approvals
                _questions.value = snapshot.questions
            }
            FrameType.PENDING_CLEARED -> envelope.decode(PendingCleared.serializer())?.let { clearPending(it.requestId) }
            FrameType.NOTIFY -> envelope.decode(Notify.serializer())?.let { show(it) }
        }
    }

    private fun clearPending(requestId: String) {
        _approvals.update { list -> list.filterNot { it.requestId == requestId } }
        _questions.update { list -> list.filterNot { it.requestId == requestId } }
    }

    /** The session list status is the source of truth for "a turn is running" between events. */
    private fun syncTurnRunning() {
        val open = _openSession.value ?: return
        val summary = _sessions.value.firstOrNull { it.id == open.sessionId } ?: return
        val running = summary.status == "running"
        if (running != open.turnRunning) _openSession.update { it?.copy(turnRunning = running) }
    }

    private fun notifiesEverything() = _capabilities.value?.notifyAllCategories == true

    private fun fallbackNotify(id: String, category: String, sessionId: String, title: String, body: String) {
        show(Notify(notificationId = id, category = category, sessionId = sessionId, title = title, body = body, ts = System.currentTimeMillis()))
    }

    private fun show(notify: Notify) {
        val host = _host.value ?: return
        if (notify.hostAudience != null && notify.hostAudience != host.audience) return
        if (!deduper.firstSighting(notify.notificationId)) return
        if (visibility.isViewing(notify.sessionId)) return
        val sessionTitle = _sessions.value.firstOrNull { it.id == notify.sessionId }?.title
        notifier.show(notify, sessionTitle)
    }

    private companion object {
        /** Errors that prove the host did not take the prompt (iOS `isDefiniteRejection`). */
        val DEFINITE_REJECTIONS = setOf(
            WireErrorCode.E_AUTH,
            WireErrorCode.E_FORBIDDEN,
            WireErrorCode.E_PROTOCOL,
            WireErrorCode.E_NOT_FOUND,
            WireErrorCode.E_BUSY,
            WireErrorCode.E_UNSUPPORTED,
        )
        const val TAIL_COUNT = 60
        const val CACHE_WRITE_DELAY_MILLIS = 800L
    }
}

/** Automation tasks of one session, as loaded by [BridgeRepository.loadSchedules]. */
data class ScheduleState(
    val sessionId: String,
    val tasks: List<ScheduleTask> = emptyList(),
    val loading: Boolean = false,
    val error: Throwable? = null,
)
