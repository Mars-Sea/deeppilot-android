package dev.hailab.deeppilot.core

import dev.hailab.deeppilot.core.attachments.PromptAttachment
import dev.hailab.deeppilot.core.models.ModelSearch
import dev.hailab.deeppilot.core.models.ProviderIcons
import dev.hailab.deeppilot.core.protocol.BridgeJson
import dev.hailab.deeppilot.core.protocol.DirectoryRequest
import dev.hailab.deeppilot.core.protocol.ModelCatalogModel
import dev.hailab.deeppilot.core.protocol.ModelProviderGroup
import dev.hailab.deeppilot.core.protocol.ModelReasoning
import dev.hailab.deeppilot.core.protocol.ModelReasoningEffort
import dev.hailab.deeppilot.core.protocol.ModelSelection
import dev.hailab.deeppilot.core.protocol.SessionModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
    private val catalog = SessionModels(
        sessionId = "s",
        current = ModelSelection("deepseek", "deepseek-chat", "low"),
        groups = listOf(
            ModelProviderGroup(
                "deepseek",
                "DeepSeek",
                listOf(
                    ModelCatalogModel(
                        "deepseek-chat",
                        "DeepSeek Chat",
                        reasoning = ModelReasoning(listOf(ModelReasoningEffort("low", "低"), ModelReasoningEffort("high", "高")), "high"),
                    ),
                    ModelCatalogModel("deepseek-reasoner", "DeepSeek Reasoner", reasoning = ModelReasoning(listOf(ModelReasoningEffort("high", "高")), "high")),
                ),
            ),
            ModelProviderGroup("openrouter", "OpenRouter", listOf(ModelCatalogModel("anthropic/claude-sonnet", "Claude Sonnet"))),
        ),
    )

    @Test
    fun searchMatchesWordsAndRunTogetherText() {
        assertEquals(listOf("deepseek-reasoner"), ModelSearch.groups(catalog, "reason").flatMap { g -> g.models.map { it.id } })
        assertEquals(1, ModelSearch.groups(catalog, "CLAUDE  sonnet").size)
        assertEquals(listOf("deepseek-chat"), ModelSearch.groups(catalog, "deepseekchat").flatMap { g -> g.models.map { it.id } })
        assertTrue(ModelSearch.groups(catalog, "gemini").isEmpty())
        assertEquals(catalog.groups, ModelSearch.groups(catalog, "  "))
        assertTrue(ModelSearch.matches(ModelSearch.tokens("ＧＰＴ"), "gpt-4o"))
    }

    @Test
    fun selectionKeepsCurrentEffortOrUsesModelDefault() {
        assertEquals("low", ModelSearch.selection(catalog, "deepseek", "deepseek-chat").reasoningEffort)
        assertEquals("high", ModelSearch.selection(catalog, "deepseek", "deepseek-reasoner").reasoningEffort)
        assertNull(ModelSearch.selection(catalog, "openrouter", "anthropic/claude-sonnet").reasoningEffort)
        assertEquals("DeepSeek Chat", catalog.displayName())
        assertEquals("x", catalog.displayName(ModelSelection("nope", "x")))
    }

    @Test
    fun providerIconsPreferModelBrandOverProvider() {
        assertEquals("claude", ProviderIcons.key("Claude Sonnet", "anthropic/claude-sonnet", "openrouter"))
        assertEquals("zhipu", ProviderIcons.key("ChatGLM 4", null, null))
        assertEquals("openrouter", ProviderIcons.key("Mystery", "m-1", "openrouter"))
        assertEquals("gemini", ProviderIcons.key(null, null, "Google"))
        assertNull(ProviderIcons.key("Mystery", "m-1", "acme"))
    }

    @Test
    fun documentsAreCappedAndTimeoutsScale() {
        val long = "a".repeat(PromptAttachment.MAX_DOCUMENT_CHARACTERS + 5)
        val doc = PromptAttachment.document("a.txt", null, long, "1")!!
        assertTrue(doc.truncated)
        assertEquals(PromptAttachment.MAX_DOCUMENT_CHARACTERS, doc.payload.length)
        assertEquals("text/plain", doc.mediaType)
        assertNull(PromptAttachment.document("a.txt", null, "  \n", "2"))
        assertTrue(PromptAttachment.isTextDocument(null, "KT"))
        assertFalse(PromptAttachment.isTextDocument("application/zip", "zip"))
        assertEquals(12_000L, PromptAttachment.sendTimeoutMillis(emptyList()))
        val big = PromptAttachment("i", PromptAttachment.Kind.Image, "p.jpg", "image/jpeg", "x".repeat(50 * 1024 * 1024))
        assertEquals(50_000L, PromptAttachment.sendTimeoutMillis(listOf(big)))
    }

    @Test
    fun directoryRequestOmitsMissingPathAndModelsDecode() {
        assertEquals("{}", BridgeJson.encodeToString(DirectoryRequest.serializer(), DirectoryRequest()))
        val decoded = BridgeJson.decodeFromString(
            SessionModels.serializer(),
            """{"sessionId":"s","current":{"provider":"p","model":"m"},"routable":false,"groups":[],"failures":[{"id":"x","name":"X","message":"down"}]}""",
        )
        assertFalse(decoded.routable)
        assertEquals("down", decoded.failures.single().message)
    }
}
