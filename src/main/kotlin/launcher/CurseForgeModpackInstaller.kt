package launcher

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipFile

/** Installs CurseForge modpacks (.zip) as a new instance. Blocking: call from a background thread. */
object CurseForgeModpackInstaller {
    // CurseForge modpack manifest format
    private const val MAX_ENTRY_BYTES = 1_000_000_000L

    private class Job(val url: String, val dest: File, val sha1: String?)

    fun install(modpackZip: File, status: (String) -> Unit, progress: (Int, Int) -> Unit): Instance {
        return ZipFile(modpackZip).use { zip ->
            // Read manifest.json from the modpack
            val manifestEntry = zip.getEntry("manifest.json")
                ?: throw IllegalStateException("This isn't a CurseForge modpack: manifest.json is missing")
            val manifest = JsonParser.parseString(zip.getInputStream(manifestEntry).bufferedReader().use { it.readText() }).asJsonObject

            // Parse manifest
            val minecraft = manifest.obj("minecraft") ?: throw IllegalStateException("Manifest missing 'minecraft' section")
            val mcVersion = minecraft.str("version") ?: throw IllegalStateException("Manifest missing Minecraft version")
            val modLoaders = minecraft.arr("modLoaders") ?: JsonArray()

            val (loader, loaderVersion) = parseModLoaders(modLoaders, mcVersion)

            val name = manifest.str("name") ?: modpackZip.nameWithoutExtension
            val inst = InstanceStore.createModpack(name, mcVersion, loader, loaderVersion)

            try {
                status("Reading ${inst.name}…")

                // Download files listed in manifest
                val files = manifest.arr("files") ?: JsonArray()
                val jobs = mutableListOf<Job>()

                for (e in files) {
                    val f = e.asJsonObject
                    val projectId = f.get("projectID")?.asInt ?: f.get("projectId")?.asInt ?: continue
                    val fileId = f.get("fileID")?.asInt ?: f.get("fileId")?.asInt ?: continue
                    val required = f.get("required")?.asBoolean ?: true

                    if (!required) continue // Skip optional files for now

                    // Use CurseForge API to get download URL
                    val downloadUrl = getDownloadUrl(projectId, fileId)
                        ?: throw IllegalStateException("No download URL for project $projectId file $fileId")

                    val fileName = f.str("fileName") ?: "file_$fileId.jar"
                    val dest = safeResolve(inst.dir, "mods/$fileName")

                    jobs += Job(downloadUrl, dest, null)
                }

                if (jobs.isNotEmpty()) {
                    downloadAll(jobs, status, progress)
                }

                // Extract overrides
                status("Copying the modpack's own files…")
                extractOverrides(zip, "overrides/", inst.dir)
                extractOverrides(zip, "overrides/", inst.dir)

            } catch (e: Exception) {
                inst.dir.deleteRecursively()
                throw e
            }
            inst
        }
    }

    private fun parseModLoaders(modLoaders: JsonArray, mcVersion: String): Pair<LoaderKind, String?> {
        for (ml in modLoaders) {
            val obj = ml.asJsonObject
            val loaderType = obj.str("id") ?: ""
            val loaderVersion = obj.str("version")

            when (loaderType.lowercase()) {
                "fabric" -> return LoaderKind.FABRIC to loaderVersion
                "quilt" -> return LoaderKind.QUILT to loaderVersion
                "neoforge" -> return LoaderKind.NEOFORGE to loaderVersion
                "forge" -> return LoaderKind.FORGE to loaderVersion
            }
        }
        return LoaderKind.VANILLA to null
    }

    private fun getDownloadUrl(projectId: Int, fileId: Int): String? {
        try {
            val fileInfo = parseJson(Http.getString("https://api.curseforge.com/v1/mods/$projectId/files/$fileId", CurseForge.headers())).obj("data")
            return fileInfo?.str("downloadUrl")
        } catch (e: Exception) {
            return null
        }
    }

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
            if (rel.isBlank()) continue
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