package dev.hailab.deeppilot.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.hailab.deeppilot.bridge.BridgeRepository
import dev.hailab.deeppilot.ui.screens.PairScreen
import dev.hailab.deeppilot.ui.screens.SessionScreen
import dev.hailab.deeppilot.ui.screens.SessionsScreen
import dev.hailab.deeppilot.ui.screens.SettingsScreen

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

    val nav = rememberNavController()
    val target = sessionToOpen.value
    LaunchedEffect(target) {
        if (target != null) {
            nav.navigate(sessionRoute(target)) { launchSingleTop = true }
            sessionToOpen.value = null
        }
    }
    NavHost(navController = nav, startDestination = "sessions") {
        composable("sessions") {
            SessionsScreen(
                repository = repository,
                onOpen = { nav.navigate(sessionRoute(it)) },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("session/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            SessionScreen(repository = repository, sessionId = id, onBack = { nav.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(
                repository = repository,
                onBack = { nav.popBackStack() },
                onPairAnother = { pairLink.value = "" },
            )
        }
    }
}

private fun sessionRoute(id: String) = "session/${Uri.encode(id)}"
