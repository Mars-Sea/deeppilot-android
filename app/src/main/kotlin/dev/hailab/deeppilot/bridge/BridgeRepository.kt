package dev.hailab.deeppilot.bridge

import android.graphics.Bitmap
import dev.hailab.deeppilot.attachments.AttachmentImages
import dev.hailab.deeppilot.core.attachments.PromptAttachment
import dev.hailab.deeppilot.core.attachments.promptDocuments
import dev.hailab.deeppilot.core.attachments.promptImages
import dev.hailab.deeppilot.core.bridge.BridgeClient
import dev.hailab.deeppilot.core.bridge.ConnectionState
import dev.hailab.deeppilot.core.bridge.HostCredentials
import dev.hailab.deeppilot.core.bridge.NotConnectedException
import dev.hailab.deeppilot.core.pairing.PairingClient
import dev.hailab.deeppilot.core.pairing.PairingLink
import dev.hailab.deeppilot.core.protocol.ApprovalRespond
import dev.hailab.deeppilot.core.protocol.ArchivedSessionsSnapshot
import dev.hailab.deeppilot.core.protocol.AttachmentData
import dev.hailab.deeppilot.core.protocol.AttachmentRequest
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
import dev.hailab.deeppilot.core.protocol.QuestionAnswer
import dev.hailab.deeppilot.core.protocol.QuestionRespond
import dev.hailab.deeppilot.core.protocol.SelectModelRequest
import dev.hailab.deeppilot.core.protocol.SendPrompt
import dev.hailab.deeppilot.core.protocol.SessionCreate
import dev.hailab.deeppilot.core.protocol.SessionCreated
import dev.hailab.deeppilot.core.protocol.SessionEvent
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
import dev.hailab.deeppilot.core.protocol.WelcomeCapabilities
import dev.hailab.deeppilot.core.protocol.Workspace
import dev.hailab.deeppilot.core.protocol.WorkspaceCreate
import dev.hailab.deeppilot.core.protocol.WorkspaceCreated
import dev.hailab.deeppilot.core.protocol.WorkspacesSnapshot
import dev.hailab.deeppilot.core.session.NotificationDeduper
import dev.hailab.deeppilot.core.session.OpenSession
import dev.hailab.deeppilot.core.session.SessionList
import dev.hailab.deeppilot.core.session.SessionReducer
import dev.hailab.deeppilot.data.DeviceIdentity
import dev.hailab.deeppilot.data.InstanceStore
import dev.hailab.deeppilot.notify.Notifier
import dev.hailab.deeppilot.security.DeviceKeys
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    private val deduper = NotificationDeduper()

    fun start() {
        scope.launch { client.frames.collect { handle(it) } }
        scope.launch {
            client.state.collect { state ->
                if (state is ConnectionState.Authenticated) {
                    _capabilities.value = state.welcome.capabilities
                    refresh()
                }
            }
        }
        _host.value?.let { client.start(it) }
    }

    // ---------- pairing ----------

    /** Pairs with the host in [link] and connects. Throws PairingFailure / PairingException. */
    suspend fun pair(link: PairingLink) {
        val profileId = UUID.randomUUID().toString()
        val publicKey = withContext(Dispatchers.IO) { keys.create(profileId) }
        val paired = try {
            PairingClient(http).pair(link, publicKey, identity.deviceName, appVersion)
        } catch (e: Exception) {
            keys.delete(profileId)
            throw e
        }
        // Phase 1 keeps one host: pairing again replaces the previous one locally.
        _host.value?.let { forget(it) }
        val credentials = HostCredentials(
            profileId = profileId,
            host = paired.host,
            deviceId = paired.deviceId,
            audience = paired.audience,
            tlsFingerprint = paired.tlsFingerprint,
        )
        store.saveHost(credentials)
        _host.value = credentials
        client.start(credentials)
    }

    /** Revokes this device on the host when it supports that, then forgets the host. */
    suspend fun unpair() {
        val current = _host.value ?: return
        if (connection.value is ConnectionState.Authenticated && _capabilities.value?.deviceRevoke == true) {
            runCatching { client.request(FrameType.DEVICE_REVOKE, timeoutMillis = 5_000) }
        }
        forget(current)
    }

    private fun forget(credentials: HostCredentials) {
        client.stop()
        keys.delete(credentials.profileId)
        store.clearHost(credentials.profileId)
        _host.value = null
        _capabilities.value = null
        _sessions.value = emptyList()
        _approvals.value = emptyList()
        _questions.value = emptyList()
        _openSession.value = null
        notifier.cancelAll()
        scope.launch { images.clear(credentials.profileId) }
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
    }

    fun openSession(sessionId: String) {
        visibility.setViewingSession(sessionId)
        notifier.cancelSession(sessionId)
        if (_openSession.value?.sessionId != sessionId) {
            val running = _sessions.value.firstOrNull { it.id == sessionId }?.status == "running"
            _openSession.value = OpenSession(sessionId, turnRunning = running)
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
            val sent = client.send(
                FrameType.SESSION_HISTORY,
                HistoryRequest.serializer(),
                HistoryRequest(current.sessionId, beforeSeq = oldest),
            )
            if (!sent) _openSession.update { it?.copy(loadingHistory = false) }
        }
    }

    // ---------- writes (throw on failure so the screen can say so) ----------

    suspend fun sendPrompt(sessionId: String, text: String, attachments: List<PromptAttachment> = emptyList()) {
        requireAuthenticated()
        client.request(
            FrameType.SESSION_SEND_PROMPT,
            SendPrompt.serializer(),
            SendPrompt(sessionId, text, images = attachments.promptImages(), documents = attachments.promptDocuments()),
            timeoutMillis = PromptAttachment.sendTimeoutMillis(attachments),
        )
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
    }

    suspend fun archiveSession(sessionId: String) {
        requireAuthenticated()
        client.request(FrameType.SESSION_ARCHIVE, SessionRef.serializer(), SessionRef(sessionId))
        _sessions.update { list -> list.filterNot { it.id == sessionId } }
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

    private fun requireAuthenticated() {
        if (connection.value !is ConnectionState.Authenticated) throw NotConnectedException()
    }

    // ---------- frames ----------

    private fun handle(envelope: Envelope) {
        when (envelope.type) {
            FrameType.RESYNC -> refresh()
            FrameType.SESSIONS_SNAPSHOT -> envelope.decode(SessionsSnapshot.serializer())?.let { snapshot ->
                _sessions.update { current ->
                    if (snapshot.full) SessionList.sorted(snapshot.sessions)
                    else SessionList.applyDelta(current, SessionsDelta(upserted = snapshot.sessions))
                }
                syncTurnRunning()
            }
            FrameType.SESSIONS_DELTA -> envelope.decode(SessionsDelta.serializer())?.let { delta ->
                _sessions.update { SessionList.applyDelta(it, delta) }
                syncTurnRunning()
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
            }
            FrameType.SESSION_EVENT -> envelope.decode(SessionEvent.serializer())?.let { event ->
                _openSession.update { open ->
                    if (open == null || open.sessionId != event.sessionId) open
                    else SessionReducer.reduce(open, event).state
                }
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
        const val TAIL_COUNT = 60
    }
}
