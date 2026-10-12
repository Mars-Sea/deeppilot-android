package dev.hailab.deeppilot.attachments

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.MimeTypeMap
import dev.hailab.deeppilot.core.attachments.PromptAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.coroutineContext

class AttachmentException(val reason: Reason, val detail: String? = null) : Exception(reason.name) {
    enum class Reason { ImageUnreadable, ImageOversized, DocumentUnsupported, DocumentUnreadable, DocumentOversized, ReadFailed, TooMany }
}

/**
 * Import-time work for prompt attachments, mirroring the iOS
 * `AttachmentIngestService`: images become ≤2048px JPEGs (quality 0.82, ≤6 MB),
 * text files and PDFs with a text layer become extracted text. Everything runs
 * off the main thread.
 */
class AttachmentIngest(private val context: Context) {
    private val resolver: ContentResolver get() = context.contentResolver

    /** A photo from the picker or camera. */
    suspend fun image(uri: Uri, name: String): PromptAttachment = withContext(Dispatchers.IO) {
        val size = querySize(uri)
        if (size != null && size > PromptAttachment.MAX_SOURCE_BYTES) throw AttachmentException(AttachmentException.Reason.ImageOversized)
        encodeImage(decode(uri), name)
    }

    /** Any file from the system document picker. */
    suspend fun file(uri: Uri): PromptAttachment = withContext(Dispatchers.IO) {
        val name = queryName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        val extension = name.substringAfterLast('.', "")
        val mediaType = resolver.getType(uri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
        val isImage = mediaType?.startsWith("image/") == true
        val size = querySize(uri)
        if (isImage) {
            if (size != null && size > PromptAttachment.MAX_SOURCE_BYTES) throw AttachmentException(AttachmentException.Reason.ImageOversized)
            return@withContext encodeImage(decode(uri), name.substringBeforeLast('.') + ".jpg")
        }
        val pdf = PromptAttachment.isPdf(mediaType, extension)
        if (!pdf && !PromptAttachment.isTextDocument(mediaType, extension)) {
            throw AttachmentException(AttachmentException.Reason.DocumentUnsupported)
        }
        if (size != null && size > PromptAttachment.MAX_SOURCE_BYTES) throw AttachmentException(AttachmentException.Reason.DocumentOversized)
        val text = if (pdf) pdfText(uri) else decodeText(readBounded(uri))
        PromptAttachment.document(name, if (pdf) "application/pdf" else mediaType, text.orEmpty(), UUID.randomUUID().toString())
            ?: throw AttachmentException(AttachmentException.Reason.DocumentUnreadable)
    }

    private fun decode(uri: Uri): Bitmap = try {
        // ImageDecoder applies EXIF rotation and lets us downsample while decoding.
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > PromptAttachment.MAX_IMAGE_DIMENSION) {
                val scale = PromptAttachment.MAX_IMAGE_DIMENSION.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1),
                )
            }
        }
    } catch (e: IOException) {
        throw AttachmentException(AttachmentException.Reason.ImageUnreadable)
    } catch (e: SecurityException) {
        throw AttachmentException(AttachmentException.Reason.ReadFailed, e.localizedMessage)
    }

    private suspend fun encodeImage(bitmap: Bitmap, name: String): PromptAttachment {
        coroutineContext.ensureActive()
        val out = ByteArrayOutputStream()
        // JPEG has no alpha: flatten transparent images onto white first.
        val opaque = if (bitmap.hasAlpha()) {
            Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also { flat ->
                android.graphics.Canvas(flat).apply {
                    drawColor(android.graphics.Color.WHITE)
                    drawBitmap(bitmap, 0f, 0f, null)
                }
            }
        } else {
            bitmap
        }
        if (!opaque.compress(Bitmap.CompressFormat.JPEG, PromptAttachment.JPEG_QUALITY, out)) {
            throw AttachmentException(AttachmentException.Reason.ImageUnreadable)
        }
        val bytes = out.toByteArray()
        if (bytes.size > PromptAttachment.MAX_JPEG_BYTES) throw AttachmentException(AttachmentException.Reason.ImageOversized)
        coroutineContext.ensureActive()
        return PromptAttachment(
            id = UUID.randomUUID().toString(),
            kind = PromptAttachment.Kind.Image,
            name = name,
            mediaType = "image/jpeg",
            payload = Base64.encodeToString(bytes, Base64.NO_WRAP),
        )
    }

    private fun readBounded(uri: Uri): ByteArray = try {
        resolver.openInputStream(uri)?.use { input ->
            val limit = PromptAttachment.MAX_SOURCE_BYTES + 1
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (out.size() < limit) {
                val read = input.read(buffer, 0, minOf(buffer.size, limit - out.size()))
                if (read < 0) break
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        } ?: throw AttachmentException(AttachmentException.Reason.ReadFailed)
    } catch (e: IOException) {
        throw AttachmentException(AttachmentException.Reason.ReadFailed, e.localizedMessage)
    }.also { if (it.size > PromptAttachment.MAX_SOURCE_BYTES) throw AttachmentException(AttachmentException.Reason.DocumentOversized) }

    private fun decodeText(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        // BOM-marked UTF-16, then strict UTF-8, then Latin-1 as the last resort (as on iOS).
        if (bytes.size >= 2 && (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() || bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())) {
            return String(bytes, Charsets.UTF_16)
        }
        val decoder = Charsets.UTF_8.newDecoder()
        return runCatching { decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString() }
            .getOrElse { String(bytes, Charsets.ISO_8859_1) }
    }

    /** The text layer of a PDF. Android exposes it from API 35; older phones cannot read it. */
    private fun pdfText(uri: Uri): String? {
        if (Build.VERSION.SDK_INT < 35) throw AttachmentException(AttachmentException.Reason.DocumentUnreadable)
        // PdfRenderer needs a seekable descriptor, so copy the bounded bytes to a cache file first.
        val temp = File.createTempFile("attachment", ".pdf", context.cacheDir)
        return try {
            temp.writeBytes(readBounded(uri))
            android.os.ParcelFileDescriptor.open(temp, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    buildString {
                        for (index in 0 until renderer.pageCount) {
                            if (length > PromptAttachment.MAX_DOCUMENT_CHARACTERS) break
                            renderer.openPage(index).use { page ->
                                page.textContents.forEach { append(it.text).append('\n') }
                            }
                        }
                    }
                }
            }
        } catch (e: AttachmentException) {
            throw e
        } catch (e: Exception) {
            throw AttachmentException(AttachmentException.Reason.DocumentUnreadable)
        } finally {
            temp.delete()
        }
    }

    private fun queryName(uri: Uri): String? = query(uri, OpenableColumns.DISPLAY_NAME) { getString(it) }

    private fun querySize(uri: Uri): Long? = query(uri, OpenableColumns.SIZE) { if (isNull(it)) null else getLong(it) }

    private fun <T> query(uri: Uri, column: String, read: android.database.Cursor.(Int) -> T?): T? = runCatching {
        resolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(column)
            if (index >= 0 && cursor.moveToFirst()) cursor.read(index) else null
        }
    }.getOrNull()
}
