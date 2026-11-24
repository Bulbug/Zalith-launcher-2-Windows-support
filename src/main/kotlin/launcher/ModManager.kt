package launcher

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.util.Properties
import java.util.zip.ZipFile

/** Mod management: enable/disable, updates, dependencies, detection. */
object ModManager {
    private val gson = Gson()
    private val disabledModsFile = File(Dirs.root, "disabled_mods.properties")

    data class ModInfo(
        val file: File,
        val name: String,
        val version: String,
        val loader: String,
        val mcVersion: String?,
        val dependencies: List<String> = emptyList(),
        val isEnabled: Boolean = true,
    )

    /** Scans an instance's mods folder and returns ModInfo for each mod. */
    fun scanMods(instance: Instance): List<ModInfo> {
        val mods = mutableListOf<ModInfo>()
        val disabled = loadDisabledMods(instance.id)

        if (instance.modsDir.exists()) {
            instance.modsDir.listFiles()?.filter { it.extension in setOf("jar", "zip") }?.forEach { f ->
                val info = extractModInfo(f)
                val enabled = !disabled.contains(f.name)
                mods += info.copy(file = f, isEnabled = enabled)
            }
        }

        return mods
    }

    /** Scans resource packs. */
    fun scanResourcePacks(instance: Instance): List<ModInfo> {
        val packs = mutableListOf<ModInfo>()
        val disabled = loadDisabledMods(instance.id)

        if (instance.resourcePacksDir.exists()) {
            instance.resourcePacksDir.listFiles()?.filter { it.extension in setOf("jar", "zip") }?.forEach { f ->
                val info = extractModInfo(f)
                val enabled = !disabled.contains(f.name)
                packs += info.copy(file = f, isEnabled = enabled)
            }
        }
        return packs
    }

    /** Scans shader packs. */
    fun scanShaderPacks(instance: Instance): List<ModInfo> {
        val packs = mutableListOf<ModInfo>()
        val disabled = loadDisabledMods(instance.id)

        if (instance.shadersDir.exists()) {
            instance.shadersDir.listFiles()?.filter { it.extension in setOf("jar", "zip") }?.forEach { f ->
                val info = extractModInfo(f)
                val enabled = !disabled.contains(f.name)
                packs += info.copy(file = f, isEnabled = enabled)
            }
        }
        return packs
    }

    /** Enables a mod by removing it from the disabled list. */
    fun enableMod(instance: Instance, modFile: File) {
        val disabled = loadDisabledMods(instance.id).toMutableList()
        disabled.remove(modFile.name)
        saveDisabledMods(instance.id, disabled)
    }

    /** Disables a mod by adding it to the disabled list. */
    fun disableMod(instance: Instance, modFile: File) {
        val disabled = loadDisabledMods(instance.id).toMutableList()
        if (modFile.name !in disabled) {
            disabled.add(modFile.name)
            saveDisabledMods(instance.id, disabled)
        }
    }

    /** Toggles mod enabled state. */
    fun toggleMod(instance: Instance, modFile: File): Boolean {
        val disabled = loadDisabledMods(instance.id).toMutableList()
        if (modFile.name in disabled) {
            disabled.remove(modFile.name)
            saveDisabledMods(instance.id, disabled)
            return true // now enabled
        } else {
            disabled.add(modFile.name)
            saveDisabledMods(instance.id, disabled)
            return false // now disabled
        }
    }

    /** Checks if a mod is enabled. */
    fun isEnabled(instance: Instance, modFile: File): Boolean {
        return modFile.name !in loadDisabledMods(instance.id)
    }

    /** Gets all disabled mod filenames for an instance. */
    fun getDisabledMods(instance: Instance): List<String> = loadDisabledMods(instance.id)

    private fun loadDisabledMods(instanceId: String): List<String> {
        if (!disabledModsFile.isFile) return emptyList()
        val p = Properties()
        disabledModsFile.inputStream().use { p.load(it) }
        val json = p.getProperty(instanceId, "[]")
        return try {
            val arr = JsonParser.parseString(json).asJsonArray
            arr.map { it.asString }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveDisabledMods(instanceId: String, mods: List<String>) {
        Dirs.root.mkdirs()
        val p = Properties()
        if (disabledModsFile.isFile) {
            disabledModsFile.inputStream().use { p.load(it) }
        }
        val arr = JsonArray()
        mods.forEach { arr.add(it) }
        p.setProperty(instanceId, gson.toJson(arr))
        disabledModsFile.outputStream().use { p.store(it, null) }
    }

    /** Extracts mod info from a jar/zip file. */
    fun extractModInfo(file: File): ModInfo {
        var name = file.nameWithoutExtension
        var version = "unknown"
        var loader = "unknown"
        var mcVersion: String? = null
        val dependencies = mutableListOf<String>()

        try {
            ZipFile(file).use { zip ->
                // Try different mod metadata files
                val metadataEntry = zip.getEntry("mcmod.info")
                    ?: zip.getEntry("fabric.mod.json")
                    ?: zip.getEntry("META-INF/mods.toml")
                    ?: zip.getEntry("quilt.mod.json")

                if (metadataEntry != null) {
                    val content = zip.getInputStream(metadataEntry).bufferedReader().use { it.readText() }
                    val json = JsonParser.parseString(content)

                    if (json.isJsonArray) {
                        val arr = json.asJsonArray
                        if (arr.isNotEmpty()) {
                            val obj = arr[0].asJsonObject
                            name = obj.get("name")?.asString ?: obj.get("modid")?.asString ?: obj.get("id")?.asString ?: name
                            version = obj.get("version")?.asString ?: version
                            mcVersion = obj.get("mcversion")?.asString ?: obj.get("minecraft")?.asString
                            val deps = obj.arr("depends") ?: obj.arr("dependencies")
                            deps?.forEach { dependencies += it.asString }
                        }
                    } else if (json.isJsonObject) {
                        val obj = json.asJsonObject
                        name = obj.get("name")?.asString ?: obj.get("id")?.asString ?: name
                        version = obj.get("version")?.asString ?: version
                        mcVersion = obj.get("minecraft")?.asString ?: obj.get("mcversion")?.asString
                        val deps = obj.arr("depends") ?: obj.arr("dependencies") ?: obj.arr("depends")
                        deps?.forEach { dependencies += it.asString }
                    }
                }

                // Check for loader-specific files
                if (zip.getEntry("fabric.mod.json") != null) loader = "fabric"
                else if (zip.getEntry("META-INF/mods.toml") != null) loader = "forge"
                else if (zip.getEntry("quilt.mod.json") != null) loader = "quilt"
                else if (zip.getEntry("META-INF/neoforge.mods.toml") != null) loader = "neoforge"
            }
        } catch (e: Exception) {
            // Ignore extraction errors
        }

        // Guess loader from filename if not detected
        if (loader == "unknown") {
            if (file.name.contains("fabric", ignoreCase = true)) loader = "fabric"
            else if (file.name.contains("forge", ignoreCase = true)) loader = "forge"
            else if (file.name.contains("neoforge", ignoreCase = true)) loader = "neoforge"
            else if (file.name.contains("quilt", ignoreCase = true)) loader = "quilt"
            else if (file.name.contains("optifine", ignoreCase = true)) loader = "optifine"
        }

        return ModInfo(file, name, version, loader, mcVersion, dependencies)
    }

    /** Checks for mod updates from Modrinth (placeholder - would need API integration). */
    fun checkForUpdates(mods: List<ModInfo>): List<ModUpdateInfo> {
        // This would integrate with Modrinth API to check for newer versions
        // For now, return empty list
        return emptyList()
    }

    data class ModUpdateInfo(
        val modName: String,
        val currentVersion: String,
        val latestVersion: String,
        val downloadUrl: String,
        val changelog: String?,
    )

    /** Detects duplicate mods (same mod ID, different versions). */
    fun detectDuplicates(mods: List<ModInfo>): List<List<ModInfo>> {
        val byId = mods.groupBy { it.file.nameWithoutExtension.split("-").first() }
        return byId.values.filter { it.size > 1 }.toList()
    }

    /** Detects suspicious files (empty, too small, wrong extension). */
    fun detectSuspicious(mods: List<ModInfo>): List<SuspiciousMod> {
        val suspicious = mutableListOf<SuspiciousMod>()
        mods.forEach { mod ->
            when {
                mod.file.length() == 0 -> suspicious += SuspiciousMod(mod, "Empty file (0 bytes)")
                mod.file.length() < 1024 -> suspicious += SuspiciousMod(mod, "Very small file (< 1 KB)")
                mod.file.extension !in setOf("jar", "zip") -> suspicious += SuspiciousMod(mod, "Unexpected file extension")
                else -> {
                    // Check if it's actually a valid jar
                    try {
                        ZipFile(mod.file).use { }
                    } catch (e: Exception) {
                        suspicious += SuspiciousMod(mod, "Invalid JAR/ZIP file: ${e.message}")
                    }
                }
            }
        }
        return suspicious
    }

    data class SuspiciousMod(
        val mod: ModInfo,
        val reason: String,
    )

    /** Backs up mod configurations (config folder). */
    fun backupConfigs(instance: Instance, destDir: File): File {
        destDir.mkdirs()
        val configDir = File(instance.dir, "config")
        if (configDir.exists()) {
            val backupDir = File(destDir, "config-backup-${System.currentTimeMillis()}")
            copyDir(configDir, backupDir)
            return backupDir
        }
        return destDir
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