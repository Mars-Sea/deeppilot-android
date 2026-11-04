package dev.hailab.deeppilot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hailab.deeppilot.R
import dev.hailab.deeppilot.core.protocol.SkillSummary
import dev.hailab.deeppilot.core.protocol.HostCommand
import dev.hailab.deeppilot.core.protocol.CommandCatalog
import dev.hailab.deeppilot.core.skills.SkillQuery
import dev.hailab.deeppilot.ui.AppType
import dev.hailab.deeppilot.ui.palette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlin.coroutines.coroutineContext

/** 输入器局部状态：查询字符只筛选目录，开菜单、重连与目录失效才重新读取。 */
@Composable
fun SkillSuggestions(
    query: SkillQuery?, catalogScope: String, load: suspend () -> List<SkillSummary>,
    onPick: (SkillSummary) -> Unit, onDismiss: () -> Unit,
    loadCommands: (suspend () -> CommandCatalog)? = null,
    onPickCommand: (HostCommand) -> Unit = {},
    read: (suspend (Boolean, (List<SkillSummary>, Boolean) -> Unit) -> List<SkillSummary>)? = null,
    readCommands: (suspend (Boolean, (CommandCatalog, Boolean) -> Unit) -> CommandCatalog)? = null,
) {
    var skills by remember(catalogScope) { mutableStateOf<List<SkillSummary>>(emptyList()) }
    var commands by remember(catalogScope) { mutableStateOf<List<HostCommand>>(emptyList()) }
    var loading by remember(catalogScope) { mutableStateOf(true) }
    var failed by remember(catalogScope) { mutableStateOf(false) }
    var staleSkills by remember(catalogScope) { mutableStateOf(false) }
    var staleCommands by remember(catalogScope) { mutableStateOf(false) }
    var consumedRetry by remember { mutableIntStateOf(0) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(catalogScope, query?.start, retry) {
        skills = emptyList()
        commands = emptyList()
        failed = false
        loading = true
        val force = retry != consumedRetry
        consumedRetry = retry
        if (query != null) coroutineScope {
            // 两种目录互不阻塞，失败也互不吞掉另一种结果。
            val skillsJob = launch {
                try {
                    val result = read?.invoke(force) { value, stale -> skills = value; staleSkills = stale } ?: load()
                    coroutineContext.ensureActive(); skills = result; staleSkills = false
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failed = true }
            }
            val commandsJob = launch {
                try {
                    val catalog = readCommands?.invoke(force) { value, stale -> commands = value.commands; staleCommands = stale } ?: loadCommands?.invoke()
                    coroutineContext.ensureActive(); commands = catalog?.commands.orEmpty(); staleCommands = false
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failed = true }
            }
            try { skillsJob.join(); commandsJob.join() } finally { loading = false }
        }
    }
    val listState = rememberLazyListState()
    // 改变查询或目录后展示最相关的首项，避免沿用滚动到底部的位置。
    LaunchedEffect(query?.query, skills, commands) { listState.scrollToItem(0) }
    if (query == null) return
    val p = palette
    val rows = (if (query.start == 0) commands.map { SlashRow(it.name, commandDescription(it), command = it) } else emptyList()) + skills.map { SlashRow(it.name, it.description, it.modelInvocable) }
    val matches = rows.filter { query.matches(listOf(SkillSummary(it.name, "", true))).isNotEmpty() }
        .sortedBy { !it.name.startsWith(query.query, ignoreCase = true) }
    // 两行语义字号加内边距；大字体可少显示几条，描述长度不再改变行高。
    val textHeight = with(LocalDensity.current) { AppType.body.lineHeight.toDp() + AppType.caption.lineHeight.toDp() }
    val rowHeight = maxOf(48.dp, textHeight + 16.dp)
    Column(Modifier.fillMaxWidth().background(p.surface, RoundedCornerShape(16.dp)).padding(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(if (loadCommands == null) R.string.composer_skills_title else R.string.composer_commands_title), style = AppType.body, color = p.foreground, modifier = Modifier.padding(8.dp))
            TextButton(onClick = { retry++ }) { Text(stringResource(R.string.catalog_refresh), style = AppType.body) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.composer_skills_close), style = AppType.body) }
        }
        if (staleSkills || staleCommands) Text(stringResource(if (loading) R.string.catalog_updating else R.string.catalog_previous), style = AppType.caption, color = p.secondary, modifier = Modifier.padding(8.dp))
        when {
            loading && matches.isEmpty() -> Text(stringResource(if (loadCommands == null) R.string.composer_skills_loading else R.string.composer_commands_loading), style = AppType.body, color = p.foreground, modifier = Modifier.padding(8.dp))
            failed && matches.isEmpty() -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.composer_skills_failed), style = AppType.body, color = p.foreground, modifier = Modifier.weight(1f).padding(8.dp))
                TextButton(onClick = { retry++ }) { Text(stringResource(R.string.composer_skills_retry), style = AppType.body) }
            }
            matches.isEmpty() -> Text(stringResource(if (loadCommands != null) (if (rows.isEmpty()) R.string.composer_commands_empty else R.string.composer_commands_noMatch) else (if (skills.isEmpty()) R.string.composer_skills_empty else R.string.composer_skills_no_match)), style = AppType.body, color = p.foreground, modifier = Modifier.padding(8.dp))
            else -> LazyColumn(Modifier.fillMaxWidth().height(minOf(rowHeight * minOf(matches.size, 3), 220.dp)), state = listState) {
                items(matches, key = { it.id }) { skill ->
                    val description = listOfNotNull(
                        if (!skill.modelInvocable) stringResource(R.string.composer_skills_user_only) else null,
                        skill.description.takeIf { it.isNotBlank() },
                    ).joinToString("：").lineSequence().joinToString(" ") { it.trim() }
                    Column(Modifier.fillMaxWidth().clickable { skill.command?.let(onPickCommand) ?: onPick(SkillSummary(skill.name, skill.description, skill.modelInvocable)) }.height(rowHeight).padding(8.dp)) {
                        Text("/" + skill.name, style = AppType.body, color = p.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        // 省略只影响显示，Text 的无障碍语义仍包含完整描述。
                        if (description.isNotEmpty()) Text(description, style = AppType.caption, color = p.secondary, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (failed && matches.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.composer_commands_catalogFailed), style = AppType.caption, color = p.secondary, modifier = Modifier.weight(1f).padding(8.dp))
            TextButton(onClick = { retry++ }) { Text(stringResource(R.string.composer_skills_retry)) }
        }
    }
}

private data class SlashRow(val name: String, val description: String, val modelInvocable: Boolean = true, val command: HostCommand? = null) {
    val id get() = (if (command == null) "skill:" else "command:") + name
}
@Composable
internal fun commandDescription(command: HostCommand): String {
    val resource = when (command.definitionId) {
        "@deepseek-ai/dsh-command-compact" -> R.string.composer_commands_description_compact
        "@deepseek-ai/dsh-command-goal" -> R.string.composer_commands_description_goal
        "@deepseek-ai/dsh-plan-mode" -> R.string.composer_commands_description_plan
        "@deepseek-ai/dsh-command-feedback" -> R.string.composer_commands_description_feedback
        "@deepseek-ai/dsh-permission-presets" -> R.string.composer_commands_description_permission
        "@deepseek-ai/dsh-session-log-export" -> R.string.composer_commands_description_export
        "client:model" -> R.string.composer_commands_description_model
        "client:file" -> R.string.composer_commands_description_file
        else -> null
    }
    return stringResource(R.string.composer_commands_row, resource?.let { stringResource(it) } ?: command.description)
}
