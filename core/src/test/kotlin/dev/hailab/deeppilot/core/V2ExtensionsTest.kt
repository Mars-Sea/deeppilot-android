package dev.hailab.deeppilot.core

import dev.hailab.deeppilot.core.bridge.AttemptId
import dev.hailab.deeppilot.core.protocol.BridgeJson
import dev.hailab.deeppilot.core.protocol.Envelope
import dev.hailab.deeppilot.core.protocol.FrameType
import dev.hailab.deeppilot.core.protocol.HostScheduleCreate
import dev.hailab.deeppilot.core.protocol.HostScheduleEdit
import dev.hailab.deeppilot.core.protocol.HostSchedulePatch
import dev.hailab.deeppilot.core.protocol.HostScheduleSnapshot
import dev.hailab.deeppilot.core.protocol.HostScheduleTarget
import dev.hailab.deeppilot.core.protocol.HostScheduleTiming
import dev.hailab.deeppilot.core.protocol.HostTimingKind
import dev.hailab.deeppilot.core.protocol.PluginUpdateProgress
import dev.hailab.deeppilot.core.protocol.PluginUpdateStatus
import dev.hailab.deeppilot.core.protocol.SessionDelta
import dev.hailab.deeppilot.core.protocol.SessionExportFormat
import dev.hailab.deeppilot.core.protocol.Welcome
import dev.hailab.deeppilot.core.protocol.WireErrorCode
import dev.hailab.deeppilot.core.schedule.HostScheduleList
import dev.hailab.deeppilot.core.schedule.ScheduleMode
import dev.hailab.deeppilot.core.session.TransientStreams
import dev.hailab.deeppilot.core.transfer.AttachmentChunkAssembler
import dev.hailab.deeppilot.core.transfer.ExportChunkWriter
import dev.hailab.deeppilot.core.transfer.MalformedTransferException
import dev.hailab.deeppilot.core.update.PluginUpdateState
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class V2ExtensionsTest {
    private fun binary(headerJson: String, body: ByteArray, format: Int = 1, length: Int? = null): ByteArray {
        val header = headerJson.toByteArray()
        return byteArrayOf(format.toByte()) + ByteBuffer.allocate(4).putInt(length ?: header.size).array() + header + body
    }

    @Test
    fun binaryFrameParsesHeaderAndBody() {
        val envelope = Envelope.parseBinary(binary("""{"v":2,"type":"s2c.session.export.chunk","id":"r1","payload":{"index":0}}""", byteArrayOf(7, 8)))!!
        assertEquals("r1", envelope.id)
        assertEquals(listOf<Byte>(7, 8), envelope.body!!.toList())
        assertNull(Envelope.parseBinary(binary("{}", byteArrayOf(), format = 2)))
        assertNull(Envelope.parseBinary(binary("""{"v":2,"type":"x"}""", byteArrayOf(), length = 999)))
        assertNull(Envelope.parseBinary(binary("not json", byteArrayOf())))
        assertNull(Envelope.parseBinary(byteArrayOf(1, 0)))
    }

    @Test
    fun newWelcomeCapabilitiesAndErrorCodesDecode() {
        val welcome = BridgeJson.decodeFromString(Welcome.serializer(),
            """{"protocolVersion":2,"deviceId":"d","capabilities":{"binaryFrames":true,"assistantStream":true,"schedulesHost":true,"pluginUpdate":true,"sessionMarkdown":true,"promptQueue":true,"future":1}}""")
        with(welcome.capabilities) {
            assertTrue(binaryFrames && assistantStream && schedulesHost && pluginUpdate && sessionMarkdown && promptQueue)
        }
        assertEquals(WireErrorCode.E_TOO_LARGE, WireErrorCode.of("E_TOO_LARGE"))
        assertEquals(WireErrorCode.E_STEER_UNAVAILABLE, WireErrorCode.of("E_STEER_UNAVAILABLE"))
    }

    @Test
    fun attemptIdIsAppendedOnlyWhenValid() {
        val url = "https://h:1/phone?at=old&x=1".toHttpUrl()
        assertEquals("https://h:1/phone?x=1&at=a1b2c3d4", AttemptId.handshakeUrl(url, "a1b2c3d4").toString())
        assertEquals(url, AttemptId.handshakeUrl(url, "bad id!"))
        assertEquals(url, AttemptId.handshakeUrl(url, "x".repeat(17)))
        repeat(50) { assertTrue(AttemptId.isValid(AttemptId.make()) && AttemptId.make().length == 8) }
    }

    @Test
    fun attachmentAssemblerChecksOrderSizeAndReceipt() {
        fun chunk(index: Int, size: Int) = Envelope.parseBinary(
            binary("""{"v":2,"type":"s2c.session.attachment.chunk","id":"r","payload":{"index":$index}}""", ByteArray(size)),
        )!!
        fun ack(chunks: Int, bytes: Int) = Envelope.parse("""{"v":2,"type":"s2c.ack","id":"r","payload":{"chunks":$chunks,"bytes":$bytes}}""")!!
        val ok = AttachmentChunkAssembler()
        ok.append(chunk(0, 10)); ok.append(chunk(1, 5))
        assertEquals(15, ok.finish(ack(2, 15))!!.size)
        assertNull(ok.finish(ack(2, 14)))
        val gap = AttachmentChunkAssembler()
        assertThrows { gap.append(chunk(1, 1)) }
        assertThrows { AttachmentChunkAssembler().append(chunk(0, AttachmentChunkAssembler.MAX_CHUNK_BYTES + 1)) }
        assertNull(AttachmentChunkAssembler().finish(ack(0, 0)))
    }

    @Test
    fun exportWriterTakesTextOrRequestedBinaryChunks() {
        val text = ByteArrayOutputStream()
        val writer = ExportChunkWriter(text, binary = false)
        writer.append(Envelope.parse("""{"v":2,"type":"s2c.session.export.chunk","id":"r","payload":{"index":0,"data":"aGk="}}""")!!)
        assertEquals("hi", text.toString())
        assertEquals(1, writer.chunks); assertEquals(2L, writer.bytes)
        val binaryChunk = Envelope.parseBinary(binary("""{"v":2,"type":"s2c.session.export.chunk","id":"r","payload":{"index":0}}""", byteArrayOf(1, 2, 3)))!!
        assertThrows { ExportChunkWriter(ByteArrayOutputStream(), binary = false).append(binaryChunk) }
        val raw = ByteArrayOutputStream()
        ExportChunkWriter(raw, binary = true).append(binaryChunk)
        assertEquals(3, raw.size())
    }

    @Test
    fun exportFormatChecksTheReceiptType() {
        assertTrue(SessionExportFormat.Zip.accepts(null))
        assertTrue(SessionExportFormat.Zip.accepts("application/zip"))
        assertFalse(SessionExportFormat.Markdown.accepts(null))
        assertTrue(SessionExportFormat.Markdown.accepts("text/markdown; charset=utf-8"))
        assertTrue(SessionExportFormat.Markdown.accepts("text/markdown; charset=\"UTF-8\""))
        assertFalse(SessionExportFormat.Markdown.accepts("text/markdown; charset=latin1"))
        assertFalse(SessionExportFormat.Zip.accepts("text/markdown"))
    }

    private fun delta(stream: String, kind: String, text: String = "") = SessionDelta("s1", stream, kind, text, 1)

    @Test
    fun transientStreamsKeepArrivalOrderAndRetire() {
        val streams = TransientStreams()
        assertTrue(streams.receive(delta("a", "stream.start")))
        streams.receive(delta("a", "thinking.delta", "think "))
        streams.receive(delta("a", "thinking.delta", "more"))
        streams.receive(delta("a", "message.delta", "Hel"))
        streams.receive(delta("a", "message.delta", "lo"))
        assertFalse(streams.receive(delta("a", "message.delta", "")))
        val stream = streams.streams.single()
        assertEquals("think more", stream.thinking)
        assertEquals("Hello", stream.text)
        assertEquals(listOf(false, true), stream.presentations.map { it.streaming })
        assertEquals("think more", stream.presentations.first().thinking)

        assertTrue(streams.finish("a"))
        assertFalse(streams.receive(delta("a", "message.delta", "late")))
        assertTrue(streams.streams.isEmpty())

        streams.receive(delta("b", "message.delta", "x"))
        assertTrue(streams.receive(delta("b", "stream.end")))
        streams.receive(delta("c", "message.delta", "y"))
        assertTrue(streams.finish())
        assertTrue(streams.clear().not() && streams.streams.isEmpty())
        assertTrue(streams.receive(delta("a", "message.delta", "after clear")))
    }

    @Test
    fun hostScheduleTimingAndTargetRoundTrip() {
        val page = BridgeJson.decodeFromString(HostScheduleSnapshot.serializer(), """{"tasks":[
            {"id":"t1","title":"a","prompt":"p","timing":{"kind":"daily","daily":{"time":"09:00","time_zone":"Asia/Shanghai"}},"target":{"kind":"session","sessionId":"s1"},"status":"active","updatedAt":"u1"},
            {"id":"t2","title":"b","prompt":"p","timing":{"kind":"at","at":{"date":"2026-11-05","time":"09:00","time_zone":"UTC"}},"target":{"kind":"new_once","sessionId":"r1"},"status":"paused"},
            {"id":"t3","title":"c","prompt":"p","timing":{"kind":"at","at":"2026-11-05T09:00:00Z"},"target":{"kind":"new"},"status":"ended"},
            {"id":"broken"}
        ],"shown":4,"total":9,"offset":0}""")
        assertEquals(listOf("t1", "t2", "t3"), page.tasks.map { it.id })
        assertEquals("Asia/Shanghai", page.tasks[0].timing.daily!!.timeZone)
        assertEquals("2026-11-05", page.tasks[1].timing.atDateTime!!.date)
        assertEquals("2026-11-05T09:00:00Z", page.tasks[2].timing.atInstant)
        assertNull(page.tasks[1].target.fixedSessionId)

        val create = BridgeJson.encodeToJsonElement(HostScheduleCreate.serializer(),
            HostScheduleCreate("r", "t", "p", HostScheduleTiming.every(3600), HostScheduleTarget.session("s1"))).jsonObject
        assertEquals("every", create.getValue("timing").jsonObject.getValue("kind").jsonPrimitive.content)
        assertEquals("3600.0", create.getValue("timing").jsonObject.getValue("every_seconds").jsonPrimitive.content)
        assertFalse(create.getValue("timing").jsonObject.containsKey("at"))
        assertEquals(HostTimingKind.AT, HostScheduleTiming.at("2026-01-01T00:00:00Z").kind)

        val edit = BridgeJson.encodeToJsonElement(HostScheduleEdit.serializer(),
            HostScheduleEdit("r", "t1", "u1", "update", HostSchedulePatch(active = false))).jsonObject
        assertEquals("false", edit.getValue("patch").jsonObject.getValue("active").jsonPrimitive.content)
        assertFalse(edit.getValue("patch").jsonObject.containsKey("title"))
    }

    @Test
    fun hostScheduleListPagesAndFilters() {
        fun page(ids: List<String>, offset: Int, total: Int, session: String = "s1") = BridgeJson.decodeFromString(HostScheduleSnapshot.serializer(),
            """{"tasks":[${ids.joinToString(",") { """{"id":"$it","timing":{"kind":"every","every_seconds":60},"target":{"kind":"session","sessionId":"$session"},"status":"active"}""" }}],"shown":${ids.size},"total":$total,"offset":$offset}""")
        var list = HostScheduleList().applying(page(listOf("a", "b"), 0, 3), reset = true, requestedOffset = 0)
        assertTrue(list.canLoadMore)
        list = list.applying(page(listOf("b", "c"), 2, 3), reset = false, requestedOffset = 2)
        assertEquals(listOf("a", "b", "c"), list.tasks.map { it.id })
        assertFalse(list.canLoadMore)
        assertEquals(2, list.replacing("a", null).tasks.size)
        val filtered = HostScheduleList().applying(page(listOf("x"), 0, 1, session = "other"), reset = true, requestedOffset = 0, sessionId = "s1")
        assertTrue(filtered.tasks.isEmpty())
        assertEquals(ScheduleMode.Host, ScheduleMode.resolve(schedules = true, schedulesHost = true))
        assertEquals(ScheduleMode.Legacy, ScheduleMode.resolve(schedules = true, schedulesHost = false))
    }

    @Test
    fun pluginUpdateTrustsTheJournalAfterReconnect() {
        val status = BridgeJson.decodeFromString(PluginUpdateStatus.serializer(),
            """{"currentVersion":"0.9.12","protocolVersion":2,"latestVersion":"0.9.13","updateAvailable":true,"compatible":true,"protocolCompatible":true,
            "versions":[{"version":"0.9.13","compatible":true,"protocolVersion":2},{"version":"0.9.14","compatible":true}],"checkedAt":1}""")
        var state = PluginUpdateState().checked(status, 0)
        assertTrue(state.fresh)
        assertTrue(state.selectable("0.9.13"))
        assertFalse(state.selectable("0.9.14"))
        assertFalse(state.selectable("0.9.12"))

        state = state.startedApply("req")
        assertTrue(state.applying)
        // The socket drops while the host swaps the plugin: not a failure.
        state = state.applyFailed("req", rejected = false)
        assertTrue(state.applying)
        assertEquals(PluginUpdateState.Error.ApplyUnknown, state.error)
        state = state.received(PluginUpdateProgress("other", "done"))
        assertTrue(state.applying)

        val after = status.copy(currentVersion = "0.9.13",
            journal = BridgeJson.decodeFromString(dev.hailab.deeppilot.core.protocol.PluginUpdateJournal.serializer(),
                """{"clientRequestId":"req","fromVersion":"0.9.12","toVersion":"0.9.13","phase":"done","startedAt":1}"""))
        state = state.checked(after, state.revision)
        assertFalse(state.applying)
        assertEquals("done", state.progress!!.phase)
        // A late non-terminal progress cannot reopen it.
        assertEquals("done", state.received(PluginUpdateProgress("req", "installing")).progress!!.phase)

        val rejected = PluginUpdateState().checked(status, 0).startedApply("r2").applyFailed("r2", rejected = true)
        assertFalse(rejected.applying)
        assertEquals(PluginUpdateState.Error.ApplyRejected, rejected.error)
        assertEquals(PluginUpdateState.Error.CheckFailed, PluginUpdateState().checked(status.copy(protocolVersion = 3), 0).error)
    }

    @Test
    fun frameTypeConstantsMatchTheSpec() {
        assertTrue(FrameType.SESSION_EXPORT_CHUNK in FrameType.CHUNK_TYPES && FrameType.SESSION_ATTACHMENT_CHUNK in FrameType.CHUNK_TYPES)
        assertNull(Envelope(type = FrameType.PLUGIN_UPDATE_PROGRESS, seq = 3).replaySeq)
        assertEquals(3L, Envelope(type = FrameType.SESSION_EVENT, seq = 3).replaySeq)
    }

    private fun assertThrows(block: () -> Unit) {
        try {
            block()
        } catch (_: MalformedTransferException) {
            return
        }
        throw AssertionError("expected MalformedTransferException")
    }
}
