package dev.hailab.deeppilot.core.protocol

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
    val width: Int? = null,
    val height: Int? = null,
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
