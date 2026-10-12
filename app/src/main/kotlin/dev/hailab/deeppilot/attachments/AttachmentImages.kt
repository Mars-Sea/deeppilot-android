package dev.hailab.deeppilot.attachments

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.util.Base64
import android.util.LruCache
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Conversation images, as the iOS `AttachmentImageService` caches them: memory,
 * then disk, then an authenticated read-back from the bridge. Only bounded
 * display thumbnails (≤900px) are kept; the originals stay on the computer.
 */
class AttachmentImages(cacheRoot: File) {
    private val root = File(cacheRoot, "attachments-v2")
    private val memory = object : LruCache<String, Bitmap>(MEMORY_BUDGET) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val lock = Mutex()
    private val inflight = mutableMapOf<String, CompletableDeferred<Bitmap?>>()

    /** Returns the image, fetching it with [fetch] (base64 bytes) only when no cache has it. */
    suspend fun image(profileId: String, sessionId: String, attachmentId: String, fetch: suspend () -> String?): Bitmap? {
        val file = fileFor(profileId, sessionId, attachmentId)
        val key = file.path
        memory.get(key)?.let { return it }
        val (deferred, owner) = lock.withLock {
            inflight[key]?.let { it to false } ?: CompletableDeferred<Bitmap?>().also { inflight[key] = it }.let { it to true }
        }
        if (!owner) return deferred.await()
        val result = runCatching { load(file) ?: fetch()?.let { store(it, file) } }.getOrNull()
        result?.let { memory.put(key, it) }
        lock.withLock { inflight.remove(key) }
        deferred.complete(result)
        return result
    }

    /** Drops everything cached for a host that was unpaired. */
    suspend fun clear(profileId: String) = withContext(Dispatchers.IO) {
        memory.evictAll()
        File(root, digest(profileId)).deleteRecursively()
    }

    private suspend fun load(file: File): Bitmap? = withContext(Dispatchers.IO) {
        if (!file.isFile || System.currentTimeMillis() - file.lastModified() > RETENTION_MILLIS) {
            file.delete()
            return@withContext null
        }
        thumbnail(file.readBytes())?.also { file.setLastModified(System.currentTimeMillis()) } ?: run {
            file.delete()
            null
        }
    }

    private suspend fun store(base64: String, file: File): Bitmap? = withContext(Dispatchers.IO) {
        if (base64.length > MAX_BASE64_LENGTH) return@withContext null
        val bytes = runCatching { Base64.decode(base64, Base64.DEFAULT) }.getOrNull() ?: return@withContext null
        val image = thumbnail(bytes) ?: return@withContext null
        runCatching {
            file.parentFile?.mkdirs()
            file.outputStream().use { image.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            prune()
        }
        image
    }

    private fun thumbnail(bytes: ByteArray): Bitmap? = runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > THUMBNAIL_PIXELS) {
                val scale = THUMBNAIL_PIXELS.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
            }
        }
    }.getOrNull()

    /** Oldest files go first once the cache passes its budget. */
    private fun prune() {
        val files = root.walkTopDown().filter { it.isFile }.toList()
        var total = files.sumOf { it.length() }
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= DISK_BUDGET) break
            total -= file.length()
            file.delete()
        }
    }

    private fun fileFor(profileId: String, sessionId: String, attachmentId: String) =
        File(File(File(root, digest(profileId)), digest(sessionId)), digest(attachmentId) + ".img")

    private fun digest(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val MEMORY_BUDGET = 32 * 1024 * 1024
        const val DISK_BUDGET = 128L * 1024 * 1024
        const val MAX_BASE64_LENGTH = 16 * 1024 * 1024
        const val THUMBNAIL_PIXELS = 900
        const val RETENTION_MILLIS = 30L * 24 * 3600 * 1000
    }
}
