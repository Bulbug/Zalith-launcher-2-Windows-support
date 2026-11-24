package launcher

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Global download manager for all downloadable content. */
object DownloadManager {
    enum class Status { PENDING, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED }

    data class DownloadTask(
        val id: String,
        val title: String,
        val url: String,
        val dest: File,
        val sha1: String? = null,
        val sha256: String? = null,
        val expectedSize: Long? = null,
        val category: String,           // "mod", "modpack", "resourcepack", "shader", "game", "library", "asset"
        var status: Status = Status.PENDING,
        var progress: Float = 0f,       // 0..1
        var downloadedBytes: Long = 0,
        var totalBytes: Long = 0,
        var speedBytesPerSec: Long = 0,
        var error: String? = null,
        var startTime: Long = 0,
        var endTime: Long = 0,
    )

    private val tasks = ConcurrentHashMap<String, DownloadTask>()
    private val listeners = ConcurrentHashMap<Int, (DownloadTask) -> Unit>()
    private var nextListenerId = AtomicInteger(0)

    /** Add a download task. Returns the task ID. */
    fun enqueue(task: DownloadTask): String {
        tasks[task.id] = task
        notifyListeners(task)
        return task.id
    }

    /** Start or resume a download task. */
    fun start(taskId: String, scope: kotlinx.coroutines.CoroutineScope) {
        val task = tasks[taskId] ?: return
        if (task.status == Status.DOWNLOADING) return
        scope.launch(Dispatchers.IO) { downloadTask(task) }
    }

    /** Pause a download task. */
    fun pause(taskId: String) {
        val task = tasks[taskId] ?: return
        if (task.status == Status.DOWNLOADING) {
            task.status = Status.PAUSED
            notifyListeners(task)
        }
    }

    /** Cancel a download task. */
    fun cancel(taskId: String) {
        val task = tasks[taskId] ?: return
        task.status = Status.CANCELLED
        notifyListeners(task)
    }

    /** Remove a completed/failed/cancelled task from the queue. */
    fun remove(taskId: String) {
        tasks.remove(taskId)
    }

    /** Clear all completed tasks. */
    fun clearCompleted() {
        tasks.entries().removeIf { it.value.status in setOf(Status.COMPLETED, Status.FAILED, Status.CANCELLED) }
    }

    /** Get all tasks. */
    fun getAll(): List<DownloadTask> = tasks.values.toList()

    /** Get tasks by status. */
    fun getByStatus(status: Status): List<DownloadTask> = tasks.values.filter { it.status == status }

    /** Get a specific task. */
    fun get(taskId: String): DownloadTask? = tasks[taskId]

    /** Register a listener for task updates. Returns listener ID for unregistration. */
    fun addListener(listener: (DownloadTask) -> Unit): Int {
        val id = nextListenerId.incrementAndGet()
        listeners[id] = listener
        // Send current state of all tasks
        tasks.values.forEach { listener(it) }
        return id
    }

    /** Unregister a listener. */
    fun removeListener(id: Int) {
        listeners.remove(id)
    }

    private fun notifyListeners(task: DownloadTask) {
        listeners.values.forEach { it(task) }
    }

    /** Actual download implementation with progress reporting. */
    private suspend fun downloadTask(task: DownloadTask) {
        task.status = Status.DOWNLOADING
        task.startTime = System.currentTimeMillis()
        notifyListeners(task)

        try {
            task.dest.parentFile?.mkdirs()
            val tmp = File(task.dest.path + ".part")

            // Support resumable downloads
            var startByte = 0L
            if (tmp.isFile && tmp.length() > 0) {
                startByte = tmp.length()
            }

            val client = Http.client
            val reqBuilder = java.net.http.HttpRequest.newBuilder(java.net.URI.create(task.url))
                .timeout(java.time.Duration.ofMinutes(10))
                .header("User-Agent", Http.UA)

            if (startByte > 0) {
                reqBuilder.header("Range", "bytes=$startByte-")
            }

            val req = reqBuilder.GET().build()
            val res = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofFile(tmp.toPath()))

            val statusCode = res.statusCode()
            if (statusCode !in 200..299 && statusCode != 206) {
                throw java.io.IOException("HTTP $statusCode for ${task.url}")
            }

            // Get total size from Content-Length or Content-Range
            val contentLength = res.headers().firstValue("Content-Length")
                .flatMap { it.toLongOrNull() }
                ?: res.headers().firstValue("Content-Range")
                    ?.split("/")?.lastOrNull()
                    ?.toLongOrNull()
                    ?: task.expectedSize ?: 0

            task.totalBytes = contentLength + startByte
            task.downloadedBytes = startByte
            notifyListeners(task)

            // Read the response body and track progress
            // Note: This is a simplified version - in production you'd want to stream and track bytes
            if (task.sha1 != null && !Http.sha1Of(tmp).equals(task.sha1, ignoreCase = true)) {
                throw java.io.IOException("Checksum mismatch (SHA-1) for ${task.url}")
            }
            if (task.sha256 != null && !Http.sha256Of(tmp).equals(task.sha256, ignoreCase = true)) {
                throw java.io.IOException("Checksum mismatch (SHA-256) for ${task.url}")
            }
            if (task.expectedSize != null && tmp.length() != task.expectedSize) {
                throw java.io.IOException("File size mismatch for ${task.url}")
            }

            java.nio.file.Files.move(tmp.toPath(), task.dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            task.status = Status.COMPLETED
            task.progress = 1f
            task.endTime = System.currentTimeMillis()
            notifyListeners(task)

        } catch (e: Exception) {
            task.status = Status.FAILED
            task.error = e.message
            task.endTime = System.currentTimeMillis()
            notifyListeners(task)
        }
    }

    /** Save queue state to disk for persistence across restarts. */
    fun saveState() {
        val file = File(Dirs.root, "download_queue.json")
        val gson = Gson()
        val arr = JsonArray()
        tasks.values.forEach { task ->
            val o = JsonObject().apply {
                addProperty("id", task.id)
                addProperty("title", task.title)
                addProperty("url", task.url)
                addProperty("dest", task.dest.absolutePath)
                addProperty("sha1", task.sha1)
                addProperty("sha256", task.sha256)
                addProperty("expectedSize", task.expectedSize ?: 0)
                addProperty("category", task.category)
                addProperty("status", task.status.name)
                addProperty("progress", task.progress)
                addProperty("downloadedBytes", task.downloadedBytes)
                addProperty("totalBytes", task.totalBytes)
                addProperty("speedBytesPerSec", task.speedBytesPerSec)
                addProperty("error", task.error)
                addProperty("startTime", task.startTime)
                addProperty("endTime", task.endTime)
            }
            arr.add(o)
        }
        file.writeText(gson.toJson(arr))
    }

    /** Load queue state from disk. */
    fun loadState(scope: kotlinx.coroutines.CoroutineScope) {
        val file = File(Dirs.root, "download_queue.json")
        if (!file.isFile) return
        try {
            val arr = JsonParser.parseString(file.readText()).asJsonArray
            val gson = Gson()
            arr.forEach { elem ->
                val o = elem.asJsonObject
                val task = DownloadTask(
                    id = o.get("id").asString,
                    title = o.get("title").asString,
                    url = o.get("url").asString,
                    dest = File(o.get("dest").asString),
                    sha1 = o.get("sha1").asString?.takeIf { it.isNotBlank() },
                    sha256 = o.get("sha256").asString?.takeIf { it.isNotBlank() },
                    expectedSize = o.get("expectedSize").asLong?.takeIf { it > 0 },
                    category = o.get("category").asString,
                    status = Status.valueOf(o.get("status").asString),
                    progress = o.get("progress").asFloat,
                    downloadedBytes = o.get("downloadedBytes").asLong,
                    totalBytes = o.get("totalBytes").asLong,
                    speedBytesPerSec = o.get("speedBytesPerSec").asLong,
                    error = o.get("error").asString?.takeIf { it.isNotBlank() },
                    startTime = o.get("startTime").asLong,
                    endTime = o.get("endTime").asLong,
                )
                // Only re-queue pending/failed tasks, not completed ones
                if (task.status in setOf(Status.PENDING, Status.FAILED, Status.DOWNLOADING)) {
                    tasks[task.id] = task.copy(status = Status.PENDING)
                    // Auto-resume pending downloads
                    if (task.status == Status.PENDING) {
                        start(task.id, scope)
                    }
                } else {
                    tasks[task.id] = task
                }
            }
        } catch (e: Exception) {
            // Ignore load errors
        }
    }
}