package dev.hailab.deeppilot.data

import android.content.Context
import androidx.core.content.edit
import dev.hailab.deeppilot.core.delivery.PendingSend
import dev.hailab.deeppilot.core.protocol.BridgeJson
import kotlinx.serialization.builtins.ListSerializer

/**
 * Unconfirmed prompt sends per host (PROTOCOL.md §10). Written synchronously
 * before every tracked send: if the row cannot be saved, the prompt is not sent.
 */
class PromptOutboxStore(context: Context) {
    private val prefs = context.getSharedPreferences("deeppilot.outbox", Context.MODE_PRIVATE)
    private val serializer = ListSerializer(PendingSend.serializer())

    fun load(profileId: String): List<PendingSend> {
        val raw = prefs.getString(profileId, null) ?: return emptyList()
        return runCatching { BridgeJson.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }

    /** False when the write did not reach disk. */
    fun save(profileId: String, rows: List<PendingSend>): Boolean {
        val editor = prefs.edit()
        if (rows.isEmpty()) editor.remove(profileId) else editor.putString(profileId, BridgeJson.encodeToString(serializer, rows))
        return editor.commit()
    }

    fun clear(profileId: String) {
        prefs.edit(commit = true) { remove(profileId) }
    }
}
