package dev.hailab.deeppilot.core

import dev.hailab.deeppilot.core.pairing.HostEndpoints
import dev.hailab.deeppilot.core.pairing.PairingError
import dev.hailab.deeppilot.core.pairing.PairingException
import dev.hailab.deeppilot.core.pairing.PairingLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PairingLinkTest {
    private val code = "A".repeat(32)
    private val fingerprint = "sha256:" + "b".repeat(43)
    private val audience = "deeppilot:" + "c".repeat(22)
    private val now = 1_756_000_000_000L

    private fun expectError(reason: PairingError, block: () -> Unit) {
        try {
            block()
            fail("expected $reason")
        } catch (e: PairingException) {
            assertEquals(reason, e.reason)
        }
    }

    @Test
    fun parsesLinkWithAllFields() {
        val link = PairingLink.parse(
            "deeppilot://pair?h=https%3A%2F%2F192.168.1.5%3A3098&c=$code&f=${fingerprint.replace(":", "%3A")}&a=$audience&e=${now + 60_000}",
            now,
        )
        assertEquals("https://192.168.1.5:3098", link.host)
        assertEquals(code, link.code)
        assertEquals(fingerprint, link.tlsFingerprint)
        assertEquals(audience, link.audience)
        assertEquals(now + 60_000, link.expiresAt)
    }

    @Test
    fun linkMayOmitAudienceAndExpiry() {
        val link = PairingLink.parse("deeppilot://pair?h=https://x.ts.net&c=$code", now)
        assertNull(link.audience)
        assertNull(link.expiresAt)
        assertNull(link.tlsFingerprint)
    }

    @Test
    fun roundTripsThroughUri() {
        val original = PairingLink("https://192.168.1.5:3098", code, fingerprint, audience, now + 1)
        assertEquals(original, PairingLink.parse(original.toUri(), now))
    }

    @Test
    fun parsesQrJson() {
        val json = """{"v":2,"type":"deeppilot-pairing","host":"wss://h.ts.net","code":"$code","expiresAt":${now + 1000},"audience":"$audience"}"""
        val link = PairingLink.parse(json, now)
        assertEquals("wss://h.ts.net", link.host)
        assertEquals(audience, link.audience)
    }

    @Test
    fun rejectsOtherVersions() {
        val json = """{"v":1,"type":"deeppilot-pairing","host":"wss://h","code":"$code","expiresAt":${now + 1000},"audience":"$audience"}"""
        expectError(PairingError.UNSUPPORTED_VERSION) { PairingLink.parse(json, now) }
    }

    @Test
    fun rejectsPlaintextHosts() {
        expectError(PairingError.INVALID_HOST) { PairingLink.parse("deeppilot://pair?h=http://192.168.1.5:3098&c=$code", now) }
        expectError(PairingError.INVALID_HOST) { PairingLink.parse("deeppilot://pair?h=ws://192.168.1.5&c=$code", now) }
        expectError(PairingError.INVALID_HOST) { PairingLink.parse("deeppilot://pair?h=https://u:p@host&c=$code", now) }
    }

    @Test
    fun rejectsBadFieldsAndExpiredCodes() {
        expectError(PairingError.INVALID_FINGERPRINT) { PairingLink.parse("deeppilot://pair?h=https://h&c=$code&f=md5:abc", now) }
        expectError(PairingError.INVALID_CODE) { PairingLink.parse("deeppilot://pair?h=https://h&c=short", now) }
        expectError(PairingError.INVALID_PAYLOAD) { PairingLink.parse("deeppilot://pair?h=https://h&c=$code&a=other", now) }
        expectError(PairingError.EXPIRED) { PairingLink.parse("deeppilot://pair?h=https://h&c=$code&e=${now - 1}", now) }
        expectError(PairingError.INVALID_PAYLOAD) { PairingLink.parse("", now) }
    }

    @Test
    fun recognisesOnlyThePairRoute() {
        assertTrue(PairingLink.isPairingUri("deeppilot://pair?h=x"))
        assertFalse(PairingLink.isPairingUri("deeppilot://session?id=1"))
    }

    @Test
    fun hostEndpointsUpgradeToTls() {
        val endpoints = HostEndpoints.of("ws://192.168.1.5:3098")!!
        assertEquals("https://192.168.1.5:3098/phone", endpoints.webSocket.toString())
        assertEquals("https://192.168.1.5:3098/phone/pair", endpoints.pair.toString())
        assertEquals("192.168.1.5:3098", endpoints.display)
        assertEquals("https://h.ts.net/phone", HostEndpoints.of("wss://h.ts.net")!!.webSocket.toString())
        assertNull(HostEndpoints.of("https://user@host"))
        assertNull(HostEndpoints.of(" "))
    }
}
