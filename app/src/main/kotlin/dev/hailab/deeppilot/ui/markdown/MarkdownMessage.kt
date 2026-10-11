package dev.hailab.deeppilot.ui.markdown

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.hailab.deeppilot.core.markdown.MarkdownMath
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Last known heights, so a message scrolled back into view does not jump. */
private val heightCache = object : LinkedHashMap<Any, Int>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, Int>?) = size > 512
}

/**
 * Assistant text as Markdown with TeX formulas (WebView + bundled KaTeX).
 * [key] identifies the message for the height cache.
 */
@Composable
fun MarkdownMessage(
    markdown: String,
    key: Any,
    modifier: Modifier = Modifier,
    onHeightChanged: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val textZoom = (LocalDensity.current.fontScale * 100).toInt()
    val theme = mapOf(
        "text" to colors.onSurface,
        "muted" to colors.onSurfaceVariant,
        "link" to colors.primary,
        "code-bg" to colors.surfaceVariant,
        "border" to colors.outlineVariant,
    )
    val payload = remember(markdown, theme) {
        val prepared = MarkdownMath.prepare(markdown)
        buildJsonObject {
            put("markdown", prepared.markdown)
            put(
                "math",
                buildJsonArray {
                    prepared.math.forEach { segment ->
                        add(buildJsonObject {
                            put("tex", segment.tex)
                            put("display", segment.display)
                        })
                    }
                },
            )
            put("theme", buildJsonObject { theme.forEach { (name, color) -> put(name, JsonPrimitive(color.css())) } })
        }.toString()
    }
    var height by remember(key) { mutableIntStateOf(synchronized(heightCache) { heightCache[key] } ?: 0) }

    AndroidView(
        factory = { context -> MessageWebView(context) },
        update = { view ->
            view.settings.textZoom = textZoom
            view.onHeight = { reported ->
                if (reported > 0 && reported != height) {
                    height = reported
                    synchronized(heightCache) { heightCache[key] = reported }
                    onHeightChanged()
                }
            }
            view.show(payload)
        },
        modifier = modifier
            .fillMaxWidth()
            .height(if (height > 0) height.dp else 24.dp),
    )
}

private fun Color.css(): String = String.format("#%06X", toArgb() and 0xFFFFFF)
