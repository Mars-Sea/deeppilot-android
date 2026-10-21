package dev.hailab.deeppilot.core.hosts

import dev.hailab.deeppilot.core.bridge.HostCredentials
import kotlinx.serialization.Serializable

/** One paired computer (iOS `InstanceProfile`). The device key lives in the Keystore under [id]. */
@Serializable
data class HostProfile(
    val id: String,
    val name: String,
    val host: String,
    val deviceId: String,
    val audience: String,
    /** SPKI pin of a LAN host's self-signed certificate; null behind a public certificate. */
    val tlsFingerprint: String? = null,
) {
    fun credentials() = HostCredentials(id, host, deviceId, audience, tlsFingerprint)
}

/**
 * Every paired computer plus which one is connected and which one the app
 * starts with (iOS `InstanceManager`). Only one host is connected at a time.
 * The active choice is not persisted: like iOS, a cold start opens the launch default.
 */
@Serializable
data class HostRoster(
    val profiles: List<HostProfile> = emptyList(),
    val activeId: String? = null,
    val launchId: String? = null,
) {
    val active: HostProfile? get() = profiles.firstOrNull { it.id == activeId }

    /** The pinned launch default, or the first host when none is pinned. */
    val launch: HostProfile? get() = profiles.firstOrNull { it.id == launchId } ?: profiles.firstOrNull()

    /** Adds a newly paired host; it becomes active only when nothing else is. */
    fun adding(profile: HostProfile) = copy(
        profiles = profiles.filterNot { it.id == profile.id } + profile,
        activeId = activeId ?: profile.id,
    )

    fun selecting(id: String) = if (profiles.any { it.id == id }) copy(activeId = id) else this

    fun updating(id: String, change: (HostProfile) -> HostProfile) =
        copy(profiles = profiles.map { if (it.id == id) change(it) else it })

    fun renaming(id: String, name: String) = updating(id) { it.copy(name = displayName(name)) }

    fun settingLaunch(id: String?) = copy(launchId = id?.takeIf { target -> profiles.any { it.id == target } })

    /** Drops [id]; when it was the active host, the launch default (or nothing) takes over. */
    fun removing(id: String): HostRoster {
        val remaining = copy(
            profiles = profiles.filterNot { it.id == id },
            launchId = launchId?.takeIf { it != id },
        )
        return if (activeId == id) remaining.copy(activeId = remaining.launch?.id) else remaining
    }

    /** The state a cold start begins in. */
    fun atLaunch() = copy(activeId = launch?.id)

    companion object {
        const val DEFAULT_NAME = "DeepPilot"

        fun displayName(name: String) = name.trim().ifEmpty { DEFAULT_NAME }
    }
}
