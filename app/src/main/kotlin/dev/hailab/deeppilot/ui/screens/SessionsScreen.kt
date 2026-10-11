package dev.hailab.deeppilot.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hailab.deeppilot.R
import dev.hailab.deeppilot.bridge.BridgeRepository
import dev.hailab.deeppilot.core.bridge.ConnectionState
import dev.hailab.deeppilot.core.protocol.SessionSummary
import dev.hailab.deeppilot.ui.connectionText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    repository: BridgeRepository,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
) {
    val sessions by repository.sessions.collectAsStateWithLifecycle()
    val connection by repository.connection.collectAsStateWithLifecycle()
    val approvals by repository.approvals.collectAsStateWithLifecycle()
    val questions by repository.questions.collectAsStateWithLifecycle()
    val waiting = (approvals.map { it.sessionId } + questions.map { it.sessionId }).toSet()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name))
                        Text(connectionText(connection), style = MaterialTheme.typography.labelMedium)
                    }
                },
                actions = {
                    IconButton(onClick = { repository.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (connection is ConnectionState.Failed) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            connectionText(connection),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { repository.reconnect() }) { Text(stringResource(R.string.action_reconnect)) }
                    }
                }
            }
            if (sessions.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(if (connection is ConnectionState.Authenticated) R.string.sessions_empty else R.string.sessions_waiting),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(sessions, key = { it.id }) { session ->
                        SessionRow(session, waiting = session.id in waiting || session.pendingApproval || session.pendingQuestion) {
                            onOpen(session.id)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: SessionSummary, waiting: Boolean, onClick: () -> Unit) {
    val title = session.title.ifBlank { stringResource(R.string.session_untitled) }
    val detail = listOfNotNull(
        session.workspaceLabel,
        session.activity?.takeIf { session.status == "running" },
        session.todos?.takeIf { it.total > 0 }?.let { "${it.done}/${it.total}" },
    ).joinToString(" · ")
    val supporting: (@Composable () -> Unit)? =
        if (detail.isNotEmpty()) { { Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis) } } else null
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = supporting,
        trailingContent = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (session.lastActivityTs > 0) {
                    Text(
                        DateUtils.getRelativeTimeSpanString(session.lastActivityTs).toString(),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                when {
                    waiting -> Badge(containerColor = MaterialTheme.colorScheme.error) { Text(stringResource(R.string.session_waiting)) }
                    session.status == "running" -> Badge(containerColor = MaterialTheme.colorScheme.primary) {
                        Text(stringResource(R.string.session_running))
                    }
                    session.status == "error" -> Badge { Text(stringResource(R.string.session_error)) }
                }
            }
        },
    )
}
