package dev.hailab.deeppilot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hailab.deeppilot.R
import dev.hailab.deeppilot.bridge.BridgeRepository
import dev.hailab.deeppilot.core.bridge.ConnectionState
import dev.hailab.deeppilot.core.protocol.SessionSummary
import dev.hailab.deeppilot.core.session.SessionGroups
import dev.hailab.deeppilot.ui.AppLayout
import dev.hailab.deeppilot.ui.AppSpacing
import dev.hailab.deeppilot.ui.AppType
import dev.hailab.deeppilot.ui.attempt
import dev.hailab.deeppilot.ui.components.BackButton
import dev.hailab.deeppilot.ui.components.NavBar
import dev.hailab.deeppilot.ui.components.NavTitle
import dev.hailab.deeppilot.ui.components.SettingsCard
import dev.hailab.deeppilot.ui.components.SettingsDivider
import dev.hailab.deeppilot.ui.components.SmallSpinner
import dev.hailab.deeppilot.ui.components.plainClickable
import dev.hailab.deeppilot.ui.errorMessage
import dev.hailab.deeppilot.ui.palette
import kotlinx.coroutines.launch

private enum class ArchivedPhase { Idle, Loading, Loaded, Failed }

/**
 * iOS `ArchivedSessionsView`, reached from Settings. Archived rows are fetched on
 * demand; restoring sends `c2s.session.unarchive` and the bridge's next sessions
 * delta is what puts the session back in the drawer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedSessionsScreen(repository: BridgeRepository, onBack: () -> Unit) {
    val p = palette
    val context = LocalContext.current
    val connection by repository.connection.collectAsStateWithLifecycle()
    val capabilities by repository.capabilities.collectAsStateWithLifecycle()
    val supported = capabilities?.sessionRestore == true
    val scope = rememberCoroutineScope()

    var archived by remember { mutableStateOf<List<SessionSummary>>(emptyList()) }
    var phase by remember { mutableStateOf(ArchivedPhase.Idle) }
    var loadFailure by remember { mutableStateOf<String?>(null) }
    var restoringId by remember { mutableStateOf<String?>(null) }
    var restoreFailure by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    suspend fun load() {
        if (!supported) return
        phase = ArchivedPhase.Loading
        var rows: List<SessionSummary> = emptyList()
        val error = attempt { rows = repository.loadArchivedSessions() }
        if (error == null) {
            archived = rows
            phase = ArchivedPhase.Loaded
            loadFailure = null
        } else {
            phase = ArchivedPhase.Failed
            loadFailure = context.errorMessage(error)
        }
    }

    val authenticated = connection is ConnectionState.Authenticated
    LaunchedEffect(authenticated, supported) { if (authenticated) load() }

    fun restore(session: SessionSummary) {
        if (restoringId != null) return
        restoringId = session.id
        scope.launch {
            val error = attempt { repository.unarchiveSession(session.id) }
            if (error == null) archived = archived.filterNot { it.id == session.id }
            else restoreFailure = context.errorMessage(error)
            restoringId = null
        }
    }

    Column(Modifier.fillMaxSize().background(p.background)) {
        NavBar(leading = { BackButton(onBack) }, principal = { NavTitle(stringResource(R.string.archived_title)) })
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                scope.launch {
                    refreshing = true
                    load()
                    refreshing = false
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = AppLayout.pageInset)
                    .padding(top = AppSpacing.small, bottom = AppSpacing.large),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.section),
            ) {
                when {
                    !supported -> MessageCard(stringResource(R.string.archived_unsupported))
                    archived.isEmpty() && phase == ArchivedPhase.Failed -> SettingsCard {
                        Column(
                            Modifier.padding(horizontal = AppSpacing.regular, vertical = AppSpacing.compact),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                loadFailure ?: stringResource(R.string.archived_load_failed),
                                style = AppType.footnote,
                                color = p.secondary,
                            )
                            Text(
                                stringResource(R.string.common_retry),
                                style = AppType.footnote.copy(fontWeight = FontWeight.SemiBold),
                                color = p.accent,
                                modifier = Modifier.plainClickable { scope.launch { load() } },
                            )
                        }
                    }
                    archived.isEmpty() -> MessageCard(
                        stringResource(if (phase == ArchivedPhase.Loading || phase == ArchivedPhase.Idle) R.string.archived_loading else R.string.archived_empty),
                    )
                    else -> {
                        val groups = SessionGroups.archived(archived)
                        groups.forEachIndexed { index, group ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    group.workspace ?: stringResource(R.string.uncategorized),
                                    style = AppType.subheadline.copy(fontWeight = FontWeight.SemiBold),
                                    color = p.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = AppSpacing.regular),
                                )
                                SettingsCard {
                                    group.sessions.forEachIndexed { row, session ->
                                        if (row > 0) SettingsDivider()
                                        ArchivedRow(
                                            session = session,
                                            restoring = restoringId == session.id,
                                            enabled = restoringId == null,
                                            onRestore = { restore(session) },
                                        )
                                    }
                                }
                                if (index == groups.lastIndex) {
                                    Text(
                                        stringResource(R.string.archived_explainer),
                                        style = AppType.footnote,
                                        color = p.secondary,
                                        modifier = Modifier.padding(horizontal = AppSpacing.regular),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    restoreFailure?.let { message ->
        AlertDialog(
            onDismissRequest = { restoreFailure = null },
            title = { Text(stringResource(R.string.archived_restore_failed)) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { restoreFailure = null }) { Text(stringResource(R.string.common_ok)) } },
        )
    }
}

@Composable
private fun MessageCard(text: String) {
    SettingsCard {
        Text(
            text,
            style = AppType.footnote,
            color = palette.secondary,
            modifier = Modifier.padding(horizontal = AppSpacing.regular, vertical = AppSpacing.compact),
        )
    }
}

@Composable
private fun ArchivedRow(session: SessionSummary, restoring: Boolean, enabled: Boolean, onRestore: () -> Unit) {
    val p = palette
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(horizontal = AppSpacing.regular, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.compact),
    ) {
        Text(
            session.title.ifEmpty { stringResource(R.string.session_untitled) },
            style = AppType.body,
            color = p.foreground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (restoring) {
            SmallSpinner()
        } else {
            Text(
                stringResource(R.string.archived_restore),
                style = AppType.subheadline.copy(fontWeight = FontWeight.SemiBold),
                color = if (enabled) p.accent else p.tertiary,
                modifier = Modifier.plainClickable(enabled = enabled, onClick = onRestore),
            )
        }
    }
}
