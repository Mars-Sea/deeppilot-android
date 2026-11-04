package dev.hailab.deeppilot.core

import dev.hailab.deeppilot.core.protocol.*
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Test

class CommandsTest {
    @Test fun onlyInitialCommandTokensRouteToCommands() {
        assertEquals("compact", SessionCommandLine.name("/compact"))
        assertEquals("goal", SessionCommandLine.name("/goal edit 目标\n正文"))
        listOf(" /compact", "正文 /compact", "/Users/a", "/compact/path", "/", "/技能", "/Compact").forEach { assertNull(it, SessionCommandLine.name(it)) }
    }
    @Test fun pickerIdentityUsesDefinitionIdAndCannotBeSpoofedByName() {
        assertEquals("host", HostCommand("permission", "custom", "custom:permission").action)
        assertEquals("permission", HostCommand("permission", "", "@deepseek-ai/dsh-permission-presets").action)
        assertEquals("file", HostCommand("file", "", "client:file").action)
        assertEquals("host", HostCommand("file", "").action)
    }
    @Test fun oldCapabilitiesAndOptionalCatalogFieldsDecodeConservatively() {
        assertFalse(BridgeJson.decodeFromString<WelcomeCapabilities>("{}").commands)
        val catalog = BridgeJson.decodeFromString<CommandCatalog>("""{"sessionId":"s","commands":[{"name":"compact","description":"compress","extra":"ignored"}]}""")
        assertFalse(catalog.permissions)
        assertFalse(catalog.export)
        assertEquals("host", catalog.commands.single().action)
    }
    @Test fun textAndAttachmentPayloadRoundTripPreservesParameters() {
        val value = CommandRequest("s", "/plan  preserve spaces\n下一行", listOf(CommandAttachment("document", "a.pdf", "text/plain", "aGk=")))
        val encoded = BridgeJson.encodeToString(CommandRequest.serializer(), value)
        assertEquals(value, BridgeJson.decodeFromString<CommandRequest>(encoded))
    }
}
