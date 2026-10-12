package dev.hailab.deeppilot.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hailab.deeppilot.R
import dev.hailab.deeppilot.bridge.BridgeRepository
import dev.hailab.deeppilot.core.protocol.MessageAttachment
import dev.hailab.deeppilot.core.protocol.MessageProjection
import dev.hailab.deeppilot.core.protocol.ToolInfo
import dev.hailab.deeppilot.core.session.Timeline
import dev.hailab.deeppilot.core.session.TimelineItem
import dev.hailab.deeppilot.ui.AppRadius
import dev.hailab.deeppilot.ui.AppSpacing
import dev.hailab.deeppilot.ui.AppType
import dev.hailab.deeppilot.ui.components.SmallSpinner
import dev.hailab.deeppilot.ui.components.plainClickable
import dev.hailab.deeppilot.ui.markdown.MarkdownMessage
import dev.hailab.deeppilot.ui.palette
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** One timeline row, as iOS `ConversationTimelineRows` renders it. */
@Composable
fun TimelineRow(item: TimelineItem, sessionId: String, repository: BridgeRepository, onHeightChanged: () -> Unit) {
    when (item) {
        is TimelineItem.Message -> MessageRow(item.message, sessionId, repository, onHeightChanged)
        is TimelineItem.Activity -> TimelineActivityRow(item.entries)
        is TimelineItem.SystemContext -> SystemContextRow(item.messages)
    }
}

@Composable
private fun MessageRow(message: MessageProjection, sessionId: String, repository: BridgeRepository, onHeightChanged: () -> Unit) {
    val p = palette
    when (message.role) {
        "user" -> TrailingBubble { UserBubble(Timeline.displayText(message), message.attachments, sessionId, repository) }
        "tool" -> ToolResultAttachmentRow(message, sessionId, repository)
        "error" -> Text(
            Timeline.displayText(message).orEmpty(),
            style = AppType.footnote,
            color = p.danger,
            modifier = Modifier.fillMaxWidth(),
        )
        else -> {
            val text = message.text.orEmpty()
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (Timeline.displayText(message) != null) {
                    MarkdownMessage(markdown = text, key = message.seq, onHeightChanged = onHeightChanged)
                }
                if (message.streaming) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        SmallSpinner(size = 11.dp)
                        Text(stringResource(R.string.conversation_generating), style = AppType.caption, color = p.secondary)
                    }
                } else {
                    AssistantActionRow(text)
                }
            }
        }
    }
}

/** Right-aligned with at least 52dp of leading space, like the iOS user row. */
@Composable
fun TrailingBubble(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        Spacer(Modifier.size(52.dp, 1.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { content() }
    }
}

/** A sent prompt: right-aligned on `secondarySystemFill`, radius 16. */
@Composable
fun UserBubble(
    text: String?,
    attachments: List<MessageAttachment>?,
    sessionId: String,
    repository: BridgeRepository,
    footer: (@Composable () -> Unit)? = null,
) {
    val p = palette
    Column(
        Modifier
            .then(if (attachments.isNullOrEmpty()) Modifier else Modifier.fillMaxWidth())
            .background(p.secondaryFill, RoundedCornerShape(AppRadius.large))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        attachments?.forEach { MessageAttachmentView(it, sessionId, repository) }
        if (text != null) {
            SelectionContainer { Text(text, style = AppType.body, color = p.foreground) }
        }
        footer?.invoke()
    }
}

/** A document chip, an image read back from the host, or (older bridges) just the photo's name. */
@Composable
private fun MessageAttachmentView(attachment: MessageAttachment, sessionId: String, repository: BridgeRepository) {
    when {
        attachment.kind == "document" -> DocumentAttachmentChip(attachment)
        !attachment.attachmentId.isNullOrEmpty() -> AttachmentThumb(attachment, sessionId, repository)
        else -> AttachmentLabel(attachment)
    }
}

@Composable
private fun AttachmentLabel(attachment: MessageAttachment) {
    val p = palette
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Outlined.Image, contentDescription = null, tint = p.secondary, modifier = Modifier.size(16.dp))
        Text(
            attachment.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.attachment_photo),
            style = AppType.footnote.copy(fontWeight = FontWeight.Medium),
            color = p.foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** iOS `DocumentAttachmentChip`: a document whose text went along with the message. */
@Composable
private fun DocumentAttachmentChip(attachment: MessageAttachment) {
    val p = palette
    val shape = RoundedCornerShape(AppRadius.medium)
    Row(
        Modifier
            .fillMaxWidth()
            .background(p.surface, shape)
            .border(0.7.dp, p.hairline, shape)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(32.dp).background(p.mutedSurface, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Description, null, tint = p.muted, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                attachment.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.attachment_document),
                style = AppType.footnote.copy(fontWeight = FontWeight.SemiBold),
                color = p.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(if (attachment.truncated == true) R.string.attachment_document_truncated else R.string.attachment_document_extracted),
                style = AppType.caption2,
                color = p.secondary,
            )
        }
    }
}

/** Copy and share under a finished answer (44×44 targets, secondary color). */
@Composable
private fun AssistantActionRow(text: String) {
    val p = palette
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1400)
            copied = false
        }
    }
    Row(Modifier.padding(start = 0.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            Modifier.size(44.dp).plainClickable {
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("DeepPilot", text))
                copied = true
            },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (copied) Icons.Filled.Check else Icons.Outlined.ContentCopy,
                contentDescription = stringResource(if (copied) R.string.a11y_copied else R.string.a11y_copy_answer),
                tint = p.secondary,
                modifier = Modifier.size(18.dp),
            )
        }
        val shareLabel = stringResource(R.string.a11y_share_answer)
        Box(
            Modifier.size(44.dp).plainClickable {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.IosShare, contentDescription = shareLabel, tint = p.secondary, modifier = Modifier.size(18.dp))
        }
    }
}

/** A tool result that carried images: a header, then the attachments and summary. */
@Composable
private fun ToolResultAttachmentRow(message: MessageProjection, sessionId: String, repository: BridgeRepository) {
    val p = palette
    val state = message.tool?.let(::visualState) ?: ActivityState.Done
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(6.dp).background(state.color(), CircleShape))
            Text(
                message.tool?.name ?: stringResource(R.string.attachment_photo),
                style = AppType.caption.copy(fontWeight = FontWeight.Medium),
                color = p.secondary,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(state.title(), style = AppType.caption2, color = state.color())
        }
        message.attachments?.forEach { MessageAttachmentView(it, sessionId, repository) }
        message.tool?.summary?.trim()?.takeIf { it.isNotEmpty() }?.let {
            Text(it, style = MonoSmall, color = p.secondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------- activity (thinking + tools) ----------

private enum class ActivityState { Running, Done, Error }

private fun visualState(tool: ToolInfo): ActivityState = when (tool.state.lowercase()) {
    "running", "pending" -> ActivityState.Running
    "error", "failed", "failure" -> ActivityState.Error
    else -> ActivityState.Done
}

@Composable
private fun ActivityState.color(): Color = when (this) {
    ActivityState.Running -> palette.foreground.copy(alpha = 0.55f)
    ActivityState.Done -> palette.muted
    ActivityState.Error -> palette.danger
}

@Composable
private fun ActivityState.title(): String = stringResource(
    when (this) {
        ActivityState.Running -> R.string.state_running
        ActivityState.Done -> R.string.state_done
        ActivityState.Error -> R.string.state_error
    },
)

private val MonoSmall = AppType.caption2.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)

/** Every run of thinking and tool calls between two answers, collapsed into one row. */
@Composable
private fun TimelineActivityRow(entries: List<MessageProjection>) {
    val p = palette
    var expanded by rememberSaveable(entries.first().seq) { mutableStateOf(false) }
    val turn by animateFloatAsState(if (expanded) 90f else 0f, label = "chevron")

    var aggregate: ActivityState? = null
    for (entry in entries) {
        val candidate: ActivityState = when {
            Timeline.isThinking(entry) -> if (entry.streaming) ActivityState.Running else ActivityState.Done
            entry.tool != null -> visualState(entry.tool!!)
            else -> continue
        }
        aggregate = when {
            candidate == ActivityState.Error -> ActivityState.Error
            candidate == ActivityState.Running && aggregate != ActivityState.Error -> ActivityState.Running
            aggregate == null -> candidate
            else -> aggregate
        }
    }
    val state = aggregate ?: ActivityState.Done
    val thinkingCount = entries.count(Timeline::isThinking)
    val toolCount = entries.size - thinkingCount
    val title = when {
        thinkingCount == 0 && toolCount == 1 -> stringResource(R.string.activity_title_single)
        thinkingCount == 0 -> stringResource(R.string.activity_title_tool, toolCount)
        toolCount == 0 && thinkingCount == 1 -> stringResource(R.string.thinking_single)
        toolCount == 0 -> stringResource(R.string.thinking_multiple, thinkingCount)
        else -> stringResource(R.string.activity_title_combined, thinkingCount, toolCount)
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .plainClickable { expanded = !expanded }
                .padding(horizontal = 4.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = p.tertiary,
                modifier = Modifier.size(12.dp).rotate(turn),
            )
            if (state == ActivityState.Running && !expanded) {
                ActivityOrb()
            } else {
                Icon(
                    Icons.Outlined.Psychology,
                    contentDescription = null,
                    tint = state.color().copy(alpha = 0.9f),
                    modifier = Modifier.size(18.dp).padding(1.dp),
                )
            }
            Text(
                title,
                style = AppType.footnote.copy(fontWeight = FontWeight.Medium),
                color = p.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(state.title(), style = AppType.caption2, color = state.color())
        }
        AnimatedVisibility(expanded, enter = fadeIn(), exit = fadeOut()) {
            Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                entries.forEachIndexed { index, entry ->
                    if (Timeline.isThinking(entry)) {
                        ThinkingDetail(entry.thinking.orEmpty(), entry.streaming)
                    } else {
                        entry.tool?.let { ToolActivityDetail(it) }
                    }
                    if (index < entries.lastIndex) {
                        Box(Modifier.padding(start = 18.dp).fillMaxWidth().height(0.5.dp).background(p.hairline))
                    }
                }
            }
        }
    }
}

/** The small spinning dot globe shown while a collapsed activity is still running. */
@Composable
private fun ActivityOrb() {
    val foreground = palette.foreground
    var phase by remember { mutableStateOf(0.0) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) {
            withFrameMillis { now -> phase = (now - start) / 1000.0 * 0.75 }
        }
    }
    Canvas(Modifier.size(18.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val unit = size.width / 18f
        for (row in 0 until 5) {
            val latitude = -1.05 + row * 0.525
            val ringRadius = cos(latitude) * 6
            val y = center.y + (sin(latitude) * 6).toFloat() * unit
            for (column in 0 until 7) {
                val angle = column * (2 * PI / 7) + row * 0.27 + phase
                val depth = ((sin(angle) * cos(latitude) + 1) / 2).toFloat()
                val diameter = (0.65f + depth * 0.8f) * unit
                val x = center.x + (cos(angle) * ringRadius).toFloat() * unit
                drawCircle(foreground.copy(alpha = 0.12f + depth * 0.52f), radius = diameter / 2, center = Offset(x, y))
            }
        }
    }
}

@Composable
private fun ThinkingDetail(text: String, streaming: Boolean) {
    val p = palette
    var expanded by remember { mutableStateOf(false) }
    val turn by animateFloatAsState(if (expanded) 90f else 0f, label = "chevron")
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .plainClickable { expanded = !expanded }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = p.tertiary,
                modifier = Modifier.size(13.dp).rotate(turn),
            )
            Text(
                stringResource(if (streaming) R.string.thinking_active else R.string.thinking_done),
                style = AppType.footnote.copy(fontWeight = FontWeight.Medium),
                color = p.secondary,
                modifier = Modifier.weight(1f),
            )
            if (streaming) SmallSpinner(size = 11.dp)
        }
        AnimatedVisibility(expanded, enter = fadeIn(), exit = fadeOut()) {
            SelectionContainer {
                Text(
                    text,
                    style = AppType.footnote.copy(lineHeight = 21.sp),
                    color = p.secondary,
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ToolActivityDetail(tool: ToolInfo) {
    val p = palette
    val state = visualState(tool)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.padding(top = 5.dp).size(6.dp).background(state.color(), CircleShape))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    toolDisplayName(tool.name),
                    style = AppType.caption.copy(fontWeight = FontWeight.Medium),
                    color = p.foreground,
                    modifier = Modifier.weight(1f),
                )
                Text(state.title(), style = AppType.caption2, color = state.color())
            }
            toolSummary(tool.summary)?.let {
                SelectionContainer {
                    Text(it, style = MonoSmall, color = p.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun toolDisplayName(name: String): String = when (name.lowercase()) {
    "ask_user_question", "ask_user" -> stringResource(R.string.tool_ask_user)
    "bash", "shell", "exec", "exec_command" -> stringResource(R.string.tool_run_command)
    "result", "tool_result" -> stringResource(R.string.tool_result)
    "read", "read_file" -> stringResource(R.string.tool_read_file)
    "write", "write_file", "apply_patch" -> stringResource(R.string.tool_write_file)
    "web", "web_search", "search" -> stringResource(R.string.tool_search)
    "browser", "browser_action" -> stringResource(R.string.tool_browse)
    else -> name.replace('_', ' ')
}

private fun toolSummary(raw: String): String? {
    var summary = raw.trim()
    if (summary.isEmpty() || summary == "失败") return null
    summary = summary.removePrefix("command=")
    val description = summary.indexOf(" description=")
    if (description >= 0) summary = summary.substring(0, description)
    return summary
}

// ---------- system context ----------

@Composable
private fun SystemContextRow(messages: List<MessageProjection>) {
    val p = palette
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(p.mutedSurface, RoundedCornerShape(AppRadius.medium))
            .padding(horizontal = AppSpacing.compact, vertical = AppSpacing.small + 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().plainClickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Outlined.SettingsSuggest, null, tint = p.muted, modifier = Modifier.size(13.dp))
            Text(
                stringResource(R.string.system_context_group, messages.size),
                style = AppType.footnote.copy(fontWeight = FontWeight.Medium),
                color = p.muted,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                null,
                tint = p.muted.copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp),
            )
        }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                messages.forEach { message ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        message.context?.label?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = AppType.caption.copy(fontWeight = FontWeight.SemiBold), color = p.muted)
                        }
                        Timeline.displayText(message)?.let {
                            Text(it, style = AppType.caption, color = p.secondary, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** The pulsing placeholder shown before the first tail arrives (iOS `ConversationSkeleton`). */
@Composable
fun ConversationSkeleton() {
    val p = palette
    val fractions = listOf(0.58f, 0.72f, 0.82f, 0.64f, 0.46f)
    var pulse by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(900)
            pulse = !pulse
        }
    }
    val alpha by animateFloatAsState(if (pulse) 0.05f else 0.10f, label = "pulse")
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        fractions.forEachIndexed { index, fraction ->
            Row(Modifier.fillMaxWidth()) {
                if (index % 2 == 1) Spacer(Modifier.weight(1f - fraction))
                Box(
                    Modifier
                        .weight(fraction)
                        .height(if (index == 2) 96.dp else 44.dp)
                        .background(p.foreground.copy(alpha = alpha), RoundedCornerShape(AppRadius.large)),
                )
                if (index % 2 == 0) Spacer(Modifier.weight(1f - fraction))
            }
        }
    }
}
