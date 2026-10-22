package dev.hailab.deeppilot.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.hailab.deeppilot.bridge.BridgeRepository
import dev.hailab.deeppilot.ui.components.plainClickable
import dev.hailab.deeppilot.ui.palette
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The compact iOS shell: a leading session drawer (82% of the width, at most
 * 360) that pushes the page over, around a stack of home → session/settings.
 */
@Composable
fun MainShell(
    repository: BridgeRepository,
    sessionToOpen: MutableState<String?>,
    onAddHost: () -> Unit,
    onEditHost: (String) -> Unit,
) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val onHome = route == null || route == HOME
    val openSessionId = if (route == SESSION) entry?.arguments?.getString("id") else null
    val scope = rememberCoroutineScope()
    var creatingPicker by remember { mutableStateOf(false) }

    fun openSession(id: String) {
        nav.navigate("session/${Uri.encode(id)}") {
            popUpTo(HOME)
            launchSingleTop = true
        }
    }

    // Another host's conversations are not this host's: drop any open session screen.
    val activeHost by repository.host.collectAsStateWithLifecycle()
    var shownHost by remember { mutableStateOf(activeHost?.profileId) }
    LaunchedEffect(activeHost?.profileId) {
        val id = activeHost?.profileId
        if (id == shownHost) return@LaunchedEffect
        shownHost = id
        if (nav.currentBackStackEntry?.destination?.route == SETTINGS) {
            nav.navigate(SETTINGS) { popUpTo(HOME) }
        } else {
            nav.popBackStack(HOME, inclusive = false)
        }
    }

    val target = sessionToOpen.value
    LaunchedEffect(target) {
        if (target != null) {
            openSession(target)
            sessionToOpen.value = null
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(palette.background)) {
        val drawerWidth = min(maxWidth * 0.82f, 360.dp)
        val widthPx = with(LocalDensity.current) { drawerWidth.toPx() }
        var progress by remember { mutableFloatStateOf(0f) }
        var settling by remember { mutableStateOf<Job?>(null) }

        fun settle(open: Boolean) {
            settling?.cancel()
            settling = scope.launch {
                animate(progress, if (open) 1f else 0f, animationSpec = tween(220)) { value, _ -> progress = value }
            }
        }

        val dragState = rememberDraggableState { delta ->
            settling?.cancel()
            progress = (progress + delta / widthPx).coerceIn(0f, 1f)
        }
        val drawerDrag = Modifier.draggable(
            state = dragState,
            orientation = Orientation.Horizontal,
            onDragStopped = { velocity -> settle(velocity > 600f || (velocity > -600f && progress > 0.5f)) },
        )

        Box(
            Modifier
                .fillMaxSize()
                .offset { IntOffset((widthPx * progress).roundToInt(), 0) }
                .then(if (onHome) drawerDrag else Modifier),
        ) {
            NavHost(navController = nav, startDestination = HOME) {
                composable(HOME) {
                    WorkbenchHome(
                        repository = repository,
                        onOpenDrawer = { settle(true) },
                        onCreateSession = { creatingPicker = true },
                        onOpenSession = ::openSession,
                    )
                }
                composable(SESSION) { backStack ->
                    val id = backStack.arguments?.getString("id").orEmpty()
                    SessionScreen(
                        repository = repository,
                        sessionId = id,
                        onBack = { nav.popBackStack() },
                        onNewSession = { creatingPicker = true },
                        onOpenSession = ::openSession,
                    )
                }
                composable(SETTINGS) {
                    SettingsScreen(
                        repository = repository,
                        onBack = { nav.popBackStack() },
                        onAddHost = onAddHost,
                        onEditHost = onEditHost,
                        onOpenIcons = { nav.navigate(APP_ICON) { launchSingleTop = true } },
                        onOpenArchived = { nav.navigate(ARCHIVED) { launchSingleTop = true } },
                    )
                }
                composable(APP_ICON) { AppIconScreen(onBack = { nav.popBackStack() }) }
                composable(ARCHIVED) { ArchivedSessionsScreen(repository = repository, onBack = { nav.popBackStack() }) }
            }
            if (progress > 0.001f) {
                Box(Modifier.fillMaxSize().then(drawerDrag).plainClickable { settle(false) })
            }
        }

        Box(
            Modifier
                .width(drawerWidth)
                .fillMaxHeight()
                .offset { IntOffset((-widthPx * (1f - progress)).roundToInt(), 0) }
                .then(drawerDrag),
        ) {
            if (progress > 0.001f) {
                SessionDrawer(
                    repository = repository,
                    selectedSessionId = openSessionId,
                    onClose = { settle(false) },
                    onOpenSettings = {
                        settle(false)
                        nav.navigate(SETTINGS) { launchSingleTop = true }
                    },
                    onCreateSession = { creatingPicker = true },
                    onOpenSession = { id ->
                        settle(false)
                        openSession(id)
                    },
                )
            }
        }

        BackHandler(enabled = progress > 0.5f) { settle(false) }

        if (creatingPicker) {
            NewSessionPicker(
                repository = repository,
                onDismiss = { creatingPicker = false },
                onCreated = { id ->
                    creatingPicker = false
                    settle(false)
                    openSession(id)
                },
            )
        }
    }
}

private const val HOME = "home"
private const val SESSION = "session/{id}"
private const val SETTINGS = "settings"
private const val APP_ICON = "settings/icon"
private const val ARCHIVED = "settings/archived"
