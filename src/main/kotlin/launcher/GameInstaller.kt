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
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipFile

/** Everything the launcher needs after a version has been downloaded. */
data class Installed(
    val id: String,                       // used as ${version_name}
    val json: JsonObject,                 // merged version JSON (vanilla + loader)
    val javaMajor: Int,
    val libs: List<Pair<String, File>>,   // (group:artifact[:classifier], jar), in classpath order
    val clientJar: File,
    val nativesDir: File,
    val assetsIndexId: String,
    val virtualAssetsDir: File?,          // only for very old versions
    val logConfig: File?,
    val logArg: String?,
    val gameDir: File = Dirs.game,        // where saves / mods / config live
)

private class Dl(val url: String, val dest: File, val sha1: String?)

private class LibPlan {
    val tasks = mutableListOf<Dl>()
    val libs = mutableListOf<Pair<String, File>>()
    val nativeJars = mutableListOf<Pair<File, List<String>>>()
}

object GameInstaller {

    suspend fun install(
        entry: VersionEntry,
        loader: LoaderKind,
        loaderVersion: String?,
        gameDir: File,
        status: (String) -> Unit,
        progress: (done: Int, total: Int) -> Unit,
    ): Installed {
        val base = installVanilla(entry, gameDir, status, progress)
        val result = if (loader == LoaderKind.VANILLA) base
        else addLoader(base, entry, loader, loaderVersion, status, progress)
        return result.copy(gameDir = gameDir)
    }

    // ------------------------------------------------------------------ vanilla

    private suspend fun installVanilla(
        entry: VersionEntry,
        gameDir: File,
        status: (String) -> Unit,
        progress: (Int, Int) -> Unit,
    ): Installed = withContext(Dispatchers.IO) {
        val id = entry.id
        val vdir = File(Dirs.versions, id)

        status("Downloading version info…")
        val vjson = File(vdir, "$id.json")
        Http.download(entry.url, vjson, entry.sha1)
        val json = parseJson(vjson.readText())

        // ---- client jar ----
        val client = json.obj("downloads")?.obj("client") ?: error("This version has no client download")
        val clientJar = File(vdir, "$id.jar")
        status("Downloading Minecraft $id…")
        Http.download(client.str("url")!!, clientJar, client.str("sha1"))

        // ---- libraries ----
        val plan = collectLibs(json)
        downloadAll(plan.tasks, "Downloading libraries", status, progress)

        val nativesDir = File(vdir, "natives")
        nativesDir.mkdirs()
        for ((jar, exclude) in plan.nativeJars) extractNatives(jar, nativesDir, exclude)

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
            val target = if (virtual) File(Dirs.assets, "virtual/$aid") else File(gameDir, "resources")
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
            libs = plan.libs,
            clientJar = clientJar,
            nativesDir = nativesDir,
            assetsIndexId = aid,
            virtualAssetsDir = virtualDir,
            logConfig = logFile,
            logArg = logArg,
        )
    }

    // ------------------------------------------------------------------ loaders

    private suspend fun addLoader(
        base: Installed,
        entry: VersionEntry,
        loader: LoaderKind,
        loaderVersion: String?,
        status: (String) -> Unit,
        progress: (Int, Int) -> Unit,
    ): Installed = withContext(Dispatchers.IO) {
        val text: String
        if (loader.usesInstaller) {
            // Forge / NeoForge: run the official installer headless into our folders
            status("Checking Java for the ${loader.label} installer…")
            val java = JavaRuntime.ensure(base.javaMajor) { status(it) }
            val installedId = try {
                ForgeInstaller.install(loader, entry.id, loaderVersion, java, status)
            } catch (e: IOException) {
                throw IllegalStateException("${loader.label} couldn't be installed for Minecraft ${entry.id}: ${e.message}")
            }
            text = File(Dirs.versions, "$installedId/$installedId.json").readText()
        } else {
            // Fabric / Quilt: their meta server hands us a ready-made version profile
            status("Setting up ${loader.label}…")
            text = try {
                loader.profileJson(entry.id, loaderVersion)
            } catch (e: IOException) {
                throw IllegalStateException("${loader.label} isn't available for Minecraft ${entry.id}")
            }
            val id = parseJson(text).str("id") ?: error("The ${loader.label} profile is missing an id")
            File(Dirs.versions, id).mkdirs()
            File(Dirs.versions, "$id/$id.json").writeText(text)
        }
        mergeLoader(base, parseJson(text), loader, status, progress)
    }

    private suspend fun mergeLoader(
        base: Installed,
        lj: JsonObject,
        loader: LoaderKind,
        status: (String) -> Unit,
        progress: (Int, Int) -> Unit,
    ): Installed {
        val lid = lj.str("id") ?: error("The ${loader.label} profile is missing an id")

        val plan = collectLibs(lj)
        downloadAll(plan.tasks, "Downloading ${loader.label} libraries", status, progress)

        // loader libraries go first; if both define the same library, the loader's copy wins
        val seen = HashSet<String>()
        val merged = mutableListOf<Pair<String, File>>()
        for (p in plan.libs + base.libs) if (seen.add(p.first)) merged += p

        val mj = base.json.deepCopy()
        lj.str("mainClass")?.let { mj.addProperty("mainClass", it) }
        for (k in listOf("jvm", "game")) {
            val extra = lj.obj("arguments")?.arr(k) ?: continue
            val args = mj.obj("arguments") ?: JsonObject().also { mj.add("arguments", it) }
            val arr = args.arr(k) ?: JsonArray().also { args.add(k, it) }
            arr.addAll(extra)
        }

        // Forge's ignore list refers to "<version_name>.jar", so give the game jar that name
        val childJar = File(Dirs.versions, "$lid/$lid.jar")
        if (!childJar.isFile) {
            childJar.parentFile.mkdirs()
            base.clientJar.copyTo(childJar)
        }

        return base.copy(id = lid, json = mj, libs = merged, clientJar = childJar)
    }

    // ------------------------------------------------------------------ helpers

    private fun collectLibs(json: JsonObject): LibPlan {
        val plan = LibPlan()
        for (e in json.arr("libraries") ?: JsonArray()) {
            val lib = e.asJsonObject
            if (!Rules.allowed(lib.arr("rules"))) continue
            val name = lib.str("name") ?: continue
            val parts = name.split(":")
            val key = if (parts.size > 3) "${parts[0]}:${parts[1]}:${parts[3]}" else "${parts[0]}:${parts[1]}"
            val downloads = lib.obj("downloads")
            val artifact = downloads?.obj("artifact")

            if (artifact != null) {
                val f = File(Dirs.libraries, artifact.str("path")!!)
                val url = artifact.str("url")
                if (!url.isNullOrBlank()) plan.tasks += Dl(url, f, artifact.str("sha1"))
                plan.libs += key to f
            } else if (downloads == null) {
                // loader-style entry: build the Maven path ourselves
                val path = mavenPath(name)
                val base = (lib.str("url") ?: "https://libraries.minecraft.net/").trimEnd('/')
                val f = File(Dirs.libraries, path)
                plan.tasks += Dl("$base/$path", f, lib.str("sha1"))
                plan.libs += key to f
            }

            // old-style natives (pre-1.19): a classifier jar we unpack into the natives folder
            val nativeKey = lib.obj("natives")?.str("windows")
            if (nativeKey != null) {
                val classifier = nativeKey.replace("\${arch}", if (Rules.is64) "64" else "32")
                val na = downloads?.obj("classifiers")?.obj(classifier)
                if (na != null) {
                    val f = File(Dirs.libraries, na.str("path")!!)
                    plan.tasks += Dl(na.str("url")!!, f, na.str("sha1"))
                    val exclude = lib.obj("extract")?.arr("exclude")?.map { it.asString } ?: emptyList()
                    plan.nativeJars += f to exclude
                }
            }
        }
        return plan
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
