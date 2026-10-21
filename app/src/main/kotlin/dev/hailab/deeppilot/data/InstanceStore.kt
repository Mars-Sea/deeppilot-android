package dev.hailab.deeppilot.data

import android.content.Context
import androidx.core.content.edit
import dev.hailab.deeppilot.core.bridge.CursorStore
import dev.hailab.deeppilot.core.bridge.HostCredentials
import dev.hailab.deeppilot.core.hosts.HostProfile
import dev.hailab.deeppilot.core.hosts.HostRoster
import dev.hailab.deeppilot.core.protocol.BridgeJson

/**
 * The paired hosts and per-host replay cursors. None of this is secret: the
 * device private keys stay in the Android Keystore. The file is still kept
 * out of backups (see data_extraction_rules.xml) so a restored phone pairs again.
 */
class InstanceStore(context: Context) : CursorStore {
    private val prefs = context.getSharedPreferences("deeppilot.instance", Context.MODE_PRIVATE)

    /** Every paired host, active set to the launch default. */
    fun loadRoster(): HostRoster {
        val raw = prefs.getString(KEY_ROSTER, null)
        val roster = raw?.let { runCatching { BridgeJson.decodeFromString(HostRoster.serializer(), it) }.getOrNull() }
            ?: migrateSingleHost()
        return roster.atLaunch()
    }

    fun saveRoster(roster: HostRoster) {
        prefs.edit(commit = true) { putString(KEY_ROSTER, BridgeJson.encodeToString(HostRoster.serializer(), roster)) }
    }

    /** The host the app connects to on launch, if any is paired. */
    fun loadHost(): HostCredentials? = loadRoster().launch?.credentials()

    fun clearCursor(profileId: String) {
        prefs.edit(commit = true) { remove(cursorKey(profileId)) }
    }

    /** Builds the roster from the single host the first release stored. */
    private fun migrateSingleHost(): HostRoster {
        val profileId = prefs.getString(LEGACY_PROFILE, null) ?: return HostRoster()
        val profile = HostProfile(
            id = profileId,
            name = HostRoster.DEFAULT_NAME,
            host = prefs.getString(LEGACY_HOST, null) ?: return HostRoster(),
            deviceId = prefs.getString(LEGACY_DEVICE, null) ?: return HostRoster(),
            audience = prefs.getString(LEGACY_AUDIENCE, null) ?: return HostRoster(),
            tlsFingerprint = prefs.getString(LEGACY_FINGERPRINT, null),
        )
        val roster = HostRoster().adding(profile)
        saveRoster(roster)
        prefs.edit(commit = true) {
            remove(LEGACY_PROFILE)
            remove(LEGACY_HOST)
            remove(LEGACY_DEVICE)
            remove(LEGACY_AUDIENCE)
            remove(LEGACY_FINGERPRINT)
        }
        return roster
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
        const val KEY_ROSTER = "hosts.roster.v2"
        const val LEGACY_PROFILE = "host.profileId"
        const val LEGACY_HOST = "host.url"
        const val LEGACY_DEVICE = "host.deviceId"
        const val LEGACY_AUDIENCE = "host.audience"
        const val LEGACY_FINGERPRINT = "host.tlsFingerprint"
        const val KEY_KEEP_CONNECTED = "settings.keepConnected"
    }
}
