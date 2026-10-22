package dev.hailab.deeppilot.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.LiveHelp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hailab.deeppilot.R
import dev.hailab.deeppilot.bridge.BridgeRepository
import dev.hailab.deeppilot.core.bridge.ConnectionState
import dev.hailab.deeppilot.core.pairing.HostEndpoints
import dev.hailab.deeppilot.core.protocol.SessionSummary
import dev.hailab.deeppilot.core.protocol.Workspace
import dev.hailab.deeppilot.core.session.RelativeTime
import dev.hailab.deeppilot.core.session.SessionGroups
import dev.hailab.deeppilot.ui.AppRadius
import dev.hailab.deeppilot.ui.AppSpacing
import dev.hailab.deeppilot.ui.AppType
import dev.hailab.deeppilot.ui.attempt
import dev.hailab.deeppilot.ui.components.AppIcon
import dev.hailab.deeppilot.ui.components.BarIconButton
import dev.hailab.deeppilot.ui.components.ConnectionStatusLight
import dev.hailab.deeppilot.ui.components.NavBar
import dev.hailab.deeppilot.ui.components.SmallSpinner
import dev.hailab.deeppilot.ui.components.phaseText
import dev.hailab.deeppilot.ui.components.plainClickable
import dev.hailab.deeppilot.ui.errorMessage
import dev.hailab.deeppilot.ui.palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ---------- home ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkbenchHome(
    repository: BridgeRepository,
    onOpenDrawer: () -> Unit,
    onCreateSession: () -> Unit,
    onOpenSession: (String) -> Unit,
) {
    val sessions by repository.sessions.collectAsStateWithLifecycle()
    val connection by repository.connection.collectAsStateWithLifecycle()
    val approvals by repository.approvals.collectAsStateWithLifecycle()
    val questions by repository.questions.collectAsStateWithLifecycle()
    val p = palette
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(p.background)) {
        NavBar(
            leading = {
                BarIconButton(Icons.Filled.Menu, stringResource(R.string.a11y_open_sidebar), onOpenDrawer)
            },
            principal = { HomeConnectionStatus(connection) },
        )
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                scope.launch {
                    refreshing = true
                    repository.refresh()
                    delay(600)
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
                    .padding(horizontal = 16.dp)
                    .padding(bottom = AppSpacing.comfortable),
            ) {
                Column(
                    Modifier.padding(top = AppSpacing.comfortable, bottom = AppSpacing.regular),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.micro),
                ) {
                    Text(greeting(), style = AppType.title2.copy(fontWeight = FontWeight.Bold), color = p.foreground)
                    Text(
                        stringResource(R.string.workbench_subtitle, dateLine()),
                        style = AppType.subheadline,
                        color = p.muted,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                        DashboardCard(
                            value = approvals.size.toString(),
                            title = stringResource(R.string.card_pending_approvals),
                            icon = Icons.Outlined.Lock,
                            modifier = Modifier.weight(1f),
                        ) { sessions.firstOrNull { it.pendingApproval }?.let { onOpenSession(it.id) } }
                        DashboardCard(
                            value = questions.size.toString(),
                            title = stringResource(R.string.card_pending_questions),
                            icon = Icons.AutoMirrored.Outlined.LiveHelp,
                            modifier = Modifier.weight(1f),
                        ) { sessions.firstOrNull { it.pendingQuestion }?.let { onOpenSession(it.id) } }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                        DashboardCard(
                            value = sessions.size.toString(),
                            title = stringResource(R.string.card_all_sessions),
                            icon = Icons.Outlined.ChatBubbleOutline,
                            modifier = Modifier.weight(1f),
                            onClick = onOpenDrawer,
                        )
                        DashboardCard(
                            value = "+",
                            title = stringResource(R.string.new_session),
                            icon = Icons.Filled.Add,
                            modifier = Modifier.weight(1f),
                            onClick = onCreateSession,
                        )
                    }
                }

                Text(
                    stringResource(R.string.heading_recent_projects),
                    style = AppType.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
                    color = p.muted,
                    modifier = Modifier.padding(top = AppSpacing.section, bottom = AppSpacing.small),
                )

                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    val recent = sessions.take(5)
                    if (recent.isEmpty()) {
                        Box(Modifier.fillMaxWidth().heightIn(min = 72.dp), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(
                                    if (connection is ConnectionState.Authenticated) R.string.sessions_empty_no_sessions
                                    else R.string.sessions_empty_connect_recent,
                                ),
                                style = AppType.subheadline,
                                color = p.muted,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        recent.forEach { session -> RecentSessionCard(session) { onOpenSession(session.id) } }
                    }
                }
            }
        }
    }
}

/** Empty while connected; a spinner while reconnecting; red once it gave up. */
@Composable
private fun HomeConnectionStatus(state: ConnectionState) {
    val p = palette
    val style = AppType.caption.copy(fontWeight = FontWeight.Medium)
    when (state) {
        is ConnectionState.Authenticated -> Unit
        is ConnectionState.Connecting, ConnectionState.Disconnected -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SmallSpinner(color = p.muted, size = 11.dp)
            Text(stringResource(R.string.phase_connecting), style = style, color = p.muted)
        }
        is ConnectionState.Failed -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(6.dp).background(p.danger, CircleShape))
            Text(stringResource(R.string.phase_failed), style = style, color = p.danger)
        }
    }
}

@Composable
private fun DashboardCard(
    value: String,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val p = palette
    Column(
        modifier
            .clip(RoundedCornerShape(AppRadius.medium))
            .background(p.surface)
            .plainClickable(onClick = onClick)
            .padding(AppSpacing.compact),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            value,
            style = AppType.body.copy(fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Black),
            color = p.foreground,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, contentDescription = null, tint = p.muted, modifier = Modifier.size(13.dp))
            Text(title, style = AppType.caption.copy(fontWeight = FontWeight.SemiBold), color = p.muted, maxLines = 1)
        }
    }
}

@Composable
private fun RecentSessionCard(session: SessionSummary, onClick: () -> Unit) {
    val p = palette
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(AppRadius.medium))
            .background(p.surface)
            .plainClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(Modifier.size(7.dp).background(recentStatusColor(session.status), CircleShape))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                session.title.ifBlank { stringResource(R.string.session_untitled) },
                style = AppType.body,
                color = p.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    session.workspaceLabel?.takeIf { it.isNotBlank() } ?: stringResource(R.string.uncategorized),
                    style = AppType.caption2,
                    color = p.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                val todos = session.todos
                if (todos != null && todos.total > 0) {
                    val fraction = (todos.done.toFloat() / todos.total).coerceIn(0f, 1f)
                    Box(Modifier.width(44.dp).height(4.dp).clip(CircleShape).background(p.foreground.copy(alpha = 0.06f))) {
                        Box(Modifier.width(44.dp * fraction).height(4.dp).clip(CircleShape).background(p.foreground))
                    }
                    Text("${todos.done}/${todos.total}", style = AppType.caption2, color = p.muted)
                }
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = p.border,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun recentStatusColor(status: String): Color {
    val p = palette
    return when (status) {
        "running" -> p.foreground.copy(alpha = 0.55f)
        "idle" -> p.border
        "error" -> p.foreground
        else -> p.muted
    }
}

@Composable
private fun greeting(): String = stringResource(
    when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 0..5 -> R.string.greeting_night
        in 6..11 -> R.string.greeting_morning
        in 12..17 -> R.string.greeting_afternoon
        else -> R.string.greeting_evening
    },
)

/** "10月12日 星期日" in Chinese, "Sun, Oct 12" otherwise (iOS `headerDateString`). */
@Composable
private fun dateLine(): String {
    val locale = LocalContext.current.resources.configuration.locales[0]
    val pattern = if (locale.language == "zh") "M月d日 EEEE" else "EEE, MMM d"
    return SimpleDateFormat(pattern, if (locale.language == "zh") locale else Locale.US).format(Date())
}

// ---------- drawer ----------

@Composable
fun SessionDrawer(
    repository: BridgeRepository,
    selectedSessionId: String?,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    onCreateSession: () -> Unit,
    onOpenSession: (String) -> Unit,
) {
    val sessions by repository.sessions.collectAsStateWithLifecycle()
    val connection by repository.connection.collectAsStateWithLifecycle()
    val host by repository.host.collectAsStateWithLifecycle()
    val roster by repository.roster.collectAsStateWithLifecycle()
    val p = palette
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connected = connection is ConnectionState.Authenticated
    val groups = remember(sessions) { SessionGroups.make(sessions) }
    var collapsed by remember { mutableStateOf(setOf<String>()) }
    var editing by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<SessionSummary?>(null) }
    var archiving by remember { mutableStateOf<SessionSummary?>(null) }
    var working by remember { mutableStateOf(setOf<String>()) }
    var actionError by remember { mutableStateOf<String?>(null) }

    fun perform(sessionId: String, action: suspend () -> Unit) {
        working = working + sessionId
        scope.launch {
            attempt { action() }?.let { actionError = context.errorMessage(it) }
            working = working - sessionId
        }
    }

    Column(Modifier.fillMaxSize().background(p.background)) {
        // Header: app icon, instance name and connection, settings and close.
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = AppSpacing.regular, end = AppSpacing.regular, top = 10.dp, bottom = AppSpacing.compact),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIcon(size = 36.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    roster.active?.name ?: host?.let { HostEndpoints.of(it.host)?.display ?: it.host } ?: stringResource(R.string.app_name),
                    style = AppType.body.copy(fontWeight = FontWeight.Bold),
                    color = p.foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    ConnectionStatusLight(connection)
                    Text(phaseText(connection), style = AppType.caption, color = p.muted, maxLines = 1)
                }
            }
            BarIconButton(Icons.Outlined.Settings, stringResource(R.string.a11y_open_settings), onOpenSettings, iconSize = 23.dp)
            BarIconButton(Icons.Filled.Close, stringResource(R.string.a11y_close_sidebar), onClose, iconSize = 21.dp)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.hairline))

        // New session.
        val canCreate = connected && !editing
        Row(
            Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(AppRadius.medium))
                .background(p.mutedSurface)
                .border(0.5.dp, p.hairline, RoundedCornerShape(AppRadius.medium))
                .plainClickable(enabled = canCreate, onClick = onCreateSession)
                .padding(horizontal = AppSpacing.compact),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val alpha = if (canCreate) 1f else 0.35f
            Icon(Icons.Filled.Add, null, tint = p.foreground.copy(alpha = alpha), modifier = Modifier.width(24.dp).size(22.dp))
            Text(
                stringResource(R.string.new_session),
                style = AppType.body.copy(fontWeight = FontWeight.SemiBold),
                color = p.foreground.copy(alpha = alpha),
            )
        }
        Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(0.5.dp).background(p.hairline))

        LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(horizontal = 16.dp)) {
            item(key = "header") {
                Row(
                    Modifier.fillMaxWidth().padding(top = 15.dp, bottom = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.drawer_projects_and_sessions),
                        style = AppType.subheadline,
                        color = p.secondary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(sessions.size.toString(), style = AppType.caption, color = p.tertiary)
                    Text(
                        stringResource(if (editing) R.string.editing_done else R.string.common_edit),
                        style = AppType.subheadline.copy(fontWeight = FontWeight.Medium),
                        color = p.foreground,
                        modifier = Modifier.plainClickable { editing = !editing },
                    )
                }
            }
            if (groups.isEmpty()) {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(if (connected) R.string.sessions_empty_no_sessions else R.string.sessions_empty_connect_projects),
                            style = AppType.subheadline,
                            color = p.secondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            groups.forEach { group ->
                val isCollapsed = group.id in collapsed
                item(key = "group:${group.id}") {
                    ProjectHeader(
                        title = group.workspace ?: stringResource(R.string.uncategorized),
                        count = group.sessions.size,
                        collapsed = isCollapsed,
                    ) { collapsed = if (isCollapsed) collapsed - group.id else collapsed + group.id }
                }
                if (!isCollapsed) {
                    items(group.sessions, key = { "s:${group.id}:${it.id}" }) { session ->
                        Row(
                            Modifier
                                .padding(bottom = 2.dp)
                                .fillMaxWidth()
                                .background(
                                    if (session.id == selectedSessionId) p.mutedSurface else Color.Transparent,
                                    RoundedCornerShape(AppRadius.medium),
                                )
                                .padding(start = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .plainClickable(enabled = !editing) { onOpenSession(session.id) },
                            ) { SessionRow(session, showsRelativeTime = !editing) }
                            if (editing) {
                                val busy = session.id in working
                                Box(
                                    Modifier.size(38.dp, 44.dp).plainClickable(enabled = !busy) { renaming = session },
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Outlined.Edit, stringResource(R.string.rename_title), tint = p.secondary, modifier = Modifier.size(18.dp)) }
                                Box(
                                    Modifier.size(38.dp, 44.dp).plainClickable(enabled = !busy) { archiving = session },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (busy) SmallSpinner(size = 12.dp)
                                    else Icon(Icons.Outlined.Archive, stringResource(R.string.archive_confirm), tint = p.danger, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
                item(key = "gap:${group.id}") { Spacer(Modifier.height(10.dp)) }
            }
            item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
        }
    }

    renaming?.let { session ->
        var draft by remember(session.id) { mutableStateOf(session.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.rename_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.rename_message), style = AppType.footnote, color = p.secondary)
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text(stringResource(R.string.rename_placeholder)) },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = draft.isNotBlank(),
                    onClick = {
                        renaming = null
                        perform(session.id) { repository.renameSession(session.id, draft.trim()) }
                    },
                ) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    archiving?.let { session ->
        AlertDialog(
            onDismissRequest = { archiving = null },
            title = { Text(stringResource(R.string.archive_title)) },
            text = { Text(stringResource(R.string.archive_message)) },
            confirmButton = {
                TextButton(onClick = {
                    archiving = null
                    perform(session.id) { repository.archiveSession(session.id) }
                }) { Text(stringResource(R.string.archive_confirm), color = p.danger) }
            },
            dismissButton = { TextButton(onClick = { archiving = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    actionError?.let { message ->
        AlertDialog(
            onDismissRequest = { actionError = null },
            title = { Text(stringResource(R.string.action_incomplete)) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { actionError = null }) { Text(stringResource(R.string.common_ok)) } },
        )
    }
}

@Composable
private fun ProjectHeader(title: String, count: Int, collapsed: Boolean, onToggle: () -> Unit) {
    val p = palette
    val turn by animateFloatAsState(if (collapsed) 0f else 90f, label = "chevron")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .plainClickable(onClick = onToggle)
            .padding(bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            if (collapsed) Icons.Outlined.Folder else Icons.Filled.Folder,
            contentDescription = null,
            tint = if (collapsed) p.muted else p.foreground,
            modifier = Modifier.width(22.dp).size(20.dp),
        )
        Text(
            title,
            style = AppType.body.copy(fontWeight = FontWeight.Medium),
            color = p.foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(count.toString(), style = AppType.caption, color = p.tertiary)
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = p.tertiary,
            modifier = Modifier.size(16.dp).rotate(turn),
        )
    }
}

@Composable
private fun SessionRow(session: SessionSummary, showsRelativeTime: Boolean) {
    val p = palette
    val status = when (session.status) {
        "running" -> p.foreground.copy(alpha = 0.55f)
        "idle" -> null
        "error" -> p.danger
        else -> p.warning
    }
    Box(Modifier.fillMaxWidth().heightIn(min = 44.dp), contentAlignment = Alignment.CenterStart) {
        if (status != null) {
            Box(Modifier.offset(x = (-7).dp).size(7.dp).background(status, CircleShape))
        }
        Column(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    session.title.ifBlank { stringResource(R.string.session_untitled) },
                    style = AppType.body,
                    color = p.foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (showsRelativeTime && session.lastActivityTs > 0) {
                    Text(relativeTime(session.lastActivityTs), style = AppType.caption2, color = p.tertiary, maxLines = 1)
                }
            }
            val todos = session.todos
            if (session.pendingApproval || session.pendingQuestion || todos != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (session.pendingApproval) Badge(Icons.Filled.PanTool, stringResource(R.string.badge_approval), p.warning)
                    if (session.pendingQuestion) Badge(Icons.AutoMirrored.Filled.Help, stringResource(R.string.badge_question), p.warning)
                    if (todos != null) {
                        Text(stringResource(R.string.todos_progress, todos.done, todos.total), style = AppType.caption2, color = p.secondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun Badge(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(11.dp))
        Text(text, style = AppType.caption2, color = color)
    }
}

/** Ticks every 30 s like the iOS `TimelineView`, so "刚刚" does not stick. */
@Composable
private fun relativeTime(timestamp: Long): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    val (unit, value) = RelativeTime.of(timestamp, now)
    val n = value.toInt()
    return when (unit) {
        RelativeTime.Unit.JustNow -> stringResource(R.string.time_just_now)
        RelativeTime.Unit.Minutes -> stringResource(R.string.time_minutes, n)
        RelativeTime.Unit.Hours -> stringResource(R.string.time_hours, n)
        RelativeTime.Unit.Days -> stringResource(R.string.time_days, n)
        RelativeTime.Unit.Weeks -> stringResource(R.string.time_weeks, n)
        RelativeTime.Unit.Months -> stringResource(R.string.time_months, n)
        RelativeTime.Unit.Years -> stringResource(R.string.time_years, n)
    }
}
