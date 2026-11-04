package dev.hailab.deeppilot.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hailab.deeppilot.bridge.BridgeRepository
import dev.hailab.deeppilot.core.onboarding.OnboardingMode
import dev.hailab.deeppilot.graph
import dev.hailab.deeppilot.ui.onboarding.OnboardingScreen
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

    val onboarding = LocalContext.current.graph.onboarding
    // Whether the overlay is on screen is kept apart from "needs the tour": finishing writes the
    // completed state at once, but the overlay stays for the closing "all set" moment and fades out.
    var onboardingPresented by remember { mutableStateOf(false) }
    val needsOnboarding = onboarding.needsOnboarding(paired = true)
    LaunchedEffect(needsOnboarding) { if (needsOnboarding) onboardingPresented = true }

    Box(Modifier.fillMaxSize()) {
        // The shell keeps connecting underneath, but TalkBack must not read it while the tour is up.
        Box(Modifier.fillMaxSize().then(if (onboardingPresented) Modifier.clearAndSetSemantics {} else Modifier)) {
            MainShell(
                repository = repository,
                sessionToOpen = sessionToOpen,
                onAddHost = { pairLink.value = "" },
                onEditHost = { editingId = it },
            )
        }
        AnimatedVisibility(
            visible = onboardingPresented,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(450)),
        ) {
            OnboardingScreen(
                mode = OnboardingMode.FirstRun,
                onComplete = { onboarding.markCompleted() },
                onDismiss = { onboardingPresented = false },
            )
        }
    }
}
