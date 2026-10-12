package dev.hailab.deeppilot.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.hailab.deeppilot.R
import dev.hailab.deeppilot.bridge.BridgeRepository
import dev.hailab.deeppilot.core.attachments.PromptAttachment
import dev.hailab.deeppilot.core.protocol.MessageAttachment
import dev.hailab.deeppilot.ui.AppRadius
import dev.hailab.deeppilot.ui.AppType
import dev.hailab.deeppilot.ui.components.SmallSpinner
import dev.hailab.deeppilot.ui.components.plainClickable
import dev.hailab.deeppilot.ui.palette

/** How a prompt attachment looks in the optimistic "sending" bubble. */
fun PromptAttachment.asMessageAttachment() = MessageAttachment(
    kind = if (kind == PromptAttachment.Kind.Image) "image" else "document",
    name = name,
    mediaType = mediaType,
    truncated = truncated.takeIf { it },
)

/**
 * iOS `AttachmentThumbView`: one conversation image, loaded through the
 * memory → disk → bridge cache, shown whole at its own aspect ratio. While it
 * loads, a gray placeholder reserves the projected size. Tap to view full screen.
 */
@Composable
fun AttachmentThumb(info: MessageAttachment, sessionId: String, repository: BridgeRepository) {
    val p = palette
    val attachmentId = info.attachmentId.orEmpty()
    var image by remember(sessionId, attachmentId) { mutableStateOf<Bitmap?>(null) }
    var viewing by remember { mutableStateOf(false) }
    LaunchedEffect(sessionId, attachmentId) {
        if (image == null) image = repository.attachmentImage(sessionId, attachmentId)
    }
    val label = info.name?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.a11y_session_image)
    val shape = RoundedCornerShape(AppRadius.large)
    val loaded = image
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(0.7.dp, p.foreground.copy(alpha = 0.06f), shape)
            .semantics { contentDescription = label }
            .plainClickable(enabled = loaded != null) { viewing = true },
        contentAlignment = Alignment.Center,
    ) {
        if (loaded != null) {
            Image(
                loaded.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().aspectRatio(loaded.width.toFloat() / loaded.height.coerceAtLeast(1)),
            )
        } else {
            val w = info.width
            val h = info.height
            val placeholder = if (w != null && h != null && w > 0 && h > 0) {
                Modifier.fillMaxWidth().heightIn(min = 110.dp, max = 520.dp).aspectRatio((w / h).toFloat().coerceIn(0.2f, 5f))
            } else {
                Modifier.fillMaxWidth().height(200.dp)
            }
            Box(placeholder.background(p.tertiaryFill), contentAlignment = Alignment.Center) { SmallSpinner() }
        }
    }
    if (viewing && loaded != null) AttachmentImageViewer(loaded) { viewing = false }
}

/** Black full-screen viewer: pinch and double-tap zoom, pan while zoomed, one Done button. */
@Composable
private fun AttachmentImageViewer(image: Bitmap, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val shownScale by animateFloatAsState(scale, label = "zoom")
    fun clamp(value: Offset): Offset {
        val limit = (scale - 1f) * 600f
        if (limit <= 0f) return Offset.Zero
        return Offset(value.x.coerceIn(-limit, limit), value.y.coerceIn(-limit, limit))
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Image(
                image.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = clamp(offset + pan)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                        })
                    }
                    .graphicsLayer {
                        scaleX = shownScale
                        scaleY = shownScale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
            Text(
                stringResource(R.string.common_done),
                style = AppType.subheadline.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f))
                    .plainClickable(onClick = onDismiss)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}
