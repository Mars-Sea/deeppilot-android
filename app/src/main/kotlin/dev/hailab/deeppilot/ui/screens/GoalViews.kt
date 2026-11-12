package dev.hailab.deeppilot.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hailab.deeppilot.R
import dev.hailab.deeppilot.core.protocol.SessionGoal
import dev.hailab.deeppilot.ui.AppSpacing
import dev.hailab.deeppilot.ui.AppType
import dev.hailab.deeppilot.ui.components.plainClickable
import dev.hailab.deeppilot.ui.palette

/**
 * The goal line inside the composer, under the task line and built like it:
 * the objective, then the round while it runs and the status otherwise.
 */
@Composable
fun GoalComposerStatus(goal: SessionGoal, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val p = palette
    Row(
        modifier.fillMaxWidth().heightIn(min = 44.dp).plainClickable(onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
    ) {
        Icon(Icons.Outlined.Flag, null, tint = p.secondary, modifier = Modifier.size(16.dp))
        Text(
            goal.objective.ifBlank { stringResource(R.string.goal_title) },
            style = AppType.caption,
            color = p.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(goalStatus(goal), style = AppType.caption, color = if (goal.phase == SessionGoal.PHASE_BLOCKED) p.danger else p.secondary)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = p.tertiary, modifier = Modifier.size(14.dp))
    }
}

/** The round while the goal is running on its own, the status in every other case. */
@Composable
private fun goalStatus(goal: SessionGoal): String = when {
    goal.isActive && goal.activation == "armed" -> goalRounds(goal)
    else -> goalPhase(goal)
}

@Composable
private fun goalRounds(goal: SessionGoal): String {
    val max = goal.maxGoalRounds
    return if (max != null && max > 0) stringResource(R.string.goal_rounds, goal.roundsStarted.toInt(), max.toInt())
    else stringResource(R.string.goal_rounds_open, goal.roundsStarted.toInt())
}

@Composable
private fun goalPhase(goal: SessionGoal): String = stringResource(
    when (goal.phase) {
        // Armed is the only activation that means "continuing by itself"; unknown is not armed.
        SessionGoal.PHASE_ACTIVE -> if (goal.activation == "disarmed") R.string.goal_phase_waiting else R.string.goal_phase_active
        SessionGoal.PHASE_PAUSED -> R.string.goal_phase_paused
        SessionGoal.PHASE_BLOCKED -> R.string.goal_phase_blocked
        SessionGoal.PHASE_COMPLETE -> R.string.goal_phase_complete
        else -> R.string.goal_phase_unknown
    },
)

/** The whole goal in a half sheet: full text, status, rounds, times and why it is blocked. */
@Composable
fun GoalDetailContent(goal: SessionGoal) {
    val p = palette
    val context = LocalContext.current
    fun time(millis: Long): String? = millis.takeIf { it > 0 }?.let {
        DateUtils.formatDateTime(context, it, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = AppSpacing.large),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.regular),
    ) {
        SelectionContainer { Text(goal.objective, style = AppType.body, color = p.foreground) }
        GoalField(stringResource(R.string.goal_detail_status), goalPhase(goal))
        GoalField(stringResource(R.string.goal_detail_rounds), goalRounds(goal))
        goal.blockedReason?.takeIf { goal.phase == SessionGoal.PHASE_BLOCKED }?.let { reason ->
            (reason.message?.takeIf { it.isNotBlank() } ?: reason.code?.takeIf { it.isNotBlank() })?.let {
                GoalField(stringResource(R.string.goal_detail_blocked), it)
            }
        }
        time(goal.createdAt)?.let { GoalField(stringResource(R.string.goal_detail_created), it) }
        time(goal.updatedAt)?.let { GoalField(stringResource(R.string.goal_detail_updated), it) }
    }
}

@Composable
private fun GoalField(label: String, value: String) {
    val p = palette
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = AppType.caption, color = p.secondary)
        Text(value, style = AppType.subheadline, color = p.foreground)
    }
}
