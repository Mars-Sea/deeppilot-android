package dev.hailab.deeppilot.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.hailab.deeppilot.R
import dev.hailab.deeppilot.core.protocol.PermissionCatalog
import dev.hailab.deeppilot.core.protocol.PermissionOption
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive

@Composable
fun CommandPermissionDialog(load: suspend () -> PermissionCatalog, select: suspend (String) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var catalog by remember { mutableStateOf<PermissionCatalog?>(null) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var confirmation by remember { mutableStateOf<PermissionOption?>(null) }
    LaunchedEffect(retry) {
        failed = false
        try { val value = load(); kotlinx.coroutines.currentCoroutineContext().ensureActive(); catalog = value }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    fun apply(option: PermissionOption) { busy = true; scope.launch {
        try { select(option.value); onDismiss() } catch (_: Exception) { failed = true }
        finally { busy = false }
    } }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(stringResource(R.string.composer_commands_permission)) },
        text = { Column {
            if (catalog == null && !failed) CircularProgressIndicator()
            LazyColumn(Modifier.heightIn(max = 360.dp)) { items(catalog?.options.orEmpty(), key = { it.value }) { option ->
                TextButton(enabled = !busy, onClick = { if (option.confirmation) confirmation = option else apply(option) }) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(permissionName(option) + if (catalog?.currentValue == option.value) " (" + stringResource(R.string.composer_commands_current) + ")" else "")
                        permissionDescription(option)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            } }
            if (failed) { Text(stringResource(R.string.composer_commands_failed)); TextButton(onClick = { retry++ }) { Text(stringResource(R.string.composer_skills_retry)) } }
        } }, confirmButton = {}, dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.composer_skills_close)) } })
    confirmation?.let { option -> AlertDialog(onDismissRequest = { confirmation = null },
        title = { Text(stringResource(R.string.composer_commands_permission_confirm)) },
        text = { Text(stringResource(R.string.composer_commands_permission_risk)) },
        confirmButton = { TextButton(onClick = { confirmation = null; apply(option) }) { Text(stringResource(R.string.composer_commands_permission_enable)) } },
        dismissButton = { TextButton(onClick = { confirmation = null }) { Text(stringResource(R.string.action_cancel)) } }) }
}

@Composable
fun CommandFeedbackDialog(submit: suspend (String, String) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("other") }
    var expanded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val categories = listOf("task-result" to R.string.composer_commands_category_task_result,
        "instruction-following" to R.string.composer_commands_category_instruction_following,
        "product-interaction" to R.string.composer_commands_category_product_interaction,
        "service-stability" to R.string.composer_commands_category_service_stability,
        "resource-cost" to R.string.composer_commands_category_resource_cost,
        "security-privacy-permission" to R.string.composer_commands_category_security_privacy_permission,
        "other" to R.string.composer_commands_category_other)
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(stringResource(R.string.composer_commands_feedback)) },
        text = { Column {
            Box { TextButton(onClick = { expanded = true }) { Text(stringResource(categories.first { it.first == category }.second)) }
                DropdownMenu(expanded, onDismissRequest = { expanded = false }) { categories.forEach { (id, label) ->
                    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { category = id; expanded = false })
                } }
            }
            OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 130.dp))
            Text(stringResource(R.string.composer_commands_feedback_privacy), style = MaterialTheme.typography.bodySmall)
            if (failed) Text(stringResource(R.string.composer_commands_failed))
        } }, confirmButton = { TextButton(enabled = !busy && text.length <= 64 * 1024, onClick = {
            busy = true; scope.launch { try { submit(text, category); onDismiss() } catch (_: Exception) { failed = true }; busy = false }
        }) { Text(stringResource(R.string.composer_commands_feedback_submit)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } })
}

/** iOS `CommandExportSheet`: full ZIP log or the conversation text as Markdown. */
@Composable
fun CommandExportDialog(export: suspend (dev.hailab.deeppilot.core.protocol.SessionExportFormat) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var format by remember { mutableStateOf(dev.hailab.deeppilot.core.protocol.SessionExportFormat.Zip) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val options = listOf(
        dev.hailab.deeppilot.core.protocol.SessionExportFormat.Zip to (R.string.composer_commands_export_zip to R.string.composer_commands_export_zip_detail),
        dev.hailab.deeppilot.core.protocol.SessionExportFormat.Markdown to (R.string.composer_commands_export_markdown to R.string.composer_commands_export_markdown_detail),
    )
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(stringResource(R.string.composer_commands_export_title)) },
        text = { Column {
            Text(stringResource(R.string.composer_commands_export_format), style = MaterialTheme.typography.labelMedium)
            options.forEach { (value, labels) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(selected = format == value, onClick = { format = value }, enabled = !busy)
                    Text(stringResource(labels.first))
                }
            }
            Text(stringResource(options.first { it.first == format }.second.second), style = MaterialTheme.typography.bodySmall)
            if (busy) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.composer_commands_exporting), style = MaterialTheme.typography.bodySmall)
            }
            if (failed) Text(stringResource(R.string.composer_commands_failed))
        } }, confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true; failed = false
            scope.launch {
                try { export(format); onDismiss() }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { failed = true }
                busy = false
            }
        }) { Text(stringResource(R.string.composer_commands_export_share)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } })
}

@Composable
private fun permissionName(option: PermissionOption): String = when (option.value) {
    "workspace-write" -> stringResource(R.string.composer_commands_permission_workspace_write)
    "danger-full-access" -> stringResource(R.string.composer_commands_permission_danger_full_access)
    "auto" -> stringResource(R.string.composer_commands_permission_auto)
    "custom" -> stringResource(R.string.composer_commands_permission_custom)
    else -> option.name
}
@Composable
private fun permissionDescription(option: PermissionOption): String? = when (option.value) {
    "workspace-write" -> stringResource(R.string.composer_commands_permission_workspace_write_detail)
    "danger-full-access" -> stringResource(R.string.composer_commands_permission_danger_full_access_detail)
    "auto" -> stringResource(R.string.composer_commands_permission_auto_detail)
    else -> option.description
}

internal fun commandResultMessage(context: android.content.Context, result: dev.hailab.deeppilot.core.protocol.CommandResult): String {
    val text = result.text
    val key = when (text) {
        "No compactable history yet." -> R.string.composer_commands_nothingToCompact
        "Compaction is unavailable because this process has an active compaction, or the agent is not idle." -> R.string.composer_commands_compact_busy
        "Compaction cancelled." -> R.string.composer_commands_compact_cancelled
        "The history selected for compaction changed before it could be replaced. The attempt is recorded in the session log." -> R.string.composer_commands_compact_changed
        "Compaction could not produce a useful summary. The attempt is recorded in the session log." -> R.string.composer_commands_compact_summary
        "Compaction did not finish cleanly; some session history may have changed. Inspect the current session state before retrying." -> R.string.composer_commands_compact_commit
        "Compaction finished, but the session could not be saved." -> R.string.composer_commands_compact_persistence
        "Usage: /compact (no arguments)" -> R.string.composer_commands_compact_usage
        else -> null
    }
    if (key != null) return context.getString(key)
    val counts = text?.let { Regex("^Compacted ([0-9]+) history items \\(~([0-9]+) tokens\\)\\.$").matchEntire(it) }?.groupValues
    if (counts != null) return context.getString(R.string.composer_commands_compact_completed, counts[1], counts[2])
    if (result.kind != "success") return context.getString(R.string.composer_commands_rejected)
    return text?.let { context.getString(R.string.composer_commands_successDetail, it) } ?: context.getString(R.string.composer_commands_success)
}
