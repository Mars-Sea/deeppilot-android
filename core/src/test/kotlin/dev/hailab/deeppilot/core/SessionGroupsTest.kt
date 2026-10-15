package dev.hailab.deeppilot.core

import dev.hailab.deeppilot.core.protocol.ArchivedSessionsSnapshot
import dev.hailab.deeppilot.core.protocol.BridgeJson
import dev.hailab.deeppilot.core.protocol.SessionSummary
import dev.hailab.deeppilot.core.session.SessionGroups
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionGroupsTest {
    private fun row(id: String, ts: Long, workspace: String?) =
        SessionSummary(id = id, lastActivityTs = ts, workspaceLabel = workspace, archived = true)

    @Test
    fun archivedGroupsKeepUncategorizedLast() {
        val groups = SessionGroups.archived(
            listOf(row("a", 50, null), row("b", 10, "web"), row("c", 30, "api"), row("d", 20, " web ")),
        )
        assertEquals(listOf("api", "web", null), groups.map { it.workspace })
        assertEquals(listOf("d", "b"), groups[1].sessions.map { it.id })
    }

    @Test
    fun decodesArchivedSnapshot() {
        val json = """{"sessions":[{"id":"s1","title":"Old","lastActivityTs":5,"archived":true,"extra":1}]}"""
        val snapshot = BridgeJson.decodeFromString(ArchivedSessionsSnapshot.serializer(), json)
        assertEquals("Old", snapshot.sessions.single().title)
        assertEquals(true, snapshot.sessions.single().archived)
    }
}
