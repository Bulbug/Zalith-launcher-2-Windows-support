package launcher

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.net.URLEncoder

/** Minimal CurseForge client: search modpacks and install them. All calls block. */
object CurseForge {
    private const val API = "https://api.curseforge.com/v1"
    private const val GAME_ID = 432 // Minecraft
    private const val CLASS_ID_MODPACK = 4471
    private const val CLASS_ID_MOD = 6
    private const val API_KEY_FILE = "curseforge_api_key.txt"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** Gets the CurseForge API key from environment variable or file. */
    private fun apiKey(): String? {
        System.getenv("CURSEFORGE_API_KEY")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val f = File(Dirs.root, API_KEY_FILE)
        if (f.isFile) return f.readText().trim().takeIf { it.isNotEmpty() }
        return null
    }

    /** Builds headers with API key if available. */
    private fun headers(): Map<String, String> = apiKey()?.let { mapOf("x-api-key" to it) } ?: emptyMap()

    data class CurseHit(
        val projectId: Int,
        val title: String,
        val author: String,
        val description: String,
        val downloads: Long,
        val latestFile: JsonObject?, // contains download info
    )

    fun searchModpacks(query: String, mcVersion: String? = null): List<CurseHit> {
        val url = buildUrl("/mods/search", query, CLASS_ID_MODPACK, mcVersion)
        return try {
            toHits(parseJson(Http.getString(url, headers())).arr("data"))
        } catch (e: IOException) {
            handleApiError(e, "searchModpacks")
            emptyList()
        }
    }

    fun searchMods(query: String, mcVersion: String? = null): List<CurseHit> {
        val url = buildUrl("/mods/search", query, CLASS_ID_MOD, mcVersion)
        return try {
            toHits(parseJson(Http.getString(url, headers())).arr("data"))
        } catch (e: IOException) {
            handleApiError(e, "searchMods")
            emptyList()
        }
    }

    private fun handleApiError(e: IOException, context: String): Nothing {
        val msg = e.message ?: ""
        when {
            msg.contains("HTTP 401") -> throw IOException("CurseForge API: Invalid or missing API key. Set CURSEFORGE_API_KEY env var or create $API_KEY_FILE in %APPDATA%\\.desklauncher\\")
            msg.contains("HTTP 403") -> throw IOException("CurseForge API: Access forbidden. Your API key may not have permission for this endpoint.")
            msg.contains("HTTP 429") -> throw IOException("CurseForge API: Rate limited. Please wait before making more requests.")
            else -> throw IOException("CurseForge API $context failed: $msg")
        }
    }

    private fun buildUrl(endpoint: String, query: String, classId: Int, mcVersion: String?): String {
        val sb = StringBuilder("$API$endpoint?gameId=$GAME_ID&classId=$classId&pageSize=30")
        if (query.isNotBlank()) sb.append("&searchFilter=${enc(query)}")
        if (mcVersion != null && mcVersion.isNotBlank()) sb.append("&gameVersion=${enc(mcVersion)}")
        return sb.toString()
    }

    fun getProject(projectId: Int): CurseHit? {
        return try {
            val url = "$API/mods/$projectId"
            val data = parseJson(Http.getString(url, headers())).obj("data") ?: return null
            val authors = data.obj("authors")?.arr("data") ?: JsonArray()
            CurseHit(
                projectId = data.int("id") ?: projectId,
                title = data.str("name") ?: "?",
                author = authors.firstOrNull()?.asJsonObject?.str("name") ?: "",
                description = data.str("summary") ?: "",
                downloads = data.get("downloadCount")?.asLong ?: 0L,
                latestFile = data.arr("latestFiles")?.firstOrNull()?.asJsonObject,
            )
        } catch (e: IOException) {
            handleApiError(e, "getProject")
            null
        }
    }

    /** Downloads the latest file for a modpack/mod project into [destDir] and returns the file. */
    fun downloadFile(projectId: Int, destDir: File, fileId: Int? = null): File {
        val files = if (fileId != null) {
            listOf(fileId)
        } else {
            val url = "$API/mods/$projectId/files"
            parseJson(Http.getString(url, headers())).arr("data")?.map { it.asJsonObject.get("id")?.asInt ?: 0 } ?: emptyList()
        }
        val fileIdToUse = files.firstOrNull() ?: throw IllegalStateException("No files found for project $projectId")

        val fileInfo = parseJson(Http.getString("$API/mods/$projectId/files/$fileIdToUse", headers())).obj("data") ?: throw IllegalStateException("No file info found")
        val fileName = fileInfo.str("fileName") ?: "download.jar"
        val downloadUrl = fileInfo.str("downloadUrl") ?: throw IllegalStateException("No download URL")
        val hash = fileInfo.obj("hashes")?.str("value") // SHA1

        val safeName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val dest = File(destDir, safeName)
        try {
            Http.download(downloadUrl, dest, hash)
        } catch (e: IOException) {
            handleApiError(e, "downloadFile")
            throw e
        }
        return dest
    }

    private fun toHits(data: JsonArray?): List<CurseHit> =
        (data ?: JsonArray()).map { it.asJsonObject }.map {
            CurseHit(
                projectId = it.int("id") ?: 0,
                title = it.str("name") ?: "?",
                author = it.obj("authors")?.arr("data")?.firstOrNull()?.asJsonObject?.str("name") ?: "",
                description = it.str("summary") ?: "",
                downloads = it.get("downloadCount")?.asLong ?: 0L,
                latestFile = it.arr("latestFiles")?.firstOrNull()?.asJsonObject,
            )
        }.filter { it.projectId > 0 }
}