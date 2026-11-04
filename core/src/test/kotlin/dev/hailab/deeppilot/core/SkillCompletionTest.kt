package dev.hailab.deeppilot.core

import dev.hailab.deeppilot.core.skills.SkillQuery
import dev.hailab.deeppilot.core.protocol.*
import org.junit.Assert.*
import org.junit.Test

class SkillCompletionTest {
    @Test fun preservesUnicodeAndSuffixWhenCaretIsInsideToken() {
        val text = "检查一下 \uD83D\uDC69\uD83C\uDFFD\u200D\uD83D\uDCBB\n/ree 后续需求"
        val query = SkillQuery.detect(text, text.indexOf("/re") + 3)!!
        assertEquals("re", query.query)
        val edit = query.complete("review", text)!!
        assertEquals("检查一下 \uD83D\uDC69\uD83C\uDFFD\u200D\uD83D\uDCBB\n/review 后续需求", edit.text)
        assertEquals(edit.text.indexOf("后续需求"), edit.caret)
    }

    @Test fun rejectsPathsSelectionsAndUncommittedComposition() {
        for (text in listOf("https://example.com/a", "/Users/a", "word/re", "普通文字", "/技能", "")) {
            assertNull(text, SkillQuery.detect(text, text.length))
        }
        assertNull(SkillQuery.detect("/re", 0))
        assertNull(SkillQuery.detect("/re", 2, 3))
        assertNull(SkillQuery.detect("/re", 3, composing = true))
        assertNull(SkillQuery.detect("/re", 99))
        assertNotNull(SkillQuery.detect("/", 1))
    }

    @Test fun ranksPrefixesFirstAndPreservesHostOrder() {
        val rows = listOf("code-review", "review", "read", "release").map { SkillSummary(it, "", true) }
        val query = SkillQuery.detect("/RE", 3)!!
        assertEquals(listOf("review", "read", "release", "code-review"), query.matches(rows).map { it.name })
        val edit = query.complete("review", "/RE")!!
        assertEquals("/review ", edit.text)
        assertEquals(8, edit.caret)
    }

    @Test fun optionalCapabilityAndUserOnlySkillsDecode() {
        val old = BridgeJson.decodeFromString(WelcomeCapabilities.serializer(), "{}")
        assertFalse(old.skills)
        val catalog = BridgeJson.decodeFromString(SessionSkills.serializer(),
            """{"sessionId":"s","skills":[{"name":"review","description":"检查代码","modelInvocable":false}]}""")
        assertFalse(catalog.skills.single().modelInvocable)
    }
}
