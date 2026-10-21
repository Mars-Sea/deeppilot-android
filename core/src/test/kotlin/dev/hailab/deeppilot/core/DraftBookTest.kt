package dev.hailab.deeppilot.core

import dev.hailab.deeppilot.core.drafts.Draft
import dev.hailab.deeppilot.core.drafts.DraftBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftBookTest {
    @Test
    fun draftsAreKeptPerConversation() {
        val book = DraftBook()
        book.set("s1", "hello", 1)
        book.set("s2", "world", 2)
        assertEquals("hello", book.text("s1"))
        assertEquals("world", book.text("s2"))
        assertEquals("", book.text("s3"))
    }

    @Test
    fun emptyTextRemovesTheDraft() {
        val book = DraftBook()
        book.set("s1", "hello", 1)
        assertTrue(book.set("s1", "", 2))
        assertTrue(book.snapshot().isEmpty())
        assertFalse(book.set("s1", "", 3))
    }

    @Test
    fun longTextIsClamped() {
        val book = DraftBook()
        book.set("s1", "x".repeat(DraftBook.MAX_CHARACTERS + 10), 1)
        assertEquals(DraftBook.MAX_CHARACTERS, book.text("s1").length)
    }

    @Test
    fun oldestDraftIsEvictedPastTheCap() {
        val book = DraftBook()
        for (i in 0..DraftBook.MAX_DRAFTS) book.set("s$i", "t$i", i.toLong())
        assertEquals(DraftBook.MAX_DRAFTS, book.snapshot().size)
        assertEquals("", book.text("s0"))
        assertEquals("t${DraftBook.MAX_DRAFTS}", book.text("s${DraftBook.MAX_DRAFTS}"))
    }

    @Test
    fun restoredDraftsKeepRecencyOrder() {
        val book = DraftBook(listOf(Draft("new", "b", 20), Draft("old", "a", 10), Draft("blank", "", 30)))
        assertEquals(listOf("old", "new"), book.snapshot().map { it.sessionId })
    }
}
