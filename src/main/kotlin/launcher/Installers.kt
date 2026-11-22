package launcher

import java.io.File
import java.io.IOException

/** Forge and NeoForge: download the official installer and run it headless into our folders. */
object ForgeInstaller {

    private class Resolved(val loaderVersion: String, val installerUrl: String, val expectedId: String)

    /** Returns the id of the version the installer created (e.g. "1.20.1-forge-47.3.0"). Blocking. */
    fun install(kind: LoaderKind, mc: String, loaderVersion: String?, javaExe: File, status: (String) -> Unit): String {
        if (!supportsMc(kind, mc)) {
            throw IllegalStateException("${kind.label} is only supported here for Minecraft 1.13 and newer")
        }
        val r = when (kind) {
            LoaderKind.FORGE -> resolveForge(mc, loaderVersion)
            LoaderKind.NEOFORGE -> resolveNeo(mc, loaderVersion)
            else -> error("${kind.label} doesn't use an installer")
        }

        val expected = File(Dirs.versions, "${r.expectedId}/${r.expectedId}.json")
        if (expected.isFile) return r.expectedId // installed before

        status("Downloading the ${kind.label} installer…")
        val jar = File(Dirs.root, "installers/${kind.name.lowercase()}-${r.loaderVersion}-installer.jar")
        Http.download(r.installerUrl, jar)

        // the installer refuses to run unless a launcher profile file exists in the target folder
        val profiles = File(Dirs.root, "launcher_profiles.json")
        if (!profiles.isFile) {
            Dirs.root.mkdirs()
            profiles.writeText("""{"profiles":{},"version":3}""")
        }

        status("Running the ${kind.label} installer (this can take a few minutes)…")
        val p = ProcessBuilder(javaExe.absolutePath, "-jar", jar.absolutePath, "--installClient", Dirs.root.absolutePath)
            .directory(Dirs.root)
            .redirectErrorStream(true)
            .start()
        val tail = ArrayDeque<String>()
        p.inputStream.bufferedReader().forEachLine { line ->
            if (tail.size >= 20) tail.removeFirst()
            tail.addLast(line)
        }
        val code = p.waitFor()
        if (code != 0) {
            throw IllegalStateException("The ${kind.label} installer failed (exit code $code):\n" + tail.joinToString("\n"))
        }

        if (expected.isFile) return r.expectedId
        // the installer named it differently than expected: take the newest matching version folder
        val found = Dirs.versions.listFiles()
            ?.filter { it.isDirectory && it.name != mc }
            ?.mapNotNull { d ->
                val f = File(d, "${d.name}.json")
                val inherits = if (f.isFile) runCatching { parseJson(f.readText()).str("inheritsFrom") }.getOrNull() else null
                val nameOk = if (kind == LoaderKind.NEOFORGE) d.name.startsWith("neoforge")
                else d.name.contains("forge") && !d.name.startsWith("neoforge")
                if (inherits == mc && nameOk) d to f.lastModified() else null
            }
            ?.maxByOrNull { it.second }
            ?.first?.name
        return found ?: throw IllegalStateException("The ${kind.label} installer finished but no version was created")
    }

    private fun supportsMc(kind: LoaderKind, mc: String): Boolean {
        val parts = mc.split(".").map { it.toIntOrNull() ?: return false }
        return if (parts[0] == 1) parts.size >= 2 && parts[1] >= 13 else parts[0] >= 26
    }

    private fun resolveForge(mc: String, wanted: String?): Resolved {
        val ver = wanted ?: run {
            val o = parseJson(Http.getString("https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json"))
            val promos = o.obj("promos") ?: throw IOException("Forge's version list is unavailable")
            promos.str("$mc-recommended") ?: promos.str("$mc-latest")
                ?: throw IllegalStateException("Forge has no build for Minecraft $mc")
        }
        val full = "$mc-$ver"
        return Resolved(
            ver,
            "https://maven.minecraftforge.net/net/minecraftforge/forge/$full/forge-$full-installer.jar",
            "$mc-forge-$ver",
        )
    }

    private fun resolveNeo(mc: String, wanted: String?): Resolved {
        val ver = wanted ?: run {
            val prefix = neoPrefix(mc) ?: throw IllegalStateException("NeoForge isn't supported for Minecraft $mc here")
            val o = parseJson(Http.getString("https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge"))
            val all = (o.arr("versions") ?: throw IOException("NeoForge's version list is unavailable"))
                .map { it.asString }
                .filter { it.startsWith(prefix) }
            val pool = all.filter { !it.contains("-") }.ifEmpty { all } // prefer stable, fall back to beta
            pool.sortedWith(Comparator { a, b -> compareNums(a.removePrefix(prefix), b.removePrefix(prefix)) })
                .lastOrNull() ?: throw IllegalStateException("NeoForge has no build for Minecraft $mc")
        }
        return Resolved(
            ver,
            "https://maven.neoforged.net/releases/net/neoforged/neoforge/$ver/neoforge-$ver-installer.jar",
            "neoforge-$ver",
        )
    }

    /**
     * NeoForge version prefix for a Minecraft version:
     *   1.21.1 -> "21.1.",  1.21 -> "21.0.",  26.1.2 -> "26.1.2.",  26.1 -> "26.1.0."
     */
    private fun neoPrefix(mc: String): String? {
        val n = mc.split(".").map { it.toIntOrNull() ?: return null }
        return when {
            n[0] == 1 && n.size >= 2 -> "${n[1]}.${n.getOrElse(2) { 0 }}."
            n[0] >= 26 -> (n + List(maxOf(0, 3 - n.size)) { 0 }).take(3).joinToString(".") + "."
            else -> null
        }
    }

    private fun compareNums(a: String, b: String): Int {
        val digits = Regex("\\d+")
        val x = digits.findAll(a).map { it.value.toLong() }.toList()
        val y = digits.findAll(b).map { it.value.toLong() }.toList()
        for (i in 0 until maxOf(x.size, y.size)) {
            val c = x.getOrElse(i) { 0L }.compareTo(y.getOrElse(i) { 0L })
            if (c != 0) return c
        }
        return 0
    }
}
