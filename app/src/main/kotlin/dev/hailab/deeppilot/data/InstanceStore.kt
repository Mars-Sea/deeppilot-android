package dev.hailab.deeppilot.data

import android.content.Context
import androidx.core.content.edit
import dev.hailab.deeppilot.core.bridge.CursorStore
import dev.hailab.deeppilot.core.bridge.HostCredentials

/**
 * The paired host and per-host replay cursor. None of this is secret: the
 * device private key stays in the Android Keystore. The file is still kept
 * out of backups (see data_extraction_rules.xml) so a restored phone pairs again.
 *
 * Phase 1 keeps a single host; multi-host switching comes later.
 */
class InstanceStore(context: Context) : CursorStore {
    private val prefs = context.getSharedPreferences("deeppilot.instance", Context.MODE_PRIVATE)

    fun loadHost(): HostCredentials? {
        val profileId = prefs.getString(KEY_PROFILE, null) ?: return null
        return HostCredentials(
            profileId = profileId,
            host = prefs.getString(KEY_HOST, null) ?: return null,
            deviceId = prefs.getString(KEY_DEVICE, null) ?: return null,
            audience = prefs.getString(KEY_AUDIENCE, null) ?: return null,
            tlsFingerprint = prefs.getString(KEY_FINGERPRINT, null),
        )
    }

    fun saveHost(credentials: HostCredentials) {
        prefs.edit(commit = true) {
            putString(KEY_PROFILE, credentials.profileId)
            putString(KEY_HOST, credentials.host)
            putString(KEY_DEVICE, credentials.deviceId)
            putString(KEY_AUDIENCE, credentials.audience)
            putString(KEY_FINGERPRINT, credentials.tlsFingerprint)
        }
    }

    fun clearHost(profileId: String) {
        prefs.edit(commit = true) {
            remove(KEY_PROFILE)
            remove(KEY_HOST)
            remove(KEY_DEVICE)
            remove(KEY_AUDIENCE)
            remove(KEY_FINGERPRINT)
            remove(cursorKey(profileId))
        }
    }

    /** Whether the foreground service keeps the bridge connected in the background. */
    var keepConnected: Boolean
        get() = prefs.getBoolean(KEY_KEEP_CONNECTED, true)
        set(value) = prefs.edit { putBoolean(KEY_KEEP_CONNECTED, value) }

    override fun load(profileId: String): Long? =
        if (prefs.contains(cursorKey(profileId))) prefs.getLong(cursorKey(profileId), 0) else null

    override fun save(profileId: String, cursor: Long) {
        prefs.edit { putLong(cursorKey(profileId), cursor) }
    }

    private fun cursorKey(profileId: String) = "cursor.$profileId"

    private companion object {
        const val KEY_PROFILE = "host.profileId"
        const val KEY_HOST = "host.url"
        const val KEY_DEVICE = "host.deviceId"
        const val KEY_AUDIENCE = "host.audience"
        const val KEY_FINGERPRINT = "host.tlsFingerprint"
        const val KEY_KEEP_CONNECTED = "settings.keepConnected"
    }
}
