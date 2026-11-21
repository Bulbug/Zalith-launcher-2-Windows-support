package launcher

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.zip.ZipFile

// ---- small JSON helpers (null-safe, so missing fields never crash) ----
fun parseJson(text: String): JsonObject = JsonParser.parseString(text).asJsonObject
fun JsonObject.obj(k: String): JsonObject? = get(k)?.takeIf { it.isJsonObject }?.asJsonObject
fun JsonObject.arr(k: String): JsonArray? = get(k)?.takeIf { it.isJsonArray }?.asJsonArray
fun JsonObject.str(k: String): String? = get(k)?.takeIf { it.isJsonPrimitive }?.asString
fun JsonObject.int(k: String): Int? = get(k)?.takeIf { it.isJsonPrimitive }?.asInt

object Http {
    private const val UA = "${AppInfo.NAME}/${AppInfo.VERSION}"

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .connectTimeout(Duration.ofSeconds(20))
        .build()

    fun getString(url: String): String {
        val req = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(60))
            .header("User-Agent", UA)
            .GET().build()
        val res = client.send(req, HttpResponse.BodyHandlers.ofString())
        if (res.statusCode() !in 200..299) throw IOException("HTTP ${res.statusCode()} for $url")
        return res.body()
    }

    /** Never throws on HTTP errors: returns (status, body). */
    fun post(url: String, body: String, contentType: String, headers: Map<String, String> = emptyMap()): Pair<Int, String> {
        val b = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(60))
            .header("User-Agent", UA)
            .header("Content-Type", contentType)
            .header("Accept", "application/json")
        headers.forEach { (k, v) -> b.header(k, v) }
        val res = client.send(b.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString())
        return res.statusCode() to res.body()
    }

    /** Never throws on HTTP errors: returns (status, body). */
    fun getWithStatus(url: String, headers: Map<String, String> = emptyMap()): Pair<Int, String> {
        val b = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(60))
            .header("User-Agent", UA)
            .header("Accept", "application/json")
        headers.forEach { (k, v) -> b.header(k, v) }
        val res = client.send(b.GET().build(), HttpResponse.BodyHandlers.ofString())
        return res.statusCode() to res.body()
    }

    fun form(vararg p: Pair<String, String>): String =
        p.joinToString("&") { (k, v) -> URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8") }

    /** Downloads [url] to [dest]. Skips if the file already exists (and matches [sha1] when given). */
    fun download(url: String, dest: File, sha1: String? = null) {
        if (dest.isFile && (sha1 == null || sha1Of(dest).equals(sha1, ignoreCase = true))) return
        dest.parentFile?.mkdirs()
        var last: Exception? = null
        repeat(3) { attempt ->
            val tmp = File(dest.path + ".part")
            try {
                tmp.delete()
                val req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMinutes(10))
                    .header("User-Agent", UA)
                    .GET().build()
                val res = client.send(req, HttpResponse.BodyHandlers.ofFile(tmp.toPath()))
                if (res.statusCode() !in 200..299) throw IOException("HTTP ${res.statusCode()} for $url")
                if (sha1 != null && !sha1Of(tmp).equals(sha1, ignoreCase = true)) {
                    throw IOException("Checksum mismatch for $url")
                }
                Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
                return
            } catch (e: Exception) {
                last = e
                tmp.delete()
                Thread.sleep(500L * (attempt + 1))
            }
        }
        throw last ?: IOException("Download failed: $url")
    }

    fun sha1Of(file: File): String {
        val md = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { ins ->
            val buf = ByteArray(65536)
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}

fun unzip(zip: File, dest: File) {
    dest.mkdirs()
    val destCanon = dest.canonicalFile
    ZipFile(zip).use { z ->
        for (e in z.entries().toList()) {
            val out = File(dest, e.name).canonicalFile
            // zip-slip protection
            if (out != destCanon && !out.path.startsWith(destCanon.path + File.separator)) {
                throw SecurityException("Bad zip entry: ${e.name}")
            }
            if (e.isDirectory) {
                out.mkdirs()
            } else {
                out.parentFile.mkdirs()
                z.getInputStream(e).use { i -> out.outputStream().use { o -> i.copyTo(o) } }
            }
        }
    }
}
