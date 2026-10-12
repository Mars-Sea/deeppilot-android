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
import androidx.compose.foundation.text.BasicTextField
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
 * the send disc at the end; once focused or holding text the field takes the
 * whole width and the send disc moves to a footer row below it.
 */
@Composable
fun PromptComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    todoItems: List<TodoItem>,
    canSubmit: Boolean,
    sending: Boolean,
    running: Boolean,
    stopping: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onOpenTodos: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = palette
    var focused by remember { mutableStateOf(false) }
    val expanded = focused || draft.isNotEmpty()
    val shape = RoundedCornerShape(AppRadius.large)

    Column(
        modifier
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
                    canSend = draft.isNotBlank() && canSubmit,
                    sending = sending,
                    running = running,
                    stopping = stopping,
                    onSend = onSend,
                    onStop = onStop,
                )
            }
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
                if (!expanded) send()
            }
            if (expanded) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Spacer(Modifier.weight(1f))
                    send()
                }
            }
        }
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
