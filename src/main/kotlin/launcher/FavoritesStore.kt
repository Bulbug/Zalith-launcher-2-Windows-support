package launcher

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.util.Properties

/** Persistent storage for favorited mods/modpacks across platforms and categories. */
object FavoritesStore {
    private val file = File(Dirs.root, "favorites.properties")
    private val gson = Gson()

    data class FavoriteEntry(
        val platform: String,          // "modrinth" or "curseforge"
        val projectId: String,         // Modrinth slug or CurseForge project ID
        val category: String,          // "mod", "modpack", "resourcepack", "shader"
        val title: String,
        val author: String,
        val description: String,
        val downloads: Long,
        val timestamp: Long = System.currentTimeMillis(),
    )

    /** Load all favorites. */
    fun load(): List<FavoriteEntry> {
        if (!file.isFile) return emptyList()
        val p = Properties()
        file.inputStream().use { p.load(it) }
        val jsonStr = p.getProperty("favorites", "[]")
        return try {
            val arr = JsonParser.parseString(jsonStr).asJsonArray
            arr.map { it.asJsonObject }.map { o ->
                FavoriteEntry(
                    platform = o.get("platform").asString,
                    projectId = o.get("projectId").asString,
                    category = o.get("category").asString,
                    title = o.get("title").asString,
                    author = o.get("author").asString,
                    description = o.get("description").asString,
                    downloads = o.get("downloads").asLong,
                    timestamp = o.get("timestamp").asLong
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Save all favorites. */
    fun save(favorites: List<FavoriteEntry>) {
        Dirs.root.mkdirs()
        val arr = JsonArray()
        favorites.forEach { f ->
            val o = JsonObject().apply {
                addProperty("platform", f.platform)
                addProperty("projectId", f.projectId)
                addProperty("category", f.category)
                addProperty("title", f.title)
                addProperty("author", f.author)
                addProperty("description", f.description)
                addProperty("downloads", f.downloads)
                addProperty("timestamp", f.timestamp)
            }
            arr.add(o)
        }
        val p = Properties()
        p.setProperty("favorites", gson.toJson(arr))
        file.outputStream().use { p.store(it, null) }
    }

    /** Add a favorite. */
    fun add(entry: FavoriteEntry) {
        val current = load()
        // Remove duplicate if exists
        val filtered = current.filterNot { it.platform == entry.platform && it.projectId == entry.projectId && it.category == entry.category }
        save(filtered + entry)
    }

    /** Remove a favorite. */
    fun remove(platform: String, projectId: String, category: String) {
        val current = load()
        val filtered = current.filterNot { it.platform == platform && it.projectId == projectId && it.category == category }
        save(filtered)
    }

    /** Check if an item is favorited. */
    fun isFavorite(platform: String, projectId: String, category: String): Boolean {
        return load().any { it.platform == platform && it.projectId == projectId && it.category == category }
    }

    /** Toggle favorite state. */
    fun toggle(entry: FavoriteEntry): Boolean {
        if (isFavorite(entry.platform, entry.projectId, entry.category)) {
            remove(entry.platform, entry.projectId, entry.category)
            return false
        } else {
            add(entry)
            return true
        }
    }
}