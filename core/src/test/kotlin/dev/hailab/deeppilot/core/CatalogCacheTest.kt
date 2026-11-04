package dev.hailab.deeppilot.core

import dev.hailab.deeppilot.core.catalog.CatalogCache
import kotlinx.coroutines.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class CatalogCacheTest {
    private val serializer = ListSerializer(String.serializer())
    private val key = CatalogCache.Key("pairing-a", "host-binding", "session", "skills")
    private suspend fun fixture(block: suspend (File, CoroutineScope, AtomicLong) -> Unit) {
        val root = Files.createTempDirectory("catalog-test-").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try { block(root, scope, AtomicLong(1_800_000_000_000)) } finally { scope.cancel(); root.deleteRecursively() }
    }
    @Test fun concurrentLoadsShareOneRequestAndEmptyCatalogStaysCached() = runBlocking { fixture { root, scope, time ->
        val cache = CatalogCache(root, scope, time::get)
        val started = CompletableDeferred<Unit>(); val gate = CompletableDeferred<List<String>>()
        val calls = AtomicInteger()
        val a = async { cache.load(key, "1", serializer) { calls.incrementAndGet(); started.complete(Unit); gate.await() } }
        started.await()
        val b = async { cache.load(key, "1", serializer) { calls.incrementAndGet(); listOf("unexpected") } }
        yield(); gate.complete(emptyList())
        assertEquals(emptyList<String>(), a.await()); assertEquals(emptyList<String>(), b.await())
        assertEquals(emptyList<String>(), cache.load(key, "1", serializer) { calls.incrementAndGet(); listOf("unexpected") })
        assertEquals(1, calls.get())
    } }
    @Test fun expiryShowsPreviousCatalogAndFailureDoesNotReplaceIt() = runBlocking { fixture { root, scope, time ->
        val cache = CatalogCache(root, scope, time::get)
        cache.load(key, "1", serializer) { listOf("old") }
        time.addAndGet(CatalogCache.LIFETIME + 1)
        var seen = emptyList<String>(); var stale = false
        try { cache.load(key, "1", serializer, onCached = { value, old -> seen = value; stale = old }) { throw IllegalStateException("offline") }; fail() } catch (_: IllegalStateException) {}
        assertEquals(listOf("old"), seen); assertTrue(stale)
        assertEquals(listOf("new"), cache.load(key, "1", serializer) { listOf("new") })
    } }
    @Test fun restartRestoresOnlySanitizedDiskValueAndAlwaysRevalidates() = runBlocking { fixture { root, scope, time ->
        CatalogCache(root, scope, time::get).load(key, "1", serializer, diskValue = { listOf("directory-only") }) { listOf("live-selection") }
        val restarted = CatalogCache(root, scope, time::get)
        var seen = emptyList<String>(); var stale = false
        val result = restarted.load(key, "1", serializer, onCached = { value, old -> seen = value; stale = old }) { listOf("validated") }
        assertEquals(listOf("directory-only"), seen); assertTrue(stale); assertEquals(listOf("validated"), result)
    } }
    @Test fun pairingHostSessionAndConnectionNeverShareAuthority() = runBlocking { fixture { root, scope, time ->
        val cache = CatalogCache(root, scope, time::get)
        cache.load(key, "1", serializer) { listOf("private") }
        for (other in listOf(key.copy(owner = "pairing-b"), key.copy(binding = "other-host"), key.copy(session = "other-session"))) {
            var shown = false
            cache.load(other, "1", serializer, onCached = { _, _ -> shown = true }) { listOf("own") }
            assertFalse(shown)
        }
        var stale = false
        cache.load(key, "new-connection", serializer, onCached = { _, old -> stale = old }) { listOf("validated") }
        assertTrue(stale)
    } }
    @Test fun invalidationCancelsOldRequestAndCannotOverwriteNewSnapshot() = runBlocking { fixture { root, scope, time ->
        val cache = CatalogCache(root, scope, time::get)
        val started = CompletableDeferred<Unit>(); val gate = CompletableDeferred<Unit>()
        val old = async { cache.load(key, "1", serializer) { started.complete(Unit); withContext(NonCancellable) { gate.await() }; listOf("old") } }
        started.await(); cache.invalidate(key.owner, key.session, setOf(key.kind))
        cache.load(key, "1", serializer) { listOf("new") }
        gate.complete(Unit)
        try { old.await(); fail() } catch (_: CancellationException) {}
        assertEquals(listOf("new"), cache.load(key, "1", serializer) { listOf("unexpected") })
    } }
    @Test fun cancelledWaiterDoesNotCancelSharedRequest() = runBlocking { fixture { root, scope, time ->
        val cache = CatalogCache(root, scope, time::get)
        val started = CompletableDeferred<Unit>(); val gate = CompletableDeferred<Unit>()
        val a = async { cache.load(key, "1", serializer) { started.complete(Unit); gate.await(); listOf("shared") } }
        started.await()
        val b = async { cache.load(key, "1", serializer) { listOf("unexpected") } }
        yield(); a.cancelAndJoin(); gate.complete(Unit)
        assertEquals(listOf("shared"), b.await())
    } }
    @Test fun incompatibleVersionCorruptionAndOldDiskAreRebuilt() = runBlocking { fixture { root, scope, time ->
        val cache = CatalogCache(root, scope, time::get)
        cache.load(key, "1", serializer) { listOf("old") }
        val file = root.walkTopDown().first { it.extension == "json" }
        for (data in listOf(file.readText().replace("\"version\":1", "\"version\":99"), "broken")) {
            file.writeText(data)
            CatalogCache(root, scope, time::get).load(key, "1", serializer, onCached = { _, _ -> fail("bad cache") }) { listOf("rebuilt") }
        }
        time.addAndGet(CatalogCache.RETENTION + 1)
        CatalogCache(root, scope, time::get).load(key, "1", serializer, onCached = { _, _ -> fail("old cache") }) { emptyList() }
    } }
} 
