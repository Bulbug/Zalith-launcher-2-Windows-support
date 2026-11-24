package launcher

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.util.Properties

/** Server manager: save, organize, and launch multiplayer server entries. */
object ServerManager {
    private val gson = Gson()
    private val serversFile = File(Dirs.root, "servers.properties")

    data class ServerEntry(
        val id: String,
        val name: String,
        val address: String,           // host:port or just host (default port 25565)
        val icon: String? = null,      // base64 encoded 64x64 PNG
        val category: String = "default",
        val hidden: Boolean = false,
        val lastConnected: Long = 0,
    )

    /** Loads all saved servers. */
    fun loadServers(): List<ServerEntry> {
        if (!serversFile.isFile) return emptyList()
        val p = Properties()
        serversFile.inputStream().use { p.load(it) }
        val json = p.getProperty("servers", "[]")
        return try {
            val arr = JsonParser.parseString(json).asJsonArray
            arr.map { it.asJsonObject }.map { o ->
                ServerEntry(
                    id = o.get("id").asString,
                    name = o.get("name").asString,
                    address = o.get("address").asString,
                    icon = o.get("icon")?.asString?.takeIf { it.isNotBlank() },
                    category = o.get("category")?.asString ?: "default",
                    hidden = o.get("hidden")?.asBoolean ?: false,
                    lastConnected = o.get("lastConnected")?.asLong ?: 0,
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Saves all servers. */
    fun saveServers(servers: List<ServerEntry>) {
        Dirs.root.mkdirs()
        val p = Properties()
        if (serversFile.isFile) {
            serversFile.inputStream().use { p.load(it) }
        }
        val arr = JsonArray()
        servers.forEach { s ->
            val o = JsonObject().apply {
                addProperty("id", s.id)
                addProperty("name", s.name)
                addProperty("address", s.address)
                s.icon?.let { addProperty("icon", it) }
                addProperty("category", s.category)
                addProperty("hidden", s.hidden)
                addProperty("lastConnected", s.lastConnected)
            }
            arr.add(o)
        }
        p.setProperty("servers", gson.toJson(arr))
        serversFile.outputStream().use { p.store(it, null) }
    }

    /** Adds a new server. */
    fun addServer(server: ServerEntry) {
        val servers = loadServers()
        if (servers.any { it.id == server.id }) return
        saveServers(servers + server)
    }

    /** Updates an existing server. */
    fun updateServer(server: ServerEntry) {
        val servers = loadServers().map { if (it.id == server.id) server else it }
        saveServers(servers)
    }

    /** Removes a server. */
    fun removeServer(id: String) {
        val servers = loadServers().filter { it.id != id }
        saveServers(servers)
    }

    /** Gets a server by ID. */
    fun getServer(id: String): ServerEntry? = loadServers().firstOrNull { it.id == id }

    /** Updates last connected time. */
    fun updateLastConnected(id: String) {
        val servers = loadServers().map { s ->
            if (s.id == id) s.copy(lastConnected = System.currentTimeMillis()) else s
        }
        saveServers(servers)
    }

    /** Generates a unique server ID. */
    fun generateId(name: String): String {
        var base = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "server" }
        var id = base
        var n = 2
        while (loadServers().any { it.id == id }) {
            id = "$base-$n"
            n++
        }
        return id
    }

    /** Parses server address into host and port. */
    fun parseAddress(address: String): Pair<String, Int> {
        if (address.contains(":")) {
            val parts = address.split(":")
            return parts[0] to parts[1].toIntOrNull() ?: 25565
        }
        return address to 25565
    }
}