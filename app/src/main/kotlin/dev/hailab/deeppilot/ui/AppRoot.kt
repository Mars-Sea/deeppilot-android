package dev.hailab.deeppilot.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    val roster by repository.roster.collectAsStateWithLifecycle()
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = editingId?.let { id -> roster.profiles.firstOrNull { it.id == id } }
    val pendingLink = pairLink.value
    if (host == null || pendingLink != null || editing != null) {
        PairScreen(
            repository = repository,
            initialLink = pendingLink,
            editing = editing,
            onCancel = if (host != null) ({
                pairLink.value = null
                editingId = null
            }) else null,
            onPaired = {
                pairLink.value = null
                editingId = null
                onPaired()
            },
        )
        return
    }
    MainShell(
        repository = repository,
        sessionToOpen = sessionToOpen,
        onAddHost = { pairLink.value = "" },
        onEditHost = { editingId = it },
    )
}
