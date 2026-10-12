package dev.hailab.deeppilot.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hailab.deeppilot.R
import dev.hailab.deeppilot.bridge.BridgeRepository
import dev.hailab.deeppilot.core.bridge.ConnectionState
import dev.hailab.deeppilot.core.protocol.PendingApproval
import dev.hailab.deeppilot.core.protocol.PendingQuestion
import dev.hailab.deeppilot.core.session.Timeline
import dev.hailab.deeppilot.ui.AppType
import dev.hailab.deeppilot.ui.attempt
import dev.hailab.deeppilot.ui.components.BackButton
import dev.hailab.deeppilot.ui.components.BarIconButton
import dev.hailab.deeppilot.ui.components.NavBar
import dev.hailab.deeppilot.ui.components.SmallSpinner
import dev.hailab.deeppilot.ui.components.plainClickable
import dev.hailab.deeppilot.ui.errorMessage
import dev.hailab.deeppilot.ui.palette
import kotlinx.coroutines.launch

@Composable
fun SessionScreen(
    repository: BridgeRepository,
    sessionId: String,
    onBack: () -> Unit,
    onNewSession: () -> Unit,
) {
    DisposableEffect(sessionId) {
        repository.openSession(sessionId)
        onDispose { repository.closeSession(sessionId) }
    }
    val p = palette
    val open by repository.openSession.collectAsStateWithLifecycle()
    val sessions by repository.sessions.collectAsStateWithLifecycle()
    val connection by repository.connection.collectAsStateWithLifecycle()
    val allApprovals by repository.approvals.collectAsStateWithLifecycle()
    val allQuestions by repository.questions.collectAsStateWithLifecycle()
    val approvals = allApprovals.filter { it.sessionId == sessionId }
    val questions = allQuestions.filter { it.sessionId == sessionId }

    val state = open?.takeIf { it.sessionId == sessionId }
    val rows = remember(state?.messages) { Timeline.group(state?.messages.orEmpty()) }
    val summary = sessions.firstOrNull { it.id == sessionId }
    val title = summary?.title?.ifBlank { null } ?: stringResource(R.string.conversation_untitled)
    val connected = connection is ConnectionState.Authenticated
    val running = state?.turnRunning == true

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var draft by rememberSaveable(sessionId) { mutableStateOf("") }
    var pendingText by remember { mutableStateOf<String?>(null) }
    var sendError by remember { mutableStateOf<String?>(null) }
    var stopping by remember { mutableStateOf(false) }
    var showsTodos by remember { mutableStateOf(false) }

    // Pending approval/question half sheet, with a chip to bring back a dismissed one.
    var dismissed by remember(sessionId) { mutableStateOf(setOf<String>()) }
    var submitting by remember { mutableStateOf(false) }
    var pendingError by remember { mutableStateOf<String?>(null) }
    val pendingIds = approvals.map { it.requestId } + questions.map { it.requestId }
    val activeId = pendingIds.firstOrNull { it !in dismissed }
    val reopenId = if (activeId == null) pendingIds.firstOrNull { it in dismissed } else null

    val listState = rememberLazyListState()
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            last.index >= info.totalItemsCount - 1 && last.offset + last.size <= info.viewportEndOffset + 8
        }
    }
    var stickToBottom by remember(sessionId) { mutableStateOf(true) }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { if (it is DragInteraction.Start) stickToBottom = false }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling -> if (!scrolling && atBottom) stickToBottom = true }
    }
    // Older history loads when the top of the timeline comes into view.
    val nearTop by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.layoutInfo.totalItemsCount > 1 } }
    LaunchedEffect(nearTop, state?.hasMore, state?.loadingHistory) {
        if (nearTop && state?.hasMore == true && !state.loadingHistory && !stickToBottom) repository.loadOlder()
    }
    var layoutTick by remember { mutableIntStateOf(0) }
    val tail = state?.messages?.lastOrNull()
    val tailKey = tail?.let { it.seq to (it.text?.length ?: 0) + (it.thinking?.length ?: 0) }
    LaunchedEffect(tailKey, pendingText, layoutTick, rows.size) {
        if (stickToBottom && listState.layoutInfo.totalItemsCount > 0) {
            listState.scrollToItem(listState.layoutInfo.totalItemsCount - 1)
            listState.scrollBy(1_000_000f)
        }
    }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty() || pendingText != null) return
        pendingText = text
        draft = ""
        stickToBottom = true
        scope.launch {
            val error = attempt { repository.sendPrompt(sessionId, text) }
            if (error != null) {
                draft = text
                sendError = context.errorMessage(error)
            }
            pendingText = null
        }
    }

    Column(Modifier.fillMaxSize().background(p.background)) {
        NavBar(
            leading = { BackButton(onBack) },
            trailing = {
                BarIconButton(Icons.Outlined.AddComment, stringResource(R.string.a11y_new_session), onNewSession, enabled = connected)
            },
            principal = { ConnectionStatusTitle(title, connection) { repository.reconnect() } },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state?.loadingHistory == true) {
                    item(key = "older") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SmallSpinner() }
                    }
                }
                if (rows.isEmpty() && pendingText == null) {
                    item(key = "empty") {
                        if (state?.loaded != true) ConversationSkeleton() else EmptyConversation()
                    }
                }
                items(rows, key = { it.key }) { row -> TimelineRow(row) { layoutTick++ } }
                pendingText?.let { text ->
                    item(key = "pending") {
                        TrailingBubble {
                            UserBubble(text, null) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    SmallSpinner(size = 11.dp)
                                    Text(stringResource(R.string.conversation_sending), style = AppType.caption, color = p.secondary)
                                }
                            }
                        }
                    }
                }
                sendError?.let { error ->
                    item(key = "send-error") {
                        Text(
                            error,
                            style = AppType.footnote,
                            color = p.danger,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth().plainClickable { sendError = null },
                        )
                    }
                }
            }

            // Reopen a dismissed approval or question; floats above the timeline.
            reopenId?.let { id ->
                val approval = approvals.firstOrNull { it.requestId == id }
                val question = questions.firstOrNull { it.requestId == id }
                Box(Modifier.align(Alignment.TopCenter).padding(horizontal = 16.dp).padding(top = 4.dp)) {
                    PendingReopenChip(
                        approval = approval != null,
                        headline = if (approval != null) stringResource(R.string.approval_title) else stringResource(R.string.question_title),
                        subtitle = approval?.toolName?.ifBlank { null } ?: question?.questions?.firstOrNull()?.question,
                    ) { dismissed = dismissed - id }
                }
            }

            AnimatedVisibility(
                visible = !atBottom,
                enter = fadeIn() + scaleIn(initialScale = 0.9f),
                exit = fadeOut() + scaleOut(targetScale = 0.9f),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 6.dp),
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .plainClickable {
                            stickToBottom = true
                            scope.launch {
                                listState.animateScrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
                                listState.scrollBy(1_000_000f)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .background(p.background, CircleShape)
                            .border(0.5.dp, p.hairline, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            stringResource(R.string.a11y_jump_latest),
                            tint = p.foreground,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
            PromptComposer(
                draft = draft,
                onDraftChange = {
                    draft = it
                    sendError = null
                },
                todoItems = summary?.todoItems.orEmpty(),
                canSubmit = connected,
                sending = pendingText != null,
                running = running,
                stopping = stopping,
                onSend = ::send,
                onStop = {
                    stopping = true
                    scope.launch {
                        attempt { repository.cancelTurn(sessionId) }?.let { sendError = context.errorMessage(it) }
                        stopping = false
                    }
                },
                onOpenTodos = { showsTodos = true },
                modifier = Modifier.padding(horizontal = 12.dp).padding(top = 6.dp),
            )
            Text(
                stringResource(R.string.conversation_disclaimer),
                style = AppType.caption2,
                color = p.tertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 4.dp),
            )
        }
    }

    activeId?.let { id ->
        fun respond(action: suspend () -> Unit) {
            submitting = true
            pendingError = null
            scope.launch {
                attempt { action() }?.let { pendingError = context.errorMessage(it) }
                submitting = false
            }
        }
        val approval: PendingApproval? = approvals.firstOrNull { it.requestId == id }
        val question: PendingQuestion? = questions.firstOrNull { it.requestId == id }
        if (approval != null) {
            ApprovalSheet(
                approval = approval,
                submitting = submitting,
                error = pendingError,
                enabled = connected,
                onRespond = { allow -> respond { repository.respondApproval(id, allow) } },
                onDismiss = { dismissed = dismissed + id },
            )
        } else if (question != null) {
            QuestionSheet(
                payload = question,
                submitting = submitting,
                error = pendingError,
                enabled = connected,
                onSubmit = { answers -> respond { repository.respondQuestion(id, answers) } },
                onDismiss = { dismissed = dismissed + id },
            )
        }
    }

    if (showsTodos) {
        val todos = summary?.todoItems.orEmpty()
        ConversationSheet(
            title = stringResource(R.string.todo_detail_title),
            subtitle = stringResource(R.string.todo_completed_progress, todos.count { it.status == "completed" }, todos.size),
            onDismiss = { showsTodos = false },
        ) { TodoListContent(todos) }
    }
}

/** Title with the connection hint under it while not connected (iOS `ConnectionStatusTitle`). */
@Composable
private fun ConnectionStatusTitle(title: String, connection: ConnectionState, onRetry: () -> Unit) {
    val p = palette
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = AppType.headline, color = p.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (connection !is ConnectionState.Authenticated) {
            val (text, color) = when (connection) {
                is ConnectionState.Connecting -> stringResource(R.string.offline_connecting) to p.warning
                ConnectionState.Disconnected -> stringResource(R.string.offline_disconnected) to p.secondary
                else -> stringResource(R.string.phase_failed) to p.danger
            }
            Row(
                Modifier.plainClickable(enabled = connection is ConnectionState.Failed, onClick = onRetry),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(Modifier.size(5.dp).background(color, CircleShape))
                Text(text, style = AppType.caption2.copy(fontWeight = FontWeight.Medium), color = color, maxLines = 1)
            }
        }
    }
}

@Composable
private fun EmptyConversation() {
    val p = palette
    Column(
        Modifier.fillMaxWidth().padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.ChatBubbleOutline, null, tint = p.muted, modifier = Modifier.size(30.dp))
        Text(stringResource(R.string.empty_title), style = AppType.headline, color = p.foreground)
        Text(stringResource(R.string.empty_subtitle), style = AppType.subheadline, color = p.secondary, textAlign = TextAlign.Center)
    }
}
