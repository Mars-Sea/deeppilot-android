package dev.hailab.deeppilot.core

import dev.hailab.deeppilot.core.hosts.HostProfile
import dev.hailab.deeppilot.core.hosts.HostRoster
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HostRosterTest {
    private fun profile(id: String) = HostProfile(id, "Mac $id", "https://$id.local:8443", "dev-$id", "deeppilot:$id")

    @Test
    fun firstHostBecomesActiveButLaterOnesDoNot() {
        val roster = HostRoster().adding(profile("a")).adding(profile("b"))
        assertEquals("a", roster.activeId)
        assertEquals(listOf("a", "b"), roster.profiles.map { it.id })
    }

    @Test
    fun launchFallsBackToFirstHost() {
        val roster = HostRoster().adding(profile("a")).adding(profile("b"))
        assertEquals("a", roster.launch?.id)
        assertEquals("b", roster.settingLaunch("b").launch?.id)
        assertNull(roster.settingLaunch("missing").launchId)
    }

    @Test
    fun removingActiveHostSwitchesToLaunchDefault() {
        val roster = HostRoster().adding(profile("a")).adding(profile("b")).adding(profile("c")).settingLaunch("c")
        val next = roster.removing("a")
        assertEquals("c", next.activeId)
        assertEquals(listOf("b", "c"), next.profiles.map { it.id })
    }

    @Test
    fun removingLaunchDefaultClearsIt() {
        val roster = HostRoster().adding(profile("a")).adding(profile("b")).settingLaunch("b").removing("b")
        assertNull(roster.launchId)
        assertEquals("a", roster.activeId)
    }

    @Test
    fun removingLastHostLeavesNothingActive() {
        assertNull(HostRoster().adding(profile("a")).removing("a").activeId)
    }

    @Test
    fun blankNameFallsBackToDefault() {
        val roster = HostRoster().adding(profile("a")).renaming("a", "  ")
        assertEquals(HostRoster.DEFAULT_NAME, roster.profiles.single().name)
    }

    @Test
    fun coldStartActivatesLaunchDefault() {
        val roster = HostRoster().adding(profile("a")).adding(profile("b")).settingLaunch("b")
        val decoded = Json.decodeFromString(HostRoster.serializer(), Json.encodeToString(HostRoster.serializer(), roster))
        assertEquals("b", decoded.atLaunch().activeId)
    }
}
