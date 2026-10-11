package dev.hailab.deeppilot.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hailab.deeppilot.BuildConfig
import dev.hailab.deeppilot.R
import dev.hailab.deeppilot.bridge.BridgeRepository
import dev.hailab.deeppilot.core.pairing.HostEndpoints
import dev.hailab.deeppilot.graph
import dev.hailab.deeppilot.service.ConnectionService
import dev.hailab.deeppilot.ui.connectionText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(repository: BridgeRepository, onBack: () -> Unit, onPairAnother: () -> Unit) {
    val context = LocalContext.current
    val host by repository.host.collectAsStateWithLifecycle()
    val connection by repository.connection.collectAsStateWithLifecycle()
    var keepConnected by remember { mutableStateOf(context.graph.store.keepConnected) }
    var confirmUnpair by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // System settings pages change these behind our back; re-read on return.
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    val notificationsOn = remember(resumes) { context.graph.notifier.canPost() }
    val batteryExempt = remember(resumes) { isIgnoringBatteryOptimizations(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Section(stringResource(R.string.settings_host)) {
                val current = host
                Text(current?.let { HostEndpoints.of(it.host)?.display ?: it.host }.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                Text(connectionText(connection), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { repository.reconnect() }) { Text(stringResource(R.string.action_reconnect)) }
                    OutlinedButton(onClick = onPairAnother) { Text(stringResource(R.string.settings_pair_again)) }
                }
            }
            HorizontalDivider()

            Section(stringResource(R.string.settings_background)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_keep_connected), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.settings_keep_connected_detail), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = keepConnected,
                        onCheckedChange = { on ->
                            keepConnected = on
                            context.graph.store.keepConnected = on
                            if (on) ConnectionService.start(context) else ConnectionService.stop(context)
                        },
                    )
                }
                StatusLine(
                    label = stringResource(R.string.settings_notifications),
                    ok = notificationsOn,
                    action = stringResource(R.string.settings_open),
                    onAction = { context.startActivity(notificationSettings(context)) },
                )
                StatusLine(
                    label = stringResource(R.string.settings_battery),
                    ok = batteryExempt,
                    action = stringResource(R.string.settings_allow),
                    onAction = { requestBatteryExemption(context) },
                )
                Text(stringResource(R.string.settings_vendor_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { context.startActivity(appDetails(context)) }) {
                    Text(stringResource(R.string.settings_app_details))
                }
            }
            HorizontalDivider()

            Section(stringResource(R.string.settings_device)) {
                Text(stringResource(R.string.settings_unpair_detail), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { confirmUnpair = true }) {
                    Text(stringResource(R.string.settings_unpair), color = MaterialTheme.colorScheme.error)
                }
            }
            Text(
                stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            title = { Text(stringResource(R.string.settings_unpair)) },
            text = { Text(stringResource(R.string.settings_unpair_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnpair = false
                    ConnectionService.stop(context)
                    scope.launch { repository.unpair() }
                }) { Text(stringResource(R.string.settings_unpair), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnpair = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun StatusLine(label: String, ok: Boolean, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(if (ok) R.string.settings_status_ok else R.string.settings_status_off),
                style = MaterialTheme.typography.bodySmall,
                color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        if (!ok) TextButton(onClick = onAction) { Text(action) }
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

/**
 * Sideloaded app, so the direct exemption request is fine (the Play policy
 * that restricts it does not apply). Falls back to the system list.
 */
@SuppressLint("BatteryLife")
private fun requestBatteryExemption(context: Context) {
    val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
    runCatching { context.startActivity(direct) }
        .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }
}

private fun notificationSettings(context: Context) =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

/** Vendor ROMs hide "auto-start" and background limits on the app details page. */
private fun appDetails(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
