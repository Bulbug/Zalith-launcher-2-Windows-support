package launcher

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipFile

/** Everything the launcher needs after a version has been downloaded. */
class Installed(
    val id: String,
    val json: JsonObject,
    val javaMajor: Int,
    val classpath: List<File>,   // libraries, in order
    val clientJar: File,
    val nativesDir: File,
    val assetsIndexId: String,
    val virtualAssetsDir: File?, // only for very old versions
    val logConfig: File?,
    val logArg: String?,
)

private class Dl(val url: String, val dest: File, val sha1: String?)

object GameInstaller {

    suspend fun install(
        entry: VersionEntry,
        status: (String) -> Unit,
        progress: (done: Int, total: Int) -> Unit,
    ): Installed = withContext(Dispatchers.IO) {
        val id = entry.id
        val vdir = File(Dirs.versions, id)

        status("Downloading version info…")
        val vjson = File(vdir, "$id.json")
        Http.download(entry.url, vjson, entry.sha1)
        val json = parseJson(vjson.readText())

        if (json.has("inheritsFrom")) {
            error("Modded versions (Fabric/Forge) aren't supported yet - that's phase 2.")
        }

        // ---- client jar ----
        val client = json.obj("downloads")?.obj("client") ?: error("This version has no client download")
        val clientJar = File(vdir, "$id.jar")
        status("Downloading Minecraft $id…")
        Http.download(client.str("url")!!, clientJar, client.str("sha1"))

        // ---- libraries ----
        val libTasks = mutableListOf<Dl>()
        val classpath = mutableListOf<File>()
        val nativeJars = mutableListOf<Pair<File, List<String>>>()

        for (e in json.arr("libraries") ?: JsonArray()) {
            val lib = e.asJsonObject
            if (!Rules.allowed(lib.arr("rules"))) continue
            val name = lib.str("name") ?: continue
            val downloads = lib.obj("downloads")
            val artifact = downloads?.obj("artifact")

            if (artifact != null) {
                val f = File(Dirs.libraries, artifact.str("path")!!)
                val url = artifact.str("url")
                if (!url.isNullOrBlank()) libTasks += Dl(url, f, artifact.str("sha1"))
                classpath += f
            } else if (downloads == null) {
                // old/loader-style entry: build the Maven path ourselves
                val path = mavenPath(name)
                val base = (lib.str("url") ?: "https://libraries.minecraft.net/").trimEnd('/')
                val f = File(Dirs.libraries, path)
                libTasks += Dl("$base/$path", f, null)
                classpath += f
            }

            // old-style natives (pre-1.19): a classifier jar we unpack into the natives folder
            val nativeKey = lib.obj("natives")?.str("windows")
            if (nativeKey != null) {
                val classifier = nativeKey.replace("\${arch}", if (Rules.is64) "64" else "32")
                val na = downloads?.obj("classifiers")?.obj(classifier)
                if (na != null) {
                    val f = File(Dirs.libraries, na.str("path")!!)
                    libTasks += Dl(na.str("url")!!, f, na.str("sha1"))
                    val exclude = lib.obj("extract")?.arr("exclude")?.map { it.asString } ?: emptyList()
                    nativeJars += f to exclude
                }
            }
        }
        downloadAll(libTasks, "Downloading libraries", status, progress)

        val nativesDir = File(vdir, "natives")
        nativesDir.mkdirs()
        for ((jar, exclude) in nativeJars) extractNatives(jar, nativesDir, exclude)

        // ---- assets ----
        val ai = json.obj("assetIndex") ?: error("This version has no asset index")
        val aid = ai.str("id")!!
        val indexFile = File(Dirs.assets, "indexes/$aid.json")
        status("Downloading asset index…")
        Http.download(ai.str("url")!!, indexFile, ai.str("sha1"))
        val index = parseJson(indexFile.readText())
        val objects = index.obj("objects") ?: JsonObject()

        val assetTasks = objects.entrySet().map { (_, v) ->
            val h = v.asJsonObject.str("hash")!!
            val p = h.substring(0, 2)
            Dl("https://resources.download.minecraft.net/$p/$h", File(Dirs.assets, "objects/$p/$h"), h)
        }
        downloadAll(assetTasks, "Downloading assets", status, progress)

        // very old versions (<= 1.7.2) want a "virtual" asset folder or a resources folder
        var virtualDir: File? = null
        val virtual = index.get("virtual")?.asBoolean == true
        val mapToRes = index.get("map_to_resources")?.asBoolean == true
        if (virtual || mapToRes) {
            val target = if (virtual) File(Dirs.assets, "virtual/$aid") else File(Dirs.game, "resources")
            for ((name, v) in objects.entrySet()) {
                val h = v.asJsonObject.str("hash")!!
                val src = File(Dirs.assets, "objects/${h.substring(0, 2)}/$h")
                val dst = File(target, name)
                if (!dst.exists()) {
                    dst.parentFile.mkdirs()
                    src.copyTo(dst)
                }
            }
            if (virtual) virtualDir = target
        }

        // ---- log4j config (important: patches the Log4Shell hole in 1.7 - 1.18.0) ----
        var logFile: File? = null
        var logArg: String? = null
        val logging = json.obj("logging")?.obj("client")
        val lf = logging?.obj("file")
        if (logging != null && lf != null) {
            logFile = File(Dirs.assets, "log_configs/${lf.str("id")}")
            Http.download(lf.str("url")!!, logFile, lf.str("sha1"))
            logArg = logging.str("argument")
        }

        Installed(
            id = id,
            json = json,
            javaMajor = json.obj("javaVersion")?.int("majorVersion") ?: 8,
            classpath = classpath,
            clientJar = clientJar,
            nativesDir = nativesDir,
            assetsIndexId = aid,
            virtualAssetsDir = virtualDir,
            logConfig = logFile,
            logArg = logArg,
        )
    }

    private suspend fun downloadAll(
        tasks: List<Dl>,
        label: String,
        status: (String) -> Unit,
        progress: (Int, Int) -> Unit,
    ) {
        val list = tasks.distinctBy { it.dest.path }
        if (list.isEmpty()) return
        val done = AtomicInteger(0)
        val sem = Semaphore(16)
        status("$label (0/${list.size})")
        coroutineScope {
            list.map { t ->
                async {
                    sem.withPermit { Http.download(t.url, t.dest, t.sha1) }
                    val n = done.incrementAndGet()
                    progress(n, list.size)
                    if (n % 25 == 0 || n == list.size) status("$label ($n/${list.size})")
                }
            }.awaitAll()
        }
    }

    private fun extractNatives(jar: File, dest: File, exclude: List<String>) {
        ZipFile(jar).use { zip ->
            for (entry in zip.entries().toList()) {
                if (entry.isDirectory) continue
                val n = entry.name
                if (exclude.any { n.startsWith(it) }) continue
                if (!n.endsWith(".dll")) continue
                val out = File(dest, File(n).name)
                zip.getInputStream(entry).use { ins -> out.outputStream().use { ins.copyTo(it) } }
            }
        }
    }

    private fun mavenPath(name: String): String {
        val p = name.split(":")
        val group = p[0].replace('.', '/')
        val art = p[1]
        val ver = p[2]
        val cls = if (p.size > 3) "-${p[3]}" else ""
        return "$group/$art/$ver/$art-$ver$cls.jar"
    }
}
