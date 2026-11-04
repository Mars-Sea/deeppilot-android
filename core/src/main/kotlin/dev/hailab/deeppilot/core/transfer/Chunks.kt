package dev.hailab.deeppilot.core.transfer

import dev.hailab.deeppilot.core.protocol.Envelope
import dev.hailab.deeppilot.core.protocol.ExportChunk
import dev.hailab.deeppilot.core.protocol.FrameType
import kotlinx.serialization.Serializable
import java.io.OutputStream
import java.util.Base64

/** A chunk or a receipt that does not add up; the whole transfer is dropped. */
class MalformedTransferException : Exception("malformed chunked transfer")

@Serializable
private data class ChunkIndex(val index: Int)

@Serializable
private data class ChunkReceipt(val chunks: Int, val bytes: Long)

/**
 * Binary attachment read-back (`s2c.session.attachment.chunk`, §5): indexes run
 * from 0 without gaps, a chunk holds at most 256 KiB, the image at most 12 MiB,
 * and the final ack must match the count and the byte total.
 */
class AttachmentChunkAssembler {
    private val buffer = java.io.ByteArrayOutputStream()
    private var nextIndex = 0

    fun append(envelope: Envelope) {
        val body = envelope.body
        if (envelope.type != FrameType.SESSION_ATTACHMENT_CHUNK || body == null ||
            envelope.decode(ChunkIndex.serializer())?.index != nextIndex ||
            body.size > MAX_CHUNK_BYTES || buffer.size() + body.size > MAX_IMAGE_BYTES
        ) {
            throw MalformedTransferException()
        }
        buffer.write(body)
        nextIndex += 1
    }

    /** The image bytes when [ack] matches what arrived, else null. */
    fun finish(ack: Envelope): ByteArray? {
        val done = ack.decode(ChunkReceipt.serializer()) ?: return null
        if (done.chunks != nextIndex || done.bytes != buffer.size().toLong() || buffer.size() == 0) return null
        return buffer.toByteArray()
    }

    companion object {
        const val MAX_CHUNK_BYTES = 256 * 1024
        const val MAX_IMAGE_BYTES = 12 * 1024 * 1024
    }
}

/**
 * Session export chunks (`s2c.session.export.chunk`): 48 KiB base64 text chunks,
 * or 256 KiB raw binary chunks when the request asked for [binary] frames.
 * Writes straight to [output]; the caller checks the receipt with [chunks] and [bytes].
 */
class ExportChunkWriter(private val output: OutputStream, private val binary: Boolean) {
    var chunks = 0
        private set
    var bytes = 0L
        private set

    fun append(envelope: Envelope) {
        if (envelope.type != FrameType.SESSION_EXPORT_CHUNK) throw MalformedTransferException()
        val body = envelope.body
        val data = if (body != null) {
            if (!binary || envelope.decode(ChunkIndex.serializer())?.index != chunks || body.size > BINARY_CHUNK_BYTES) {
                throw MalformedTransferException()
            }
            body
        } else {
            val chunk = envelope.decode(ExportChunk.serializer())
            if (chunk == null || chunk.index != chunks || chunk.data.length > TEXT_CHUNK_BASE64) throw MalformedTransferException()
            val decoded = try { Base64.getDecoder().decode(chunk.data) } catch (_: IllegalArgumentException) { throw MalformedTransferException() }
            if (decoded.size > TEXT_CHUNK_BYTES) throw MalformedTransferException()
            decoded
        }
        output.write(data)
        chunks += 1
        bytes += data.size
    }

    companion object {
        const val TEXT_CHUNK_BYTES = 48 * 1024
        const val TEXT_CHUNK_BASE64 = 64 * 1024
        const val BINARY_CHUNK_BYTES = 256 * 1024
    }
}
