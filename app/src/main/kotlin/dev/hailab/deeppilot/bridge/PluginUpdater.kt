package dev.hailab.deeppilot.bridge

import dev.hailab.deeppilot.core.bridge.BridgeClient
import dev.hailab.deeppilot.core.bridge.BridgeException
import dev.hailab.deeppilot.core.protocol.BridgeJson
import dev.hailab.deeppilot.core.protocol.ClientRequestId
import dev.hailab.deeppilot.core.protocol.FrameType
import dev.hailab.deeppilot.core.protocol.PROTOCOL_VERSION
import dev.hailab.deeppilot.core.protocol.PluginUpdateAccepted
import dev.hailab.deeppilot.core.protocol.PluginUpdateApply
import dev.hailab.deeppilot.core.protocol.PluginUpdateProgress
import dev.hailab.deeppilot.core.protocol.PluginUpdateStatus
import dev.hailab.deeppilot.core.protocol.Welcome
import dev.hailab.deeppilot.core.protocol.WireErrorCode
import dev.hailab.deeppilot.core.update.PluginUpdateState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Updates the DeepPilot plugin on the connected computer (iOS
 * `PluginUpdateController`). State belongs to one host: switching hosts drops it.
 * The host unloads the plugin while installing, so the socket closing is expected;
 * after reconnecting, a check reads the host's journal to learn how it ended.
 */
class PluginUpdater(private val client: BridgeClient, private val scope: CoroutineScope) {
    data class View(
        val state: PluginUpdateState = PluginUpdateState(),
        val connected: Boolean = false,
        val supported: Boolean = false,
        val scopes: List<String> = emptyList(),
        val checking: Boolean = false,
    ) {
        val canCheck: Boolean get() = connected && supported && SESSIONS_READ in scopes
        val canManage: Boolean get() = SESSIONS_MANAGE in scopes
        val canApply: Boolean
            get() = canCheck && canManage && state.fresh && !checking && !state.applying &&
                state.status?.protocolVersion == PROTOCOL_VERSION
    }

    private val _view = MutableStateFlow(View())
    val view: StateFlow<View> = _view.asStateFlow()
    private var profileId: String? = null
    /** Bumped whenever host, connection or permissions change; late answers of an older one are dropped. */
    private var token = 0L

    /** Called on every connection state change; [welcome] is null while not authenticated. */
    @Synchronized
    fun updateConnection(profileId: String?, welcome: Welcome?) {
        val supported = welcome?.capabilities?.pluginUpdate == true
        val scopes = welcome?.scopes.orEmpty()
        val connected = welcome != null
        if (this.profileId != profileId) {
            this.profileId = profileId
            token++
            _view.value = View(connected = connected, supported = supported, scopes = scopes)
            return
        }
        val current = _view.value
        if (current.connected != connected || current.supported != supported || current.scopes != scopes) {
            token++
            _view.value = current.copy(connected = connected, supported = supported, scopes = scopes, checking = false, state = current.state.reconnected())
        }
        // Back after the host swapped the plugin: read the journal right away.
        if (connected && supported && _view.value.state.applying) check()
    }

    fun check() {
        val start = synchronized(this) {
            val current = _view.value
            if (!current.canCheck || current.checking) return
            _view.value = current.copy(checking = true, state = current.state.copy(error = null, fresh = false))
            token to current.state.revision
        }
        scope.launch {
            val (expected, revision) = start
            val result = runCatching {
                client.request(FrameType.PLUGIN_UPDATE_CHECK, timeoutMillis = 30_000).decode(PluginUpdateStatus.serializer())
            }.getOrNull()
            synchronized(this@PluginUpdater) {
                if (expected != token) return@launch
                _view.update {
                    it.copy(checking = false, state = if (result != null) it.state.checked(result, revision) else it.state.failedCheck())
                }
            }
        }
    }

    /** Installs [version]; the caller has confirmed it with the user. */
    fun apply(version: String) {
        val start = synchronized(this) {
            val current = _view.value
            if (!current.canApply || !current.state.selectable(version)) return
            val id = ClientRequestId.make()
            _view.value = current.copy(state = current.state.startedApply(id))
            token to id
        }
        scope.launch {
            val (expected, id) = start
            val outcome = runCatching {
                client.request(FrameType.PLUGIN_UPDATE_APPLY, BridgeJson.encodeToJsonElement(PluginUpdateApply.serializer(), PluginUpdateApply(version, id)), timeoutMillis = 30_000)
                    .decode(PluginUpdateAccepted.serializer())
            }
            synchronized(this@PluginUpdater) {
                if (expected != token) return@launch
                val ack = outcome.getOrNull()
                if (ack != null && ack.clientRequestId == id && ack.accepted) return@launch
                val code = (outcome.exceptionOrNull() as? BridgeException)?.code
                _view.update { it.copy(state = it.state.applyFailed(id, rejected = code in REJECTIONS)) }
            }
        }
    }

    @Synchronized
    fun receive(progress: PluginUpdateProgress) {
        val current = _view.value
        if (!current.connected || !current.supported) return
        val next = current.state.received(progress)
        if (next == current.state) return
        _view.value = current.copy(state = next)
        if (progress.phase == "done") check()
    }

    private companion object {
        const val SESSIONS_READ = "sessions.read"
        const val SESSIONS_MANAGE = "sessions.manage"
        /** Answers that prove the host did not start the update. */
        val REJECTIONS = setOf(
            WireErrorCode.E_AUTH, WireErrorCode.E_FORBIDDEN, WireErrorCode.E_PROTOCOL,
            WireErrorCode.E_NOT_FOUND, WireErrorCode.E_BUSY, WireErrorCode.E_UNSUPPORTED,
        )
    }
}
