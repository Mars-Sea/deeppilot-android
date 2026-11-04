package dev.hailab.deeppilot.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import dev.hailab.deeppilot.core.protocol.*
import dev.hailab.deeppilot.ui.screens.CommandPermissionDialog
import dev.hailab.deeppilot.ui.screens.CommandFeedbackDialog
import dev.hailab.deeppilot.ui.screens.PromptComposer

/** 仅调试构建包含的离线验收入口，目录为合成数据，使用产品的真实输入器。 */
class SkillCompletionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DeepPilotTheme {
                val cacheMode = intent.getBooleanExtra("cache", false)
                val uiScope = rememberCoroutineScope()
                val catalogCache = remember { dev.hailab.deeppilot.core.catalog.CatalogCache(java.io.File(cacheDir, "CatalogUITest-v1"), uiScope) }
                var reads by remember { mutableIntStateOf(0) }
                val generatedSkills = remember { listOf(SkillSummary("review", "检查代码中的逻辑与回归问题，给出具体改进建议。\n这是多段长描述，用于确认候选只显示一行而不挤占聊天区域，完整说明仍可由辅助功能读取。", true),
                                SkillSummary("release", "准备发布检查", false),
                                SkillSummary("office-xlsx", "处理电子表格", true),
                                SkillSummary("notes", "整理笔记", true),
                                SkillSummary("translate", "翻译文档", true),
                                SkillSummary("workspace", "整理工作目录", true)) }
                LaunchedEffect(Unit) { if (intent.getBooleanExtra("cacheReset", false)) catalogCache.remove("ui-test") }
                val commandMode = intent.getBooleanExtra("commands", false)
                val skillsFail = intent.getBooleanExtra("skillsFail", false)
                var permission by remember { mutableStateOf(false) }
                var feedback by remember { mutableStateOf(false) }
                var applied by remember { mutableIntStateOf(0) }
                var saved by remember { mutableIntStateOf(0) }
                var draft by remember { mutableStateOf("") }
                var actions by remember { mutableIntStateOf(0) }
                val focus = remember { FocusRequester() }
                Column(Modifier.fillMaxSize().background(palette.background).systemBarsPadding().imePadding().padding(12.dp)) {
                    Text(actions.toString(), color = palette.foreground)
                    if (cacheMode) Text("目录读取：$reads", color = palette.foreground)
                    if (commandMode) Text("权限执行：$applied，反馈保存：$saved", color = palette.foreground)
                    Spacer(Modifier.weight(1f))
                    PromptComposer(
                        draft = draft, onDraftChange = { draft = it },
                        attachments = emptyList(), onRemoveAttachment = { actions++ }, onAddAttachment = { actions++ },
                        todoItems = emptyList(), models = null, modelsLoading = false, modelsError = null,
                        onOpenModels = { actions++ }, onSelectEffort = { actions++ }, changingEffort = false,
                        canSubmit = true, sending = false, running = false, stopping = false,
                        onSend = { actions++ }, onStop = { actions++ }, onOpenTodos = { actions++ },
                        focusRequester = focus, supportsSkills = true, skillCatalogScope = "offline-fixture",
                        onLoadSkills = {
                            if (skillsFail) throw IllegalStateException("synthetic unavailable")
                            generatedSkills
                        },
                        onReadSkills = if (!cacheMode) null else ({ force, cached ->
                            catalogCache.load(dev.hailab.deeppilot.core.catalog.CatalogCache.Key("ui-test", "synthetic-host", "s", "skills"), "fixture", kotlinx.serialization.builtins.ListSerializer(SkillSummary.serializer()), force, cached) {
                                reads++
                                if (intent.getBooleanExtra("cacheSlow", false)) kotlinx.coroutines.delay(15000)
                                generatedSkills
                            }
                        }),
                        onLoadCommands = if (!commandMode) null else ({
                            CommandCatalog("fixture", listOf(
                                HostCommand("compact", "", "@deepseek-ai/dsh-command-compact"),
                                HostCommand("permission", "", "@deepseek-ai/dsh-permission-presets"),
                                HostCommand("feedback", "", "@deepseek-ai/dsh-command-feedback"),
                                HostCommand("model", "", "client:model"),
                                HostCommand("file", "", "client:file"),
                            ), permissions = true)
                        }),
                        onSelectCommand = { command -> when (command.action) {
                            "permission" -> permission = true
                            "feedback" -> feedback = true
                            else -> actions++
                        } },
                    )
                }
                if (permission) CommandPermissionDialog(
                    load = { PermissionCatalog("fixture", listOf(
                        PermissionOption("workspace-write", "Workspace", confirmation = false),
                        PermissionOption("danger-full-access", "Full", confirmation = true),
                    ), "workspace-write") },
                    select = { applied++ }, onDismiss = { permission = false },
                )
                if (feedback) CommandFeedbackDialog(submit = { _, _ -> saved++ }, onDismiss = { feedback = false })
            }
        }
    }
}
