package dev.hailab.deeppilot.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hailab.deeppilot.R
import dev.hailab.deeppilot.bridge.BridgeRepository
import dev.hailab.deeppilot.core.bridge.ConnectionState
import dev.hailab.deeppilot.core.protocol.HostScheduleTarget
import dev.hailab.deeppilot.core.protocol.HostScheduleTask
import dev.hailab.deeppilot.core.protocol.HostSchedulePatch
import dev.hailab.deeppilot.core.protocol.HostScheduleTiming
import dev.hailab.deeppilot.core.protocol.HostTargetKind
import dev.hailab.deeppilot.core.protocol.HostTaskStatus
import dev.hailab.deeppilot.core.protocol.HostTimingKind
import dev.hailab.deeppilot.core.schedule.HostScheduleFilter
import dev.hailab.deeppilot.core.schedule.HostScheduleList
import dev.hailab.deeppilot.ui.AppLayout
import dev.hailab.deeppilot.ui.AppSpacing
import dev.hailab.deeppilot.ui.AppType
import dev.hailab.deeppilot.ui.attempt
import dev.hailab.deeppilot.ui.components.SettingsCard
import dev.hailab.deeppilot.ui.components.SettingsDivider
import dev.hailab.deeppilot.ui.components.SettingsRow
import dev.hailab.deeppilot.ui.components.SmallSpinner
import dev.hailab.deeppilot.ui.components.SwipeAction
import dev.hailab.deeppilot.ui.components.SwipeActionsRow
import dev.hailab.deeppilot.ui.components.plainClickable
import dev.hailab.deeppilot.ui.errorMessage
import dev.hailab.deeppilot.ui.palette
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.TimeZone

private sealed interface HostSchedulePage {
    data object Tasks : HostSchedulePage
    data class Detail(val task: HostScheduleTask) : HostSchedulePage
    data class Editor(val task: HostScheduleTask?) : HostSchedulePage
    data class History(val task: HostScheduleTask) : HostSchedulePage
}

/**
 * iOS `HostScheduleListScreen` with its detail, editor and history, for hosts
 * on the host-level schedule model (`schedulesHost`). [filterSessionId] limits
 * the list to tasks delivered into that conversation; null lists everything.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostScheduleSheet(repository: BridgeRepository, filterSessionId: String?, onDismiss: () -> Unit) {
    val p = palette
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connection by repository.connection.collectAsStateWithLifecycle()
    val capabilities by repository.capabilities.collectAsStateWithLifecycle()
    val revision by repository.scheduleRevision.collectAsStateWithLifecycle()
    val sessions by repository.sessions.collectAsStateWithLifecycle()
    val connected = connection is ConnectionState.Authenticated
    val supported = capabilities?.schedulesHost == true

    var filter by remember { mutableStateOf(HostScheduleFilter.All) }
    var list by remember { mutableStateOf(HostScheduleList()) }
    var loading by remember { mutableStateOf(false) }
    var listError by remember { mutableStateOf<String?>(null) }
    var limitSeconds by remember { mutableStateOf<Double?>(null) }
    var page by remember { mutableStateOf<HostSchedulePage>(HostSchedulePage.Tasks) }
    var busy by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<HostScheduleTask?>(null) }
    var failure by remember { mutableStateOf<Pair<String, String>?>(null) }
    var reloads by remember { mutableIntStateOf(0) }

    fun sessionTitle(id: String): String? = sessions.firstOrNull { it.id == id }?.title?.ifBlank { null }

    suspend fun load(reset: Boolean) {
        if (loading || (!reset && !list.canLoadMore)) return
        loading = true
        listError = null
        val offset = if (reset) 0 else list.nextOffset
        var snapshot: dev.hailab.deeppilot.core.protocol.HostScheduleSnapshot? = null
        val error = attempt { snapshot = repository.hostSchedules(filter.active, offset) }
        snapshot?.let { list = (if (reset) HostScheduleList() else list).applying(it, reset, offset, filterSessionId) }
        if (error != null) listError = context.errorMessage(error)
        loading = false
        // A session's own list keeps paging until it has something to show or the host has no more.
        if (error == null && filterSessionId != null && list.tasks.isEmpty() && list.canLoadMore) load(reset = false)
    }

    LaunchedEffect(connected, supported, filter, revision, reloads) {
        if (connected && supported) {
            load(reset = true)
            if (limitSeconds == null) runCatching { limitSeconds = repository.hostScheduleLimits().newSessionIntervalSeconds }
        }
    }

    /** Runs a change; the list is reloaded from the host afterwards, as iOS does. */
    fun mutate(titleRes: Int, after: (HostScheduleTask?) -> Unit = {}, block: suspend () -> HostScheduleTask?) {
        if (busy) return
        busy = true
        scope.launch {
            var result: HostScheduleTask? = null
            val error = attempt { result = block() }
            busy = false
            if (error != null) failure = context.getString(titleRes) to context.errorMessage(error)
            else after(result)
            reloads++
        }
    }

    fun setActive(task: HostScheduleTask, active: Boolean) = mutate(
        if (active) R.string.schedule_host_resume else R.string.schedule_host_pause,
        after = { updated -> (page as? HostSchedulePage.Detail)?.let { if (updated != null) page = HostSchedulePage.Detail(updated) } },
    ) { repository.editHostSchedule(task, HostSchedulePatch(active = active)) }

    fun delete(task: HostScheduleTask) = mutate(R.string.schedule_delete, after = { deleting = null; page = HostSchedulePage.Tasks }) {
        repository.deleteHostSchedule(task)
        null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = p.groupedBackground,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.94f).imePadding()) {
            when (val current = page) {
                HostSchedulePage.Tasks -> {
                    SheetBar(
                        title = stringResource(R.string.schedule_title),
                        leading = stringResource(R.string.action_cancel) to onDismiss,
                    ) {
                        if (supported && connected) {
                            MenuPicker(HostScheduleFilter.entries, filter, { filterLabel(it) }) { filter = it }
                        }
                        Box(
                            Modifier.size(44.dp).plainClickable(enabled = supported && connected && !busy) { page = HostSchedulePage.Editor(null) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                stringResource(R.string.schedule_add),
                                tint = if (supported && connected && !busy) p.accent else p.tertiary,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    when {
                        !connected -> EmptyState(stringResource(R.string.schedule_offline), null)
                        !supported -> EmptyState(stringResource(R.string.schedule_host_unsupported), null)
                        !list.loaded && loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                SmallSpinner(size = 20.dp)
                                Text(stringResource(R.string.schedule_loading), style = AppType.subheadline, color = p.secondary)
                            }
                        }
                        list.tasks.isEmpty() -> EmptyState(
                            stringResource(if (filterSessionId == null) R.string.schedule_host_empty else R.string.schedule_host_session_empty),
                            listError,
                        )
                        else -> Column(
                            Modifier
                                .verticalScroll(rememberScrollState())
                                .navigationBarsPadding()
                                .padding(horizontal = AppLayout.pageInset)
                                .padding(top = AppSpacing.small, bottom = AppSpacing.large),
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.small),
                        ) {
                            SettingsCard {
                                list.tasks.forEachIndexed { index, task ->
                                    if (index > 0) SettingsDivider()
                                    SwipeActionsRow(
                                        actions = listOf(
                                            SwipeAction(stringResource(R.string.schedule_edit), Icons.Outlined.Edit, p.accent) {
                                                page = HostSchedulePage.Editor(task)
                                            },
                                            SwipeAction(
                                                stringResource(if (task.isActive) R.string.schedule_host_pause else R.string.schedule_host_resume),
                                                if (task.isActive) Icons.Outlined.PauseCircle else Icons.Outlined.PlayCircle,
                                                p.warning,
                                            ) { setActive(task, !task.isActive) },
                                            SwipeAction(stringResource(R.string.schedule_delete), Icons.Outlined.Delete, p.danger) { deleting = task },
                                        ),
                                    ) { HostScheduleRow(task, ::sessionTitle) { page = HostSchedulePage.Detail(task) } }
                                }
                                if (list.canLoadMore) {
                                    SettingsDivider()
                                    Box(
                                        Modifier.fillMaxWidth().plainClickable(enabled = !loading) { scope.launch { load(reset = false) } }.padding(12.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (loading) SmallSpinner() else Text(stringResource(R.string.schedule_host_load_more), style = AppType.body, color = p.accent)
                                    }
                                }
                            }
                            limitSeconds?.let { Footnote(stringResource(R.string.schedule_host_limits, it.toInt())) }
                            if (list.total > 0 && filterSessionId == null) Footnote(stringResource(R.string.schedule_host_total, list.tasks.size, list.total))
                            listError?.let { Footnote(it) }
                        }
                    }
                }
                is HostSchedulePage.Detail -> HostScheduleDetail(
                    repository = repository,
                    task = current.task,
                    busy = busy,
                    limitSeconds = limitSeconds,
                    sessionTitle = ::sessionTitle,
                    onBack = { page = HostSchedulePage.Tasks },
                    onEdit = { page = HostSchedulePage.Editor(current.task) },
                    onHistory = { page = HostSchedulePage.History(current.task) },
                    onRun = {
                        mutate(R.string.schedule_host_run, after = { updated -> if (updated != null) page = HostSchedulePage.Detail(updated) }) {
                            repository.runHostSchedule(current.task)
                        }
                    },
                    onSetActive = { setActive(current.task, it) },
                    onDelete = { deleting = current.task },
                    onRefreshed = { page = HostSchedulePage.Detail(it) },
                )
                is HostSchedulePage.Editor -> HostScheduleEditor(
                    task = current.task,
                    defaultTarget = current.task?.target
                        ?: filterSessionId?.let { HostScheduleTarget.session(it) }
                        ?: sessions.firstOrNull()?.let { HostScheduleTarget.session(it.id) }
                        ?: HostScheduleTarget.newOnce(),
                    sessions = sessions.map { it.id to (it.title.ifBlank { null } ?: stringResource(R.string.conversation_untitled)) },
                    limitSeconds = limitSeconds,
                    saving = busy,
                    onCancel = { page = current.task?.let { HostSchedulePage.Detail(it) } ?: HostSchedulePage.Tasks },
                    onSave = { title, prompt, timing, target ->
                        val task = current.task
                        mutate(
                            if (task == null) R.string.schedule_host_create_title else R.string.schedule_host_edit_title,
                            after = { saved -> page = saved?.let { HostSchedulePage.Detail(it) } ?: HostSchedulePage.Tasks },
                        ) {
                            if (task == null) repository.createHostSchedule(title, prompt, timing, target)
                            else repository.editHostSchedule(
                                task,
                                HostSchedulePatch(
                                    title = title.takeIf { it != task.title },
                                    prompt = prompt.takeIf { it != task.prompt },
                                    timing = timing.takeIf { it != task.timing },
                                    target = target.takeIf { it != task.target },
                                ),
                            )
                        }
                    },
                )
                is HostSchedulePage.History -> ScheduleHistoryPage(current.task.id, { repository.hostScheduleHistory(current.task.id) }) {
                    page = HostSchedulePage.Detail(current.task)
                }
            }
        }
    }

    deleting?.let { task ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.schedule_host_delete_confirm)) },
            confirmButton = { TextButton(onClick = { delete(task) }, enabled = !busy) { Text(stringResource(R.string.schedule_delete), color = p.danger) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    failure?.let { (title, message) ->
        AlertDialog(
            onDismissRequest = { failure = null },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { failure = null }) { Text(stringResource(R.string.common_ok)) } },
        )
    }
}

@Composable
private fun filterLabel(filter: HostScheduleFilter): String = stringResource(
    when (filter) {
        HostScheduleFilter.All -> R.string.schedule_host_filter_all
        HostScheduleFilter.Active -> R.string.schedule_host_filter_active
        HostScheduleFilter.Inactive -> R.string.schedule_host_filter_inactive
    },
)

@Composable
private fun Footnote(text: String) {
    Text(text, style = AppType.footnote, color = palette.secondary, modifier = Modifier.padding(horizontal = AppSpacing.regular))
}

@Composable
private fun statusColor(task: HostScheduleTask): Color = when (task.status) {
    HostTaskStatus.ACTIVE -> palette.accent
    HostTaskStatus.PAUSED -> palette.warning
    else -> palette.muted
}

@Composable
private fun HostScheduleRow(task: HostScheduleTask, sessionTitle: (String) -> String?, onOpen: () -> Unit) {
    val p = palette
    val color = statusColor(task)
    Row(
        Modifier
            .fillMaxWidth()
            .background(p.surface)
            .plainClickable(onClick = onOpen)
            .padding(horizontal = AppSpacing.regular, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            if (task.isActive) Icons.Outlined.History else Icons.Outlined.PauseCircle,
            null,
            tint = color,
            modifier = Modifier.width(24.dp).size(22.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    task.title.ifEmpty { stringResource(R.string.schedule_untitled) },
                    style = AppType.headline,
                    color = p.foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    statusText(task.status),
                    style = AppType.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = color,
                    modifier = Modifier.background(color.copy(alpha = 0.12f), CircleShape).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Text(timingText(task.timing), style = AppType.subheadline, color = p.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(targetText(task.target, sessionTitle), style = AppType.caption, color = p.tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (task.isActive) formatTimestamp(task.scheduledAt)?.let {
                Text(stringResource(R.string.schedule_host_next) + ": " + it, style = AppType.caption, color = p.tertiary)
            }
        }
        Chevron()
    }
}

@Composable
private fun ColumnScope.HostScheduleDetail(
    repository: BridgeRepository,
    task: HostScheduleTask,
    busy: Boolean,
    limitSeconds: Double?,
    sessionTitle: (String) -> String?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onHistory: () -> Unit,
    onRun: () -> Unit,
    onSetActive: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onRefreshed: (HostScheduleTask) -> Unit,
) {
    val p = palette
    // Viewing re-reads the task (the host does not activate its session for it).
    LaunchedEffect(task.id) { runCatching { repository.hostSchedule(task.id) }.getOrNull()?.takeIf { it != task }?.let(onRefreshed) }
    SheetBar(title = stringResource(R.string.schedule_host_detail_title), leading = stringResource(R.string.action_back) to onBack) {
        Text(
            stringResource(R.string.schedule_edit),
            style = AppType.body,
            color = if (busy) p.tertiary else p.accent,
            modifier = Modifier.padding(horizontal = 10.dp).plainClickable(enabled = !busy, onClick = onEdit),
        )
    }
    Column(
        Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = AppLayout.pageInset)
            .padding(top = AppSpacing.small, bottom = AppSpacing.large),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.section),
    ) {
        SettingsCard {
            Column(Modifier.padding(horizontal = AppSpacing.regular, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(task.title.ifEmpty { stringResource(R.string.schedule_untitled) }, style = AppType.headline, color = p.foreground, modifier = Modifier.weight(1f, fill = false))
                    val color = statusColor(task)
                    Text(
                        statusText(task.status),
                        style = AppType.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = color,
                        modifier = Modifier.background(color.copy(alpha = 0.12f), CircleShape).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                if (task.prompt.isNotEmpty()) Text(task.prompt, style = AppType.body, color = p.secondary)
            }
        }
        Labeled(stringResource(R.string.schedule_host_detail_schedule)) {
            Field(stringResource(R.string.schedule_host_timing_label), timingText(task.timing))
            SettingsDivider()
            Field(stringResource(R.string.schedule_host_target), targetText(task.target, sessionTitle))
            if (task.isActive) formatTimestamp(task.scheduledAt)?.let { SettingsDivider(); Field(stringResource(R.string.schedule_host_next), it) }
            formatTimestamp(task.updatedAt)?.let { SettingsDivider(); Field(stringResource(R.string.schedule_host_updated), it) }
            if (task.executionActive == true) {
                SettingsDivider()
                Field(stringResource(R.string.schedule_host_executing), stringResource(R.string.schedule_host_executing_value))
            }
            formatTimestamp(task.deliveryFloorEndsAt)?.let { SettingsDivider(); Field(stringResource(R.string.schedule_host_delivery_floor), it) }
        }
        task.lastDelivery?.let { delivery ->
            Labeled(stringResource(R.string.schedule_host_last_delivery)) {
                formatTimestamp(delivery.deliveredAt)?.let { Field(stringResource(R.string.schedule_host_delivered_at), it) }
                formatTimestamp(delivery.scheduledAt)?.let { SettingsDivider(); Field(stringResource(R.string.schedule_host_scheduled_at), it) }
                delivery.targetSessionId?.let { id ->
                    SettingsDivider()
                    Field(stringResource(R.string.schedule_host_target), stringResource(R.string.schedule_host_target_session, sessionTitle(id) ?: id))
                }
            }
        }
        limitSeconds?.takeIf { task.target.kind != HostTargetKind.SESSION }?.let { Footnote(stringResource(R.string.schedule_host_limits, it.toInt())) }
        SettingsCard {
            ActionRow(stringResource(R.string.schedule_host_run), Icons.Outlined.PlayCircle, p.accent, enabled = !busy && task.status != HostTaskStatus.ENDED, onClick = onRun)
            SettingsDivider()
            ActionRow(
                stringResource(if (task.isActive) R.string.schedule_host_pause else R.string.schedule_host_resume),
                if (task.isActive) Icons.Outlined.PauseCircle else Icons.Outlined.PlayCircle,
                p.accent,
                enabled = !busy,
            ) { onSetActive(!task.isActive) }
            SettingsDivider()
            ActionRow(stringResource(R.string.schedule_history), Icons.Outlined.History, p.accent, enabled = true, onClick = onHistory)
            SettingsDivider()
            ActionRow(stringResource(R.string.schedule_delete), Icons.Outlined.Delete, p.danger, enabled = !busy, onClick = onDelete)
        }
    }
}

@Composable
private fun Labeled(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title.uppercase(), style = AppType.footnote, color = palette.secondary, modifier = Modifier.padding(horizontal = AppSpacing.regular))
        SettingsCard(content = content)
    }
}

@Composable
private fun Field(label: String, value: String) {
    SettingsRow(title = label) {
        Text(value, style = AppType.body, color = palette.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ActionRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, enabled: Boolean, onClick: () -> Unit) {
    val p = palette
    Row(
        Modifier.fillMaxWidth().plainClickable(enabled = enabled, onClick = onClick).padding(horizontal = AppSpacing.regular, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = if (enabled) tint else p.tertiary, modifier = Modifier.size(20.dp))
        Text(label, style = AppType.body, color = if (enabled) tint else p.tertiary)
    }
}

private val TIMING_KINDS = listOf(
    HostTimingKind.AFTER to R.string.schedule_after,
    HostTimingKind.EVERY to R.string.schedule_every,
    HostTimingKind.AT to R.string.schedule_at,
    HostTimingKind.DAILY to R.string.schedule_daily,
    HostTimingKind.WEEKLY to R.string.schedule_weekly,
    HostTimingKind.CRON to R.string.schedule_cron,
    HostTimingKind.RRULE to R.string.schedule_rrule,
)

private val TARGET_KINDS = listOf(
    HostTargetKind.SESSION to R.string.schedule_host_target_session_option,
    HostTargetKind.NEW_ONCE to R.string.schedule_host_target_new_once,
    HostTargetKind.NEW to R.string.schedule_host_target_new,
)

/** iOS `HostScheduleEditorView`: name, prompt, timing and delivery target. */
@Composable
private fun ColumnScope.HostScheduleEditor(
    task: HostScheduleTask?,
    defaultTarget: HostScheduleTarget,
    sessions: List<Pair<String, String>>,
    limitSeconds: Double?,
    saving: Boolean,
    onCancel: () -> Unit,
    onSave: (String, String, HostScheduleTiming, HostScheduleTarget) -> Unit,
) {
    val p = palette
    val context = LocalContext.current
    val timing = task?.timing
    var title by remember(task) { mutableStateOf(task?.title.orEmpty()) }
    var prompt by remember(task) { mutableStateOf(task?.prompt.orEmpty()) }
    var kind by remember(task) { mutableStateOf(timing?.kind?.takeIf { k -> TIMING_KINDS.any { it.first == k } } ?: HostTimingKind.AFTER) }
    var minutes by remember(task) { mutableIntStateOf(maxOf(1, ((timing?.afterSeconds ?: timing?.everySeconds ?: 600.0) / 60).toInt())) }
    var time by remember(task) { mutableStateOf(clockOf(timing?.daily?.time ?: timing?.weekly?.time) ?: LocalTime.of(9, 0)) }
    var weekdays by remember(task) { mutableStateOf(timing?.weekly?.weekdays?.toSet() ?: setOf(2)) }
    var cron by remember(task) { mutableStateOf(timing?.cron?.expression ?: "0 9 * * 1-5") }
    var rrule by remember(task) { mutableStateOf(timing?.rrule ?: "FREQ=DAILY;BYHOUR=9;BYMINUTE=0") }
    var at by remember(task) { mutableStateOf(instantOf(timing) ?: LocalDateTime.now().plusHours(1).truncatedTo(ChronoUnit.HOURS)) }
    val timeZone = remember(task) {
        timing?.daily?.timeZone ?: timing?.weekly?.timeZone ?: timing?.cron?.timeZone ?: timing?.atDateTime?.timeZone ?: TimeZone.getDefault().id
    }
    val target = task?.target ?: defaultTarget
    var targetKind by remember(task) { mutableStateOf(target.kind) }
    var targetSessionId by remember(task) { mutableStateOf(target.fixedSessionId ?: sessions.firstOrNull()?.first) }

    fun buildTiming(): HostScheduleTiming? = when (kind) {
        HostTimingKind.AFTER -> HostScheduleTiming.after(maxOf(60L, minutes * 60L))
        HostTimingKind.EVERY -> HostScheduleTiming.every(maxOf(60L, minutes * 60L))
        HostTimingKind.AT -> HostScheduleTiming.at(at.atZone(ZoneId.systemDefault()).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        HostTimingKind.DAILY -> HostScheduleTiming.daily(clockText(time), timeZone)
        HostTimingKind.WEEKLY -> weekdays.takeIf { it.isNotEmpty() }?.let { HostScheduleTiming.weekly(clockText(time), timeZone, it.sorted()) }
        HostTimingKind.CRON -> cron.trim().takeIf { it.isNotEmpty() }?.let { HostScheduleTiming.cron(it, timeZone) }
        HostTimingKind.RRULE -> rrule.trim().takeIf { it.isNotEmpty() }?.let { HostScheduleTiming.rrule(it) }
        else -> null
    }

    fun buildTarget(): HostScheduleTarget? = when (targetKind) {
        HostTargetKind.SESSION -> targetSessionId?.takeIf { it.isNotEmpty() }?.let { HostScheduleTarget.session(it) }
        // Keep the host's reserved session when editing, or "reuse after first delivery" turns into "always new".
        HostTargetKind.NEW_ONCE -> HostScheduleTarget(HostTargetKind.NEW_ONCE, task?.target?.sessionId.takeIf { task?.target?.kind == HostTargetKind.NEW_ONCE }, target.create)
        else -> HostScheduleTarget(HostTargetKind.NEW, create = target.create)
    }

    val trimmedTitle = title.trim()
    val trimmedPrompt = prompt.trim()
    val builtTiming = buildTiming()
    val builtTarget = buildTarget()
    val canSave = trimmedTitle.isNotEmpty() && trimmedPrompt.isNotEmpty() && builtTiming != null && builtTarget != null

    SheetBar(
        title = stringResource(if (task == null) R.string.schedule_host_create_title else R.string.schedule_host_edit_title),
        leading = stringResource(R.string.action_cancel) to onCancel,
    ) {
        if (saving) {
            Box(Modifier.padding(horizontal = 14.dp)) { SmallSpinner() }
        } else {
            Text(
                stringResource(R.string.common_save),
                style = AppType.body.copy(fontWeight = FontWeight.SemiBold),
                color = if (canSave) p.accent else p.tertiary,
                modifier = Modifier.padding(horizontal = 10.dp).plainClickable(enabled = canSave) {
                    onSave(trimmedTitle, trimmedPrompt, builtTiming!!, builtTarget!!)
                },
            )
        }
    }
    Column(
        Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = AppLayout.pageInset)
            .padding(top = AppSpacing.small, bottom = AppSpacing.large),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.section),
    ) {
        SettingsCard {
            FormField(title, { title = it.take(120) }, stringResource(R.string.schedule_name), singleLine = true)
            SettingsDivider()
            FormField(prompt, { prompt = it }, stringResource(R.string.schedule_prompt), singleLine = false)
        }
        Labeled(stringResource(R.string.schedule_host_timing_label)) {
            SettingsRow(title = stringResource(R.string.schedule_type)) {
                MenuPicker(TIMING_KINDS.map { it.first }, kind, { k -> stringResource(TIMING_KINDS.first { it.first == k }.second) }) { kind = it }
            }
            when (kind) {
                HostTimingKind.AFTER, HostTimingKind.EVERY -> {
                    SettingsDivider()
                    SettingsRow(title = "$minutes ${stringResource(R.string.schedule_minutes)}") {
                        Stepper(
                            canDecrease = minutes > 1,
                            canIncrease = minutes < 24 * 60,
                            onDecrease = { minutes = maxOf(1, minutes - 5) },
                            onIncrease = { minutes = minOf(24 * 60, minutes + 5) },
                        )
                    }
                }
                HostTimingKind.AT -> {
                    SettingsDivider()
                    SettingsRow(title = stringResource(R.string.schedule_at_time)) {
                        PickerChip(at.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))) { pickDateTime(context, at) { at = it } }
                    }
                }
                HostTimingKind.DAILY, HostTimingKind.WEEKLY -> {
                    SettingsDivider()
                    SettingsRow(title = stringResource(R.string.schedule_time)) {
                        PickerChip(clockText(time)) {
                            TimePickerDialog(context, { _, hour, minute -> time = LocalTime.of(hour, minute) }, time.hour, time.minute, DateFormat.is24HourFormat(context)).show()
                        }
                    }
                    if (kind == HostTimingKind.WEEKLY) {
                        SettingsDivider()
                        Column(Modifier.padding(horizontal = AppSpacing.regular, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.schedule_weekdays), style = AppType.caption, color = p.secondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                (1..7).forEach { weekday ->
                                    val selected = weekday in weekdays
                                    Box(
                                        Modifier
                                            .size(30.dp)
                                            .background(if (selected) p.accent else p.mutedSurface, CircleShape)
                                            .plainClickable { weekdays = if (selected) weekdays - weekday else weekdays + weekday },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(weekdayLabel(weekday), style = AppType.caption.copy(fontWeight = FontWeight.SemiBold), color = if (selected) Color.White else p.foreground)
                                    }
                                }
                            }
                        }
                    }
                }
                HostTimingKind.CRON -> {
                    SettingsDivider()
                    FormField(cron, { cron = it }, stringResource(R.string.schedule_cron_expression), singleLine = true, plain = true)
                }
                HostTimingKind.RRULE -> {
                    SettingsDivider()
                    FormField(rrule, { rrule = it }, stringResource(R.string.schedule_rrule_expression), singleLine = true, plain = true)
                }
            }
            if (kind in setOf(HostTimingKind.DAILY, HostTimingKind.WEEKLY, HostTimingKind.CRON)) {
                SettingsDivider()
                Text(
                    stringResource(R.string.schedule_timezone) + ": " + timeZone,
                    style = AppType.caption,
                    color = p.secondary,
                    modifier = Modifier.padding(horizontal = AppSpacing.regular, vertical = 10.dp),
                )
            }
        }
        Labeled(stringResource(R.string.schedule_host_target)) {
            SettingsRow(title = stringResource(R.string.schedule_host_target)) {
                MenuPicker(TARGET_KINDS.map { it.first }, targetKind, { k -> stringResource(TARGET_KINDS.first { it.first == k }.second) }) { targetKind = it }
            }
            if (targetKind == HostTargetKind.SESSION) {
                SettingsDivider()
                val known = sessions.map { it.first }
                val options = if (targetSessionId != null && targetSessionId !in known) known + targetSessionId!! else known
                SettingsRow(title = stringResource(R.string.schedule_host_target_pick)) {
                    if (options.isEmpty()) Text(stringResource(R.string.schedule_host_target_required), style = AppType.body, color = p.secondary)
                    else MenuPicker(options, targetSessionId ?: options.first(), { id ->
                        sessions.firstOrNull { it.first == id }?.second ?: stringResource(R.string.schedule_host_target_archived_session)
                    }) { targetSessionId = it }
                }
            }
        }
        limitSeconds?.takeIf { targetKind != HostTargetKind.SESSION }?.let { Footnote(stringResource(R.string.schedule_host_limits, it.toInt())) }
    }
}

@Composable
private fun PickerChip(text: String, onClick: () -> Unit) {
    val p = palette
    Text(
        text,
        style = AppType.body,
        color = p.foreground,
        modifier = Modifier
            .background(p.secondaryFill, RoundedCornerShape(8.dp))
            .plainClickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    )
}

private fun pickDateTime(context: Context, current: LocalDateTime, onPicked: (LocalDateTime) -> Unit) {
    DatePickerDialog(context, { _, year, month, day ->
        TimePickerDialog(context, { _, hour, minute ->
            onPicked(LocalDateTime.of(LocalDate.of(year, month + 1, day), LocalTime.of(hour, minute)))
        }, current.hour, current.minute, DateFormat.is24HourFormat(context)).show()
    }, current.year, current.monthValue - 1, current.dayOfMonth).show()
}

/** The protocol's `time` is `HH:mm` or `HH:mm:ss`; the editor sends `HH:mm`, as iOS does. */
private fun clockText(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)

private fun clockOf(value: String?): LocalTime? = value?.split(':')?.takeIf { it.size >= 2 }?.let {
    runCatching { LocalTime.of(it[0].toInt(), it[1].toInt()) }.getOrNull()
}

private fun instantOf(timing: HostScheduleTiming?): LocalDateTime? {
    if (timing?.kind != HostTimingKind.AT) return null
    timing.atInstant?.let { text -> return parseInstant(text)?.atZone(ZoneId.systemDefault())?.toLocalDateTime() }
    val value = timing.atDateTime ?: return null
    return runCatching { LocalDateTime.of(LocalDate.parse(value.date), clockOf(value.time) ?: return null) }.getOrNull()
}

private fun parseInstant(value: String): Instant? =
    runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull() ?: runCatching { Instant.parse(value) }.getOrNull()

/** Local medium date and short time; the raw string when it does not parse, so nothing is hidden. */
private fun formatTimestamp(value: String?): String? {
    if (value.isNullOrEmpty()) return null
    val instant = parseInstant(value) ?: return value
    return instant.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
}

@Composable
private fun statusText(status: String): String = stringResource(
    when (status) {
        HostTaskStatus.ACTIVE -> R.string.schedule_host_status_active
        HostTaskStatus.PAUSED -> R.string.schedule_host_status_paused
        HostTaskStatus.ENDED -> R.string.schedule_host_status_ended
        else -> R.string.schedule_host_status_unknown
    },
)

@Composable
private fun timingText(timing: HostScheduleTiming): String {
    fun minutes(seconds: Double?) = maxOf(1, Math.round((seconds ?: 0.0) / 60).toInt())
    fun short(value: String) = value.split(':').takeIf { it.size >= 2 }?.let { "${it[0]}:${it[1]}" } ?: value
    return when (timing.kind) {
        HostTimingKind.AFTER -> stringResource(R.string.schedule_host_timing_after, minutes(timing.afterSeconds))
        HostTimingKind.EVERY -> stringResource(R.string.schedule_host_timing_every, minutes(timing.everySeconds))
        HostTimingKind.AT -> stringResource(
            R.string.schedule_host_timing_at,
            timing.atInstant?.let { formatTimestamp(it) }
                ?: timing.atDateTime?.let { "${it.date} ${short(it.time)} ${it.timeZone}" }
                ?: "",
        )
        HostTimingKind.DAILY -> stringResource(R.string.schedule_host_timing_daily, timing.daily?.let { "${short(it.time)} ${it.timeZone}" }.orEmpty())
        HostTimingKind.WEEKLY -> stringResource(
            R.string.schedule_host_timing_weekly,
            timing.weekly?.weekdays.orEmpty().sorted().filter { it in 1..7 }.joinToString("") { weekdayLabel(it) },
            timing.weekly?.let { "${short(it.time)} ${it.timeZone}" }.orEmpty(),
        )
        HostTimingKind.CRON -> stringResource(R.string.schedule_host_timing_cron, timing.cron?.expression.orEmpty())
        HostTimingKind.RRULE -> stringResource(R.string.schedule_host_timing_rrule, timing.rrule.orEmpty())
        else -> timing.kind
    }
}

@Composable
private fun targetText(target: HostScheduleTarget, sessionTitle: (String) -> String?): String = when (target.kind) {
    HostTargetKind.SESSION -> stringResource(R.string.schedule_host_target_session, target.sessionId?.let { sessionTitle(it) ?: it }.orEmpty())
    HostTargetKind.NEW_ONCE -> target.sessionId?.let(sessionTitle)?.let { stringResource(R.string.schedule_host_target_session, it) }
        ?: stringResource(R.string.schedule_host_target_new_once)
    else -> stringResource(R.string.schedule_host_target_new)
}
