package dev.hailab.deeppilot.ui.screens

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hailab.deeppilot.R
import dev.hailab.deeppilot.bridge.BridgeRepository
import dev.hailab.deeppilot.core.bridge.BridgeException
import dev.hailab.deeppilot.core.protocol.DirectoryListing
import dev.hailab.deeppilot.core.protocol.Workspace
import dev.hailab.deeppilot.ui.AppRadius
import dev.hailab.deeppilot.ui.AppType
import dev.hailab.deeppilot.ui.attempt
import dev.hailab.deeppilot.ui.components.NavBarContent
import dev.hailab.deeppilot.ui.components.ProminentButton
import dev.hailab.deeppilot.ui.components.SmallSpinner
import dev.hailab.deeppilot.ui.components.plainClickable
import dev.hailab.deeppilot.ui.errorMessage
import dev.hailab.deeppilot.ui.palette
import kotlinx.coroutines.launch

/**
 * iOS `NewSessionProjectPicker`: choose the project for a new session, or add
 * a folder on the computer as a new project (browsed from the phone, or picked
 * on the computer when the host cannot list folders remotely).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSessionPicker(repository: BridgeRepository, onDismiss: () -> Unit, onCreated: (String) -> Unit) {
    val p = palette
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var browsing by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }

    fun create(workspace: Workspace) {
        if (creating) return
        creating = true
        createError = null
        scope.launch {
            var id: String? = null
            val error = attempt { id = repository.createSession(workspace.id) }
            creating = false
            val created = id
            when {
                error != null -> {
                    browsing = false
                    createError = context.errorMessage(error)
                }
                created != null -> onCreated(created)
                else -> onDismiss()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = p.groupedBackground,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.94f)) {
            NavBarContent(
                leading = {
                    Text(
                        stringResource(if (browsing) R.string.action_back else R.string.action_cancel),
                        style = AppType.body,
                        color = p.accent,
                        modifier = Modifier.padding(horizontal = 10.dp).plainClickable { if (browsing) browsing = false else onDismiss() },
                    )
                },
                principal = {
                    Text(
                        stringResource(if (browsing) R.string.picker_folder_title else R.string.picker_title),
                        style = AppType.headline,
                        color = p.foreground,
                        maxLines = 1,
                    )
                },
                background = p.groupedBackground,
            )
            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (browsing) {
                    HostDirectoryBrowser(repository, onWorkspaceReady = ::create)
                } else {
                    ProjectList(repository, creating, createError, onAddProject = { browsing = true }, onSelect = ::create)
                }
                androidx.compose.animation.AnimatedVisibility(creating, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().background(p.foreground.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                        Row(
                            Modifier.height(48.dp).clip(CircleShape).background(p.surface).padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            SmallSpinner()
                            Text(stringResource(R.string.picker_creating), style = AppType.subheadline.copy(fontWeight = FontWeight.Medium), color = p.foreground)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectList(
    repository: BridgeRepository,
    creating: Boolean,
    createError: String?,
    onAddProject: () -> Unit,
    onSelect: (Workspace) -> Unit,
) {
    val p = palette
    val context = LocalContext.current
    val capabilities by repository.capabilities.collectAsStateWithLifecycle()
    val sessions by repository.sessions.collectAsStateWithLifecycle()
    val supported = capabilities?.projectSelection == true
    var workspaces by remember { mutableStateOf<List<Workspace>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val unsupported = stringResource(R.string.picker_unsupported)

    LaunchedEffect(supported) {
        if (!supported) {
            loadError = unsupported
            return@LaunchedEffect
        }
        var loaded: List<Workspace> = emptyList()
        val error = attempt { loaded = repository.loadWorkspaces() }
        if (error != null) loadError = context.errorMessage(error) else {
            loadError = null
            workspaces = loaded
        }
    }
    val ordered = remember(workspaces, sessions) {
        workspaces.orEmpty().sortedWith(
            compareByDescending<Workspace> { workspace ->
                sessions.filter { it.workspaceId == workspace.id || it.id in workspace.sessionIds }.maxOfOrNull { it.lastActivityTs } ?: 0
            }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.picker_prompt), style = AppType.subheadline, color = p.muted, modifier = Modifier.padding(bottom = 2.dp))

        PickerRow(enabled = !creating && supported, onClick = onAddProject) {
            Icon(Icons.Filled.CreateNewFolder, null, tint = p.foreground, modifier = Modifier.width(28.dp).size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.picker_add_project), style = AppType.body.copy(fontWeight = FontWeight.SemiBold), color = p.foreground)
                Text(stringResource(R.string.picker_add_project_subtitle), style = AppType.caption, color = p.muted)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = p.tertiary, modifier = Modifier.size(16.dp))
        }

        createError?.let { Notice(it, Icons.Outlined.WarningAmber) }
        val list = workspaces
        when {
            loadError != null -> Notice(loadError.orEmpty(), Icons.Outlined.WarningAmber)
            list == null -> Row(
                Modifier.fillMaxWidth().heightIn(min = 76.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            ) {
                SmallSpinner()
                Text(stringResource(R.string.picker_loading), style = AppType.body, color = p.muted)
            }
            ordered.isEmpty() -> Notice(stringResource(R.string.picker_empty), Icons.Outlined.Folder)
            else -> {
                Text(
                    stringResource(R.string.picker_existing),
                    style = AppType.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
                    color = p.muted,
                    modifier = Modifier.padding(top = 8.dp),
                )
                ordered.forEach { workspace ->
                    PickerRow(enabled = !creating, onClick = { onSelect(workspace) }) {
                        Icon(Icons.Filled.Folder, null, tint = p.foreground, modifier = Modifier.width(28.dp).size(20.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                workspace.title.ifBlank { workspace.path },
                                style = AppType.body.copy(fontWeight = FontWeight.Medium),
                                color = p.foreground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(workspace.path, style = AppType.caption, color = p.muted, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(workspace.sessionIds.size.toString(), style = AppType.caption, color = p.tertiary)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = p.tertiary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

/** A card row on the grouped background: radius 12, hairline border, at least 62pt tall. */
@Composable
private fun PickerRow(enabled: Boolean, onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val p = palette
    val shape = RoundedCornerShape(AppRadius.medium)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(p.background)
            .border(1.dp, p.hairline, shape)
            .plainClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun Notice(text: String, icon: ImageVector) {
    val p = palette
    Column(
        Modifier.fillMaxWidth().heightIn(min = 110.dp).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, null, tint = p.muted, modifier = Modifier.size(26.dp))
        Text(text, style = AppType.subheadline, color = p.muted, textAlign = TextAlign.Center)
    }
}

/** iOS `HostDirectoryBrowser`: breadcrumbs, subfolders, and "choose this folder". */
@Composable
private fun HostDirectoryBrowser(repository: BridgeRepository, onWorkspaceReady: (Workspace) -> Unit) {
    val p = palette
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var listing by remember { mutableStateOf<DirectoryListing?>(null) }
    var loading by remember { mutableStateOf(false) }
    var adopting by remember { mutableStateOf(false) }
    var waitingOnComputer by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    fun load(path: String?) {
        if (loading) return
        loading = true
        scope.launch {
            var result: DirectoryListing? = null
            val error = attempt { result = repository.listDirectory(path) }
            listing = result
            errorText = error?.let { if (it is BridgeException) it.message ?: context.errorMessage(it) else context.errorMessage(it) }
            loading = false
        }
    }

    fun adopt(path: String) {
        if (adopting) return
        adopting = true
        scope.launch {
            var workspace: Workspace? = null
            val error = attempt { workspace = repository.createWorkspace(path) }
            adopting = false
            val ready = workspace
            if (error != null) errorText = context.errorMessage(error) else if (ready != null) onWorkspaceReady(ready)
        }
    }

    fun pickOnComputer() {
        if (waitingOnComputer) return
        waitingOnComputer = true
        scope.launch {
            var picked: String? = null
            val error = attempt { picked = repository.pickDirectoryOnComputer() }
            waitingOnComputer = false
            if (error != null) errorText = context.errorMessage(error) else picked?.let(::adopt)
        }
    }

    LaunchedEffect(Unit) { load(null) }

    val current = listing
    Column(Modifier.fillMaxSize()) {
        when {
            current != null -> {
                Breadcrumbs(current, ::load)
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(p.hairline))
                val visible = current.entries.filterNot { it.hidden }
                LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    items(visible, key = { it.path }) { entry ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .background(p.background)
                                .plainClickable { load(entry.path) }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Outlined.Folder, null, tint = p.foreground, modifier = Modifier.width(28.dp).size(20.dp))
                            Text(entry.name, style = AppType.body, color = p.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = p.tertiary, modifier = Modifier.size(16.dp))
                        }
                    }
                    if (visible.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.picker_no_subfolders), style = AppType.subheadline, color = p.muted)
                            }
                        }
                    }
                    if (current.truncated) {
                        item {
                            Text(stringResource(R.string.picker_list_truncated), style = AppType.caption, color = p.muted, modifier = Modifier.padding(16.dp))
                        }
                    }
                }
                errorText?.let {
                    Text(it, style = AppType.footnote, color = p.danger, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                }
                Box(Modifier.fillMaxWidth().background(p.surface.copy(alpha = 0.92f)).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    ProminentButton(
                        text = stringResource(if (adopting) R.string.picker_adding_folder else R.string.picker_choose_current),
                        onClick = { adopt(current.path) },
                        enabled = !adopting && !loading,
                        busy = adopting,
                        radius = AppRadius.medium,
                        height = 48.dp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            loading -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            ) {
                SmallSpinner(size = 20.dp)
                Text(stringResource(R.string.picker_loading_folder), style = AppType.subheadline, color = p.muted)
            }
            else -> BrowseUnavailable(
                message = unavailableMessage(errorText),
                waiting = waitingOnComputer,
                enabled = !waitingOnComputer && !adopting,
                onPickOnComputer = ::pickOnComputer,
                onRetry = { load(null) },
            )
        }
    }
}

@Composable
private fun unavailableMessage(errorText: String?): String = when {
    errorText == null -> stringResource(R.string.picker_browse_unavailable_default)
    errorText.contains("needs the browse capability") && errorText.contains("native") ->
        stringResource(R.string.picker_browse_unavailable_native)
    else -> errorText
}

@Composable
private fun Breadcrumbs(listing: DirectoryListing, onOpen: (String) -> Unit) {
    val p = palette
    LazyRow(
        Modifier.fillMaxWidth().height(46.dp).background(p.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        itemsIndexed(listing.crumbs, key = { _, crumb -> crumb.path }) { index, crumb ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (index > 0) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = p.tertiary, modifier = Modifier.size(12.dp))
                Box(Modifier.heightIn(min = 44.dp).plainClickable { onOpen(crumb.path) }, contentAlignment = Alignment.Center) {
                    Text(
                        if (crumb.path == listing.home) stringResource(R.string.picker_home) else crumb.name,
                        style = AppType.subheadline.copy(fontWeight = if (crumb.path == listing.path) FontWeight.SemiBold else FontWeight.Normal),
                        color = p.foreground,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.BrowseUnavailable(
    message: String,
    waiting: Boolean,
    enabled: Boolean,
    onPickOnComputer: () -> Unit,
    onRetry: () -> Unit,
) {
    val p = palette
    Column(
        Modifier.fillMaxWidth().weight(1f).padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Icon(Icons.Outlined.Devices, null, tint = p.foreground, modifier = Modifier.size(36.dp))
        Text(stringResource(R.string.picker_browse_unavailable_title), style = AppType.headline, color = p.foreground, textAlign = TextAlign.Center)
        Text(message, style = AppType.subheadline, color = p.muted, textAlign = TextAlign.Center)
        ProminentButton(
            text = stringResource(if (waiting) R.string.picker_pick_on_computer_waiting else R.string.picker_pick_on_computer),
            onClick = onPickOnComputer,
            enabled = enabled,
            busy = false,
            height = 44.dp,
            radius = AppRadius.medium,
            modifier = Modifier.widthIn(min = 210.dp),
        )
        Text(
            stringResource(R.string.picker_retry_browsing),
            style = AppType.subheadline,
            color = p.accent,
            modifier = Modifier.heightIn(min = 44.dp).padding(vertical = 12.dp).plainClickable(onClick = onRetry),
        )
    }
}

