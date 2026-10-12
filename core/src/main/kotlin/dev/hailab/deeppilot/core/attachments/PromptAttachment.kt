package dev.hailab.deeppilot.core.attachments

import dev.hailab.deeppilot.core.protocol.PromptDocument
import dev.hailab.deeppilot.core.protocol.PromptImage

/** A prompt attachment ready to send (PROTOCOL.md §5): a base64 JPEG, or extracted text. */
data class PromptAttachment(
    val id: String,
    val kind: Kind,
    val name: String,
    val mediaType: String,
    /** Image: standard base64 without a data-URL prefix. Document: plain text. */
    val payload: String,
    val truncated: Boolean = false,
) {
    enum class Kind { Image, Document }

    companion object {
        const val MAX_PER_PROMPT = 4
        const val MAX_SOURCE_BYTES = 20 * 1024 * 1024
        const val MAX_IMAGE_DIMENSION = 2_048
        const val MAX_JPEG_BYTES = 6 * 1024 * 1024
        const val JPEG_QUALITY = 82
        const val MAX_DOCUMENT_CHARACTERS = 256 * 1024

        private const val TIMEOUT_FLOOR_MILLIS = 12_000L
        private const val TIMEOUT_CEILING_MILLIS = 50_000L

        private val textExtensions = setOf(
            "txt", "md", "markdown", "json", "jsonl", "csv", "tsv", "xml", "yaml", "yml",
            "html", "css", "js", "jsx", "ts", "tsx", "swift", "m", "mm", "h", "c", "cc",
            "cpp", "java", "kt", "kts", "py", "rb", "go", "rs", "sh", "zsh", "fish", "sql",
            "toml", "ini", "conf", "log", "diff", "patch",
        )
        private val textMediaTypes = setOf(
            "application/json", "application/xml", "application/x-yaml", "application/yaml",
            "application/javascript", "application/x-sh", "application/sql", "application/toml",
        )

        fun isPdf(mediaType: String?, extension: String): Boolean =
            mediaType == "application/pdf" || extension.lowercase() == "pdf"

        /** Text-like files whose bytes can be sent as text; binary formats are refused. */
        fun isTextDocument(mediaType: String?, extension: String): Boolean =
            mediaType?.startsWith("text/") == true || mediaType in textMediaTypes ||
                extension.lowercase() in textExtensions

        /** Trims and caps extracted text; null when nothing readable is left. */
        fun document(name: String, mediaType: String?, text: String, id: String): PromptAttachment? {
            val clean = text.trim()
            if (clean.isEmpty()) return null
            val truncated = clean.length > MAX_DOCUMENT_CHARACTERS
            return PromptAttachment(
                id = id,
                kind = Kind.Document,
                name = name,
                mediaType = mediaType?.takeIf { it.isNotBlank() } ?: "text/plain",
                payload = if (truncated) clean.take(MAX_DOCUMENT_CHARACTERS) else clean,
                truncated = truncated,
            )
        }

        /**
         * Attachments ride inside the request frame, so the ack budget grows with
         * the upload (~256 KiB/s) and stays under the host's 60 s stale sweep.
         */
        fun sendTimeoutMillis(attachments: List<PromptAttachment>): Long {
            val bytes = attachments.sumOf { it.payload.toByteArray(Charsets.UTF_8).size.toLong() }
            val scaled = 10_000L + bytes * 1_000L / (256 * 1024)
            return scaled.coerceIn(TIMEOUT_FLOOR_MILLIS, TIMEOUT_CEILING_MILLIS)
        }
    }
}

fun List<PromptAttachment>.promptImages(): List<PromptImage>? =
    filter { it.kind == PromptAttachment.Kind.Image }
        .map { PromptImage(mediaType = it.mediaType, data = it.payload, name = it.name) }
        .ifEmpty { null }

fun List<PromptAttachment>.promptDocuments(): List<PromptDocument>? =
    filter { it.kind == PromptAttachment.Kind.Document }
        .map { PromptDocument(mediaType = it.mediaType, name = it.name, text = it.payload, truncated = it.truncated.takeIf { t -> t }) }
        .ifEmpty { null }
