package dev.hailab.deeppilot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hailab.deeppilot.R
import dev.hailab.deeppilot.bridge.PluginUpdater
import dev.hailab.deeppilot.core.update.PluginUpdateState
import dev.hailab.deeppilot.ui.AppLayout
import dev.hailab.deeppilot.ui.AppSpacing
import dev.hailab.deeppilot.ui.AppType
import dev.hailab.deeppilot.ui.components.SettingsCard
import dev.hailab.deeppilot.ui.components.SettingsDivider
import dev.hailab.deeppilot.ui.components.SettingsRow
import dev.hailab.deeppilot.ui.components.SmallSpinner
import dev.hailab.deeppilot.ui.components.plainClickable
import dev.hailab.deeppilot.ui.palette

/** iOS `PluginUpdateSheet`: current version, last update, versions to install and live progress. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginUpdateSheet(updater: PluginUpdater, onDismiss: () -> Unit) {
    val p = palette
    val view by updater.view.collectAsStateWithLifecycle()
    val state = view.state
    val status = state.status
    var confirming by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { updater.check() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = p.groupedBackground,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            SheetBar(title = stringResource(R.string.plugin_update_title), leading = null) {
                Text(
                    stringResource(R.string.composer_skills_close),
                    style = AppType.body.copy(fontWeight = FontWeight.SemiBold),
                    color = p.accent,
                    modifier = Modifier.padding(horizontal = 10.dp).plainClickable(onClick = onDismiss),
                )
            }
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = AppLayout.pageInset)
                    .padding(top = AppSpacing.small, bottom = AppSpacing.large),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.section),
            ) {
                if (status != null) {
                    SettingsCard {
                        SettingsRow(title = stringResource(R.string.plugin_update_current)) {
                            Text(status.currentVersion, style = AppType.body, color = p.secondary)
                        }
                    }
                    status.journal?.let { journal ->
                        Section(stringResource(R.string.plugin_update_journal)) {
                            Column(Modifier.padding(horizontal = AppSpacing.regular, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(R.string.plugin_update_journal_versions, journal.fromVersion, journal.toVersion), style = AppType.body, color = p.foreground)
                                Text(phaseText(journal.phase), style = AppType.subheadline, color = p.secondary)
                            }
                        }
                    }
                    if (status.reason != null) Note(stringResource(R.string.plugin_update_offline))
                    Section(stringResource(R.string.plugin_update_versions)) {
                        if (status.versions.isEmpty()) {
                            Text(
                                stringResource(R.string.plugin_update_no_versions),
                                style = AppType.body,
                                color = p.secondary,
                                modifier = Modifier.padding(horizontal = AppSpacing.regular, vertical = 11.dp),
                            )
                        }
                        status.versions.forEachIndexed { index, version ->
                            if (index > 0) SettingsDivider()
                            val current = version.version == status.currentVersion
                            val enabled = version.selectable && !current && view.canApply
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .plainClickable(enabled = enabled) { confirming = version.version }
                                    .padding(horizontal = AppSpacing.regular, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(version.version, style = AppType.body, color = if (enabled || current) p.foreground else p.tertiary, modifier = Modifier.weight(1f))
                                when {
                                    !version.selectable -> Text(stringResource(R.string.plugin_update_incompatible), style = AppType.subheadline, color = p.secondary)
                                    current -> Icon(Icons.Filled.Check, null, tint = p.accent, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
                state.progress?.let { progress ->
                    Section(stringResource(R.string.plugin_update_progress)) {
                        Column(Modifier.padding(horizontal = AppSpacing.regular, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(phaseText(progress.phase), style = AppType.body, color = p.foreground)
                            progress.percent?.let { percent ->
                                LinearProgressIndicator(
                                    progress = { (percent / 100.0).coerceIn(0.0, 1.0).toFloat() },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = p.accent,
                                    trackColor = p.secondaryFill,
                                )
                            }
                        }
                    }
                }
                if (view.connected && !view.canManage) Note(stringResource(R.string.plugin_update_manage_required))
                if (!view.connected) Note(stringResource(R.string.plugin_update_reconnecting))
                state.error?.let { error ->
                    Note(
                        stringResource(
                            when (error) {
                                PluginUpdateState.Error.CheckFailed -> R.string.plugin_update_check_failed
                                PluginUpdateState.Error.ApplyRejected -> R.string.plugin_update_apply_rejected
                                PluginUpdateState.Error.ApplyUnknown -> R.string.plugin_update_apply_unknown
                            },
                        ),
                    )
                }
                SettingsCard {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .plainClickable(enabled = view.canCheck && !view.checking) { updater.check() }
                            .padding(horizontal = AppSpacing.regular, vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        if (view.checking) SmallSpinner()
                        else Text(
                            stringResource(R.string.plugin_update_check),
                            style = AppType.body,
                            color = if (view.canCheck) p.accent else p.tertiary,
                        )
                    }
                }
                Note(stringResource(R.string.plugin_update_detail))
            }
        }
    }

    confirming?.let { version ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(stringResource(R.string.plugin_update_confirm)) },
            text = { Text(stringResource(R.string.plugin_update_confirm_detail, version)) },
            confirmButton = {
                TextButton(onClick = { confirming = null; updater.apply(version) }) { Text(stringResource(R.string.plugin_update_apply)) }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title.uppercase(), style = AppType.footnote, color = palette.secondary, modifier = Modifier.padding(horizontal = AppSpacing.regular))
        SettingsCard(content = content)
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = AppType.footnote, color = palette.secondary, modifier = Modifier.padding(horizontal = AppSpacing.regular))
}

@Composable
private fun phaseText(phase: String): String = stringResource(
    when (phase) {
        "preparing" -> R.string.plugin_update_phase_preparing
        "installing" -> R.string.plugin_update_phase_installing
        "verifying" -> R.string.plugin_update_phase_verifying
        "restart-required" -> R.string.plugin_update_phase_restart_required
        "done" -> R.string.plugin_update_phase_done
        "rolled-back" -> R.string.plugin_update_phase_rolled_back
        "failed" -> R.string.plugin_update_phase_failed
        else -> R.string.plugin_update_phase_unknown
    },
)
