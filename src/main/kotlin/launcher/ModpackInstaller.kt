package launcher

import com.google.gson.JsonArray
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipFile

/** Installs Modrinth modpacks (.mrpack) as a new instance. Blocking: call from a background thread. */
object ModpackInstaller {
    // the .mrpack spec only allows downloads from these hosts
    private val allowedHosts = setOf("cdn.modrinth.com", "github.com", "raw.githubusercontent.com", "gitlab.com")
    private const val MAX_ENTRY_BYTES = 1_000_000_000L

    private class Job(val url: String, val dest: File, val sha1: String?)

    fun install(mrpack: File, status: (String) -> Unit, progress: (Int, Int) -> Unit): Instance {
        return ZipFile(mrpack).use { zip ->
            val indexEntry = zip.getEntry("modrinth.index.json")
                ?: throw IllegalStateException("This isn't a Modrinth modpack: modrinth.index.json is missing")
            val index = parseJson(zip.getInputStream(indexEntry).bufferedReader().use { it.readText() })
            if (index.str("game") != "minecraft") throw IllegalStateException("This modpack isn't for Minecraft")
            val deps = index.obj("dependencies") ?: throw IllegalStateException("The modpack has no dependency list")
            val mc = deps.str("minecraft")
                ?: throw IllegalStateException("The modpack doesn't say which Minecraft version it needs")

            val (loader, loaderVersion) = when {
                deps.has("fabric-loader") -> LoaderKind.FABRIC to deps.str("fabric-loader")
                deps.has("quilt-loader") -> LoaderKind.QUILT to deps.str("quilt-loader")
                deps.has("neoforge") -> LoaderKind.NEOFORGE to deps.str("neoforge")
                deps.has("forge") -> LoaderKind.FORGE to deps.str("forge")
                else -> LoaderKind.VANILLA to null
            }

            val name = index.str("name") ?: mrpack.nameWithoutExtension
            val inst = InstanceStore.createModpack(name, mc, loader, loaderVersion)
            try {
                status("Reading ${inst.name}…")
                val jobs = mutableListOf<Job>()
                for (e in index.arr("files") ?: JsonArray()) {
                    val f = e.asJsonObject
                    if (f.obj("env")?.str("client") == "unsupported") continue
                    val path = f.str("path") ?: continue
                    val dest = safeResolve(inst.dir, path)
                    val url = f.arr("downloads")?.map { it.asString }?.firstOrNull { host(it) in allowedHosts }
                        ?: throw IllegalStateException("No allowed download link for $path")
                    jobs += Job(url, dest, f.obj("hashes")?.str("sha1"))
                }
                downloadAll(jobs, status, progress)

                status("Copying the modpack's own files…")
                extractOverrides(zip, "overrides/", inst.dir)
                extractOverrides(zip, "client-overrides/", inst.dir)
            } catch (e: Exception) {
                inst.dir.deleteRecursively()
                throw e
            }
            inst
        }
    }

    private fun host(url: String): String =
        runCatching { URI(url).host }.getOrNull()?.lowercase() ?: ""

    private fun downloadAll(jobs: List<Job>, status: (String) -> Unit, progress: (Int, Int) -> Unit) {
        if (jobs.isEmpty()) return
        val pool = Executors.newFixedThreadPool(8)
        try {
            val done = AtomicInteger(0)
            status("Downloading mods (0/${jobs.size})")
            val futures = jobs.map { j ->
                pool.submit(Callable<Unit> {
                    Http.download(j.url, j.dest, j.sha1)
                    val n = done.incrementAndGet()
                    progress(n, jobs.size)
                    if (n % 5 == 0 || n == jobs.size) status("Downloading mods ($n/${jobs.size})")
                })
            }
            for (f in futures) {
                try {
                    f.get()
                } catch (e: ExecutionException) {
                    throw (e.cause ?: e)
                }
            }
        } finally {
            pool.shutdownNow()
        }
    }

    private fun extractOverrides(zip: ZipFile, prefix: String, dir: File) {
        for (e in zip.entries().toList()) {
            if (e.isDirectory || !e.name.startsWith(prefix)) continue
            val rel = e.name.removePrefix(prefix)
            if (rel.isBlank() || rel == "instance.json") continue
            val out = safeResolve(dir, rel)
            out.parentFile.mkdirs()
            zip.getInputStream(e).use { ins -> out.outputStream().use { o -> copyLimited(ins, o) } }
        }
    }

    private fun copyLimited(ins: InputStream, out: OutputStream) {
        val buf = ByteArray(65536)
        var total = 0L
        while (true) {
            val n = ins.read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_ENTRY_BYTES) throw IllegalStateException("A file inside the modpack is too large")
            out.write(buf, 0, n)
        }
    }

    /** Resolves [rel] inside [base] and refuses anything that would escape it (zip-slip / "..\" tricks). */
    private fun safeResolve(base: File, rel: String): File {
        if (rel.isBlank()) throw SecurityException("Empty path in modpack")
        val b = base.canonicalFile
        val f = File(base, rel).canonicalFile
        if (!f.path.startsWith(b.path + File.separator)) throw SecurityException("Unsafe path in modpack: $rel")
        return f
    }
}
