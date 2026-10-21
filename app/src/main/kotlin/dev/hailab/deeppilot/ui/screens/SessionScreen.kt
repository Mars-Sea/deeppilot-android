package dev.hailab.deeppilot.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hailab.deeppilot.R
import dev.hailab.deeppilot.attachments.AttachmentIngest
import dev.hailab.deeppilot.attachments.CameraFiles
import dev.hailab.deeppilot.bridge.BridgeRepository
import dev.hailab.deeppilot.bridge.PromptUnconfirmedException
import dev.hailab.deeppilot.core.attachments.PromptAttachment
import dev.hailab.deeppilot.core.bridge.ConnectionState
import dev.hailab.deeppilot.core.delivery.PendingSend
import dev.hailab.deeppilot.core.protocol.ModelSelection
import dev.hailab.deeppilot.core.protocol.PendingApproval
import dev.hailab.deeppilot.core.protocol.PendingQuestion
import dev.hailab.deeppilot.core.protocol.SessionModels
import dev.hailab.deeppilot.core.session.Timeline
import dev.hailab.deeppilot.ui.AppRadius
import dev.hailab.deeppilot.ui.AppType
import dev.hailab.deeppilot.ui.attempt
import dev.hailab.deeppilot.ui.components.BackButton
import dev.hailab.deeppilot.ui.components.BarIconButton
import dev.hailab.deeppilot.ui.components.NavBar
import dev.hailab.deeppilot.ui.components.SmallSpinner
import dev.hailab.deeppilot.ui.components.plainClickable
import dev.hailab.deeppilot.ui.errorMessage
import dev.hailab.deeppilot.ui.palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SessionScreen(
    repository: BridgeRepository,
    sessionId: String,
    onBack: () -> Unit,
    onNewSession: () -> Unit,
    onOpenSession: (String) -> Unit,
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
    val focusManager = LocalFocusManager.current
    var draft by rememberSaveable(sessionId) { mutableStateOf(repository.draft(sessionId)) }
    // Keep the unsent text per conversation, across leaving the screen and relaunches.
    LaunchedEffect(sessionId) { snapshotFlow { draft }.collect { repository.saveDraft(sessionId, it) } }
    var pendingText by remember { mutableStateOf<String?>(null) }
    var sendError by remember { mutableStateOf<String?>(null) }
    var stopping by remember { mutableStateOf(false) }
    var showsTodos by remember { mutableStateOf(false) }
    var showsSchedules by remember { mutableStateOf(false) }
    val allPendingSends by repository.pendingSends.collectAsStateWithLifecycle()
    val pendingSends = allPendingSends.filter { it.sessionId == sessionId }
    // While an unconfirmed send is on screen, ask the host about it every few seconds.
    val awaitingReceipts = pendingSends.any { it.delivery != PendingSend.Delivery.Rejected }
    LaunchedEffect(sessionId, connected, awaitingReceipts) {
        while (connected && awaitingReceipts) {
            repository.reconcileDeliveries()
            delay(5_000)
        }
    }
    val capabilities by repository.capabilities.collectAsStateWithLifecycle()
    val supportsSchedules = capabilities?.schedules == true
    val supportsFork = capabilities?.sessionFork == true
    LaunchedEffect(sessionId, connected, supportsSchedules) {
        if (connected && supportsSchedules) repository.loadSchedules(sessionId)
    }

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

    // Attachments waiting to be sent, plus imports still being prepared.
    val attachments = remember(sessionId) { mutableStateListOf<PromptAttachment>() }
    var pendingAttachments by remember { mutableStateOf<List<PromptAttachment>>(emptyList()) }
    var importing by remember { mutableIntStateOf(0) }
    var alert by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Branch at an answer and open the new session; the button owns its spinner.
    suspend fun fork(atSeq: Long) {
        var newId: String? = null
        val error = attempt { newId = repository.forkSession(sessionId, atSeq) }
        if (error != null) alert = context.getString(R.string.schedule_fork_failed) to context.errorMessage(error)
        else newId?.let(onOpenSession)
    }
    val capabilityTitle = stringResource(R.string.composer_capability_title)
    val ingest = remember { AttachmentIngest(context.applicationContext) }
    var showsAttachmentSheet by remember { mutableStateOf(false) }

    fun addAttachment(block: suspend () -> PromptAttachment) {
        if (attachments.size + importing >= PromptAttachment.MAX_PER_PROMPT) {
            alert = capabilityTitle to context.getString(R.string.composer_attachments_max)
            return
        }
        importing++
        scope.launch {
            try {
                var imported: PromptAttachment? = null
                attempt { imported = block() }?.let { alert = capabilityTitle to context.errorMessage(it) }
                imported?.let { if (attachments.size < PromptAttachment.MAX_PER_PROMPT) attachments += it }
            } finally {
                importing--
            }
        }
    }
    val photoName = { index: Int -> context.getString(R.string.attachment_photo_name, index) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(PromptAttachment.MAX_PER_PROMPT)) { uris ->
        val first = attachments.size + importing + 1
        uris.forEachIndexed { index, uri ->
            val name = photoName(first + index)
            addAttachment { ingest.image(uri, name) }
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri -> addAttachment { ingest.file(uri) } }
    }
    var cameraTarget by rememberSaveable { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraTarget
        if (saved && uri != null) {
            val name = context.getString(R.string.attachment_camera_name, attachments.size + importing + 1)
            addAttachment { ingest.image(uri, name) }
        }
    }
    fun launchCamera() {
        val uri = CameraFiles.newPhoto(context)
        cameraTarget = uri
        runCatching { camera.launch(uri) }.onFailure {
            alert = capabilityTitle to context.getString(R.string.composer_camera_unavailable)
        }
    }
    // The QR scanner declares CAMERA, so the system camera app needs the grant too.
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else alert = capabilityTitle to context.getString(R.string.composer_camera_unavailable)
    }

    // Model catalog for this session (§4), loaded once connected and supported.
    var models by remember(sessionId) { mutableStateOf<SessionModels?>(null) }
    var modelsLoading by remember { mutableStateOf(false) }
    var modelsError by remember { mutableStateOf<String?>(null) }
    var showsModelSheet by remember { mutableStateOf(false) }
    var changingEffort by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    fun loadModels() {
        if (modelsLoading) return
        if (capabilities?.models != true) {
            modelsError = context.getString(R.string.model_switch_unavailable)
            return
        }
        modelsLoading = true
        scope.launch {
            var loaded: SessionModels? = null
            val error = attempt { loaded = repository.loadModels(sessionId) }
            if (error == null) {
                models = loaded
                modelsError = null
            } else {
                modelsError = context.errorMessage(error)
            }
            modelsLoading = false
        }
    }
    suspend fun selectModel(selection: ModelSelection) {
        val selected = repository.selectModel(sessionId, selection)
        models = models?.copy(current = selected)
        modelsError = null
    }
    LaunchedEffect(sessionId, connected, capabilities?.models) {
        if (connected && capabilities?.models == true) loadModels()
    }

    fun send() {
        val text = draft.trim()
        val outgoing = attachments.toList()
        if ((text.isEmpty() && outgoing.isEmpty()) || pendingText != null) return
        pendingText = text
        pendingAttachments = outgoing
        draft = ""
        attachments.clear()
        stickToBottom = true
        scope.launch {
            val error = attempt { repository.sendPrompt(sessionId, text, outgoing) }
            if (error is PromptUnconfirmedException) {
                // The host may have it: keep the marker in the timeline and leave the
                // composer empty so the same prompt is not sent twice.
                alert = context.getString(R.string.delivery_unconfirmed_title) to context.errorMessage(error)
            } else if (error != null) {
                // Put the draft back unless something new was typed meanwhile.
                if (draft.isBlank() && attachments.isEmpty()) {
                    draft = text
                    attachments += outgoing
                }
                sendError = context.errorMessage(error)
            } else {
                keyboard?.hide()
            }
            pendingText = null
            pendingAttachments = emptyList()
        }
    }

    Column(Modifier.fillMaxSize().background(p.background)) {
        NavBar(
            leading = { BackButton(onBack) },
            trailing = {
                BarIconButton(Icons.Outlined.AddComment, stringResource(R.string.a11y_new_session), onNewSession, enabled = connected)
                if (supportsSchedules) {
                    BarIconButton(Icons.Outlined.History, stringResource(R.string.schedule_title), { showsSchedules = true })
                }
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
                if (rows.isEmpty() && pendingText == null && pendingSends.isEmpty()) {
                    item(key = "empty") {
                        if (state?.loaded != true) ConversationSkeleton() else EmptyConversation()
                    }
                }
                items(rows, key = { it.key }) { row ->
                    TimelineRow(row, sessionId, repository, onFork = if (supportsFork) ::fork else null) { layoutTick++ }
                }
                // Sends from earlier that the host never confirmed (the in-flight one is below).
                items(
                    pendingSends.filter { it.delivery != PendingSend.Delivery.Sending || pendingText == null },
                    key = { "outbox-${it.clientSendId}" },
                ) { pending ->
                    PendingSendBubble(pending) { repository.dismissPendingSend(pending.clientSendId) }
                }
                pendingText?.let { text ->
                    item(key = "pending") {
                        TrailingBubble {
                            UserBubble(text.ifEmpty { null }, pendingAttachments.map { it.asMessageAttachment() }, sessionId, repository) {
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

            androidx.compose.animation.AnimatedVisibility(
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
                attachments = attachments,
                onRemoveAttachment = { attachments.remove(it) },
                onAddAttachment = { showsAttachmentSheet = true },
                models = models,
                modelsLoading = modelsLoading,
                modelsError = modelsError,
                onOpenModels = {
                    focusManager.clearFocus()
                    showsModelSheet = true
                },
                onSelectEffort = { effort ->
                    val current = models
                    if (current != null && effort.id != current.current.reasoningEffort && !changingEffort) {
                        changingEffort = true
                        scope.launch {
                            attempt { selectModel(current.current.copy(reasoningEffort = effort.id)) }?.let {
                                alert = capabilityTitle to context.getString(R.string.thinking_unsupported)
                            }
                            changingEffort = false
                        }
                    }
                },
                changingEffort = changingEffort,
                focusRequester = focusRequester,
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

    if (showsAttachmentSheet) {
        AttachmentPickerSheet(
            onCamera = {
                showsAttachmentSheet = false
                if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
                    alert = capabilityTitle to context.getString(R.string.composer_camera_unavailable)
                } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    launchCamera()
                } else {
                    cameraPermission.launch(Manifest.permission.CAMERA)
                }
            },
            onPhotos = {
                showsAttachmentSheet = false
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onFiles = {
                showsAttachmentSheet = false
                filePicker.launch(arrayOf("*/*"))
            },
            onDismiss = { showsAttachmentSheet = false },
        )
    }

    if (showsModelSheet) {
        ModelPickerSheet(
            catalog = models,
            loading = modelsLoading,
            loadError = modelsError,
            onRefresh = ::loadModels,
            onSelect = { selectModel(it) },
            onDismiss = {
                showsModelSheet = false
                // Picking a model flows straight back into typing.
                scope.launch {
                    delay(300)
                    if (!showsModelSheet) runCatching { focusRequester.requestFocus() }
                }
            },
        )
    }

    alert?.let { (title, message) ->
        AlertDialog(
            onDismissRequest = { alert = null },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { alert = null }) { Text(stringResource(R.string.common_ok)) } },
        )
    }

    if (showsSchedules) {
        ScheduleListSheet(repository = repository, sessionId = sessionId, onDismiss = { showsSchedules = false })
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

/** iOS `pendingSendRow`: a prompt the host has not confirmed, with a way to retire the marker. */
@Composable
private fun PendingSendBubble(pending: PendingSend, onDismiss: () -> Unit) {
    val p = palette
    TrailingBubble {
        Column(
            Modifier
                .background(p.secondaryFill, RoundedCornerShape(AppRadius.large))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (pending.text.isNotEmpty()) Text(pending.text, style = AppType.body, color = p.foreground)
            if (pending.attachmentCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Outlined.AttachFile, null, tint = p.foreground, modifier = Modifier.size(14.dp))
                    Text(
                        pluralStringResource(R.plurals.delivery_attachments, pending.attachmentCount, pending.attachmentCount),
                        style = AppType.footnote.copy(fontWeight = FontWeight.Medium),
                        color = p.foreground,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (pending.delivery == PendingSend.Delivery.Sending) {
                    SmallSpinner(size = 11.dp)
                    Text(stringResource(R.string.conversation_sending), style = AppType.caption, color = p.secondary)
                } else {
                    Icon(Icons.AutoMirrored.Outlined.HelpOutline, null, tint = p.secondary, modifier = Modifier.size(13.dp))
                    Text(
                        stringResource(if (pending.delivery == PendingSend.Delivery.Rejected) R.string.delivery_rejected else R.string.delivery_unknown),
                        style = AppType.caption,
                        color = p.secondary,
                    )
                }
            }
            if (pending.delivery != PendingSend.Delivery.Sending) {
                // Never retried automatically, so the user must be able to retire it.
                Text(
                    stringResource(if (pending.delivery == PendingSend.Delivery.Rejected) R.string.delivery_dismiss else R.string.delivery_acknowledge),
                    style = AppType.caption,
                    color = p.accent,
                    modifier = Modifier.plainClickable(onClick = onDismiss),
                )
            }
        }
    }
}
