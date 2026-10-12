package dev.hailab.deeppilot.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hailab.deeppilot.bridge.BridgeRepository
import dev.hailab.deeppilot.ui.screens.MainShell
import dev.hailab.deeppilot.ui.screens.PairScreen

@Composable
fun AppRoot(
    repository: BridgeRepository,
    pairLink: MutableState<String?>,
    sessionToOpen: MutableState<String?>,
    onPaired: () -> Unit,
) {
    val host by repository.host.collectAsStateWithLifecycle()
    val pendingLink = pairLink.value
    if (host == null || pendingLink != null) {
        PairScreen(
            repository = repository,
            initialLink = pendingLink,
            onCancel = if (host != null) ({ pairLink.value = null }) else null,
            onPaired = {
                pairLink.value = null
                onPaired()
            },
        )
        return
    }
    MainShell(
        repository = repository,
        sessionToOpen = sessionToOpen,
        onPairAnother = { pairLink.value = "" },
    )
}
