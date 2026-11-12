package dev.hailab.deeppilot.core.bridge

import dev.hailab.deeppilot.core.pairing.HostEndpoints
import dev.hailab.deeppilot.core.protocol.HostRoutes
import dev.hailab.deeppilot.core.protocol.LanRoute
import dev.hailab.deeppilot.core.protocol.RemoteRoute
import dev.hailab.deeppilot.core.trust.SpkiFingerprint

/** One address a connection attempt may use. A null [tlsFingerprint] means the system trust store. */
data class Route(
    val url: String,
    val tlsFingerprint: String?,
    val kind: Kind,
    /** Give up after [RouteSelector.LAN_CONNECT_TIMEOUT_MILLIS]: another route follows. */
    val quick: Boolean = false,
) {
    enum class Kind { Lan, Remote, Paired }
}

/**
 * Picks the addresses to try for one connection round from the pairing and the
 * routes the host last announced (welcome `routes`, capability `routeHints`):
 * the LAN first on Wi-Fi or Ethernet, then the public address, then whatever
 * the device was paired with.
 */
object RouteSelector {
    const val LAN_CONNECT_TIMEOUT_MILLIS = 1_500L
    /** After a failed LAN attempt the public route goes first for this long, on the same network. */
    const val LAN_FAILURE_MEMORY_MILLIS = 10 * 60_000L
    private const val MAX_LAN_URLS = 2

    /**
     * Keeps only what the protocol allows: `https://` everywhere, RFC 1918
     * literals with a valid pin for the LAN. Null when nothing usable is left.
     */
    fun sanitize(routes: HostRoutes?): HostRoutes? {
        if (routes == null) return null
        val pin = routes.lan?.tlsFingerprint?.takeIf { SpkiFingerprint.isValid(it) }
        val lanUrls = routes.lan?.urls.orEmpty().filter { url -> httpsHost(url)?.let(::isPrivateIPv4) == true }
        val lan = if (pin != null && lanUrls.isNotEmpty()) LanRoute(lanUrls, pin) else null
        val remote = routes.remote?.url?.takeIf { httpsHost(it) != null }?.let { RemoteRoute(it) }
        return if (lan == null && remote == null) null else HostRoutes(lan, remote)
    }

    /**
     * The routes of one round, in order, without duplicates. A recently failed
     * LAN is skipped only while a public route can stand in for it.
     */
    fun candidates(credentials: HostCredentials, lanEligible: Boolean, lanRecentlyFailed: Boolean): List<Route> {
        val routes = sanitize(credentials.routes)
        val remote = routes?.remote?.url?.let { Route(it, null, Route.Kind.Remote) }
        val lan = routes?.lan
            ?.takeIf { lanEligible && !(lanRecentlyFailed && remote != null) }
            ?.let { hint -> hint.urls.take(MAX_LAN_URLS).map { Route(it, hint.tlsFingerprint, Route.Kind.Lan) } }
            .orEmpty()
        val ordered = lan + listOfNotNull(remote) + Route(credentials.host, credentials.tlsFingerprint, Route.Kind.Paired)
        val unique = ordered.distinctBy { key(it.url) }
        return unique.mapIndexed { index, route ->
            route.copy(quick = route.kind == Route.Kind.Lan && index < unique.lastIndex)
        }
    }

    /**
     * The credentials after a welcome announced [announced]. The routes are
     * replaced whole; a LAN-paired device also takes the new pin, so a rebuilt
     * certificate key does not force a new pairing (§2).
     */
    fun adopt(credentials: HostCredentials, announced: HostRoutes?): HostCredentials {
        val routes = sanitize(announced) ?: return credentials
        val pin = routes.lan?.tlsFingerprint
        return credentials.copy(
            routes = routes,
            tlsFingerprint = if (credentials.tlsFingerprint != null && pin != null) pin else credentials.tlsFingerprint,
        )
    }

    private fun key(url: String): String = HostEndpoints.of(url)?.base?.toString() ?: url

    private fun httpsHost(url: String): String? {
        if (!url.trim().lowercase().startsWith("https://")) return null
        return HostEndpoints.of(url)?.base?.host
    }

    private fun isPrivateIPv4(host: String): Boolean {
        val parts = host.split('.').map { it.toIntOrNull() ?: return false }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        return parts[0] == 10 || (parts[0] == 172 && parts[1] in 16..31) || (parts[0] == 192 && parts[1] == 168)
    }
}
