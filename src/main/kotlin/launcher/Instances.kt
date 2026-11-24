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
) {
    val dir: File get() = File(Dirs.instances, id)
    val modsDir: File get() = File(dir, "mods")
    val resourcePacksDir: File get() = File(dir, "resourcepacks")
    val shadersDir: File get() = File(dir, "shaderpacks")
    val savesDir: File get() = File(dir, "saves")
    val subtitle: String
        get() = "$mc - ${loader.label}" + (loaderVersion?.let { " $it" } ?: "") + (optifineVersion?.let { " + OptiFine" } ?: "")
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
        val o = JsonObject()
        o.addProperty("name", clean)
        o.addProperty("mc", mc)
        o.addProperty("loader", loader.name)
        if (loaderVersion != null) o.addProperty("loaderVersion", loaderVersion)
        if (optifineVersion != null) o.addProperty("optifineVersion", optifineVersion)
        o.addProperty("isModpack", false)
        File(inst.dir, FILE).writeText(o.toString())
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
        val o = JsonObject()
        o.addProperty("name", clean)
        o.addProperty("mc", mc)
        o.addProperty("loader", loader.name)
        if (loaderVersion != null) o.addProperty("loaderVersion", loaderVersion)
        o.addProperty("isModpack", true)
        File(inst.dir, FILE).writeText(o.toString())
        return inst
    }

    fun delete(inst: Instance) {
        // only ever delete inside the instances folder
        val root = Dirs.instances.canonicalFile
        val target = inst.dir.canonicalFile
        if (target.parentFile == root) target.deleteRecursively()
    }
}
