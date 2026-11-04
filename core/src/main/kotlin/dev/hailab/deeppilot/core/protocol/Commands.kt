package dev.hailab.deeppilot.core.protocol

import kotlinx.serialization.Serializable

@Serializable data class HostCommand(val name: String, val description: String, val definitionId: String? = null, val input: CommandInput? = null) {
    val action: String get() = when (definitionId) {
        "client:model" -> "model"
        "client:file" -> "file"
        "@deepseek-ai/dsh-permission-presets" -> "permission"
        "@deepseek-ai/dsh-command-feedback" -> "feedback"
        "@deepseek-ai/dsh-session-log-export" -> "export"
        else -> "host"
    }
}
@Serializable data class CommandInput(val hint: String, val attachments: Boolean = false)
@Serializable data class CommandCatalog(val sessionId: String, val commands: List<HostCommand>, val permissions: Boolean = false, val export: Boolean = false)
@Serializable data class CommandAttachment(val type: String, val name: String, val mediaType: String, val data: String)
@Serializable data class CommandRequest(val sessionId: String, val line: String, val attachments: List<CommandAttachment> = emptyList(), val expectedDefinitionId: String? = null)
@Serializable data class CommandResult(val kind: String, val text: String? = null, val sourceEventSeq: Int? = null)
@Serializable data class CommandExecution(val sessionId: String, val commandId: String, val result: CommandResult)
@Serializable data class PermissionOption(val value: String, val name: String, val description: String? = null, val confirmation: Boolean = false)
@Serializable data class PermissionCatalog(val sessionId: String, val options: List<PermissionOption>, val currentValue: String? = null)
@Serializable data class FeedbackRequest(val sessionId: String, val text: String, val category: String)
@Serializable data class ExportChunk(val index: Int, val data: String = "")
@Serializable data class ExportDone(val sessionId: String, val fileName: String = "", val chunks: Int, val bytes: Long, val mediaType: String? = null)

/** `c2s.session.export`. [format] stays absent for ZIP so older bridges see the old request. */
@Serializable data class ExportRequest(val sessionId: String, val format: String? = null, val binary: Boolean? = null)

/** ZIP (the default) or the conversation text as Markdown (`sessionMarkdown`). */
enum class SessionExportFormat(val wire: String, val fileExtension: String) {
    Zip("zip", "zip"),
    Markdown("markdown", "md");

    /** Old ZIP receipts may omit the type; Markdown must be confirmed by a bridge that knows it. */
    fun accepts(mediaType: String?): Boolean {
        if (mediaType == null) return this == Zip
        val parts = mediaType.split(';').map { it.trim().lowercase() }
        if (parts.first() != if (this == Markdown) "text/markdown" else "application/zip") return false
        if (this == Markdown) {
            for (parameter in parts.drop(1)) {
                val pair = parameter.split('=', limit = 2).map { it.trim() }
                if (pair.first() == "charset" && (pair.size != 2 || pair[1].trim('"') != "utf-8")) return false
            }
        }
        return true
    }
}

/** 仅草稿起始命令词进入执行通道；正文中的斜杠与文件路径仍是普通内容。 */
object SessionCommandLine {
    fun name(text: String): String? = Regex("^/([a-z][a-z0-9_-]*)(?:\\s|$)").find(text)?.groupValues?.get(1)
}

@Serializable data class ModelsChanged(val sessionId: String, val current: ModelSelection? = null)

@Serializable data class SkillsChanged(val sessionId: String? = null)
