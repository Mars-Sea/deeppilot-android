package dev.hailab.deeppilot.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.LiveHelp
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.hailab.deeppilot.R
import dev.hailab.deeppilot.core.protocol.PendingApproval
import dev.hailab.deeppilot.core.protocol.PendingQuestion
import dev.hailab.deeppilot.core.protocol.QuestionAnswer
import dev.hailab.deeppilot.core.protocol.QuestionItem
import dev.hailab.deeppilot.ui.AppRadius
import dev.hailab.deeppilot.ui.AppSpacing
import dev.hailab.deeppilot.ui.AppType
import dev.hailab.deeppilot.ui.components.BorderedButton
import dev.hailab.deeppilot.ui.components.ProminentButton
import dev.hailab.deeppilot.ui.components.SmallSpinner
import dev.hailab.deeppilot.ui.components.plainClickable
import dev.hailab.deeppilot.ui.palette
import kotlinx.coroutines.launch

/** iOS `conversationActionSheetPresentation`: half sheet, radius 28, drag indicator. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationSheet(
    title: String,
    subtitle: String?,
    onDismiss: () -> Unit,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = palette
    val state = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = p.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(title, style = AppType.title3.copy(fontWeight = FontWeight.SemiBold), color = p.foreground)
                    if (subtitle != null) Text(subtitle, style = AppType.subheadline, color = p.secondary)
                }
                Box(
                    Modifier.size(44.dp).plainClickable {
                        scope.launch { state.hide() }.invokeOnCompletion { onDismiss() }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(30.dp).background(p.tertiaryFill, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Close, stringResource(R.string.sheet_close), tint = p.secondary, modifier = Modifier.size(15.dp))
                    }
                }
            }
            Column(Modifier.weight(1f, fill = false), content = content)
            footer?.let { Column(Modifier.fillMaxWidth().background(p.background), content = it) }
        }
    }
}

@Composable
fun ApprovalSheet(
    approval: PendingApproval,
    submitting: Boolean,
    error: String?,
    enabled: Boolean,
    onRespond: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = palette
    ConversationSheet(
        title = stringResource(R.string.approval_title),
        subtitle = stringResource(R.string.approval_subtitle),
        onDismiss = onDismiss,
        footer = {
            Column(
                Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PendingResponseStatus(submitting, error)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BorderedButton(
                        stringResource(R.string.approval_deny),
                        onClick = { onRespond(false) },
                        enabled = enabled && !submitting,
                        modifier = Modifier.weight(1f),
                    )
                    ProminentButton(
                        stringResource(R.string.approval_approve),
                        onClick = { onRespond(true) },
                        enabled = enabled && !submitting,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (approval.summary.isNotEmpty()) {
                SelectionContainer { Text(approval.summary, style = AppType.body, color = p.foreground) }
            }
            if (approval.toolName.isNotEmpty()) {
                Text(approval.toolName, style = AppType.caption, color = p.secondary)
            }
            SelectionContainer {
                Text(
                    approval.toolArguments?.takeIf { it.isNotBlank() } ?: stringResource(R.string.approval_details_unavailable),
                    style = AppType.callout.copy(fontFamily = FontFamily.Monospace),
                    color = p.foreground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(p.surface, RoundedCornerShape(AppRadius.large))
                        .padding(16.dp),
                )
            }
        }
    }
}

/** UI draft kept apart from the wire answer, as iOS `QuestionAnswerDraft`. */
private data class AnswerDraft(
    val selection: Set<String> = emptySet(),
    val custom: String = "",
    val usesCustom: Boolean = false,
) {
    fun toggleOption(label: String, multi: Boolean) = if (multi) {
        copy(selection = if (label in selection) selection - label else selection + label)
    } else {
        copy(selection = setOf(label), usesCustom = false)
    }

    fun toggleCustom(multi: Boolean) =
        copy(usesCustom = if (multi) !usesCustom else true, selection = if (multi) selection else emptySet())

    fun answer(item: QuestionItem): QuestionAnswer? {
        val freeText = item.options.isEmpty()
        val includeCustom = freeText || usesCustom
        val text = if (includeCustom) custom.trim() else ""
        if (includeCustom && text.isEmpty()) return null
        val selected = if (freeText || (usesCustom && !item.multiSelect)) emptyList()
        else item.options.map { it.label }.filter { it in selection }
        if (selected.isEmpty() && text.isEmpty()) return null
        return QuestionAnswer(id = item.id, selected = selected, custom = text.ifEmpty { null })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuestionSheet(
    payload: PendingQuestion,
    submitting: Boolean,
    error: String?,
    enabled: Boolean,
    onSubmit: (List<QuestionAnswer>) -> Unit,
    onDismiss: () -> Unit,
) {
    val questions = payload.questions
    val drafts = remember(payload.requestId) { mutableStateMapOf<String, AnswerDraft>() }
    val answers = questions.mapNotNull { q -> (drafts[q.id] ?: AnswerDraft()).answer(q)?.let { q.id to it } }.toMap()
    val allAnswered = questions.isNotEmpty() && questions.all { it.id in answers }
    val multiPage = questions.size > 1
    val pager = rememberPagerState(pageCount = { questions.size })
    val scope = rememberCoroutineScope()
    val p = palette

    ConversationSheet(
        title = stringResource(R.string.question_title),
        subtitle = if (multiPage) stringResource(R.string.question_count, questions.size) else null,
        onDismiss = onDismiss,
        footer = {
            Box(Modifier.padding(horizontal = 20.dp)) { PendingResponseStatus(submitting, error) }
            if (multiPage) {
                val page = pager.currentPage
                val last = page >= questions.lastIndex
                Column(
                    Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.pending_indicator, page + 1, questions.size), style = AppType.caption, color = p.secondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BorderedButton(
                            stringResource(R.string.pending_previous),
                            onClick = { scope.launch { pager.animateScrollToPage(page - 1) } },
                            enabled = page > 0 && !submitting,
                            modifier = Modifier.weight(1f),
                        )
                        if (last) {
                            ProminentButton(
                                stringResource(R.string.question_submit),
                                onClick = { onSubmit(questions.mapNotNull { answers[it.id] }) },
                                enabled = enabled && allAnswered && !submitting,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            ProminentButton(
                                stringResource(R.string.pending_next),
                                onClick = { scope.launch { pager.animateScrollToPage(page + 1) } },
                                enabled = questions[page].id in answers && !submitting,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            } else {
                Column(
                    Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ProminentButton(
                        stringResource(R.string.question_submit),
                        onClick = { onSubmit(questions.mapNotNull { answers[it.id] }) },
                        enabled = enabled && allAnswered && !submitting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!allAnswered) {
                        Text(
                            stringResource(R.string.question_missing, questions.size - answers.size),
                            style = AppType.caption,
                            color = p.secondary,
                        )
                    }
                }
            }
        },
    ) {
        if (multiPage) {
            HorizontalPager(state = pager, verticalAlignment = Alignment.Top) { index ->
                QuestionPage(questions[index], drafts[questions[index].id] ?: AnswerDraft()) { drafts[questions[index].id] = it }
            }
        } else {
            questions.firstOrNull()?.let { q ->
                QuestionPage(q, drafts[q.id] ?: AnswerDraft()) { drafts[q.id] = it }
            }
        }
    }
}

@Composable
private fun QuestionPage(item: QuestionItem, draft: AnswerDraft, onChange: (AnswerDraft) -> Unit) {
    val p = palette
    val freeText = item.options.isEmpty()
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            item.question,
            style = AppType.body.copy(fontWeight = FontWeight.Medium),
            color = p.foreground,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        if (!freeText) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item.options.forEach { option ->
                    ChoiceRow(option.label, option.description, option.label in draft.selection, item.multiSelect) {
                        onChange(draft.toggleOption(option.label, item.multiSelect))
                    }
                }
                ChoiceRow(stringResource(R.string.question_custom), null, draft.usesCustom, item.multiSelect) {
                    onChange(draft.toggleCustom(item.multiSelect))
                }
            }
        }
        if (freeText || draft.usesCustom) {
            BasicTextField(
                value = draft.custom,
                onValueChange = { onChange(draft.copy(custom = it)) },
                textStyle = AppType.body.copy(color = p.foreground),
                cursorBrush = SolidColor(p.accent),
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(p.surface, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                decorationBox = { inner ->
                    Box {
                        if (draft.custom.isEmpty()) {
                            Text(stringResource(R.string.question_custom_placeholder), style = AppType.body, color = p.tertiary)
                        }
                        inner()
                    }
                },
            )
        }
    }
}

@Composable
private fun ChoiceRow(label: String, description: String?, selected: Boolean, multi: Boolean, onClick: () -> Unit) {
    val p = palette
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(if (selected) p.mutedSurface else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(14.dp))
            .plainClickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            when {
                multi && selected -> Icons.Filled.CheckBox
                multi -> Icons.Filled.CheckBoxOutlineBlank
                selected -> Icons.Filled.RadioButtonChecked
                else -> Icons.Filled.RadioButtonUnchecked
            },
            contentDescription = null,
            tint = if (selected) p.foreground else p.secondary,
            modifier = Modifier.padding(top = 1.dp).size(21.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = AppType.body, color = p.foreground)
            if (!description.isNullOrEmpty()) Text(description, style = AppType.caption, color = p.secondary)
        }
    }
}

@Composable
private fun PendingResponseStatus(submitting: Boolean, error: String?) {
    val p = palette
    when {
        submitting -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SmallSpinner()
            Text(stringResource(R.string.pending_responding), style = AppType.subheadline, color = p.secondary)
        }
        !error.isNullOrEmpty() -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.WarningAmber, null, tint = p.danger, modifier = Modifier.size(16.dp))
            Text(error, style = AppType.subheadline, color = p.danger)
        }
    }
}

/** Brings back a request whose sheet the user dismissed (iOS `PendingReopenChip`). */
@Composable
fun PendingReopenChip(approval: Boolean, headline: String, subtitle: String?, onReopen: () -> Unit) {
    val p = palette
    val shape = RoundedCornerShape(AppRadius.medium)
    Row(
        Modifier
            .fillMaxWidth()
            .background(p.surface, shape)
            .border(0.5.dp, p.hairline, shape)
            .plainClickable(onClick = onReopen)
            .padding(horizontal = AppSpacing.regular, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
    ) {
        Box(Modifier.size(26.dp).background(p.warning.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(
                if (approval) Icons.Filled.Shield else Icons.AutoMirrored.Outlined.LiveHelp,
                null,
                tint = p.warning,
                modifier = Modifier.size(15.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(stringResource(R.string.pending_reopen_title), style = AppType.subheadline.copy(fontWeight = FontWeight.SemiBold), color = p.foreground)
            Text(
                if (subtitle.isNullOrEmpty()) headline else "$headline · $subtitle",
                style = AppType.caption,
                color = p.muted,
                maxLines = 1,
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = p.muted, modifier = Modifier.size(16.dp))
    }
}
