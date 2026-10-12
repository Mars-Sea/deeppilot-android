package dev.hailab.deeppilot.ui.markdown

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.hailab.deeppilot.R
import dev.hailab.deeppilot.core.markdown.MarkdownMath
import dev.hailab.deeppilot.ui.palette
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
    val p = palette
    val textZoom = (LocalDensity.current.fontScale * 100).toInt()
    val theme = mapOf(
        "text" to p.foreground,
        "secondary" to p.secondary,
        "link" to p.accent,
        "surface" to p.surface,
        "muted-surface" to p.mutedSurface,
        "hairline" to p.hairline,
        "edge" to p.foreground.copy(alpha = 0.07f),
        "quote-bar" to p.foreground.copy(alpha = 0.22f),
    )
    val labels = mapOf(
        "code" to stringResource(R.string.markdown_code),
        "copy" to stringResource(R.string.markdown_code_copy),
        "copied" to stringResource(R.string.markdown_code_copied),
        "table" to stringResource(R.string.markdown_table),
        "copyTable" to stringResource(R.string.markdown_table_copy),
    )
    val payload = remember(markdown, theme, labels) {
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
            put("labels", buildJsonObject { labels.forEach { (name, text) -> put(name, text) } })
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

/** CSS rgba(), so translucent tokens (hairline, muted surface) keep their alpha. */
private fun Color.css(): String {
    val argb = toArgb()
    return "rgba(${(argb shr 16) and 0xFF}, ${(argb shr 8) and 0xFF}, ${argb and 0xFF}, ${"%.3f".format(java.util.Locale.US, alpha)})"
}
