package dev.hailab.deeppilot.core

import dev.hailab.deeppilot.core.bridge.HostCredentials
import dev.hailab.deeppilot.core.bridge.Route
import dev.hailab.deeppilot.core.bridge.RouteSelector
import dev.hailab.deeppilot.core.cache.ConversationRetention
import dev.hailab.deeppilot.core.protocol.BridgeJson
import dev.hailab.deeppilot.core.protocol.Envelope
import dev.hailab.deeppilot.core.protocol.FileReference
import dev.hailab.deeppilot.core.protocol.HostRoutes
import dev.hailab.deeppilot.core.protocol.HostScheduleUpdated
import dev.hailab.deeppilot.core.protocol.LanRoute
import dev.hailab.deeppilot.core.protocol.MessageProjection
import dev.hailab.deeppilot.core.protocol.ReferenceCandidates
import dev.hailab.deeppilot.core.protocol.RemoteRoute
import dev.hailab.deeppilot.core.protocol.SessionEvent
import dev.hailab.deeppilot.core.protocol.SessionGoal
import dev.hailab.deeppilot.core.protocol.SessionReference
import dev.hailab.deeppilot.core.protocol.SessionSummary
import dev.hailab.deeppilot.core.protocol.SessionTail
import dev.hailab.deeppilot.core.protocol.SessionsDelta
import dev.hailab.deeppilot.core.protocol.Welcome
import dev.hailab.deeppilot.core.references.ReferenceQuery
import dev.hailab.deeppilot.core.protocol.ToolEdit
import dev.hailab.deeppilot.core.session.EditDiff
import dev.hailab.deeppilot.core.session.EditLine
import dev.hailab.deeppilot.core.session.OpenSession
import dev.hailab.deeppilot.core.session.SessionReducer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.Deflater

/** The protocol additions made after 2026-11-04: goals, tool targets and edits, transport and references. */
class ProtocolCatchupTest {
    // ---------- goal ----------

    private val goalFixture: ByteArray = requireNotNull(
        javaClass.getResourceAsStream("/protocol-fixtures/session-goal.json"),
    ) { "protocol-fixtures/session-goal.json is missing from the test resources" }.use { it.readBytes() }

    private fun summaryWith(goalJson: String): SessionSummary =
        BridgeJson.decodeFromString(SessionSummary.serializer(), """{"id":"s1","title":"t","status":"idle","goal":$goalJson}""")

    @Test
    fun everyGoalPhaseOfTheSharedFixtureDecodes() {
        val cases = Json.parseToJsonElement(goalFixture.toString(Charsets.UTF_8)).jsonObject
        assertTrue("fixture lost cases", cases.size >= 7)
        for ((name, value) in cases) {
            val goal = summaryWith(value.toString()).goal
            if (value is JsonNull) {
                assertNull(name, goal)
                continue
            }
            assertNotNull(name, goal)
            assertEquals(name, value.jsonObject.getValue("phase").toString().trim('"'), goal!!.phase)
        }
        val blocked = summaryWith(cases.getValue("blocked").toString()).goal!!
        assertEquals("dependency-unavailable", blocked.blockedReason!!.code)
        assertTrue(blocked.isTerminal)
        val unknown = summaryWith(cases.getValue("unknownActivation").toString()).goal!!
        assertNull("an absent activation must stay unknown, not armed", unknown.activation)
        assertTrue(unknown.isActive)
        assertEquals(3L, unknown.maxGoalRounds)
    }

    @Test
    fun aMalformedOrUnknownGoalNeverCostsTheSummary() {
        assertNull(summaryWith("\"soon\"").goal)
        assertNull(summaryWith("""{"objective":"no id"}""").goal)
        assertNull(summaryWith("""{"id":"g","revision":"x"}""").goal)
        val future = summaryWith("""{"id":"g","phase":"reviewing","activation":"maybe","roundsStarted":1.0}""").goal!!
        assertEquals("reviewing", future.phase)
        assertFalse(future.isActive)
        assertFalse(future.isTerminal)
        // A delta row replaces the previous summary whole, goal included.
        val delta = BridgeJson.decodeFromString(SessionsDelta.serializer(), """{"upserted":[{"id":"s1"},{"id":"s2","goal":7}]}""")
        assertEquals(listOf("s1", "s2"), delta.upserted.map { it.id })
        assertTrue(delta.upserted.all { it.goal == null })
    }

    @Test
    fun vendoredGoalFixtureMatchesThePluginRepository() {
        val root = System.getenv("DSH_DEEPPILOT_DIR")?.takeIf { it.isNotBlank() }?.let(::File) ?: File("../../dsh-deeppilot")
        val source = File(root, "tests/fixtures/session-goal.json")
        assumeTrue("no dsh-deeppilot checkout at ${root.absolutePath}", source.isFile)
        assertArrayEquals(
            "session-goal.json differs from ${source.path}; run scripts/sync-protocol-fixtures.sh",
            source.readBytes(),
            goalFixture,
        )
    }

    // ---------- tool target / edit, error rows ----------

    private fun event(kind: String, seq: Long, data: String) =
        SessionEvent("s1", kind, seq, BridgeJson.parseToJsonElement(data))

    @Test
    fun toolTargetAndEditSurviveTheResultMerge() {
        val start = event(
            "tool.start", 5,
            """{"seq":5,"role":"tool","tool":{"name":"edit","state":"running","summary":"path=a.kt","callId":"c1",
               "target":{"kind":"path","value":"src/a.kt"},
               "edit":{"path":"src/a.kt","operation":"replace","oldText":"a","newText":"b"}}}""",
        )
        var state = SessionReducer.reduce(OpenSession("s1"), start).state
        state = SessionReducer.reduce(state, event("tool.end", 6, """{"ok":true,"callId":"c1","seq":6,"summary":"done"}""")).state
        val tool = state.messages.single().tool!!
        assertEquals("done", tool.summary)
        assertEquals("src/a.kt", tool.target!!.value)
        assertEquals("b", tool.edit!!.newText)
        assertEquals("replace", tool.edit!!.operation)
    }

    @Test
    fun truncatedEditAndUnknownTargetKindDecode() {
        val row = BridgeJson.decodeFromString(
            MessageProjection.serializer(),
            """{"seq":1,"role":"tool","tool":{"name":"write","state":"ok","summary":"",
               "target":{"kind":"hologram","value":"x","scope":"src"},
               "edit":{"path":"a","operation":"insert","insertLine":3.0,"truncated":true}}}""",
        )
        assertEquals("hologram", row.tool!!.target!!.kind)
        assertEquals(3L, row.tool!!.edit!!.insertLine)
        assertTrue(row.tool!!.edit!!.truncated)
        assertNull(row.tool!!.edit!!.newText)
    }

    @Test
    fun anAbnormalTurnEndAddsTheErrorRowATailWouldCarry() {
        val failed = SessionReducer.reduce(OpenSession("s1", turnRunning = true), event("turn.end", 9, """{"ok":false,"text":"模型调用失败（E_X）","ts":42}"""))
        val row = failed.state.messages.single()
        assertEquals("error", row.role)
        assertEquals(9L, row.seq)
        assertEquals(42L, row.ts)
        assertEquals("模型调用失败（E_X）", failed.turnCompletion!!.body)
        assertFalse(failed.turnCompletion!!.succeeded)

        val fine = SessionReducer.reduce(OpenSession("s1"), event("turn.end", 9, """{"ok":true,"text":"ignored"}"""))
        assertTrue(fine.state.messages.isEmpty())
    }

    @Test
    fun editPreviewShowsOnlyTheChangedLines() {
        val replace = EditDiff.preview(ToolEdit("a.kt", "replace", "fun a() {\n  one()\n}", "fun a() {\n  two()\n  three()\n}"))!!
        assertEquals(
            listOf(EditLine(EditLine.Kind.Removed, "  one()"), EditLine(EditLine.Kind.Added, "  two()"), EditLine(EditLine.Kind.Added, "  three()")),
            replace.lines,
        )
        assertEquals(0, replace.hidden)

        val created = EditDiff.preview(ToolEdit("a.kt", "create", newText = (1..20).joinToString("\n") { "line $it" }), maxLines = 5)!!
        assertEquals(5, created.lines.size)
        assertEquals(15, created.hidden)
        assertTrue(created.lines.all { it.kind == EditLine.Kind.Added })

        val deleted = EditDiff.preview(ToolEdit("a.kt", "replace", "gone", ""))!!
        assertEquals(listOf(EditLine(EditLine.Kind.Removed, "gone")), deleted.lines)
        assertNull(EditDiff.preview(ToolEdit("a.kt", "replace", truncated = true)))
        assertNull(EditDiff.preview(ToolEdit("a.kt", "replace", "same", "same")))
    }

    // ---------- incremental tail ----------

    private fun row(seq: Long, text: String) = MessageProjection(seq = seq, role = "assistant", text = text)

    @Test
    fun incrementalTailReplacesTheChangedWindowAndOverwritesOlderRows() {
        val local = listOf(row(1, "a"), row(2, "b"), row(3, "c"), row(4, "stale"), row(9, "later"))
        val merged = SessionReducer.mergeIncremental(local, listOf(row(2, "b2"), row(5, "e"), row(6, "f")), sinceSeq = 3)
        assertEquals(listOf(1L, 2L, 3L, 5L, 6L, 9L), merged.map { it.seq })
        assertEquals("b2", merged[1].text)
        assertEquals(local, SessionReducer.mergeIncremental(local, emptyList(), sinceSeq = 3))
    }

    @Test
    fun syncedSeqFollowsTheTailNotLiveEvents() {
        assertEquals(6L, SessionReducer.syncedSeq(listOf(row(5, "e"), row(6, "f"))))
        assertEquals(8L, SessionReducer.syncedSeq(listOf(row(2, "b")), sinceSeq = 8))
        assertEquals(8L, SessionReducer.syncedSeq(emptyList(), sinceSeq = 8))
        assertNull(SessionReducer.syncedSeq(emptyList()))

        val tail = BridgeJson.decodeFromString(SessionTail.serializer(), """{"sessionId":"s1","messages":[],"incremental":true,"sinceSeq":8.0}""")
        assertTrue(tail.incremental)
        assertEquals(8L, tail.sinceSeq)

        // The cache keeps the anchor only while its window still reaches it.
        val kept = ConversationRetention.snapshot(OpenSession("s1", messages = listOf(row(5, "e"), row(6, "f")), syncedSeq = 6))
        assertEquals(6L, kept.syncedSeq)
        val unreachable = ConversationRetention.snapshot(OpenSession("s1", messages = listOf(row(5, "e")), syncedSeq = 2))
        assertNull(unreachable.syncedSeq)
    }

    // ---------- compressed frames ----------

    private fun deflate(text: String, format: Int = Envelope.COMPRESSED_FORMAT): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
        deflater.setInput(text.toByteArray(Charsets.UTF_8))
        deflater.finish()
        val out = java.io.ByteArrayOutputStream().apply { write(format) }
        val buffer = ByteArray(1024)
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        return out.toByteArray()
    }

    @Test
    fun deflatedFramesInflateWithinTheLimitOnly() {
        val text = """{"v":2,"type":"s2c.pong","payload":{"note":"${"目标".repeat(4000)}"}}"""
        assertEquals(text, Envelope.inflate(deflate(text), maxBytes = 1 shl 20))
        assertNull("over the limit", Envelope.inflate(deflate(text), maxBytes = 1000))
        assertNull("wrong format byte", Envelope.inflate(deflate(text, format = 1), maxBytes = 1 shl 20))
        assertNull("garbage", Envelope.inflate(byteArrayOf(2, 1, 2, 3, 4, 5), maxBytes = 1 shl 20))
        assertNull("truncated", Envelope.inflate(deflate(text).copyOf(40), maxBytes = 1 shl 20))
        assertNull(Envelope.inflate(byteArrayOf(2), maxBytes = 10))
    }

    // ---------- routes ----------

    private val pin = "sha256:" + "A".repeat(43)
    private val otherPin = "sha256:" + "B".repeat(43)

    private fun credentials(host: String, fingerprint: String?, routes: HostRoutes?) =
        HostCredentials("p1", host, "d1", "deeppilot:" + "a".repeat(22), fingerprint, routes)

    @Test
    fun routesKeepOnlyHttpsPrivateLanAndHttpsRemote() {
        val routes = HostRoutes(
            LanRoute(listOf("https://192.168.1.20:3098", "http://192.168.1.21:3098", "https://100.64.0.2:3098", "https://example.com", "https://172.20.0.4:3098"), pin),
            RemoteRoute("wss://example.ts.net"),
        )
        val clean = RouteSelector.sanitize(routes)!!
        assertEquals(listOf("https://192.168.1.20:3098", "https://172.20.0.4:3098"), clean.lan!!.urls)
        assertNull("only https is announced", clean.remote)
        assertNull(RouteSelector.sanitize(HostRoutes(LanRoute(listOf("https://192.168.1.20:3098"), "sha256:short"))))
        assertNull(RouteSelector.sanitize(null))
    }

    @Test
    fun lanGoesFirstOnWifiThenRemoteThenThePairing() {
        val routes = HostRoutes(LanRoute(listOf("https://192.168.1.20:3098"), pin), RemoteRoute("https://example.ts.net"))
        val funnelPaired = credentials("https://example.ts.net", null, routes)

        val home = RouteSelector.candidates(funnelPaired, lanEligible = true, lanRecentlyFailed = false)
        assertEquals(listOf(Route.Kind.Lan, Route.Kind.Remote), home.map { it.kind })
        assertTrue("a LAN try with a fallback gives up fast", home[0].quick)
        assertEquals(pin, home[0].tlsFingerprint)
        assertNull(home[1].tlsFingerprint)

        val cellular = RouteSelector.candidates(funnelPaired, lanEligible = false, lanRecentlyFailed = false)
        assertEquals(listOf(Route.Kind.Remote), cellular.map { it.kind })
        val remembered = RouteSelector.candidates(funnelPaired, lanEligible = true, lanRecentlyFailed = true)
        assertEquals(listOf(Route.Kind.Remote), remembered.map { it.kind })

        // Paired on the LAN: the public route is a new way out, the old address stays last.
        val lanPaired = credentials("https://192.168.1.9:3098", pin, routes)
        val moved = RouteSelector.candidates(lanPaired, lanEligible = true, lanRecentlyFailed = false)
        assertEquals(listOf(Route.Kind.Lan, Route.Kind.Remote, Route.Kind.Paired), moved.map { it.kind })

        // No public route: a failed LAN hint is still the best address there is.
        val lanOnly = credentials("https://192.168.1.9:3098", pin, HostRoutes(LanRoute(listOf("https://192.168.1.20:3098"), pin)))
        val only = RouteSelector.candidates(lanOnly, lanEligible = true, lanRecentlyFailed = true)
        assertEquals(listOf("https://192.168.1.20:3098", "https://192.168.1.9:3098"), only.map { it.url })

        // The hint is the pairing address: one attempt, at the normal timeout.
        val same = credentials("https://192.168.1.20:3098", pin, HostRoutes(LanRoute(listOf("https://192.168.1.20:3098/"), pin)))
        val single = RouteSelector.candidates(same, lanEligible = true, lanRecentlyFailed = false).single()
        assertFalse(single.quick)

        val bare = RouteSelector.candidates(credentials("https://example.ts.net", null, null), lanEligible = true, lanRecentlyFailed = false)
        assertEquals(listOf(Route("https://example.ts.net", null, Route.Kind.Paired)), bare)
    }

    @Test
    fun aWelcomeReplacesRoutesAndRepinsOnlyLanPairings() {
        val announced = HostRoutes(LanRoute(listOf("https://10.0.0.5:3098"), otherPin), RemoteRoute("https://example.ts.net"))
        val lanPaired = RouteSelector.adopt(credentials("https://192.168.1.9:3098", pin, null), announced)
        assertEquals(otherPin, lanPaired.tlsFingerprint)
        assertEquals(announced, lanPaired.routes)
        val funnelPaired = RouteSelector.adopt(credentials("https://example.ts.net", null, null), announced)
        assertNull("a public pairing never gains a pin", funnelPaired.tlsFingerprint)
        val kept = credentials("https://example.ts.net", null, announced)
        assertEquals(kept, RouteSelector.adopt(kept, null))

        val welcome = BridgeJson.decodeFromString(Welcome.serializer(), """{"protocolVersion":2,"deviceId":"d","routes":"nope","capabilities":{"routeHints":true,"goalView":true}}""")
        assertNull(welcome.routes)
        assertTrue(welcome.capabilities.goalView)
    }

    // ---------- references ----------

    @Test
    fun atWordsAreDetectedButMailAndMentionsAreNot() {
        assertEquals(ReferenceQuery("src/Ma", 4, 11), ReferenceQuery.detect("see @src/Ma now", 11))
        assertEquals(ReferenceQuery("", 0, 1), ReferenceQuery.detect("@", 1))
        assertEquals("my fi", ReferenceQuery.detect("open @\"my fi", 12)!!.query)
        assertNull(ReferenceQuery.detect("mail a@b.com", 12))
        assertNull(ReferenceQuery.detect("@[标题](dsh-session:abc)", 5))
        assertNull(ReferenceQuery.detect("@src", 4, composing = true))
        assertNull(ReferenceQuery.detect("@src", 1, 4))
        assertNull(ReferenceQuery.detect("line\n@\"a\" b", 11))
    }

    @Test
    fun completingWritesPlainTextTheHostUnderstands() {
        val draft = "see @src/Ma now"
        val query = ReferenceQuery.detect(draft, 10)!!
        val file = query.complete(FileReference("src/Main.kt"), draft)!!
        assertEquals("see @src/Main.kt now", file.text)
        assertEquals("see @src/Main.kt ".length, file.caret)

        val dir = query.complete(FileReference("src/main", "directory"), draft)!!
        assertEquals("see @src/main/ now", dir.text)
        assertEquals("see @src/main/".length, dir.caret)

        val spaced = ReferenceQuery.detect("@my", 3)!!.complete(FileReference("my file.txt"), "@my")!!
        assertEquals("@\"my file.txt\" ", spaced.text)
        val quoted = ReferenceQuery.detect("@\"my fi\" tail", 7)!!
        assertEquals("@\"my file.txt\" tail", quoted.complete(FileReference("my file.txt"), "@\"my fi\" tail")!!.text)

        val mention = "@[修复登录](dsh-session:YWJj)"
        val session = query.complete(SessionReference("s2", "修复登录", mention, sameWorkspace = true), draft)!!
        assertEquals("see $mention now", session.text)
        assertNull(query.complete(SessionReference("s2", "x", ""), draft))

        val reply = BridgeJson.decodeFromString(
            ReferenceCandidates.serializer(),
            """{"sessionId":"s1","query":"src","files":{"available":true,"items":[{"path":"src/","kind":"directory"}]},"sessions":{"available":false,"items":[]}}""",
        )
        assertEquals("directory", reply.files.items.single().kind)
        assertFalse(reply.sessions.available)
    }

    // ---------- schedules ----------

    @Test
    fun deleteEndedReceiptCarriesTheCountOnlyWhenFresh() {
        val fresh = BridgeJson.decodeFromString(HostScheduleUpdated.serializer(), """{"clientRequestId":"r1","deletedEnded":3}""")
        assertEquals(3, fresh.deletedEnded)
        val replayed = BridgeJson.decodeFromString(HostScheduleUpdated.serializer(), """{"clientRequestId":"r1","replayed":true}""")
        assertNull(replayed.deletedEnded)
        assertTrue(replayed.replayed)
    }
}
