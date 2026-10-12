package dev.hailab.deeppilot.core.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Kotlin mirror of the bridge protocol v2. The normative spec is PROTOCOL.md in
// Mars-Sea/dsh-deeppilot; src/protocol.ts there and ProtocolModels.swift in the
// iOS app are the other mirrors. Optional fields stay nullable so frames from
// older bridges still decode.

/** Frame type names used by this client. */
object FrameType {
    const val AUTH_CHALLENGE = "s2c.auth.challenge"
    const val AUTH_PROVE = "c2s.auth.prove"
    const val WELCOME = "s2c.welcome"
    const val PING = "c2s.ping"
    const val PONG = "s2c.pong"
    const val ERROR = "s2c.error"
    const val ACK = "s2c.ack"
    const val RESUME_DONE = "s2c.resume.done"
    const val RESYNC = "s2c.resync"

    const val SESSIONS_LIST = "c2s.sessions.list"
    const val SESSIONS_SNAPSHOT = "s2c.sessions.snapshot"
    const val SESSIONS_DELTA = "s2c.sessions.delta"

    const val SESSION_OPEN = "c2s.session.open"
    const val SESSION_CLOSE = "c2s.session.close"
    const val SESSION_TAIL = "s2c.session.tail"
    const val SESSION_HISTORY = "c2s.session.history"
    const val HISTORY_PAGE = "s2c.history.page"
    const val SESSION_EVENT = "s2c.session.event"
    const val SESSION_SEND_PROMPT = "c2s.session.sendPrompt"
    const val SESSION_CANCEL = "c2s.session.cancel"

    const val PENDING_APPROVAL = "s2c.pending.approval"
    const val PENDING_QUESTION = "s2c.pending.question"
    const val PENDING_CLEARED = "s2c.pending.cleared"
    const val PENDING_LIST = "c2s.pending.list"
    const val PENDING_SNAPSHOT = "s2c.pending.snapshot"
    const val APPROVAL_RESPOND = "c2s.approval.respond"
    const val QUESTION_RESPOND = "c2s.question.respond"

    const val NOTIFY = "s2c.notify"
    const val DEVICE_REVOKE = "c2s.device.revoke"

    const val WORKSPACES_LIST = "c2s.workspaces.list"
    const val WORKSPACES_SNAPSHOT = "s2c.workspaces.snapshot"
    const val SESSION_CREATE = "c2s.session.create"
    const val SESSION_RENAME = "c2s.session.rename"
    const val SESSION_ARCHIVE = "c2s.session.archive"
    const val SESSIONS_ARCHIVED = "c2s.sessions.archived"
    const val SESSION_UNARCHIVE = "c2s.session.unarchive"

    const val SESSION_MODELS = "c2s.session.models"
    const val SESSION_SELECT_MODEL = "c2s.session.selectModel"
    const val SESSION_ATTACHMENT = "c2s.session.attachment"
    const val DIRECTORY_LIST = "c2s.directory.list"
    const val DIRECTORY_PICK = "c2s.directory.pick"
    const val WORKSPACE_CREATE = "c2s.workspace.create"

    const val SESSION_RENAMED = "s2c.session.renamed"
    const val SESSION_ARCHIVED = "s2c.session.archived"
    const val SESSION_FORK = "c2s.session.fork"

    const val SCHEDULE_LIST = "c2s.schedule.list"
    const val SCHEDULE_HISTORY = "c2s.schedule.history"
    const val SCHEDULE_CREATE = "c2s.schedule.create"
    const val SCHEDULE_UPDATE = "c2s.schedule.update"
    const val SCHEDULE_DELETE = "c2s.schedule.delete"
    const val SCHEDULE_CHANGED = "s2c.schedule.changed"
}

/** Device scopes requested at pairing; the product grants all of them (§2.2). */
val DefaultScopes = listOf(
    "sessions.read",
    "prompt.send",
    "sessions.manage",
    "interactions.respond",
    "notifications.register",
    "schedule.manage",
)

// ---------- pairing (HTTPS) ----------

@Serializable
data class PairRequest(
    val v: Int = PROTOCOL_VERSION,
    val code: String,
    /** 65-byte uncompressed X9.63 P-256 public key, base64url. */
    val publicKey: String,
    val deviceName: String,
    val appVersion: String,
    val scopes: List<String> = DefaultScopes,
)

@Serializable
data class PairResponse(
    val ok: Boolean = false,
    val v: Int? = null,
    val deviceId: String? = null,
    val audience: String? = null,
    val scopes: List<String>? = null,
    val tlsFingerprint: String? = null,
)

// ---------- handshake ----------

@Serializable
data class AuthChallenge(
    val nonce: String,
    val audience: String,
    val issuedAt: Long,
    val expiresAt: Long,
)

@Serializable
data class AuthProve(
    val nonce: String,
    val audience: String,
    val issuedAt: Long,
    val expiresAt: Long,
    val deviceId: String,
    val deviceName: String,
    val appVersion: String,
    val resumeCursor: Long? = null,
    val signature: String,
)

@Serializable
data class WelcomeCapabilities(
    val historyPaging: Boolean = false,
    val replay: Boolean = false,
    val approvals: Boolean = false,
    val questions: Boolean = false,
    val pendingSnapshot: Boolean = false,
    val promptDelivery: Boolean = false,
    val notifyAllCategories: Boolean = false,
    val models: Boolean = false,
    val sessionManagement: Boolean = false,
    val sessionFork: Boolean = false,
    val sessionRestore: Boolean = false,
    val projectSelection: Boolean = false,
    val schedules: Boolean = false,
    val push: Boolean = false,
    val widgetPush: Boolean = false,
    val liveActivityPush: Boolean = false,
    val deviceRevoke: Boolean = false,
)

@Serializable
data class Welcome(
    val protocolVersion: Int,
    val serverVersion: String? = null,
    val deviceId: String,
    val scopes: List<String> = emptyList(),
    val capabilities: WelcomeCapabilities = WelcomeCapabilities(),
    val cursor: Long = 0,
    val resumed: Boolean = false,
)

@Serializable
data class ErrorPayload(val code: String? = null, val message: String? = null)

// ---------- session list ----------

@Serializable
data class TodoCounts(val done: Int = 0, val total: Int = 0)

@Serializable
data class TodoItem(val content: String, val status: String = "pending")

@Serializable
data class SessionSummary(
    val id: String,
    val title: String = "",
    /** running | idle | error | unknown */
    val status: String = "unknown",
    val lastActivityTs: Long = 0,
    val todos: TodoCounts? = null,
    val todoItems: List<TodoItem>? = null,
    val activity: String? = null,
    val pendingApproval: Boolean = false,
    val pendingQuestion: Boolean = false,
    val workspaceLabel: String? = null,
    val workspaceId: String? = null,
    val workspacePath: String? = null,
    val archived: Boolean = false,
)

@Serializable
data class SessionsSnapshot(val full: Boolean = true, val sessions: List<SessionSummary> = emptyList())

/** Answer to `c2s.sessions.archived` (§4c); rows never appear in the live list. */
@Serializable
data class ArchivedSessionsSnapshot(val sessions: List<SessionSummary> = emptyList())

@Serializable
data class SessionsDelta(
    val upserted: List<SessionSummary> = emptyList(),
    val removedIds: List<String> = emptyList(),
)

// ---------- messages ----------

@Serializable
data class ToolInfo(
    val name: String = "tool",
    /** running | ok | error (the reducer also writes done for a merged result) */
    val state: String = "running",
    val summary: String = "",
    val callId: String? = null,
)

@Serializable
data class MessageAttachment(
    val kind: String,
    val name: String? = null,
    val mediaType: String? = null,
    val attachmentId: String? = null,
    val width: Double? = null,
    val height: Double? = null,
    val truncated: Boolean? = null,
)

@Serializable
data class MessageContext(val label: String? = null, val form: String? = null)

@Serializable
data class MessageProjection(
    val seq: Long,
    /** user | assistant | tool | system | error */
    val role: String,
    val text: String? = null,
    val thinking: String? = null,
    val streaming: Boolean = false,
    val tool: ToolInfo? = null,
    val attachments: List<MessageAttachment>? = null,
    val context: MessageContext? = null,
    val ts: Long = 0,
    val truncated: Boolean = false,
)

@Serializable
data class SessionOpen(val sessionId: String, val tailCount: Int? = null)

@Serializable
data class SessionRef(val sessionId: String)

@Serializable
data class SessionTail(
    val sessionId: String,
    val messages: List<MessageProjection> = emptyList(),
    val oldestSeq: Long = 0,
    val hasMore: Boolean = false,
)

@Serializable
data class HistoryRequest(val sessionId: String, val beforeSeq: Long, val limit: Int = 100)

@Serializable
data class HistoryPage(
    val sessionId: String,
    val messages: List<MessageProjection> = emptyList(),
    val hasMore: Boolean = false,
)

@Serializable
data class SessionEvent(
    val sessionId: String,
    val kind: String,
    val seq: Long,
    val data: JsonElement? = null,
)

@Serializable
data class DeltaBody(val text: String? = null)

@Serializable
data class TurnEndBody(val ok: Boolean? = null)

@Serializable
data class ToolEndBody(
    val ok: Boolean? = null,
    val callId: String? = null,
    val seq: Long? = null,
    val summary: String? = null,
    val attachments: List<MessageAttachment>? = null,
    val ts: Long? = null,
)

// ---------- write path ----------

@Serializable
data class PromptImage(val mediaType: String, val data: String, val name: String? = null)

@Serializable
data class PromptDocument(
    val mediaType: String,
    val name: String,
    val text: String,
    val truncated: Boolean? = null,
)

@Serializable
data class SendPrompt(
    val sessionId: String,
    val text: String,
    val images: List<PromptImage>? = null,
    val documents: List<PromptDocument>? = null,
)

// ---------- pending interactions ----------

@Serializable
data class PendingApproval(
    val requestId: String,
    val sessionId: String,
    val toolName: String = "",
    val summary: String = "",
    val toolArguments: String? = null,
    /** read | write | destructive */
    val riskLevel: String = "write",
)

@Serializable
data class QuestionOption(val label: String, val description: String? = null)

@Serializable
data class QuestionItem(
    val id: String,
    val question: String,
    val multiSelect: Boolean = false,
    val options: List<QuestionOption> = emptyList(),
)

@Serializable
data class PendingQuestion(
    val requestId: String,
    val sessionId: String,
    val questions: List<QuestionItem> = emptyList(),
)

@Serializable
data class PendingSnapshot(
    val approvals: List<PendingApproval> = emptyList(),
    val questions: List<PendingQuestion> = emptyList(),
)

@Serializable
data class PendingCleared(val requestId: String)

@Serializable
data class ApprovalRespond(
    val requestId: String,
    /** allow | deny */
    val decision: String,
    val reason: String? = null,
)

@Serializable
data class QuestionAnswer(
    val id: String,
    val selected: List<String> = emptyList(),
    /** Only when the user typed non-blank text; never an empty string (§5). */
    val custom: String? = null,
)

@Serializable
data class QuestionRespond(val requestId: String, val answers: List<QuestionAnswer>)

// ---------- notifications ----------

object NotifyCategory {
    const val TURN_COMPLETED = "turn.completed"
    const val APPROVAL_REQUIRED = "approval.required"
    const val QUESTION_ASKED = "question.asked"
    const val SESSION_ERROR = "session.error"
}

@Serializable
data class Notify(
    val hostAudience: String? = null,
    val notificationId: String,
    val category: String,
    val sessionId: String,
    val title: String = "",
    val body: String = "",
    val ts: Long = 0,
)

@Serializable
data class RevokeAck(val revoked: Boolean = false)

// ---------- projects and session management (§4b, §4c) ----------

@Serializable
data class Workspace(
    val id: String,
    val title: String = "",
    val path: String = "",
    val sessionIds: List<String> = emptyList(),
)

@Serializable
data class WorkspacesSnapshot(val workspaces: List<Workspace> = emptyList())

@Serializable
data class SessionCreate(val workspaceId: String)

@Serializable
data class SessionCreated(val sessionId: String? = null)

@Serializable
data class SessionRename(val sessionId: String, val title: String)

@Serializable
data class SessionRenamed(val sessionId: String? = null, val title: String? = null)

// ---------- models (§4 会话模型目录与切换) ----------

@Serializable
data class ModelSelection(val provider: String, val model: String, val reasoningEffort: String? = null)

@Serializable
data class SessionModelsRequest(val sessionId: String)

@Serializable
data class SelectModelRequest(
    val sessionId: String,
    val provider: String,
    val model: String,
    val reasoningEffort: String? = null,
)

@Serializable
data class ModelReasoningEffort(val id: String, val name: String, val description: String? = null)

@Serializable
data class ModelReasoning(val efforts: List<ModelReasoningEffort> = emptyList(), val defaultEffort: String? = null)

@Serializable
data class ModelCatalogModel(
    val id: String,
    val name: String,
    val description: String? = null,
    val reasoning: ModelReasoning? = null,
)

@Serializable
data class ModelProviderGroup(val id: String, val name: String, val models: List<ModelCatalogModel> = emptyList())

@Serializable
data class ModelCatalogFailure(val id: String, val name: String, val message: String = "")

@Serializable
data class SessionModels(
    val sessionId: String,
    val current: ModelSelection,
    val routable: Boolean = true,
    val groups: List<ModelProviderGroup> = emptyList(),
    val failures: List<ModelCatalogFailure> = emptyList(),
) {
    /** The catalog entry for [selection] (the current route by default). */
    fun model(selection: ModelSelection = current): ModelCatalogModel? =
        groups.firstOrNull { it.id == selection.provider }?.models?.firstOrNull { it.id == selection.model }

    fun displayName(selection: ModelSelection = current): String = model(selection)?.name ?: selection.model
}

@Serializable
data class SessionModelSelected(val sessionId: String, val selected: ModelSelection)

// ---------- attachments read-back ----------

@Serializable
data class AttachmentRequest(val sessionId: String, val attachmentId: String)

@Serializable
data class AttachmentData(val mediaType: String? = null, val data: String = "")

// ---------- adding a project (§4b) ----------

@Serializable
data class DirectoryRequest(val path: String? = null)

@Serializable
data class DirectoryEntry(val name: String, val path: String, val hidden: Boolean = false)

@Serializable
data class DirectoryListing(
    val path: String,
    val home: String = "",
    val crumbs: List<DirectoryEntry> = emptyList(),
    val entries: List<DirectoryEntry> = emptyList(),
    val truncated: Boolean = false,
)

@Serializable
data class DirectoryPicked(val path: String? = null)

@Serializable
data class WorkspaceCreate(val path: String)

@Serializable
data class WorkspaceCreated(val workspace: Workspace, val created: Boolean = false)

// ---------- session fork (optional v2 extension) ----------

/** Idempotency key for mutating requests: epoch millis and a lowercase UUID, as on iOS. */
object ClientRequestId {
    fun make(nowMillis: Long = System.currentTimeMillis()): String =
        "$nowMillis-${java.util.UUID.randomUUID().toString().lowercase()}"
}

@Serializable
data class SessionFork(val clientRequestId: String, val sessionId: String, val atSeq: Long? = null)

@Serializable
data class SessionForked(
    val clientRequestId: String? = null,
    val sourceSessionId: String? = null,
    val sessionId: String = "",
    val atSeq: Double? = null,
    val replayed: Boolean = false,
)

// ---------- schedules (optional v2 extension) ----------

/** One DSH Schedule task. [kind] is after | at | every | daily | weekly | cron. */
@Serializable
data class ScheduleTask(
    val id: String,
    val kind: String = "after",
    val title: String = "",
    val prompt: String = "",
    val scheduledAt: String = "",
    val state: String = "",
    val deliveryMode: String = "",
    val afterSeconds: Double? = null,
    val everySeconds: Double? = null,
    val time: String? = null,
    val timeZone: String? = null,
    val weekdays: List<Int>? = null,
    val expression: String? = null,
)

/** The last-read record sent with an update so the host can refuse a stale edit. */
@Serializable
data class ScheduleExpected(
    val id: String,
    val kind: String,
    val title: String,
    val prompt: String,
    val scheduledAt: String,
    val afterSeconds: Double? = null,
    val everySeconds: Double? = null,
    val time: String? = null,
    val timeZone: String? = null,
    val weekdays: List<Int>? = null,
    val expression: String? = null,
) {
    companion object {
        fun of(task: ScheduleTask) = ScheduleExpected(
            task.id, task.kind, task.title, task.prompt, task.scheduledAt,
            task.afterSeconds, task.everySeconds, task.time, task.timeZone, task.weekdays, task.expression,
        )
    }
}

@Serializable
data class ScheduleListRequest(val sessionId: String)

@Serializable
data class ScheduleSnapshot(val sessionId: String = "", val tasks: List<ScheduleTask> = emptyList())

@Serializable
data class ScheduleHistoryRequest(val sessionId: String, val id: String, val limit: Int = 20, val before: String? = null)

@Serializable
data class ScheduleHistoryRecord(
    val scheduledAt: String = "",
    val deliveredAt: String = "",
    val messageId: String = "",
    val prompt: String? = null,
)

@Serializable
data class ScheduleHistory(
    val id: String = "",
    val records: List<ScheduleHistoryRecord> = emptyList(),
    val earlierRecordsUnavailable: Boolean = false,
    val nextBefore: String? = null,
)

@Serializable
data class ScheduleHistoryPayload(val sessionId: String = "", val history: ScheduleHistory = ScheduleHistory())

@Serializable
data class ScheduleClock(
    val time: String,
    @SerialName("time_zone") val timeZone: String,
    val weekdays: List<Int>? = null,
)

@Serializable
data class ScheduleCron(val expression: String, @SerialName("time_zone") val timeZone: String)

/** `c2s.schedule.create`: a title, a prompt and exactly one selector. */
@Serializable
data class ScheduleCreate(
    val clientRequestId: String,
    val sessionId: String,
    val title: String,
    val prompt: String,
    @SerialName("after_seconds") val afterSeconds: Long? = null,
    @SerialName("every_seconds") val everySeconds: Long? = null,
    val daily: ScheduleClock? = null,
    val weekly: ScheduleClock? = null,
    val cron: ScheduleCron? = null,
)

@Serializable
data class ScheduleUpdate(
    val clientRequestId: String,
    val sessionId: String,
    val id: String,
    val expected: ScheduleExpected,
    val title: String? = null,
    val prompt: String? = null,
)

@Serializable
data class ScheduleDelete(val clientRequestId: String, val sessionId: String, val id: String)

@Serializable
data class ScheduleUpdated(
    val clientRequestId: String = "",
    val sessionId: String = "",
    val task: ScheduleTask? = null,
    val deleted: Boolean = false,
    val replayed: Boolean = false,
)
