package dev.hailab.deeppilot.core.pairing

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Endpoints derived from a host string such as `https://192.168.1.149:3098`,
 * `wss://name.ts.net` or a bare `192.168.1.149:3098`.
 *
 * Every bridge transport is TLS (§2.2): plain `ws://` / `http://` input is
 * upgraded rather than honoured, and a host carrying credentials is refused.
 * OkHttp opens WebSockets from `https` URLs, so all three endpoints are https.
 */
class HostEndpoints private constructor(val base: HttpUrl) {
    val pair: HttpUrl get() = base.newBuilder().encodedPath("/phone/pair").query(null).build()
    val health: HttpUrl get() = base.newBuilder().encodedPath("/phone/health").query(null).build()
    val webSocket: HttpUrl get() = base.newBuilder().encodedPath("/phone").query(null).build()

    /** Host and port without scheme, for display. */
    val display: String get() = if (base.port == 443) base.host else "${base.host}:${base.port}"

    companion object {
        fun of(hostText: String): HostEndpoints? {
            var text = hostText.trim()
            if (text.isEmpty()) return null
            for (prefix in listOf("ws://", "http://", "wss://", "https://")) {
                if (text.lowercase().startsWith(prefix)) {
                    text = text.substring(prefix.length)
                    break
                }
            }
            val url = "https://$text".toHttpUrlOrNull() ?: return null
            if (url.host.isEmpty() || url.username.isNotEmpty() || url.password.isNotEmpty()) return null
            if (text.substringBefore('/').contains('@')) return null
            return HostEndpoints(url)
        }
    }
}
