package dev.hailab.deeppilot.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import dev.hailab.deeppilot.core.attachments.PromptAttachment
import dev.hailab.deeppilot.core.protocol.ModelReasoning
import dev.hailab.deeppilot.core.protocol.ModelReasoningEffort
import dev.hailab.deeppilot.core.protocol.SessionModels
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hailab.deeppilot.R
import dev.hailab.deeppilot.core.protocol.TodoItem
import dev.hailab.deeppilot.ui.AppRadius
import dev.hailab.deeppilot.ui.AppSpacing
import dev.hailab.deeppilot.ui.AppType
import dev.hailab.deeppilot.ui.components.SmallSpinner
import dev.hailab.deeppilot.ui.components.plainClickable
import dev.hailab.deeppilot.ui.palette

/**
 * iOS `PromptComposer`: a floating rounded box. Idle it is one 52dp row with
 * the attach and send buttons at the end; once focused or holding a draft the
 * field takes the whole width and a footer row below carries the model and
 * thinking selectors, the attach button and the send disc.
 */
@Composable
fun PromptComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    attachments: List<PromptAttachment>,
    onRemoveAttachment: (PromptAttachment) -> Unit,
    onAddAttachment: () -> Unit,
    todoItems: List<TodoItem>,
    models: SessionModels?,
    modelsLoading: Boolean,
    modelsError: String?,
    onOpenModels: () -> Unit,
    onSelectEffort: (ModelReasoningEffort) -> Unit,
    changingEffort: Boolean,
    canSubmit: Boolean,
    sending: Boolean,
    running: Boolean,
    stopping: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onOpenTodos: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val p = palette
    var focused by remember { mutableStateOf(false) }
    val expanded = focused || draft.isNotEmpty() || attachments.isNotEmpty()
    val shape = RoundedCornerShape(AppRadius.large)
    val working = sending || running || stopping

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (attachments.isNotEmpty()) AttachmentChips(attachments, onRemoveAttachment)

        Column(
            Modifier
                .fillMaxWidth()
                .background(if (p.dark) p.surface else p.background, shape)
                .border(0.5.dp, p.hairline, shape),
        ) {
            if (todoItems.isNotEmpty()) {
                TodoComposerStatus(todoItems, onOpenTodos, Modifier.padding(horizontal = AppSpacing.regular))
                Box(Modifier.padding(horizontal = AppSpacing.regular).fillMaxWidth().height(0.5.dp).background(p.hairline))
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (expanded) 0.dp else 52.dp)
                    .padding(horizontal = AppSpacing.regular, vertical = AppSpacing.small),
                verticalArrangement = Arrangement.spacedBy(if (expanded) 8.dp else 0.dp),
            ) {
                val send = @Composable {
                    SendButton(
                        canSend = (draft.isNotBlank() || attachments.isNotEmpty()) && canSubmit,
                        sending = sending,
                        running = running,
                        stopping = stopping,
                        onSend = onSend,
                        onStop = onStop,
                    )
                }
                val attach = @Composable { AttachButton(onAddAttachment) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BasicTextField(
                        value = draft,
                        onValueChange = onDraftChange,
                        textStyle = AppType.body.copy(color = p.foreground),
                        cursorBrush = SolidColor(p.accent),
                        maxLines = 8,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = if (expanded) 4.dp else 0.dp)
                            .focusRequester(focusRequester)
                            .onFocusChanged { focused = it.isFocused },
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (draft.isEmpty()) {
                                    Text(stringResource(R.string.composer_placeholder), style = AppType.body, color = p.tertiary)
                                }
                                inner()
                            }
                        },
                    )
                    if (!expanded) {
                        attach()
                        send()
                    }
                }
                if (expanded) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ModelButton(models, modelsLoading, modelsError, onOpenModels)
                        val reasoning = models?.model()?.reasoning
                        if (models != null && reasoning != null && reasoning.efforts.isNotEmpty()) {
                            EffortButton(models, reasoning, changingEffort, enabled = !changingEffort && !working, onSelectEffort)
                        }
                        Spacer(Modifier.weight(1f).widthIn(min = 4.dp))
                        attach()
                        send()
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachButton(onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).plainClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Add, stringResource(R.string.attachment_add), tint = palette.foreground, modifier = Modifier.size(24.dp))
    }
}

/** Borderless model selector, fixed width so the controls after it never move. */
@Composable
private fun ModelButton(models: SessionModels?, loading: Boolean, error: String?, onClick: () -> Unit) {
    val p = palette
    val title = models?.displayName() ?: stringResource(R.string.model_fallback_title)
    val color = if (error != null && models == null) p.danger else p.foreground
    val label = stringResource(R.string.model_current_a11y, title)
    Row(
        Modifier
            .width(ModelButtonWidth)
            .height(32.dp)
            .semantics { contentDescription = label }
            .plainClickable(onClick = onClick)
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ModelButtonIcon(models, loading, color)
        Text(
            title,
            style = AppType.subheadline.copy(fontWeight = FontWeight.Medium),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Icon(Icons.Filled.KeyboardArrowDown, null, tint = p.tertiary, modifier = Modifier.size(14.dp))
    }
}

/** Thinking intensity menu for the current model (iOS `reasoningEffortButton`). */
@Composable
private fun EffortButton(
    models: SessionModels,
    reasoning: ModelReasoning,
    changing: Boolean,
    enabled: Boolean,
    onSelect: (ModelReasoningEffort) -> Unit,
) {
    val p = palette
    var open by remember { mutableStateOf(false) }
    val activeId = models.current.reasoningEffort ?: reasoning.defaultEffort
    val activeName = reasoning.efforts.firstOrNull { it.id == activeId }?.name ?: activeId ?: stringResource(R.string.thinking_default)
    val label = stringResource(R.string.thinking_current_a11y, activeName)
    Box {
        Row(
            Modifier
                .heightIn(min = 32.dp)
                .semantics { contentDescription = label }
                .plainClickable(enabled = enabled) { open = true }
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (changing) SmallSpinner(size = 12.dp)
            Text(
                activeName,
                style = AppType.footnote.copy(fontWeight = FontWeight.Medium),
                color = if (enabled || changing) p.foreground else p.tertiary,
                maxLines = 1,
            )
            Icon(Icons.Filled.KeyboardArrowDown, null, tint = p.tertiary, modifier = Modifier.size(14.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = p.surface) {
            reasoning.efforts.forEach { effort ->
                DropdownMenuItem(
                    text = { Text(effort.name, style = AppType.body, color = p.foreground) },
                    leadingIcon = {
                        Box(Modifier.size(18.dp)) {
                            if (effort.id == activeId) Icon(Icons.Filled.Check, null, tint = p.foreground, modifier = Modifier.size(18.dp))
                        }
                    },
                    onClick = {
                        open = false
                        onSelect(effort)
                    },
                )
            }
        }
    }
}

/** Pending attachments above the box: capsules with an icon, the name and a remove button. */
@Composable
private fun AttachmentChips(attachments: List<PromptAttachment>, onRemove: (PromptAttachment) -> Unit) {
    val p = palette
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(attachments, key = { it.id }) { attachment ->
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(p.surface)
                    .border(1.dp, p.hairline, CircleShape)
                    .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(
                    if (attachment.kind == PromptAttachment.Kind.Image) Icons.Outlined.Image else Icons.Outlined.Description,
                    null,
                    tint = p.foreground,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    attachment.name,
                    style = AppType.caption,
                    color = p.foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 180.dp),
                )
                Icon(
                    Icons.Filled.Cancel,
                    stringResource(R.string.attachment_remove_a11y, attachment.name),
                    tint = p.secondary,
                    modifier = Modifier.size(16.dp).plainClickable { onRemove(attachment) },
                )
            }
        }
    }
}

/** iOS `AttachmentPickerSheet`: camera, photos and files tiles with the limits hint. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentPickerSheet(onCamera: () -> Unit, onPhotos: () -> Unit, onFiles: () -> Unit, onDismiss: () -> Unit) {
    val p = palette
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = p.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.attachment_add), style = AppType.headline, color = p.foreground)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AttachmentTile(stringResource(R.string.attachment_camera), Icons.Outlined.PhotoCamera, onCamera, Modifier.weight(1f))
                AttachmentTile(stringResource(R.string.attachment_photos), Icons.Outlined.PhotoLibrary, onPhotos, Modifier.weight(1f))
                AttachmentTile(stringResource(R.string.attachment_files), Icons.Outlined.Folder, onFiles, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Info, null, tint = p.secondary, modifier = Modifier.padding(top = 1.dp).size(15.dp))
                Text(stringResource(R.string.attachments_hint), style = AppType.footnote, color = p.secondary)
            }
        }
    }
}

@Composable
private fun AttachmentTile(title: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = palette
    Column(
        modifier
            .heightIn(min = 82.dp)
            .clip(RoundedCornerShape(AppRadius.large))
            .background(p.surface)
            .plainClickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, null, tint = p.foreground, modifier = Modifier.size(26.dp))
        Text(
            title,
            style = AppType.footnote.copy(fontWeight = FontWeight.Medium),
            color = p.foreground,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SendButton(
    canSend: Boolean,
    sending: Boolean,
    running: Boolean,
    stopping: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val p = palette
    val canStop = running && !sending
    val progress = sending || stopping
    val fill = when {
        canStop || progress -> p.danger
        canSend -> p.prominentFill
        else -> p.foreground.copy(alpha = 0.05f)
    }
    val label = stringResource(
        when {
            sending -> R.string.conversation_sending
            stopping -> R.string.a11y_stopping
            canStop -> R.string.a11y_stop
            else -> R.string.a11y_send
        },
    )
    Box(
        Modifier
            .size(44.dp)
            .background(fill, CircleShape)
            .semantics { contentDescription = label }
            .plainClickable(enabled = (canStop && !stopping) || (canSend && !progress)) { if (canStop) onStop() else onSend() },
        contentAlignment = Alignment.Center,
    ) {
        when {
            progress -> SmallSpinner(color = Color.White, size = 18.dp)
            canStop -> Icon(Icons.Filled.Stop, null, tint = Color.White, modifier = Modifier.size(18.dp))
            else -> Icon(
                Icons.Filled.ArrowUpward,
                null,
                tint = if (canSend) Color.White else p.muted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** The task line inside the composer: a progress ring, the current task and done/total. */
@Composable
private fun TodoComposerStatus(items: List<TodoItem>, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val p = palette
    val done = items.count { it.status == "completed" }
    val allDone = done == items.size
    val current = items.firstOrNull { it.status == "in_progress" } ?: items.firstOrNull { it.status == "pending" }
    val title = when {
        allDone -> stringResource(R.string.todo_all_done)
        current != null -> current.content
        else -> stringResource(R.string.todo_title)
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .plainClickable(onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
    ) {
        ProgressRing(done.toFloat() / items.size.coerceAtLeast(1), allDone)
        Text(
            title,
            style = AppType.caption,
            color = p.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text("$done/${items.size}", style = AppType.caption, color = p.secondary)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = p.tertiary, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun ProgressRing(fraction: Float, allDone: Boolean) {
    val p = palette
    Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(16.dp)) {
            val stroke = 2.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(p.foreground.copy(alpha = 0.12f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(
                p.foreground.copy(alpha = 0.55f),
                -90f,
                360f * fraction,
                false,
                Offset(inset, inset),
                arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        if (allDone) Icon(Icons.Filled.Check, null, tint = p.muted, modifier = Modifier.size(9.dp))
    }
}

/** Full task list in a half sheet (iOS `TodoListSheet`). */
@Composable
fun TodoListContent(items: List<TodoItem>) {
    val p = palette
    val done = items.count { it.status == "completed" }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
        Box(
            Modifier
                .padding(bottom = 16.dp)
                .fillMaxWidth()
                .height(4.dp)
                .background(p.foreground.copy(alpha = 0.1f), CircleShape),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(done.toFloat() / items.size.coerceAtLeast(1))
                    .height(4.dp)
                    .background(p.foreground, CircleShape),
            )
        }
        items.forEachIndexed { index, item ->
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TodoIcon(item.status)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        item.content,
                        style = AppType.body,
                        color = if (item.status == "completed") p.secondary else p.foreground,
                    )
                    if (item.status == "in_progress") {
                        Text(stringResource(R.string.todo_in_progress), style = AppType.caption, color = p.muted)
                    }
                }
            }
            if (index < items.lastIndex) {
                Box(Modifier.padding(start = 30.dp).fillMaxWidth().height(0.5.dp).background(p.hairline))
            }
        }
    }
}

@Composable
private fun TodoIcon(status: String) {
    val p = palette
    Box(Modifier.width(18.dp).height(24.dp), contentAlignment = Alignment.Center) {
        when (status) {
            "completed" -> Box(Modifier.size(18.dp).background(p.muted, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, null, tint = if (p.dark) Color.Black else Color.White, modifier = Modifier.size(12.dp))
            }
            "in_progress" -> Box(
                Modifier.size(18.dp).border(1.5.dp, p.foreground.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(9.dp).background(p.foreground.copy(alpha = 0.7f), CircleShape)) }
            else -> Box(Modifier.size(18.dp).border(1.5.dp, p.muted, CircleShape))
        }
    }
}
