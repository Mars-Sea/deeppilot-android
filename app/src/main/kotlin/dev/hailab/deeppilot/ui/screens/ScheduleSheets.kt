package dev.hailab.deeppilot.ui.screens

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hailab.deeppilot.R
import dev.hailab.deeppilot.bridge.BridgeRepository
import dev.hailab.deeppilot.core.protocol.ScheduleClock
import dev.hailab.deeppilot.core.protocol.ScheduleCreate
import dev.hailab.deeppilot.core.protocol.ScheduleCron
import dev.hailab.deeppilot.core.protocol.ScheduleHistory
import dev.hailab.deeppilot.core.protocol.ScheduleTask
import dev.hailab.deeppilot.ui.AppLayout
import dev.hailab.deeppilot.ui.AppSpacing
import dev.hailab.deeppilot.ui.AppType
import dev.hailab.deeppilot.ui.attempt
import dev.hailab.deeppilot.ui.components.NavBarContent
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
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import java.util.TimeZone

/** Which page the schedule sheet shows (iOS presents these as stacked sheets). */
private sealed interface SchedulePage {
    data object Tasks : SchedulePage
    data class Editor(val task: ScheduleTask?) : SchedulePage
    data class History(val task: ScheduleTask) : SchedulePage
}

/** iOS selectors offered when creating a task (`at` is host-only). */
private enum class ScheduleKindOption(val wire: String, val label: Int) {
    After("after", R.string.schedule_after),
    Every("every", R.string.schedule_every),
    Daily("daily", R.string.schedule_daily),
    Weekly("weekly", R.string.schedule_weekly),
    Cron("cron", R.string.schedule_cron),
}

/** iOS `ScheduleListView` and its editor and history sheets, as one large sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleListSheet(repository: BridgeRepository, sessionId: String, onDismiss: () -> Unit) {
    val p = palette
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val capabilities by repository.capabilities.collectAsStateWithLifecycle()
    val supported = capabilities?.schedules == true
    val state by repository.schedules.collectAsStateWithLifecycle()
    val schedules = state?.takeIf { it.sessionId == sessionId }
    val tasks = schedules?.tasks.orEmpty()

    var page by remember { mutableStateOf<SchedulePage>(SchedulePage.Tasks) }
    var busy by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ScheduleTask?>(null) }
    // The alert names the operation that actually failed.
    var failure by remember { mutableStateOf<Pair<String, String>?>(null) }

    fun save(task: ScheduleTask?, title: String, prompt: String, create: ScheduleCreate?) {
        if (busy) return
        busy = true
        scope.launch {
            val error = attempt {
                if (task != null) repository.updateSchedule(sessionId, task, title, prompt)
                else if (create != null) repository.createSchedule(sessionId, create)
            }
            busy = false
            if (error == null) page = SchedulePage.Tasks
            else failure = context.getString(if (task == null) R.string.schedule_create_title else R.string.schedule_edit_title) to
                context.errorMessage(error)
        }
    }

    fun delete(task: ScheduleTask) {
        if (busy) return
        busy = true
        scope.launch {
            val error = attempt { repository.deleteSchedule(sessionId, task.id) }
            busy = false
            deleting = null
            if (error != null) failure = context.getString(R.string.schedule_delete) to context.errorMessage(error)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = p.groupedBackground,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.94f).imePadding()) {
            when (val current = page) {
                SchedulePage.Tasks -> {
                    SheetBar(
                        title = stringResource(R.string.schedule_title),
                        leading = stringResource(R.string.action_cancel) to onDismiss,
                    ) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .plainClickable(enabled = supported && !busy) { page = SchedulePage.Editor(null) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                stringResource(R.string.schedule_add),
                                tint = if (supported && !busy) p.accent else p.tertiary,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    when {
                        !supported -> EmptyState(stringResource(R.string.schedule_unsupported), null)
                        tasks.isEmpty() && schedules?.loading != false -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                SmallSpinner(size = 20.dp)
                                Text(stringResource(R.string.schedule_loading), style = AppType.subheadline, color = p.secondary)
                            }
                        }
                        tasks.isEmpty() -> EmptyState(
                            stringResource(R.string.schedule_empty),
                            schedules?.error?.let { context.errorMessage(it) } ?: stringResource(R.string.schedule_prompt_placeholder),
                        )
                        else -> Column(
                            Modifier
                                .verticalScroll(rememberScrollState())
                                .navigationBarsPadding()
                                .padding(horizontal = AppLayout.pageInset)
                                .padding(top = AppSpacing.small, bottom = AppSpacing.large),
                        ) {
                            SettingsCard {
                                tasks.forEachIndexed { index, task ->
                                    if (index > 0) SettingsDivider()
                                    SwipeActionsRow(
                                        actions = listOf(
                                            SwipeAction(stringResource(R.string.schedule_edit), Icons.Outlined.Edit, p.accent) {
                                                page = SchedulePage.Editor(task)
                                            },
                                            SwipeAction(stringResource(R.string.schedule_delete), Icons.Outlined.Delete, p.danger) {
                                                deleting = task
                                            },
                                        ),
                                    ) { ScheduleRow(task) { page = SchedulePage.History(task) } }
                                }
                            }
                        }
                    }
                }
                is SchedulePage.Editor -> ScheduleEditor(
                    task = current.task,
                    saving = busy,
                    onCancel = { page = SchedulePage.Tasks },
                    onSave = { title, prompt, create -> save(current.task, title, prompt, create) },
                )
                is SchedulePage.History -> ScheduleHistoryPage(repository, sessionId, current.task) { page = SchedulePage.Tasks }
            }
        }
    }

    deleting?.let { task ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.schedule_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { delete(task) }, enabled = !busy) { Text(stringResource(R.string.schedule_delete), color = p.danger) }
            },
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

/** The sheet's inline navigation bar: a text button, a centered title, optional trailing items. */
@Composable
private fun SheetBar(
    title: String,
    leading: Pair<String, () -> Unit>?,
    trailing: @Composable () -> Unit = {},
) {
    val p = palette
    NavBarContent(
        leading = {
            if (leading != null) {
                Text(
                    leading.first,
                    style = AppType.body,
                    color = p.accent,
                    modifier = Modifier.padding(horizontal = 10.dp).plainClickable(onClick = leading.second),
                )
            }
        },
        trailing = { trailing() },
        principal = { Text(title, style = AppType.headline, color = p.foreground, maxLines = 1) },
        background = p.groupedBackground,
    )
}

/** iOS `ContentUnavailableView` with the clock symbol. */
@Composable
private fun EmptyState(title: String, description: String?) {
    val p = palette
    Box(Modifier.fillMaxSize().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.History, null, tint = p.secondary, modifier = Modifier.size(44.dp))
            Spacer(Modifier.size(4.dp))
            Text(title, style = AppType.title3.copy(fontWeight = FontWeight.Bold), color = p.foreground, textAlign = TextAlign.Center)
            if (description != null) {
                Text(description, style = AppType.subheadline, color = p.secondary, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun ScheduleRow(task: ScheduleTask, onOpen: () -> Unit) {
    val p = palette
    Row(
        Modifier
            .fillMaxWidth()
            .background(p.surface)
            .plainClickable(onClick = onOpen)
            .padding(horizontal = AppSpacing.regular, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Outlined.History,
            null,
            tint = if (task.state == "overdue") p.warning else p.accent,
            modifier = Modifier.width(24.dp).size(22.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(task.title.ifEmpty { stringResource(R.string.schedule_untitled) }, style = AppType.headline, color = p.foreground)
            Text(task.prompt, style = AppType.subheadline, color = p.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(task.scheduledAt, style = AppType.caption, color = p.tertiary)
        }
        Chevron()
    }
}

/** iOS `ScheduleEditorView`: title and prompt; the schedule itself only when creating. */
@Composable
private fun ColumnScope.ScheduleEditor(
    task: ScheduleTask?,
    saving: Boolean,
    onCancel: () -> Unit,
    onSave: (title: String, prompt: String, create: ScheduleCreate?) -> Unit,
) {
    val p = palette
    val context = LocalContext.current
    var title by remember(task) { mutableStateOf(task?.title.orEmpty()) }
    var prompt by remember(task) { mutableStateOf(task?.prompt.orEmpty()) }
    var kind by remember(task) { mutableStateOf(ScheduleKindOption.entries.firstOrNull { it.wire == task?.kind } ?: ScheduleKindOption.After) }
    var minutes by remember(task) {
        mutableIntStateOf(maxOf(1, ((task?.afterSeconds ?: task?.everySeconds ?: 600.0) / 60).toInt()))
    }
    var time by remember(task) { mutableStateOf(LocalTime.now().withSecond(0).withNano(0)) }
    var weekdays by remember(task) { mutableStateOf(task?.weekdays?.toSet() ?: setOf(1)) }
    var cron by remember(task) { mutableStateOf(task?.expression ?: "*/15 9-17 * * 1-5") }
    val timeZone = remember(task) { task?.timeZone ?: TimeZone.getDefault().id }

    val trimmedTitle = title.trim()
    val trimmedPrompt = prompt.trim()
    val canSave = trimmedTitle.isNotEmpty() && trimmedPrompt.isNotEmpty() && when {
        task != null -> true
        kind == ScheduleKindOption.Weekly -> weekdays.isNotEmpty()
        kind == ScheduleKindOption.Cron -> cron.isNotBlank()
        else -> true
    }

    fun submit() {
        if (!canSave || saving) return
        val clock = time.toString().let { if (it.length == 5) "$it:00" else it }
        val create = if (task != null) null else {
            val base = ScheduleCreate(clientRequestId = "", sessionId = "", title = trimmedTitle, prompt = trimmedPrompt)
            when (kind) {
                ScheduleKindOption.After -> base.copy(afterSeconds = maxOf(1, minutes) * 60L)
                ScheduleKindOption.Every -> base.copy(everySeconds = maxOf(60, minutes) * 60L)
                ScheduleKindOption.Daily -> base.copy(daily = ScheduleClock(clock, timeZone))
                ScheduleKindOption.Weekly -> base.copy(weekly = ScheduleClock(clock, timeZone, weekdays.sorted()))
                ScheduleKindOption.Cron -> base.copy(cron = ScheduleCron(cron.trim(), timeZone))
            }
        }
        onSave(trimmedTitle, trimmedPrompt, create)
    }

    SheetBar(
        title = stringResource(if (task == null) R.string.schedule_create_title else R.string.schedule_edit_title),
        leading = stringResource(R.string.action_cancel) to onCancel,
    ) {
        if (saving) {
            Box(Modifier.padding(horizontal = 14.dp)) { SmallSpinner() }
        } else {
            Text(
                stringResource(R.string.common_save),
                style = AppType.body.copy(fontWeight = FontWeight.SemiBold),
                color = if (canSave) p.accent else p.tertiary,
                modifier = Modifier.padding(horizontal = 10.dp).plainClickable(enabled = canSave) { submit() },
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
            FormField(title, { title = it }, stringResource(R.string.schedule_name), singleLine = true)
            SettingsDivider()
            FormField(prompt, { prompt = it }, stringResource(R.string.schedule_prompt), singleLine = false)
        }
        if (task == null) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.schedule_type).uppercase(),
                    style = AppType.footnote,
                    color = p.secondary,
                    modifier = Modifier.padding(horizontal = AppSpacing.regular),
                )
                SettingsCard {
                    SettingsRow(title = stringResource(R.string.schedule_type)) {
                        MenuPicker(ScheduleKindOption.entries, kind, { stringResource(it.label) }) { kind = it }
                    }
                    if (kind == ScheduleKindOption.After || kind == ScheduleKindOption.Every) {
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
                    if (kind == ScheduleKindOption.Daily || kind == ScheduleKindOption.Weekly) {
                        SettingsDivider()
                        SettingsRow(title = stringResource(R.string.schedule_time)) {
                            Text(
                                time.toString(),
                                style = AppType.body,
                                color = p.foreground,
                                modifier = Modifier
                                    .background(p.secondaryFill, RoundedCornerShape(8.dp))
                                    .plainClickable {
                                        TimePickerDialog(
                                            context,
                                            { _, hour, minute -> time = LocalTime.of(hour, minute) },
                                            time.hour,
                                            time.minute,
                                            DateFormat.is24HourFormat(context),
                                        ).show()
                                    }
                                    .padding(horizontal = 11.dp, vertical = 6.dp),
                            )
                        }
                    }
                    if (kind == ScheduleKindOption.Weekly) {
                        SettingsDivider()
                        Column(
                            Modifier.padding(horizontal = AppSpacing.regular, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
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
                                        Text(
                                            weekdayLabel(weekday),
                                            style = AppType.caption.copy(fontWeight = FontWeight.SemiBold),
                                            color = if (selected) Color.White else p.foreground,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (kind == ScheduleKindOption.Cron) {
                        SettingsDivider()
                        FormField(cron, { cron = it }, stringResource(R.string.schedule_cron_expression), singleLine = true, plain = true)
                    }
                    SettingsDivider()
                    Text(
                        stringResource(R.string.schedule_timezone) + ": " + timeZone,
                        style = AppType.caption,
                        color = p.secondary,
                        modifier = Modifier.padding(horizontal = AppSpacing.regular, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

/** 1 = Sunday … 7 = Saturday, as the iOS editor numbers them, in the narrow local form. */
private fun weekdayLabel(weekday: Int): String {
    val day = DayOfWeek.SUNDAY.plus((weekday - 1).toLong())
    return day.getDisplayName(TextStyle.NARROW_STANDALONE, Locale.getDefault())
}

@Composable
private fun FormField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean,
    plain: Boolean = false,
) {
    val p = palette
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = AppType.body.copy(color = p.foreground),
        cursorBrush = SolidColor(p.accent),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        maxLines = if (singleLine) 1 else 6,
        keyboardOptions = if (plain) KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false) else KeyboardOptions.Default,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(horizontal = AppSpacing.regular, vertical = 11.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = AppType.body, color = p.tertiary)
                inner()
            }
        },
    )
}

/** iOS `Stepper`: a − / + capsule. */
@Composable
private fun Stepper(canDecrease: Boolean, canIncrease: Boolean, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    val p = palette
    Row(
        Modifier.background(p.secondaryFill, RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(46.dp, 32.dp).plainClickable(enabled = canDecrease, onClick = onDecrease), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Remove, null, tint = if (canDecrease) p.foreground else p.tertiary, modifier = Modifier.size(18.dp))
        }
        Box(Modifier.size(1.dp, 18.dp).background(p.hairline))
        Box(Modifier.size(46.dp, 32.dp).plainClickable(enabled = canIncrease, onClick = onIncrease), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Add, null, tint = if (canIncrease) p.foreground else p.tertiary, modifier = Modifier.size(18.dp))
        }
    }
}

/** iOS `ScheduleHistoryView`: when each run was delivered and what it said. */
@Composable
private fun ColumnScope.ScheduleHistoryPage(repository: BridgeRepository, sessionId: String, task: ScheduleTask, onDone: () -> Unit) {
    val p = palette
    val context = LocalContext.current
    var history by remember(task.id) { mutableStateOf<ScheduleHistory?>(null) }
    var loading by remember(task.id) { mutableStateOf(true) }
    var failure by remember(task.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(task.id) {
        var loaded: ScheduleHistory? = null
        val error = attempt { loaded = repository.loadScheduleHistory(sessionId, task.id) }
        history = loaded
        loading = false
        if (error != null) failure = context.errorMessage(error)
    }

    SheetBar(title = stringResource(R.string.schedule_history), leading = null) {
        Text(
            stringResource(R.string.common_done),
            style = AppType.body.copy(fontWeight = FontWeight.SemiBold),
            color = p.accent,
            modifier = Modifier.padding(horizontal = 10.dp).plainClickable(onClick = onDone),
        )
    }
    val records = history?.records.orEmpty()
    Box(Modifier.weight(1f)) {
        when {
            loading && history == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { SmallSpinner(size = 20.dp) }
            records.isEmpty() -> EmptyState(stringResource(R.string.schedule_history_empty), null)
            else -> Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = AppLayout.pageInset)
                    .padding(top = AppSpacing.small, bottom = AppSpacing.large),
            ) {
                SettingsCard {
                    records.forEachIndexed { index, record ->
                        if (index > 0) SettingsDivider()
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = AppSpacing.regular, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(record.deliveredAt, style = AppType.caption, color = p.secondary)
                            record.prompt?.takeIf { it.isNotEmpty() }?.let { Text(it, style = AppType.body, color = p.foreground) }
                        }
                    }
                }
            }
        }
    }

    failure?.let { message ->
        AlertDialog(
            onDismissRequest = { failure = null },
            title = { Text(stringResource(R.string.schedule_history)) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { failure = null }) { Text(stringResource(R.string.common_ok)) } },
        )
    }
}
