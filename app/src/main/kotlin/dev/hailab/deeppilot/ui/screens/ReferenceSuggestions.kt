package dev.hailab.deeppilot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hailab.deeppilot.R
import dev.hailab.deeppilot.core.protocol.FileReference
import dev.hailab.deeppilot.core.protocol.ReferenceCandidates
import dev.hailab.deeppilot.core.protocol.SessionReference
import dev.hailab.deeppilot.core.references.ReferenceQuery
import dev.hailab.deeppilot.ui.AppType
import dev.hailab.deeppilot.ui.palette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Files and other sessions for the `@` word under the caret. Every keystroke is
 * a new host query, so it waits for a pause; the previous rows stay up meanwhile.
 */
@Composable
fun ReferenceSuggestions(
    query: ReferenceQuery?,
    scopeKey: String,
    load: suspend (String) -> ReferenceCandidates,
    onPickFile: (FileReference) -> Unit,
    onPickSession: (SessionReference) -> Unit,
    onDismiss: () -> Unit,
) {
    var candidates by remember(scopeKey) { mutableStateOf<ReferenceCandidates?>(null) }
    var loading by remember(scopeKey) { mutableStateOf(false) }
    var failed by remember(scopeKey) { mutableStateOf(false) }
    val text = query?.query
    LaunchedEffect(scopeKey, text) {
        if (text == null) {
            candidates = null
            return@LaunchedEffect
        }
        loading = true
        failed = false
        try {
            delay(DEBOUNCE_MILLIS)
            candidates = load(text)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
            candidates = null
        } finally {
            loading = false
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(candidates) { listState.scrollToItem(0) }
    if (query == null) return

    val p = palette
    val rows = candidates?.let { found ->
        found.files.items.map { ReferenceRow.File(it) } + found.sessions.items.map { ReferenceRow.Session(it) }
    }.orEmpty()
    val textHeight = with(LocalDensity.current) { AppType.body.lineHeight.toDp() + AppType.caption.lineHeight.toDp() }
    val rowHeight = maxOf(48.dp, textHeight + 16.dp)
    Column(Modifier.fillMaxWidth().background(p.surface, RoundedCornerShape(16.dp)).padding(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.composer_references_title), style = AppType.body, color = p.foreground, modifier = Modifier.padding(8.dp))
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.composer_skills_close), style = AppType.body) }
        }
        when {
            rows.isNotEmpty() -> LazyColumn(Modifier.fillMaxWidth().height(minOf(rowHeight * minOf(rows.size, 3), 220.dp)), state = listState) {
                items(rows, key = { it.id }) { row ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                when (row) {
                                    is ReferenceRow.File -> onPickFile(row.file)
                                    is ReferenceRow.Session -> onPickSession(row.session)
                                }
                            }
                            .height(rowHeight)
                            .padding(8.dp),
                    ) {
                        // A long path matters most at its end, so the start is what gets cut.
                        Text(row.title, style = AppType.body, color = p.foreground, maxLines = 1, overflow = TextOverflow.StartEllipsis)
                        row.caption()?.let { Text(it, style = AppType.caption, color = p.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
            failed -> Text(stringResource(R.string.composer_references_failed), style = AppType.body, color = p.foreground, modifier = Modifier.padding(8.dp))
            loading || candidates == null -> Text(stringResource(R.string.composer_references_loading), style = AppType.body, color = p.foreground, modifier = Modifier.padding(8.dp))
            else -> Text(stringResource(R.string.composer_references_empty), style = AppType.body, color = p.foreground, modifier = Modifier.padding(8.dp))
        }
    }
}

private sealed interface ReferenceRow {
    val id: String
    val title: String

    data class File(val file: FileReference) : ReferenceRow {
        override val id get() = "file:" + file.path
        override val title get() = "@" + file.path
    }

    data class Session(val session: SessionReference) : ReferenceRow {
        override val id get() = "session:" + session.sessionId
        override val title get() = "@" + session.label
    }
}

@Composable
private fun ReferenceRow.caption(): String? = when (this) {
    is ReferenceRow.File -> if (file.kind == "directory") stringResource(R.string.composer_references_directory) else null
    is ReferenceRow.Session -> stringResource(
        if (session.sameWorkspace) R.string.composer_references_same_workspace else R.string.composer_references_session,
    )
}

private const val DEBOUNCE_MILLIS = 180L
