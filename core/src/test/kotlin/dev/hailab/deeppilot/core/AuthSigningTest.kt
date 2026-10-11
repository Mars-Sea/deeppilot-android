package dev.hailab.deeppilot.core

import dev.hailab.deeppilot.core.auth.AuthSigning
import dev.hailab.deeppilot.core.protocol.AuthChallenge
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthSigningTest {
    @Test
    fun canonicalTextMatchesTheProtocol() {
        val challenge = AuthChallenge(
            nonce = "bm9uY2U",
            audience = "deeppilot:abc",
            issuedAt = 1_756_000_000_000,
            expiresAt = 1_756_000_030_000,
        )
        val text = AuthSigning.canonical(challenge, "dev-1", "Pixel 9", "0.1.0", 1042).toString(Charsets.UTF_8)
        assertEquals(
            listOf(
                "deeppilot-auth-v2",
                "device-id:ZGV2LTE",
                "nonce:bm9uY2U",
                "audience:ZGVlcHBpbG90OmFiYw",
                "issued-at:1756000000000",
                "expires-at:1756000030000",
                "device-name:UGl4ZWwgOQ",
                "app-version:MC4xLjA",
                "resume-cursor:1042",
            ).joinToString("\n"),
            text,
        )
    }

    @Test
    fun missingCursorIsADash() {
        val challenge = AuthChallenge("n", "a", 1, 2)
        val text = AuthSigning.canonical(challenge, "d", "n", "v", null).toString(Charsets.UTF_8)
        assertEquals("resume-cursor:-", text.lines().last())
    }
}
