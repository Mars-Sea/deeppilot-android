package dev.hailab.deeppilot.ui.screens

import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import dev.hailab.deeppilot.R
import dev.hailab.deeppilot.bridge.BridgeRepository
import dev.hailab.deeppilot.core.pairing.HostEndpoints
import dev.hailab.deeppilot.core.pairing.PairingLink
import dev.hailab.deeppilot.ui.attempt
import dev.hailab.deeppilot.ui.errorText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairScreen(
    repository: BridgeRepository,
    initialLink: String?,
    onCancel: (() -> Unit)?,
    onPaired: () -> Unit,
) {
    var text by rememberSaveable(initialLink) { mutableStateOf(initialLink.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun submit(value: String) {
        if (busy || value.isBlank()) return
        scope.launch {
            busy = true
            error = attempt { repository.pair(PairingLink.parse(value.trim())) }
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
    val preview = remember(text) {
        runCatching { PairingLink.parse(text.trim()) }.getOrNull()?.let { HostEndpoints.of(it.host)?.display }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pair_title)) },
                navigationIcon = {
                    if (onCancel != null) {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.pair_intro), style = MaterialTheme.typography.bodyLarge)
            Button(
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
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text(stringResource(R.string.pair_scan))
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.pair_paste_hint), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    error = null
                },
                label = { Text(stringResource(R.string.pair_link_label)) },
                minLines = 3,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                onClick = {
                    val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                    val pasted = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                    if (!pasted.isNullOrBlank()) {
                        text = pasted.trim()
                        error = null
                    }
                },
                enabled = !busy,
            ) { Text(stringResource(R.string.pair_paste)) }
            if (preview != null) {
                Text(stringResource(R.string.pair_host_preview, preview), style = MaterialTheme.typography.bodyMedium)
            }
            error?.let {
                Text(errorText(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(
                onClick = { submit(text) },
                enabled = !busy && text.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (busy) CircularProgressIndicator(modifier = Modifier.height(18.dp)) else Text(stringResource(R.string.pair_submit))
            }
        }
    }
}
