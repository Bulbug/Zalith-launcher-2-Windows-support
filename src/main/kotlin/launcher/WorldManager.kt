package launcher

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** World manager: manage single-player saves and backups. */
object WorldManager {
    private val gson = Gson()

    data class WorldInfo(
        val name: String,
        val path: File,
        val lastPlayed: Long,
        val sizeBytes: Long,
        val version: String?,
        val gameMode: String?,
        val isHardcore: Boolean,
        val icon: File?, // level.dat icon
    )

    /** Scans an instance's saves directory for worlds. */
    fun scanWorlds(instance: Instance): List<WorldInfo> {
        val worlds = mutableListOf<WorldInfo>()
        if (!instance.savesDir.exists()) return worlds

        instance.savesDir.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            val levelDat = File(dir, "level.dat")
            if (levelDat.isFile) {
                try {
                    val nbt = readLevelDat(levelDat)
                    val name = nbt.getString("LevelName") ?: dir.name
                    val lastPlayed = nbt.getLong("LastPlayed") ?: dir.lastModified()
                    val version = nbt.getString("Version")?.let { "MC ${it}" }
                    val gameMode = nbt.getInt("GameType")?.let { gameModeName(it) }
                    val isHardcore = nbt.getBoolean("hardcore") ?: false

                    // Get size
                    var size = 0L
                    dir.walk().forEach { f -> if (f.isFile) size += f.length() }

                    // Icon
                    val iconFile = File(dir, "icon.png")
                    val icon = if (iconFile.isFile()) iconFile else null

                    worlds += WorldInfo(
                        name = name,
                        path = dir,
                        lastPlayed = lastPlayed,
                        sizeBytes = size,
                        version = version,
                        gameMode = gameMode,
                        isHardcore = isHardcore,
                        icon = icon,
                    )
                } catch (e: Exception) {
                    // Fallback to basic info
                    var size = 0L
                    dir.walk().forEach { f -> if (f.isFile) size += f.length() }
                    worlds += WorldInfo(
                        name = dir.name,
                        path = dir,
                        lastPlayed = dir.lastModified(),
                        sizeBytes = size,
                        version = null,
                        gameMode = null,
                        isHardcore = false,
                        icon = null,
                    )
                }
            }
        }

        return worlds.sortedByDescending { it.lastPlayed }
    }

    /** Reads level.dat NBT data. */
    private fun readLevelDat(file: File): NbtCompound {
        // Simplified NBT reading - in production use a proper NBT library
        return NbtCompound(file)
    }

    /** Converts game mode ID to name. */
    private fun gameModeName(id: Int): String = when (id) {
        0 -> "Survival"
        1 -> "Creative"
        2 -> "Adventure"
        3 -> "Spectator"
        else -> "Unknown ($id)"
    }

    /** Creates a backup of a world. */
    fun backupWorld(world: WorldInfo, destDir: File): File {
        destDir.mkdirs()
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        val backupName = "${world.name.replace(Regex("[\\\\/:*?\"<>|]"), "_")}_backup_$timestamp"
        val backupDir = File(destDir, backupName)
        backupDir.mkdirs()

        copyDir(world.path, backupDir)

        // Create metadata
        val meta = JsonObject().apply {
            addProperty("name", world.name)
            addProperty("originalPath", world.path.absolutePath)
            addProperty("backedUpAt", System.currentTimeMillis())
            addProperty("version", world.version)
            addProperty("gameMode", world.gameMode)
            addProperty("isHardcore", world.isHardcore)
        }
        File(backupDir, "world-backup-meta.json").writeText(gson.toJson(meta))

        return backupDir
    }

    /** Restores a world from backup. */
    fun restoreWorld(backupDir: File, instance: Instance): File {
        val metaFile = File(backupDir, "world-backup-meta.json")
        val meta = if (metaFile.isFile()) {
            JsonParser.parseString(metaFile.readText()).asJsonObject
        } else {
            JsonObject()
        }

        val worldName = meta.get("name")?.asString ?: backupDir.name
        val safeName = worldName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val destDir = File(instance.savesDir, safeName)
        var finalDest = destDir
        var counter = 1
        while (finalDest.exists()) {
            finalDest = File(instance.savesDir, "${safeName}_$counter")
            counter++
        }

        finalDest.mkdirs()
        copyDir(backupDir, finalDest)

        return finalDest
    }

    /** Deletes a world. */
    fun deleteWorld(world: WorldInfo) {
        world.path.deleteRecursively()
    }

    /** Renames a world. */
    fun renameWorld(world: WorldInfo, newName: String): File {
        val safeName = newName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val destDir = File(world.path.parentFile, safeName)
        var finalDest = destDir
        var counter = 1
        while (finalDest.exists()) {
            finalDest = File(world.path.parentFile, "${safeName}_$counter")
            counter++
        }
        world.path.renameTo(finalDest)
        return finalDest
    }

    /** Copies a world (duplicate). */
    fun duplicateWorld(world: WorldInfo, newName: String? = null): File {
        val name = newName ?: "${world.name} (copy)"
        return renameWorld(world, name)
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

    /** Simple NBT compound parser for level.dat. */
    private class NbtCompound(private val file: File) {
        // This is a placeholder - real implementation would use an NBT library
        fun getString(key: String): String? = null
        fun getLong(key: String): Long? = null
        fun getInt(key: String): Int? = null
        fun getBoolean(key: String): Boolean? = null
    }
}