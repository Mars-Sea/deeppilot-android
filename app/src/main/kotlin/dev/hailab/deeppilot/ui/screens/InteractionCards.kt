package dev.hailab.deeppilot.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hailab.deeppilot.R
import dev.hailab.deeppilot.core.protocol.PendingApproval
import dev.hailab.deeppilot.core.protocol.PendingQuestion
import dev.hailab.deeppilot.core.protocol.QuestionAnswer

@Composable
fun ApprovalCard(approval: PendingApproval, enabled: Boolean, onRespond: (allow: Boolean, onFailed: () -> Unit) -> Unit) {
    var answered by remember(approval.requestId) { mutableStateOf(false) }
    val destructive = approval.riskLevel == "destructive"
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (destructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.approval_title, approval.toolName), style = MaterialTheme.typography.titleSmall)
            if (approval.summary.isNotBlank()) Text(approval.summary, style = MaterialTheme.typography.bodyMedium)
            approval.toolArguments?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (destructive) {
                Text(
                    stringResource(R.string.approval_destructive),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        answered = true
                        onRespond(false) { answered = false }
                    },
                    enabled = enabled && !answered,
                ) { Text(stringResource(R.string.approval_deny)) }
                Button(
                    onClick = {
                        answered = true
                        onRespond(true) { answered = false }
                    },
                    enabled = enabled && !answered,
                    colors = if (destructive) {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                ) { Text(stringResource(R.string.approval_allow)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuestionCard(question: PendingQuestion, enabled: Boolean, onRespond: (List<QuestionAnswer>, onFailed: () -> Unit) -> Unit) {
    val selected = remember(question.requestId) { mutableStateMapOf<String, List<String>>() }
    val custom = remember(question.requestId) { mutableStateMapOf<String, String>() }
    var answered by remember(question.requestId) { mutableStateOf(false) }
    val complete = question.questions.all { !selected[it.id].isNullOrEmpty() || !custom[it.id].isNullOrBlank() }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            question.questions.forEach { item ->
                Text(item.question, style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item.options.forEach { option ->
                        val current = selected[item.id].orEmpty()
                        val isOn = option.label in current
                        FilterChip(
                            selected = isOn,
                            onClick = {
                                selected[item.id] = when {
                                    isOn -> current - option.label
                                    item.multiSelect -> current + option.label
                                    else -> listOf(option.label)
                                }
                            },
                            label = { Text(option.label) },
                            enabled = enabled && !answered,
                        )
                    }
                }
                OutlinedTextField(
                    value = custom[item.id].orEmpty(),
                    onValueChange = { custom[item.id] = it },
                    placeholder = { Text(stringResource(R.string.question_custom_hint)) },
                    enabled = enabled && !answered,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Button(
                onClick = {
                    answered = true
                    onRespond(
                        question.questions.map { item ->
                            QuestionAnswer(
                                id = item.id,
                                selected = selected[item.id].orEmpty(),
                                custom = custom[item.id]?.trim()?.takeIf { it.isNotEmpty() },
                            )
                        },
                    ) { answered = false }
                },
                enabled = enabled && !answered && complete,
            ) { Text(stringResource(R.string.question_submit)) }
        }
    }
}
