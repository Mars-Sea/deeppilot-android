package dev.hailab.deeppilot.core.catalog

import dev.hailab.deeppilot.core.protocol.BridgeJson
import kotlinx.coroutines.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors

/** 可重建元数据；配对身份、主机、会话、目录种类均参与键，不保存凭据与操作结果。 */
class CatalogCache(private val root: File, private val scope: CoroutineScope, private val now: () -> Long = System::currentTimeMillis) {
    data class Key(val owner: String, val binding: String, val session: String, val kind: String) {
        val id: String get() = digest(listOf(owner, binding, session, kind))
    }
    private data class Entry(val value: JsonElement, val date: Long, val epoch: String?)
    private data class Pending(val token: Any, val epoch: String, val task: Deferred<Entry>)
    @Serializable private data class Disk(val version: Int, val key: String, val date: Long, val value: JsonElement)
    private val lock = Any()
    private val memory = mutableMapOf<Key, Entry>()
    private val pending = mutableMapOf<Key, Pending>()
    private val revisions = mutableMapOf<Key, Int>()
    private val invalidated = mutableSetOf<Key>()
    private fun file(key: Key) = File(File(root, digest(listOf(key.owner))), key.id + ".json")

    suspend fun <T> load(key: Key, epoch: String, serializer: KSerializer<T>, force: Boolean = false,
        onCached: ((T, Boolean) -> Unit)? = null, diskValue: (T) -> T = { it }, fetch: suspend () -> T): T {
        val revision = synchronized(lock) { revisions[key] ?: 0 }
        val shouldRead = synchronized(lock) { key !in memory && key !in invalidated }
        if (shouldRead) {
            val restored = withContext(io) { read(key) }
            synchronized(lock) {
                if ((revisions[key] ?: 0) != revision) throw CancellationException("目录已失效")
                if (key !in memory && restored != null) memory[key] = restored
            }
        }
        synchronized(lock) { memory[key] }?.let { entry ->
            val age = now() - entry.date
            if (age in 0..RETENTION) {
                val value = runCatching { BridgeJson.decodeFromJsonElement(serializer, entry.value) }.getOrNull()
                if (value != null) {
                    val stale = force || entry.epoch != epoch || age >= LIFETIME
                    onCached?.invoke(value, stale)
                    if (!stale) return value
                }
            }
        }
        val operation = synchronized(lock) {
            pending[key]?.takeIf { it.epoch == epoch } ?: run {
                pending.remove(key)?.task?.cancel()
                val token = Any()
                val task = scope.async(start = CoroutineStart.LAZY) {
                    val value = fetch()
                    currentCoroutineContext().ensureActive()
                    val entry = Entry(BridgeJson.encodeToJsonElement(serializer, value), now(), epoch)
                    synchronized(lock) {
                        if ((revisions[key] ?: 0) != revision || pending[key]?.token !== token) throw CancellationException("目录已失效")
                        memory[key] = entry; invalidated.remove(key)
                        if (memory.size > 64) memory.minByOrNull { it.value.date }?.key?.let { memory.remove(it) }
                    }
                    // 单线程磁盘队列让失效删除总在已经提交的写入之后执行。
                    withContext(io) {
                        val current = synchronized(lock) { (revisions[key] ?: 0) == revision && pending[key]?.token === token }
                        if (current) write(key, Disk(1, key.id, entry.date, BridgeJson.encodeToJsonElement(serializer, diskValue(value))))
                    }
                    entry
                }
                Pending(token, epoch, task).also {
                    pending[key] = it
                    task.invokeOnCompletion { synchronized(lock) { if (pending[key]?.token === token) pending.remove(key) } }
                    task.start()
                }
            }
        }
        try {
            val entry = operation.task.await()
            currentCoroutineContext().ensureActive()
            synchronized(lock) { if ((revisions[key] ?: 0) != revision) throw CancellationException("目录已失效") }
            return BridgeJson.decodeFromJsonElement(serializer, entry.value)
        } finally {
            // 某个界面退出不取消其他调用者共用的请求；只有完成或显式失效才清理。
            synchronized(lock) { if (operation.task.isCompleted && pending[key]?.token === operation.token) pending.remove(key) }
        }
    }
    fun invalidate(owner: String? = null, session: String? = null, kinds: Set<String>? = null) {
        synchronized(lock) {
            (memory.keys + pending.keys + revisions.keys).toSet().filter { (owner == null || it.owner == owner) && (session == null || it.session == session) && (kinds == null || it.kind in kinds) }.forEach { key ->
                revisions[key] = (revisions[key] ?: 0) + 1
                invalidated += key; memory.remove(key); pending.remove(key)?.task?.cancel()
                val target = file(key); scope.launch(io) { target.delete() }
            }
        }
    }
    fun remove(owner: String) {
        invalidate(owner)
        scope.launch(io) { File(root, digest(listOf(owner))).deleteRecursively() }
    }
    private fun read(key: Key): Entry? = runCatching {
        val file = file(key)
        if (!file.isFile || file.length() > MAX_ENTRY) return null
        val disk = BridgeJson.decodeFromString(Disk.serializer(), file.readText())
        if (disk.version != 1 || disk.key != key.id || now() - disk.date !in 0..RETENTION) return null
        Entry(disk.value, disk.date, null)
    }.getOrNull()
    private fun write(key: Key, disk: Disk) {
        runCatching {
            val bytes = BridgeJson.encodeToString(Disk.serializer(), disk).toByteArray(Charsets.UTF_8)
            if (bytes.size > MAX_ENTRY) return
            val target = file(key); target.parentFile!!.mkdirs()
            val temporary = File(target.parentFile, target.name + ".tmp")
            try {
                temporary.writeBytes(bytes)
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally { temporary.delete() }
            var size = 0L
            root.walkTopDown().filter { it.isFile && it.extension == "json" }.sortedByDescending { it.lastModified() }.forEachIndexed { index, file ->
                size += file.length()
                if (index >= 64 || size > 16 * 1024 * 1024) file.delete()
            }
        }
    }
    companion object {
        const val LIFETIME = 300_000L
        const val RETENTION = 7 * 86_400_000L
        private const val MAX_ENTRY = 3 * 1024 * 1024
        private val io = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "catalog-cache").apply { isDaemon = true } }.asCoroutineDispatcher()
        fun digest(parts: List<String>): String = MessageDigest.getInstance("SHA-256")
            .digest(parts.joinToString("") { "${it.length}:$it" }.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
