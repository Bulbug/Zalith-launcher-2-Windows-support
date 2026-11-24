package launcher

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.net.URI
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.FileInputStream

/** Export and import instance profiles as .dlpack files (ZIP with instance.json + mods folder) */
object InstanceExport {

    /** Exports an instance to a .dlpack file. Blocking. */
    fun export(inst: Instance, dest: File, status: (String) -> Unit): File {
        status("Preparing export…")
        dest.parentFile.mkdirs()
        File(Dirs.root, "exports").mkdirs()

        val tempDir = File(Dirs.root, "temp_export_${inst.id}")
        if (tempDir.exists()) tempDir.deleteRecursively()
        tempDir.mkdirs()

        try {
            // Copy instance.json
            val instanceJson = File(inst.dir, "instance.json")
            val targetJson = File(tempDir, "instance.json")
            instanceJson.copyTo(targetJson)

            // Copy mods folder if exists
            if (inst.modsDir.exists()) {
                val targetMods = File(tempDir, "mods")
                copyDir(inst.modsDir, targetMods)
            }

            // Copy resourcepacks folder if exists
            if (inst.resourcePacksDir.exists()) {
                val targetRp = File(tempDir, "resourcepacks")
                copyDir(inst.resourcePacksDir, targetRp)
            }

            // Copy shaderpacks folder if exists
            if (inst.shadersDir.exists()) {
                val targetSp = File(tempDir, "shaderpacks")
                copyDir(inst.shadersDir, targetSp)
            }

            // Create ZIP
            status("Creating package…")
            ZipOutputStream(BufferedOutputStream(FileOutputStream(dest))).use { zip ->
                zip.setLevel(6)
                addToZip(zip, tempDir, "")
            }

            status("Export complete: ${dest.name}")
            return dest
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /** Imports an instance from a .dlpack file. Blocking. Returns the created instance. */
    fun importPack(file: File, status: (String) -> Unit): Instance {
        status("Reading package…")
        val tempDir = File(Dirs.root, "temp_import_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            // Extract ZIP
            ZipFile(file).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val out = safeResolve(tempDir, entry.name)
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile.mkdirs()
                        zip.getInputStream(entry).use { ins ->
                            out.outputStream().use { ins.copyTo(it) }
                        }
                    }
                }
            }

            // Read instance.json
            val instanceJson = File(tempDir, "instance.json")
            if (!instanceJson.isFile) throw IllegalStateException("Invalid package: instance.json not found")

            val json = JsonParser.parseString(instanceJson.readText()).asJsonObject
            val name = json.str("name") ?: file.nameWithoutExtension
            val mc = json.str("mc") ?: throw IllegalStateException("Invalid package: no mc version")
            val loader = LoaderKind.valueOf(json.str("loader") ?: "VANILLA")
            val loaderVersion = json.str("loaderVersion")
            val optifineVersion = json.str("optifineVersion")

            // Create instance
            val inst = InstanceStore.create(name, mc, loader, loaderVersion, optifineVersion)

            // Copy mods
            val sourceMods = File(tempDir, "mods")
            if (sourceMods.exists()) {
                status("Copying mods…")
                copyDir(sourceMods, inst.modsDir)
            }

            // Copy resourcepacks
            val sourceRp = File(tempDir, "resourcepacks")
            if (sourceRp.exists()) {
                status("Copying resource packs…")
                copyDir(sourceRp, inst.resourcePacksDir)
            }

            // Copy shaderpacks
            val sourceSp = File(tempDir, "shaderpacks")
            if (sourceSp.exists()) {
                status("Copying shader packs…")
                copyDir(sourceSp, inst.shadersDir)
            }

            status("Import complete: ${inst.name}")
            return inst
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun copyDir(source: File, target: File) {
        target.mkdirs()
        source.listFiles()?.forEach { f ->
            if (f.isDirectory) {
                copyDir(f, File(target, f.name))
            } else {
                f.copyTo(File(target, f.name), overwrite = true)
            }
        }
    }

    private fun addToZip(zip: ZipOutputStream, file: File, basePath: String) {
        val path = if (basePath.isEmpty()) file.name else "$basePath/${file.name}"
        if (file.isDirectory) {
            zip.putNextEntry(ZipEntry("$path/"))
            zip.closeEntry()
            file.listFiles()?.forEach { addToZip(zip, it, path) }
        } else {
            zip.putNextEntry(ZipEntry(path))
            FileInputStream(file).use { zip.write(it.readAllBytes()) }
            zip.closeEntry()
        }
    }

    /** Resolves [rel] inside [base] and refuses anything that would escape it (zip-slip / "..\" tricks). */
    private fun safeResolve(base: File, rel: String): File {
        if (rel.isBlank()) throw SecurityException("Empty path in package")
        val b = base.canonicalFile
        val f = File(base, rel).canonicalFile
        if (!f.path.startsWith(b.path + File.separator)) throw SecurityException("Unsafe path in package: $rel")
        return f
    }
}