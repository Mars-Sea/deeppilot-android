package dev.hailab.deeppilot.ui.screens

import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import dev.hailab.deeppilot.R
import dev.hailab.deeppilot.bridge.BridgeRepository
import dev.hailab.deeppilot.core.hosts.HostProfile
import dev.hailab.deeppilot.core.pairing.HostEndpoints
import dev.hailab.deeppilot.core.pairing.PairingLink
import dev.hailab.deeppilot.ui.AppLayout
import dev.hailab.deeppilot.ui.AppRadius
import dev.hailab.deeppilot.ui.AppSpacing
import dev.hailab.deeppilot.ui.AppType
import dev.hailab.deeppilot.ui.attempt
import dev.hailab.deeppilot.ui.components.AppIcon
import dev.hailab.deeppilot.ui.components.NavBar
import dev.hailab.deeppilot.ui.components.NavTitle
import dev.hailab.deeppilot.ui.components.ProminentButton
import dev.hailab.deeppilot.ui.components.SmallSpinner
import dev.hailab.deeppilot.ui.components.plainClickable
import dev.hailab.deeppilot.ui.errorText
import dev.hailab.deeppilot.ui.palette
import kotlinx.coroutines.launch

/**
 * iOS `PairingFlowView`: hero on first launch, QR scan first, manual entry folded below.
 * From settings it adds a host, or edits [editing]: a new pairing replaces its
 * credentials, and with none only the name is saved.
 */
@Composable
fun PairScreen(
    repository: BridgeRepository,
    initialLink: String?,
    onCancel: (() -> Unit)?,
    onPaired: () -> Unit,
    editing: HostProfile? = null,
) {
    val p = palette
    var text by rememberSaveable(initialLink) { mutableStateOf(initialLink.orEmpty()) }
    var manualOpen by rememberSaveable { mutableStateOf(!initialLink.isNullOrEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val firstLaunch = onCancel == null
    var name by rememberSaveable(editing?.id) { mutableStateOf(editing?.name.orEmpty()) }

    fun hostOf(value: String) = runCatching { PairingLink.parse(value.trim()) }.getOrNull()
        ?.let { HostEndpoints.of(it.host)?.display ?: it.host }

    var connectingTo by remember { mutableStateOf<String?>(null) }
    fun submit(value: String) {
        if (busy || value.isBlank()) return
        scope.launch {
            busy = true
            error = null
            connectingTo = hostOf(value)
            error = attempt { repository.pair(PairingLink.parse(value.trim()), name, editing?.id) }
            busy = false
            if (error == null) onPaired()
        }
    }

    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let {
            text = it
            submit(it)
        }
    }
    val scanPrompt = stringResource(R.string.pair_scan_prompt)

    Column(Modifier.fillMaxSize().background(p.background)) {
        if (!firstLaunch) {
            NavBar(
                leading = {
                    Text(
                        stringResource(R.string.action_cancel),
                        style = AppType.body,
                        color = p.foreground,
                        modifier = Modifier.padding(horizontal = 10.dp).plainClickable { onCancel?.invoke() },
                    )
                },
                principal = {
                    NavTitle(stringResource(if (editing != null) R.string.settings_host_edit else R.string.settings_host_add))
                },
                trailing = {
                    if (editing != null) {
                        Text(
                            stringResource(R.string.action_save),
                            style = AppType.body.copy(fontWeight = FontWeight.SemiBold),
                            color = if (busy) p.muted else p.foreground,
                            modifier = Modifier.padding(horizontal = 10.dp).plainClickable(enabled = !busy) {
                                if (text.isNotBlank()) {
                                    submit(text)
                                } else {
                                    repository.renameHost(editing.id, name)
                                    onPaired()
                                }
                            },
                        )
                    }
                },
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .then(if (firstLaunch) Modifier.statusBarsPadding() else Modifier)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.section)
                .padding(top = if (firstLaunch) 0.dp else AppSpacing.section, bottom = AppSpacing.large)
                .widthIn(max = 480.dp),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.large),
        ) {
            if (firstLaunch) {
                Column(
                    Modifier.padding(top = AppSpacing.hero),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.comfortable),
                ) {
                    AppIcon(size = 56.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                        Text(stringResource(R.string.pair_hero_title), style = AppType.title2.copy(fontWeight = FontWeight.Bold), color = p.foreground)
                        Text(stringResource(R.string.pair_hero_subtitle), style = AppType.subheadline, color = p.muted)
                    }
                }
                Spacer(Modifier.height(AppSpacing.section))
            }

            if (!firstLaunch) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(p.surface, RoundedCornerShape(AppRadius.large))
                        .padding(AppSpacing.regular),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.compact),
                ) {
                    val placeholder = stringResource(R.string.settings_host_name_placeholder)
                    Text(placeholder, style = AppType.caption.copy(fontWeight = FontWeight.SemiBold), color = p.muted)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 46.dp)
                            .background(p.mutedSurface, RoundedCornerShape(AppRadius.medium))
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (name.isEmpty()) Text(placeholder, style = AppType.body, color = p.muted)
                        BasicTextField(
                            value = name,
                            onValueChange = { name = it },
                            enabled = !busy,
                            singleLine = true,
                            textStyle = AppType.body.copy(color = p.foreground),
                            cursorBrush = SolidColor(p.accent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    editing?.let { profile ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(Icons.Filled.Computer, null, tint = p.muted, modifier = Modifier.size(13.dp))
                            Text(HostEndpoints.of(profile.host)?.display ?: profile.host, style = AppType.footnote, color = p.muted)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.regular)) {
                ProminentButton(
                    text = stringResource(R.string.pair_scan),
                    icon = Icons.Filled.QrCodeScanner,
                    onClick = {
                        scanner.launch(
                            ScanOptions()
                                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                .setBeepEnabled(false)
                                .setOrientationLocked(false)
                                .setPrompt(scanPrompt),
                        )
                    },
                    enabled = !busy,
                    radius = AppRadius.medium,
                    height = AppLayout.largeControlHeight,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Manual entry, folded by default.
                val turn by animateFloatAsState(if (manualOpen) 90f else 0f, label = "chevron")
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(p.surface, RoundedCornerShape(AppRadius.large))
                        .padding(horizontal = AppSpacing.regular),
                ) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp).plainClickable(enabled = !busy) { manualOpen = !manualOpen },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.pair_manual_open),
                            style = AppType.subheadline.copy(fontWeight = FontWeight.Medium),
                            color = p.foreground,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = p.muted, modifier = Modifier.size(18.dp).rotate(turn))
                    }
                    AnimatedVisibility(manualOpen) {
                        Column(Modifier.padding(bottom = AppSpacing.regular), verticalArrangement = Arrangement.spacedBy(AppSpacing.regular)) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Icon(Icons.Filled.ContentPaste, null, tint = p.muted, modifier = Modifier.size(12.dp))
                                    Text(
                                        stringResource(R.string.pair_link_label),
                                        style = AppType.caption.copy(fontWeight = FontWeight.SemiBold),
                                        color = p.muted,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Box(
                                        Modifier.heightIn(min = 44.dp).plainClickable(enabled = !busy) {
                                            val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                                            val pasted = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                                            if (!pasted.isNullOrBlank()) {
                                                text = pasted.trim()
                                                error = null
                                            }
                                        },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            stringResource(R.string.pair_paste),
                                            style = AppType.caption.copy(fontWeight = FontWeight.SemiBold),
                                            color = p.foreground,
                                            modifier = Modifier.padding(horizontal = 6.dp),
                                        )
                                    }
                                }
                                BasicTextField(
                                    value = text,
                                    onValueChange = {
                                        text = it
                                        error = null
                                    },
                                    enabled = !busy,
                                    textStyle = AppType.footnote.copy(fontFamily = FontFamily.Monospace, color = p.foreground),
                                    cursorBrush = SolidColor(p.accent),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(104.dp)
                                        .background(p.mutedSurface, RoundedCornerShape(AppRadius.medium))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                )
                                Text(stringResource(R.string.pair_manual_help), style = AppType.caption2, color = p.muted.copy(alpha = 0.85f))
                            }
                            ProminentButton(
                                text = stringResource(R.string.pair_submit),
                                onClick = { submit(text) },
                                enabled = text.isNotBlank() && !busy,
                                busy = busy,
                                radius = AppRadius.medium,
                                height = AppLayout.largeControlHeight,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                val target = connectingTo
                if (busy && target != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.small), verticalAlignment = Alignment.CenterVertically) {
                        SmallSpinner()
                        Text(stringResource(R.string.pair_connecting, target), style = AppType.footnote, color = p.muted)
                    }
                }
                error?.let {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(p.danger.copy(alpha = 0.08f), RoundedCornerShape(AppRadius.medium))
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Filled.Warning, null, tint = p.danger, modifier = Modifier.padding(top = 1.dp).size(15.dp))
                        Text(errorText(it), style = AppType.footnote, color = p.danger)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                Text(stringResource(R.string.pair_scan_hint), style = AppType.footnote, color = p.muted)
                if (editing != null) Text(stringResource(R.string.settings_host_edit_footer), style = AppType.footnote, color = p.muted)
            }
        }
    }
}
