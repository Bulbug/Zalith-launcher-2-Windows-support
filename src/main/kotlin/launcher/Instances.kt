package launcher

import com.google.gson.JsonObject
import java.io.File

/** A named game setup: one Minecraft version + loader, with its own saves, mods and settings. */
class Instance(
    val id: String,                // folder name under instances/
    val name: String,
    val mc: String,
    val loader: LoaderKind,
    val loaderVersion: String?,    // null = newest available
    val optifineVersion: String? = null,  // OptiFine build version (e.g., "1.20.1-OptiFine_HD_U_I1")
    val isModpack: Boolean = false,        // Whether this instance was created from a modpack
    val javaMajor: Int = 0,                // 0 = auto-detect, otherwise specific Java version
    val ramMb: Int = 0,                    // 0 = use global setting, otherwise per-instance RAM
    val jvmArgs: String = "",              // Additional JVM arguments
    val windowWidth: Int = 0,              // 0 = default
    val windowHeight: Int = 0,             // 0 = default
    val fullscreen: Boolean = false,       // Fullscreen preference
    val createdAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long = 0,
    val playTimeMs: Long = 0,
) {
    val dir: File get() = File(Dirs.instances, id)
    val modsDir: File get() = File(dir, "mods")
    val resourcePacksDir: File get() = File(dir, "resourcepacks")
    val shadersDir: File get() = File(dir, "shaderpacks")
    val savesDir: File get() = File(dir, "saves")
    val configDir: File get() = File(dir, "config")
    val subtitle: String
        get() = "$mc - ${loader.label}" + (loaderVersion?.let { " $it" } ?: "") + (optifineVersion?.let { " + OptiFine" } ?: "")

    /** Returns effective RAM (instance setting or global default). */
    fun effectiveRam(globalRam: Int): Int = if (ramMb > 0) ramMb else globalRam

    /** Returns effective Java version (instance setting or auto-detect). */
    fun effectiveJavaMajor(autoJava: Int): Int = if (javaMajor > 0) javaMajor else autoJava

    /** Returns effective JVM args (instance + global). */
    fun effectiveJvmArgs(globalArgs: String = ""): String {
        val parts = mutableListOf<String>()
        if (globalArgs.isNotBlank()) parts += globalArgs
        if (jvmArgs.isNotBlank()) parts += jvmArgs
        return parts.joinToString(" ")
    }
}

object InstanceStore {
    private const val FILE = "instance.json"

    fun list(): List<Instance> =
        Dirs.instances.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { read(it) }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()

    private fun read(dir: File): Instance? {
        val f = File(dir, FILE)
        if (!f.isFile) return null
        return try {
            val o = parseJson(f.readText())
            Instance(
                id = dir.name,
                name = o.str("name") ?: dir.name,
                mc = o.str("mc")!!,
                loader = LoaderKind.valueOf(o.str("loader") ?: "VANILLA"),
                loaderVersion = o.str("loaderVersion"),
                optifineVersion = o.str("optifineVersion"),
                isModpack = o.get("isModpack")?.asBoolean ?: false,
                javaMajor = o.get("javaMajor")?.asInt ?: 0,
                ramMb = o.get("ramMb")?.asInt ?: 0,
                jvmArgs = o.str("jvmArgs") ?: "",
                windowWidth = o.get("windowWidth")?.asInt ?: 0,
                windowHeight = o.get("windowHeight")?.asInt ?: 0,
                fullscreen = o.get("fullscreen")?.asBoolean ?: false,
                createdAt = o.get("createdAt")?.asLong ?: System.currentTimeMillis(),
                lastPlayedAt = o.get("lastPlayedAt")?.asLong ?: 0,
                playTimeMs = o.get("playTimeMs")?.asLong ?: 0,
            )
        } catch (e: Exception) {
            null
        }
    }

    fun create(name: String, mc: String, loader: LoaderKind, loaderVersion: String?, optifineVersion: String?): Instance {
        val clean = name.trim().take(40).ifBlank { "$mc ${loader.label}" }
        var slug = clean.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "instance" }
        // folder names Windows doesn't allow
        if (Regex("(con|prn|aux|nul|com\\d|lpt\\d)").matches(slug)) slug += "-1"
        var id = slug
        var n = 2
        while (File(Dirs.instances, id).exists()) {
            id = "$slug-$n"
            n++
        }
        val inst = Instance(id, clean, mc, loader, loaderVersion, optifineVersion)
        inst.dir.mkdirs()
        inst.modsDir.mkdirs()
        inst.resourcePacksDir.mkdirs()
        inst.shadersDir.mkdirs()
        inst.savesDir.mkdirs()
        inst.configDir.mkdirs()
        save(inst)
        return inst
    }

    fun createModpack(name: String, mc: String, loader: LoaderKind, loaderVersion: String?): Instance {
        val clean = name.trim().take(40).ifBlank { "$mc ${loader.label}" }
        var slug = clean.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "modpack" }
        // folder names Windows doesn't allow
        if (Regex("(con|prn|aux|nul|com\\d|lpt\\d)").matches(slug)) slug += "-1"
        var id = slug
        var n = 2
        while (File(Dirs.instances, id).exists()) {
            id = "$slug-$n"
            n++
        }
        val inst = Instance(id, clean, mc, loader, loaderVersion, null, isModpack = true)
        inst.dir.mkdirs()
        inst.modsDir.mkdirs()
        inst.resourcePacksDir.mkdirs()
        inst.shadersDir.mkdirs()
        inst.savesDir.mkdirs()
        inst.configDir.mkdirs()
        save(inst)
        return inst
    }

    fun save(inst: Instance) {
        val o = JsonObject()
        o.addProperty("name", inst.name)
        o.addProperty("mc", inst.mc)
        o.addProperty("loader", inst.loader.name)
        if (inst.loaderVersion != null) o.addProperty("loaderVersion", inst.loaderVersion)
        if (inst.optifineVersion != null) o.addProperty("optifineVersion", inst.optifineVersion)
        o.addProperty("isModpack", inst.isModpack)
        if (inst.javaMajor > 0) o.addProperty("javaMajor", inst.javaMajor)
        if (inst.ramMb > 0) o.addProperty("ramMb", inst.ramMb)
        if (inst.jvmArgs.isNotBlank()) o.addProperty("jvmArgs", inst.jvmArgs)
        if (inst.windowWidth > 0) o.addProperty("windowWidth", inst.windowWidth)
        if (inst.windowHeight > 0) o.addProperty("windowHeight", inst.windowHeight)
        o.addProperty("fullscreen", inst.fullscreen)
        o.addProperty("createdAt", inst.createdAt)
        o.addProperty("lastPlayedAt", inst.lastPlayedAt)
        o.addProperty("playTimeMs", inst.playTimeMs)
        File(inst.dir, FILE).writeText(o.toString())
    }

    /** Renames an instance. */
    fun rename(inst: Instance, newName: String): Instance {
        val updated = inst.copy(name = newName.trim().take(40))
        save(updated)
        return updated
    }

    /** Duplicates an instance with a new name. */
    fun duplicate(inst: Instance, newName: String? = null): Instance {
        val name = newName ?: "${inst.name} (copy)"
        val newInst = create(name, inst.mc, inst.loader, inst.loaderVersion, inst.optifineVersion)
        // Copy mods
        if (inst.modsDir.exists()) copyDir(inst.modsDir, newInst.modsDir)
        if (inst.resourcePacksDir.exists()) copyDir(inst.resourcePacksDir, newInst.resourcePacksDir)
        if (inst.shadersDir.exists()) copyDir(inst.shadersDir, newInst.shadersDir)
        if (inst.configDir.exists()) copyDir(inst.configDir, newInst.configDir)
        return newInst
    }

    /** Changes the game version and/or loader for an instance. */
    fun changeVersion(inst: Instance, mc: String, loader: LoaderKind, loaderVersion: String?, optifineVersion: String?): Instance {
        val updated = inst.copy(
            mc = mc,
            loader = loader,
            loaderVersion = loaderVersion,
            optifineVersion = optifineVersion,
        )
        save(updated)
        return updated
    }

    /** Updates last played time and play time. */
    fun updatePlayStats(inst: Instance, sessionTimeMs: Long) {
        val updated = inst.copy(
            lastPlayedAt = System.currentTimeMillis(),
            playTimeMs = inst.playTimeMs + sessionTimeMs,
        )
        save(updated)
    }

    /** Backs up instance data (saves, configs, mods list). */
    fun backup(inst: Instance, destDir: File): File {
        destDir.mkdirs()
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.getDefault()).format(java.util.Date())
        val backupDir = File(destDir, "${inst.id}_backup_$timestamp")
        backupDir.mkdirs()

        // Copy saves
        if (inst.savesDir.exists()) copyDir(inst.savesDir, File(backupDir, "saves"))
        // Copy configs
        if (inst.configDir.exists()) copyDir(inst.configDir, File(backupDir, "config"))
        // Copy mods list (not the actual mods, just the list)
        val modList = File(backupDir, "mods-list.txt")
        val mods = inst.modsDir.listFiles()?.filter { it.extension in setOf("jar", "zip") }?.map { it.name } ?: emptyList()
        modList.writeText(mods.joinToString("\n"))

        // Copy resource packs list
        val rpList = File(backupDir, "resourcepacks-list.txt")
        val rps = inst.resourcePacksDir.listFiles()?.filter { it.extension in setOf("jar", "zip") }?.map { it.name } ?: emptyList()
        rpList.writeText(rps.joinToString("\n"))

        // Copy instance.json
        File(inst.dir, FILE).copyTo(File(backupDir, "instance.json"))

        return backupDir
    }

    /** Restores instance from backup. */
    fun restoreFromBackup(inst: Instance, backupDir: File) {
        if (!backupDir.exists()) throw IllegalStateException("Backup directory not found")
        // Restore saves
        val savesBackup = File(backupDir, "saves")
        if (savesBackup.exists()) {
            inst.savesDir.deleteRecursively()
            copyDir(savesBackup, inst.savesDir)
        }
        // Restore configs
        val configBackup = File(backupDir, "config")
        if (configBackup.exists()) {
            inst.configDir.deleteRecursively()
            copyDir(configBackup, inst.configDir)
        }
    }

    fun delete(inst: Instance) {
        // only ever delete inside the instances folder
        val root = Dirs.instances.canonicalFile
        val target = inst.dir.canonicalFile
        if (target.parentFile == root) target.deleteRecursively()
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
}
