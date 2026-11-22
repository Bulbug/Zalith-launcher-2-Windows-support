/*
 * Adapted from Zalith Launcher 2 (game/download/game/optifine/Install.OptiFine.kt).
 * Copyright (C) 2025-2026 MovTery <movtery228@qq.com> and contributors
 * Licensed under GPL-3.0. https://www.gnu.org/licenses/gpl-3.0.txt
 */
package launcher

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

private const val LAUNCH_WRAPPER_MAIN = "net.minecraft.launchwrapper.Launch"
private const val OPTIFINE_TWEAKER = "optifine.OptiFineTweaker"

/**
 * Installs OptiFine as a loader profile: downloads the installer, patches the
 * vanilla jar with its Patcher (Java 8), places the libraries, and returns a
 * version profile JSON for [GameInstaller.mergeLoader]. Blocking.
 */
object OptiFineInstaller {

    fun install(mc: String, loaderVersion: String, status: (String) -> Unit): JsonObject {
        val build = OptiFine.listFor(mc).firstOrNull { it.version == loaderVersion }
            ?: throw IllegalStateException("OptiFine $loaderVersion isn't available for Minecraft $mc")

        val mavenVersion = build.fileName
            .removePrefix("preview_")
            .removePrefix("OptiFine_")
            .removeSuffix(".jar")
        val libDir = File(Dirs.libraries, "optifine/OptiFine/$mavenVersion")

        val installer = File(Dirs.root, "installers/optifine-${build.fileName}")
        status("Downloading OptiFine ${build.displayName}…")
        OptiFine.download(build, installer)

        // ---- main OptiFine library (patched jar, or the installer itself for old builds) ----
        val libJar = File(libDir, "OptiFine-$mavenVersion.jar")
        if (!libJar.isFile) {
            ZipFile(installer).use { zip ->
                if (zip.getEntry("optifine/Patcher.class") != null) {
                    status("Patching Minecraft with OptiFine (this can take a minute)…")
                    runPatcher(installer, File(Dirs.versions, "$mc/$mc.jar"), libJar)
                    stripZipEntry(libJar, "META-INF/mods.toml")
                } else {
                    copyZipSkipping(zip, libJar, "META-INF/mods.toml")
                }
            }
        }

        // ---- launchwrapper that ships inside the installer ----
        val launchWrapperLibs = mutableListOf<String>()
        ZipFile(installer).use { zip ->
            if (zip.getEntry("launchwrapper-2.0.jar") != null) {
                val target = File(Dirs.libraries, "optifine/launchwrapper/2.0/launchwrapper-2.0.jar")
                extractEntry(zip, "launchwrapper-2.0.jar", target)
                launchWrapperLibs += "optifine:launchwrapper:2.0"
            }
            val lwVersion = zip.getEntry("launchwrapper-of.txt")?.let { e ->
                zip.getInputStream(e).bufferedReader().use { it.readText().trim() }
            }
            if (lwVersion != null && zip.getEntry("launchwrapper-of-$lwVersion.jar") != null) {
                val target = File(Dirs.libraries, "optifine/launchwrapper-of/$lwVersion/launchwrapper-of-$lwVersion.jar")
                extractEntry(zip, "launchwrapper-of-$lwVersion.jar", target)
                launchWrapperLibs += "optifine:launchwrapper-of:$lwVersion"
            }
        }
        if (launchWrapperLibs.isEmpty()) launchWrapperLibs += "net.minecraft:launchwrapper:1.12"

        // ---- version profile JSON, merged into vanilla by GameInstaller ----
        val json = JsonObject().apply {
            addProperty("id", build.version)
            addProperty("inheritsFrom", mc)
            addProperty("type", "release")
            add("libraries", JsonArray().apply {
                add(JsonObject().apply { addProperty("name", "optifine:OptiFine:$mavenVersion") })
                launchWrapperLibs.forEach { add(JsonObject().apply { addProperty("name", it) }) }
            })
            addProperty("mainClass", LAUNCH_WRAPPER_MAIN)
            add("arguments", JsonObject().apply {
                add("game", JsonArray().apply {
                    add("--tweakClass")
                    add(OPTIFINE_TWEAKER)
                })
            })
        }
        // old versions (pre-1.13) only read the minecraftArguments string
        val vjson = parseJson(File(Dirs.versions, "$mc/$mc.json").readText())
        if (vjson.obj("arguments") == null) {
            json.addProperty("minecraftArguments", "--tweakClass $OPTIFINE_TWEAKER")
        }
        return json
    }

    /**
     * Runs the installer's own Patcher: java -cp installer.jar optifine.Patcher
     * <vanilla.jar> <installer.jar> <output.jar>. Needs Java 8 (ZL2 uses JRE 8 too).
     */
    private fun runPatcher(installer: File, vanillaJar: File, output: File) {
        val java = JavaRuntime.ensure(8) { }
        output.parentFile.mkdirs()
        val p = ProcessBuilder(
            java.absolutePath, "-cp", installer.absolutePath, "optifine.Patcher",
            vanillaJar.absolutePath, installer.absolutePath, output.absolutePath,
        )
            .redirectErrorStream(true)
            .start()
        val tail = ArrayDeque<String>()
        p.inputStream.bufferedReader().forEachLine { line ->
            if (tail.size >= 20) tail.removeFirst()
            tail.addLast(line)
        }
        val code = p.waitFor()
        if (code != 0 || !output.isFile) {
            throw IllegalStateException("The OptiFine patcher failed (exit code $code):\n" + tail.joinToString("\n"))
        }
    }

    private fun extractEntry(zip: ZipFile, name: String, target: File) {
        if (target.isFile) return
        target.parentFile.mkdirs()
        val e = zip.getEntry(name) ?: return
        zip.getInputStream(e).use { ins -> target.outputStream().use { ins.copyTo(it) } }
    }

    /** Copies every zip entry into [target] except [skip]. */
    private fun copyZipSkipping(zip: ZipFile, target: File, vararg skip: String) {
        target.parentFile.mkdirs()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(target))).use { out ->
            for (e in zip.entries()) {
                if (e.name in skip) continue
                out.putNextEntry(ZipEntry(e.name))
                if (!e.isDirectory) zip.getInputStream(e).use { it.copyTo(out) }
                out.closeEntry()
            }
        }
    }

    /** Rewrites [jar] in place without [strip] (the Patcher output keeps Forge-conflicting metadata). */
    private fun stripZipEntry(jar: File, strip: String) {
        if (!jar.isFile) return
        val tmp = File(jar.parentFile, jar.name + ".strip")
        ZipFile(jar).use { zip -> copyZipSkipping(zip, tmp, strip) }
        if (!tmp.renameTo(jar)) {
            tmp.copyTo(jar, overwrite = true)
            tmp.delete()
        }
    }
}
