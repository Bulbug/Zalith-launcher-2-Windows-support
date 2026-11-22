package launcher

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.net.URLEncoder

class ModHit(
    val projectId: String,
    val title: String,
    val author: String,
    val description: String,
    val downloads: Long,
)

/** Minimal Modrinth client: search mods and install them (plus their required dependencies). All calls block. */
object Modrinth {
    private const val API = "https://api.modrinth.com/v2"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun search(query: String, mc: String, loaders: List<String>): List<ModHit> {
        // facets: outer list = AND, inner list = OR
        val facets = JsonArray()
        facets.add(JsonArray().apply { add("project_type:mod") })
        facets.add(JsonArray().apply { add("versions:$mc") })
        facets.add(JsonArray().apply { loaders.forEach { add("categories:$it") } })
        val index = if (query.isBlank()) "downloads" else "relevance"
        val url = "$API/search?query=${enc(query)}&facets=${enc(facets.toString())}&limit=30&index=$index"
        return toHits(parseJson(Http.getString(url)).arr("hits"))
    }

    fun searchPacks(query: String): List<ModHit> {
        val facets = JsonArray()
        facets.add(JsonArray().apply { add("project_type:modpack") })
        val index = if (query.isBlank()) "downloads" else "relevance"
        val url = "$API/search?query=${enc(query)}&facets=${enc(facets.toString())}&limit=30&index=$index"
        return toHits(parseJson(Http.getString(url)).arr("hits"))
    }

    private fun toHits(hits: JsonArray?): List<ModHit> =
        (hits ?: JsonArray()).map { it.asJsonObject }.map {
            ModHit(
                projectId = it.str("project_id") ?: "",
                title = it.str("title") ?: "?",
                author = it.str("author") ?: "",
                description = it.str("description") ?: "",
                downloads = it.get("downloads")?.asLong ?: 0L,
            )
        }.filter { it.projectId.isNotEmpty() }

    /** Downloads the newest .mrpack of a modpack project into [destDir] and returns the file. */
    fun downloadPack(projectId: String, destDir: File): File {
        val versions = JsonParser.parseString(Http.getString("$API/project/${enc(projectId)}/version"))
            .asJsonArray.map { it.asJsonObject }
        val v = versions.firstOrNull { it.str("version_type") == "release" } ?: versions.firstOrNull()
            ?: throw IllegalStateException("This modpack has no versions")
        val f = v.arr("files")?.map { it.asJsonObject }
            ?.firstOrNull { it.str("filename")?.endsWith(".mrpack") == true }
            ?: throw IllegalStateException("This modpack has no .mrpack file")
        val safeName = f.str("filename")!!.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val dest = File(destDir, safeName)
        Http.download(f.str("url")!!, dest, f.obj("hashes")?.str("sha1"))
        return dest
    }

    /** Installs the newest compatible version of [projectId] plus required dependencies. Returns the file names added. */
    fun install(
        projectId: String,
        mc: String,
        loaders: List<String>,
        modsDir: File,
        status: (String) -> Unit,
    ): List<String> {
        val added = mutableListOf<String>()
        installInto(projectId, null, mc, loaders, modsDir, status, HashSet(), added)
        return added
    }

    fun listInstalled(modsDir: File): List<File> =
        modsDir.listFiles()?.filter { it.isFile && it.name.endsWith(".jar") }?.sortedBy { it.name.lowercase() }
            ?: emptyList()

    private fun installInto(
        projectId: String?,
        versionId: String?,
        mc: String,
        loaders: List<String>,
        modsDir: File,
        status: (String) -> Unit,
        seen: MutableSet<String>,
        added: MutableList<String>,
    ) {
        if (projectId != null && !seen.add(projectId)) return

        val version = (if (versionId != null) fetchVersion(versionId) else latestVersion(projectId!!, mc, loaders))
            ?: throw IllegalStateException(
                "No version of this mod fits Minecraft $mc on ${loaders.joinToString("/")}"
            )
        if (projectId == null) {
            val pid = version.str("project_id")
            if (pid != null && !seen.add(pid)) return
        }

        val files = version.arr("files")?.map { it.asJsonObject }
        if (files.isNullOrEmpty()) throw IllegalStateException("This mod version has no files")
        val file = files.firstOrNull { it.get("primary")?.asBoolean == true } ?: files.first()
        val name = file.str("filename") ?: throw IllegalStateException("This mod file has no name")

        status("Downloading $name…")
        Http.download(file.str("url")!!, File(modsDir, name), file.obj("hashes")?.str("sha1"))
        added += name

        for (d in version.arr("dependencies") ?: JsonArray()) {
            val dep = d.asJsonObject
            if (dep.str("dependency_type") != "required") continue
            if (dep.str("project_id") == null && dep.str("version_id") == null) continue
            installInto(dep.str("project_id"), dep.str("version_id"), mc, loaders, modsDir, status, seen, added)
        }
    }

    private fun latestVersion(projectId: String, mc: String, loaders: List<String>): JsonObject? {
        val gv = JsonArray().apply { add(mc) }.toString()
        val ld = JsonArray().apply { loaders.forEach { add(it) } }.toString()
        val url = "$API/project/${enc(projectId)}/version?game_versions=${enc(gv)}&loaders=${enc(ld)}"
        val versions = JsonParser.parseString(Http.getString(url)).asJsonArray.map { it.asJsonObject }
        return versions.firstOrNull { it.str("version_type") == "release" } ?: versions.firstOrNull()
    }

    private fun fetchVersion(versionId: String): JsonObject? =
        runCatching { parseJson(Http.getString("$API/version/${enc(versionId)}")) }.getOrNull()
}
