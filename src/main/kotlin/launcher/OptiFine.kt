/*
 * Adapted from Zalith Launcher 2 (game/addons/modloader/optifine/OptiFineVersions.kt
 * and game/download/game/optifine/Download.OptiFine.kt).
 * Copyright (C) 2025-2026 MovTery <movtery228@qq.com> and contributors
 * Licensed under GPL-3.0. https://www.gnu.org/licenses/gpl-3.0.txt
 */
package launcher

import java.io.File
import java.io.IOException

/** One installable OptiFine build for a Minecraft version. */
data class OptiFineBuild(
    val displayName: String,   // "1.12.2 HD U F5"
    val fileName: String,      // "OptiFine_1.12.2_HD_U_F5.jar"
    val version: String,       // "1.12.2-OptiFine_HD_U_F5" (version folder / JSON id)
    val inherit: String,       // "1.12.2"
    val isPreview: Boolean,
)

/**
 * Talks to optifine.net: scrapes the downloads page for the version list and
 * resolves the real jar URL behind the ad gate. All calls block.
 */
object OptiFine {
    private const val URL = "https://optifine.net"
    private const val DOWNLOADS = "$URL/downloads"
    private const val ADLOADX = "$URL/adloadx"

    private var cache: List<OptiFineBuild>? = null

    /** All OptiFine builds for [mc] (newest last, matching the page order). Blocking. */
    fun listFor(mc: String): List<OptiFineBuild> = list().filter { it.inherit == mc }

    private fun list(): List<OptiFineBuild> {
        cache?.let { return it }
        val html = Http.getString(DOWNLOADS)

        val names = Regex("""<td class=['"]colFile['"]>([^<]+)</td>""").findAll(html)
            .map { it.groupValues[1].trim() }.toList()
        val dates = Regex("""<td class=['"]colDate['"]>([^<]+)</td>""").findAll(html)
            .map { it.groupValues[1].trim() }.toList()
        val jars = Regex("""adfoc\.us[^>]+f=([^&]+\.jar)""").findAll(html)
            .map { it.groupValues[1].trim() }.toList()

        if (names.size != jars.size) {
            throw IOException("Couldn't parse OptiFine's download page (${names.size} names vs ${jars.size} files) - the site layout may have changed")
        }

        val builds = names.indices.map { i ->
            val jar = jars[i]
            val rawName = jar.removeSuffix(".jar").removePrefix("preview_")
            val isPreview = jar.startsWith("preview_")
            val rawSpaced = rawName.replace("_", " ")
            val displayName = rawSpaced
                .replace("OptiFine ", "")
                .replace("HD U ", "")
                .replace(".0 ", " ")
            val inherit = displayName.split(" ").first()
            val version = if (rawName.contains("$inherit.0_")) {
                //OptiFine_1.9.0_HD_U_E7 -> 1.9-OptiFine_HD_U_E7
                "$inherit-${rawName.replace("$inherit.0_", "")}"
            } else {
                "$inherit-${rawName.replace("${inherit}_", "")}"
            }
            OptiFineBuild(
                displayName = displayName,
                fileName = (if (isPreview) "preview_" else "") + "$rawName.jar",
                version = version,
                inherit = inherit,
                isPreview = isPreview,
            )
        }
        cache = builds
        return builds
    }

    /**
     * Downloads the installer jar for [build] into [dest].
     * The downloads page only links an ad gate; the real URL comes from an
     * adloadx hop whose HTML contains the downloadx link.
     */
    fun download(build: OptiFineBuild, dest: File) {
        if (dest.isFile) return
        val html = Http.getString("$ADLOADX?f=${build.fileName}")
        val path = Regex("""downloadx\?f=[^"'<>]+""").findAll(html)
            .map { it.value }
            .lastOrNull()
            ?: throw IOException("Couldn't find the download link for ${build.fileName} - OptiFine's site may have changed")
        Http.download("$URL/$path", dest)
    }
}
