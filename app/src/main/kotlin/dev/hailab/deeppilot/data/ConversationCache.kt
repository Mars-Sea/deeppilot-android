package dev.hailab.deeppilot.data

import android.content.Context
import dev.hailab.deeppilot.core.cache.CachedConversation
import dev.hailab.deeppilot.core.cache.ConversationRetention
import dev.hailab.deeppilot.core.protocol.BridgeJson
import dev.hailab.deeppilot.core.protocol.SessionSummary
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Offline copy of each host's session list and recently opened conversations
 * (iOS `ConversationStore`, M-8 offline viewing). Every host has its own
 * folder: session ids are only unique within one host. Failures are swallowed;
 * the next tail from the host heals any gap.
 */
class ConversationCache(context: Context) {
    private val root = File(context.filesDir, "conversations")
    /** One writer thread: files of a host are never written concurrently. */
    private val io = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val sessionList = ListSerializer(SessionSummary.serializer())
    private val order = ListSerializer(String.serializer())

    suspend fun loadSessions(profileId: String): List<SessionSummary> =
        read(File(dir(profileId), SESSIONS), sessionList).orEmpty()

    suspend fun saveSessions(profileId: String, sessions: List<SessionSummary>) {
        write(File(dir(profileId), SESSIONS), sessionList, sessions)
    }

    suspend fun loadConversation(profileId: String, sessionId: String): CachedConversation? =
        read(conversationFile(profileId, sessionId), CachedConversation.serializer())?.takeIf { it.sessionId == sessionId }

    /** Stores [conversation] and drops whatever falls out of the recently-opened window. */
    suspend fun saveConversation(profileId: String, conversation: CachedConversation) {
        if (conversation.messages.isEmpty()) return
        write(conversationFile(profileId, conversation.sessionId), CachedConversation.serializer(), conversation)
        val orderFile = File(dir(profileId), ORDER)
        val (next, evicted) = ConversationRetention.touch(read(orderFile, order).orEmpty(), conversation.sessionId)
        write(orderFile, order, next)
        withContext(io) { evicted.forEach { conversationFile(profileId, it).delete() } }
    }

    /** Deletes everything kept for a host that was removed. */
    suspend fun remove(profileId: String) = withContext(io) { dir(profileId).deleteRecursively() }

    private suspend fun <T> read(file: File, serializer: KSerializer<T>): T? = withContext(io) {
        runCatching { if (file.isFile) BridgeJson.decodeFromString(serializer, file.readText()) else null }.getOrNull()
    }

    private suspend fun <T> write(file: File, serializer: KSerializer<T>, value: T) = withContext(io) {
        runCatching {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(BridgeJson.encodeToString(serializer, value))
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        }
    }

    private fun dir(profileId: String) = File(root, digest(profileId))

    private fun conversationFile(profileId: String, sessionId: String) = File(dir(profileId), "s-${digest(sessionId)}.json")

    private fun digest(value: String) =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val SESSIONS = "sessions.json"
        const val ORDER = "recent.json"
    }
}
