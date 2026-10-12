package dev.hailab.deeppilot

import android.app.Application
import dev.hailab.deeppilot.attachments.AttachmentImages
import dev.hailab.deeppilot.bridge.AppVisibility
import dev.hailab.deeppilot.data.AppPreferences
import dev.hailab.deeppilot.bridge.BridgeRepository
import dev.hailab.deeppilot.core.bridge.BridgeClient
import dev.hailab.deeppilot.data.DeviceIdentity
import dev.hailab.deeppilot.data.InstanceStore
import dev.hailab.deeppilot.data.PromptOutboxStore
import dev.hailab.deeppilot.notify.Notifier
import dev.hailab.deeppilot.security.DeviceKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

/** Process-wide objects. The bridge lives here, not in an Activity, so the
 *  foreground service can keep it connected while no screen is open. */
class AppGraph(private val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val http = OkHttpClient()
    val store = InstanceStore(app)
    val identity = DeviceIdentity(app)
    val visibility = AppVisibility()
    val notifier = Notifier(app)
    val keys = DeviceKeys(app)

    val client = BridgeClient(
        baseHttp = http,
        signerFor = keys::signer,
        deviceName = { identity.deviceName },
        appVersion = BuildConfig.VERSION_NAME,
        cursorStore = store,
        parentScope = scope,
    )

    val repository = BridgeRepository(
        client = client,
        store = store,
        keys = keys,
        notifier = notifier,
        visibility = visibility,
        http = http,
        identity = identity,
        appVersion = BuildConfig.VERSION_NAME,
        scope = scope,
        images = AttachmentImages(app.cacheDir),
        outbox = PromptOutboxStore(app),
    )
    val preferences = AppPreferences(app)

    fun start() {
        notifier.createChannels()
        visibility.install(onForeground = { client.poke() })
        repository.start()
    }
}
