package dev.hailab.deeppilot.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.UnfoldMore
import dev.hailab.deeppilot.data.AppLanguage
import dev.hailab.deeppilot.data.AppearanceOption
import dev.hailab.deeppilot.data.FontSizeOption
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import dev.hailab.deeppilot.ui.AppLayout
import dev.hailab.deeppilot.ui.AppSpacing
import dev.hailab.deeppilot.ui.AppType
import dev.hailab.deeppilot.ui.components.AppIcon
import dev.hailab.deeppilot.ui.components.BackButton
import dev.hailab.deeppilot.ui.components.ConnectionStatusLight
import dev.hailab.deeppilot.ui.components.NavBar
import dev.hailab.deeppilot.ui.components.NavTitle
import dev.hailab.deeppilot.ui.components.SettingsCard
import dev.hailab.deeppilot.ui.components.SettingsDivider
import dev.hailab.deeppilot.ui.components.SettingsRow
import dev.hailab.deeppilot.ui.components.phaseText
import dev.hailab.deeppilot.ui.components.plainClickable
import dev.hailab.deeppilot.ui.palette
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxWidth
import dev.hailab.deeppilot.core.bridge.ConnectionState
import dev.hailab.deeppilot.core.hosts.HostProfile

@Composable
fun SettingsScreen(
    repository: BridgeRepository,
    onBack: () -> Unit,
    onAddHost: () -> Unit,
    onEditHost: (String) -> Unit,
    onOpenIcons: () -> Unit,
    onOpenArchived: () -> Unit,
) {
    val p = palette
    val context = LocalContext.current
    val host by repository.host.collectAsStateWithLifecycle()
    val connection by repository.connection.collectAsStateWithLifecycle()
    var keepConnected by remember { mutableStateOf(context.graph.store.keepConnected) }
    val roster by repository.roster.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<HostProfile?>(null) }
    val scope = rememberCoroutineScope()

    // System settings pages change these behind our back; re-read on return.
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    val notificationsOn = remember(resumes) { context.graph.notifier.canPost() }
    val batteryExempt = remember(resumes) { isIgnoringBatteryOptimizations(context) }

    Column(Modifier.fillMaxSize().background(p.background)) {
        NavBar(leading = { BackButton(onBack) }, principal = { NavTitle(stringResource(R.string.settings_title)) })
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = AppLayout.pageInset)
                .padding(top = AppSpacing.small, bottom = AppSpacing.large),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.section),
        ) {
            // Hosts (iOS hostCard)
            SettingsCard {
                Row(Modifier.heightIn(min = 44.dp).padding(horizontal = 17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.settings_hosts),
                        style = AppType.body.copy(fontWeight = FontWeight.SemiBold),
                        color = p.foreground,
                        modifier = Modifier.weight(1f),
                    )
                    Row(
                        Modifier.heightIn(min = 44.dp).plainClickable(onClick = onAddHost),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Filled.Add, null, tint = p.foreground, modifier = Modifier.size(16.dp))
                        Text(
                            stringResource(R.string.action_add),
                            style = AppType.subheadline.copy(fontWeight = FontWeight.SemiBold),
                            color = p.foreground,
                        )
                    }
                }
                roster.profiles.forEach { profile ->
                    SettingsDivider()
                    HostRow(
                        profile = profile,
                        active = profile.id == host?.profileId,
                        launchDefault = profile.id == roster.launch?.id,
                        connection = connection,
                        onSelect = { repository.selectHost(profile.id) },
                        onEdit = { onEditHost(profile.id) },
                        onSetLaunch = { repository.setLaunchHost(profile.id) },
                        onReconnect = { repository.reconnect() },
                        onDelete = { pendingDelete = profile },
                    )
                }
            }

            AppearanceCard()

            SettingsCard {
                SettingsRow(title = stringResource(R.string.settings_icon_title), onClick = onOpenIcons) { Chevron() }
            }

            SettingsCard {
                SettingsRow(title = stringResource(R.string.archived_title), onClick = onOpenArchived) { Chevron() }
            }

            // Background connection and notifications (Android has no APNs; see the push plan).
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                SettingsCard {
                    SettingsRow(
                        title = stringResource(R.string.settings_keep_connected),
                        subtitle = stringResource(R.string.settings_keep_connected_detail),
                    ) {
                        Switch(
                            checked = keepConnected,
                            onCheckedChange = { on ->
                                keepConnected = on
                                context.graph.store.keepConnected = on
                                if (on) ConnectionService.start(context) else ConnectionService.stop(context)
                            },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = p.accent,
                                checkedThumbColor = Color.White,
                                uncheckedTrackColor = p.secondaryFill,
                                uncheckedThumbColor = Color.White,
                                uncheckedBorderColor = Color.Transparent,
                            ),
                        )
                    }
                    SettingsDivider()
                    SettingsRow(title = stringResource(R.string.settings_notification_permission)) {
                        StatusText(
                            stringResource(if (notificationsOn) R.string.settings_auth_allowed else R.string.settings_auth_denied),
                            if (notificationsOn) p.success else p.danger,
                        )
                    }
                    SettingsDivider()
                    SettingsRow(title = stringResource(R.string.settings_battery)) {
                        StatusText(
                            stringResource(if (batteryExempt) R.string.settings_auth_allowed else R.string.settings_auth_denied),
                            if (batteryExempt) p.success else p.warning,
                        )
                    }
                    SettingsDivider()
                    SettingsRow(
                        title = stringResource(if (notificationsOn) R.string.settings_open_notification_settings else R.string.settings_allow_notifications),
                        onClick = { context.startActivity(notificationSettings(context)) },
                    ) { Chevron() }
                    if (!batteryExempt) {
                        SettingsDivider()
                        SettingsRow(title = stringResource(R.string.settings_allow_battery), onClick = { requestBatteryExemption(context) }) { Chevron() }
                    }
                    SettingsDivider()
                    SettingsRow(title = stringResource(R.string.settings_app_details), onClick = { context.startActivity(appDetails(context)) }) { Chevron() }
                }
                Text(
                    stringResource(R.string.settings_vendor_hint),
                    style = AppType.footnote,
                    color = p.muted,
                    modifier = Modifier.padding(horizontal = AppSpacing.regular),
                )
            }

            // About
            SettingsCard {
                SettingsRow(title = stringResource(R.string.settings_about_version)) {
                    Text(BuildConfig.VERSION_NAME, style = AppType.body, color = p.secondary)
                }
                SettingsDivider()
                SettingsRow(title = stringResource(R.string.settings_about_protocol)) {
                    Text("v2", style = AppType.body, color = p.secondary)
                }
                SettingsDivider()
                SettingsRow(title = stringResource(R.string.settings_about_privacy)) {
                    Text(stringResource(R.string.settings_about_privacy_value), style = AppType.subheadline, color = p.secondary, maxLines = 1)
                }
            }
        }
    }

    pendingDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.settings_host_delete_title, profile.name)) },
            text = { Text(stringResource(R.string.settings_host_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    if (roster.profiles.size <= 1) ConnectionService.stop(context)
                    scope.launch { repository.deleteHost(profile.id) }
                }) { Text(stringResource(R.string.action_delete), color = p.danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** One paired computer (iOS `instanceRow`): tap to connect to it, menu to edit, pin or delete. */
@Composable
private fun HostRow(
    profile: HostProfile,
    active: Boolean,
    launchDefault: Boolean,
    connection: ConnectionState,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onSetLaunch: () -> Unit,
    onReconnect: () -> Unit,
    onDelete: () -> Unit,
) {
    val p = palette
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .plainClickable(enabled = !active, onClick = onSelect)
            .padding(horizontal = 17.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(size = 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    profile.name,
                    style = AppType.subheadline.copy(fontWeight = FontWeight.SemiBold),
                    color = p.foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (launchDefault) {
                    Text(
                        stringResource(R.string.settings_host_default_badge),
                        style = AppType.caption2.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                        color = p.foreground,
                        modifier = Modifier
                            .background(p.mutedSurface, CircleShape)
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                profile.tlsFingerprint?.let { fingerprint ->
                    Icon(Icons.Filled.Lock, null, tint = p.muted, modifier = Modifier.size(11.dp))
                    Text(
                        fingerprint.removePrefix("sha256:").take(8),
                        style = AppType.caption.copy(fontFamily = FontFamily.Monospace),
                        color = p.muted,
                        maxLines = 1,
                    )
                    Text("·", style = AppType.caption, color = p.muted)
                }
                Text(
                    HostEndpoints.of(profile.host)?.display ?: profile.host,
                    style = AppType.caption,
                    color = p.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (active) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ConnectionStatusLight(connection, backdrop = p.surface)
                Text(phaseText(connection), style = AppType.caption.copy(fontWeight = FontWeight.Medium), color = p.muted, maxLines = 1)
            }
        }
        Box {
            Box(
                Modifier
                    .size(32.dp)
                    .background(p.mutedSurface, CircleShape)
                    .plainClickable { menuOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.MoreHoriz,
                    stringResource(R.string.settings_host_actions_named, profile.name),
                    tint = p.foreground,
                    modifier = Modifier.size(20.dp),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_edit)) },
                    leadingIcon = { Icon(Icons.Filled.Edit, null) },
                    onClick = {
                        menuOpen = false
                        onEdit()
                    },
                )
                if (!launchDefault) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_host_set_launch)) },
                        leadingIcon = { Icon(Icons.Filled.PushPin, null) },
                        onClick = {
                            menuOpen = false
                            onSetLaunch()
                        },
                    )
                }
                if (active) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_reconnect)) },
                        leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                        onClick = {
                            menuOpen = false
                            onReconnect()
                        },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete), color = p.danger) },
                    leadingIcon = { Icon(Icons.Filled.Delete, null, tint = p.danger) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}

/** iOS `appearanceCard`: theme segments, text size and language menus. */
@Composable
private fun AppearanceCard() {
    val p = palette
    val preferences = LocalContext.current.graph.preferences
    val appearance by preferences.appearance.collectAsStateWithLifecycle()
    val fontSize by preferences.fontSize.collectAsStateWithLifecycle()
    val language by preferences.language.collectAsStateWithLifecycle()
    SettingsCard {
        SettingsRow(title = stringResource(R.string.settings_theme)) {
            SegmentedControl(
                options = AppearanceOption.entries,
                selected = appearance,
                label = { stringResource(it.titleRes()) },
                onSelect = preferences::setAppearance,
                modifier = Modifier.widthIn(max = 210.dp),
            )
        }
        SettingsDivider()
        SettingsRow(title = stringResource(R.string.settings_font_size)) {
            MenuPicker(
                options = FontSizeOption.entries,
                selected = fontSize,
                label = { stringResource(it.titleRes()) },
                onSelect = preferences::setFontSize,
            )
        }
        SettingsDivider()
        SettingsRow(title = stringResource(R.string.settings_language)) {
            MenuPicker(
                options = AppLanguage.entries,
                selected = language,
                label = {
                    when (it) {
                        AppLanguage.System -> stringResource(R.string.settings_language_system)
                        // Each language in its own script, so it is recognisable from any other.
                        AppLanguage.Chinese -> "简体中文"
                        AppLanguage.English -> "English"
                    }
                },
                onSelect = { if (it != language) preferences.setLanguage(it) },
            )
        }
    }
}

private fun AppearanceOption.titleRes() = when (this) {
    AppearanceOption.System -> R.string.appearance_system
    AppearanceOption.Light -> R.string.appearance_light
    AppearanceOption.Dark -> R.string.appearance_dark
}

private fun FontSizeOption.titleRes() = when (this) {
    FontSizeOption.System -> R.string.font_size_automatic
    FontSizeOption.Small -> R.string.font_size_small
    FontSizeOption.Medium -> R.string.font_size_medium
    FontSizeOption.Large -> R.string.font_size_large
    FontSizeOption.XLarge -> R.string.font_size_xlarge
}

/** UIKit-style segmented control: a gray track with the chosen segment raised. */
@Composable
private fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = palette
    Row(
        modifier
            .height(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(p.tertiaryFill)
            .padding(2.dp),
    ) {
        options.forEach { option ->
            val chosen = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(
                        if (chosen) {
                            Modifier
                                .shadow(2.dp, RoundedCornerShape(7.dp))
                                .background(if (p.dark) Color(0xFF636366) else Color.White, RoundedCornerShape(7.dp))
                        } else {
                            Modifier
                        },
                    )
                    .plainClickable { onSelect(option) }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = AppType.footnote.copy(fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Medium),
                    color = p.foreground,
                    maxLines = 1,
                )
            }
        }
    }
}

/** iOS `Menu` with the current value and an up/down chevron; the chosen item carries a check. */
@Composable
internal fun <T> MenuPicker(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    val p = palette
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.heightIn(min = 44.dp).plainClickable { open = true },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(label(selected), style = AppType.body, color = p.secondary, maxLines = 1)
            Icon(Icons.Filled.UnfoldMore, null, tint = p.tertiary, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = p.surface) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option), style = AppType.body, color = p.foreground) },
                    leadingIcon = {
                        Box(Modifier.size(18.dp)) {
                            if (option == selected) Icon(Icons.Filled.Check, null, tint = p.foreground, modifier = Modifier.size(18.dp))
                        }
                    },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun StatusText(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Text(text, style = AppType.subheadline.copy(fontWeight = FontWeight.Medium), color = color)
    }
}

@Composable
internal fun Chevron() {
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = palette.tertiary, modifier = Modifier.size(18.dp))
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
