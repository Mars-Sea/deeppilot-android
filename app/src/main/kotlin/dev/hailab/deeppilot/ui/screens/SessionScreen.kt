package dev.hailab.deeppilot.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hailab.deeppilot.R
import dev.hailab.deeppilot.bridge.BridgeRepository
import dev.hailab.deeppilot.core.bridge.ConnectionState
import dev.hailab.deeppilot.core.protocol.MessageProjection
import dev.hailab.deeppilot.ui.attempt
import dev.hailab.deeppilot.ui.connectionText
import dev.hailab.deeppilot.ui.errorMessage
import dev.hailab.deeppilot.ui.markdown.MarkdownMessage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(repository: BridgeRepository, sessionId: String, onBack: () -> Unit) {
    DisposableEffect(sessionId) {
        repository.openSession(sessionId)
        onDispose { repository.closeSession(sessionId) }
    }
    val open by repository.openSession.collectAsStateWithLifecycle()
    val sessions by repository.sessions.collectAsStateWithLifecycle()
    val connection by repository.connection.collectAsStateWithLifecycle()
    val allApprovals by repository.approvals.collectAsStateWithLifecycle()
    val allQuestions by repository.questions.collectAsStateWithLifecycle()
    val approvals = allApprovals.filter { it.sessionId == sessionId }
    val questions = allQuestions.filter { it.sessionId == sessionId }

    val state = open?.takeIf { it.sessionId == sessionId }
    val messages = state?.messages.orEmpty()
    val title = sessions.firstOrNull { it.id == sessionId }?.title?.ifBlank { null } ?: stringResource(R.string.session_untitled)
    val connected = connection is ConnectionState.Authenticated
    val running = state?.turnRunning == true

    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var draft by rememberSaveable(sessionId) { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    fun report(error: Throwable?) {
        if (error != null) scope.launch { snackbar.showSnackbar(context.errorMessage(error)) }
    }

    val listState = rememberLazyListState()
    val itemCount = messages.size + approvals.size + questions.size + if (state?.hasMore == true) 1 else 0
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            last.index >= info.totalItemsCount - 1 && last.offset + last.size <= info.viewportEndOffset + 8
        }
    }
    // Follow the conversation while the user is at the bottom; stop once they scroll up.
    var stickToBottom by remember(sessionId) { mutableStateOf(true) }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { if (it is DragInteraction.Start) stickToBottom = false }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling -> if (!scrolling && atBottom) stickToBottom = true }
    }
    // Bumped when a rendered message changes height (Markdown and formulas lay out after load).
    var layoutTick by remember { mutableIntStateOf(0) }
    val tailKey = messages.lastOrNull()?.let { it.seq to (it.text?.length ?: 0) + (it.thinking?.length ?: 0) }
    LaunchedEffect(tailKey, approvals.size, questions.size, layoutTick) {
        if (itemCount > 0 && stickToBottom) {
            listState.scrollToItem(itemCount - 1)
            listState.scrollBy(1_000_000f)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val subtitle = when {
                            !connected -> connectionText(connection)
                            running -> stringResource(R.string.session_running)
                            else -> null
                        }
                        subtitle?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Composer(
                text = draft,
                onTextChange = { draft = it },
                enabled = connected && !sending,
                running = running,
                onSend = {
                    val text = draft.trim()
                    if (text.isNotEmpty()) {
                        scope.launch {
                            sending = true
                            val error = attempt { repository.sendPrompt(sessionId, text) }
                            sending = false
                            if (error == null) draft = "" else snackbar.showSnackbar(context.errorMessage(error))
                        }
                    }
                },
                onStop = { scope.launch { report(attempt { repository.cancelTurn(sessionId) }) } },
            )
        },
    ) { padding ->
        if (state?.loaded != true && messages.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state?.hasMore == true) {
                item(key = "older") {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (state.loadingHistory) {
                            CircularProgressIndicator(Modifier.padding(8.dp))
                        } else {
                            TextButton(onClick = { repository.loadOlder() }) { Text(stringResource(R.string.session_load_older)) }
                        }
                    }
                }
            }
            items(messages, key = { "m${it.seq}" }) { MessageRow(it, onHeightChanged = { layoutTick++ }) }
            items(approvals, key = { "a${it.requestId}" }) { approval ->
                ApprovalCard(approval, enabled = connected) { allow, onFailed ->
                    scope.launch {
                        val error = attempt { repository.respondApproval(approval.requestId, allow) }
                        if (error != null) onFailed()
                        report(error)
                    }
                }
            }
            items(questions, key = { "q${it.requestId}" }) { question ->
                QuestionCard(question, enabled = connected) { answers, onFailed ->
                    scope.launch {
                        val error = attempt { repository.respondQuestion(question.requestId, answers) }
                        if (error != null) onFailed()
                        report(error)
                    }
                }
            }
        }
    }
}

@Composable
private fun Composer(
    text: String,
    onTextChange: (String) -> Unit,
    enabled: Boolean,
    running: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text(stringResource(R.string.composer_hint)) },
                maxLines = 6,
                modifier = Modifier.weight(1f),
            )
            if (running) {
                IconButton(onClick = onStop, enabled = enabled) {
                    Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.action_stop))
                }
            }
            IconButton(onClick = onSend, enabled = enabled && text.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.action_send))
            }
        }
    }
}

@Composable
private fun MessageRow(message: MessageProjection, onHeightChanged: () -> Unit) {
    when (message.role) {
        "user" -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.widthIn(max = 320.dp),
            ) {
                SelectionContainer {
                    Text(message.text.orEmpty(), modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
        }
        "tool" -> ToolRow(message)
        "system", "error" -> Text(
            message.text.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = if (message.role == "error") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        )
        else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            message.thinking?.takeIf { it.isNotBlank() }?.let { ThinkingBlock(it, message.streaming && message.text.isNullOrEmpty()) }
            val text = message.text.orEmpty()
            if (text.isNotEmpty() || (message.streaming && message.thinking.isNullOrEmpty())) {
                MarkdownMessage(
                    markdown = if (message.streaming) "$text▍" else text,
                    key = message.seq,
                    onHeightChanged = onHeightChanged,
                )
            }
        }
    }
}

@Composable
private fun ThinkingBlock(text: String, streaming: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                stringResource(if (streaming) R.string.message_thinking_live else R.string.message_thinking),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (expanded || streaming) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ToolRow(message: MessageProjection) {
    val tool = message.tool
    var expanded by remember { mutableStateOf(false) }
    val color = when (tool?.state) {
        "error" -> MaterialTheme.colorScheme.error
        "running" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val marker = when (tool?.state) {
        "running" -> "◌"
        "error" -> "✕"
        else -> "✓"
    }
    Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 4.dp)) {
        Text(
            "$marker ${tool?.name ?: "tool"}  ${tool?.summary.orEmpty()}",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = color,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (expanded && !message.text.isNullOrBlank()) {
            SelectionContainer {
                Text(message.text.orEmpty(), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
