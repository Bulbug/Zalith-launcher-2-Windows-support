package launcher

import com.google.gson.JsonParser

enum class LoaderKind(
    val label: String,
    private val meta: String?,
    /** Loader names as Modrinth calls them (used to find compatible mods). */
    val modrinth: List<String>,
    /** Forge and NeoForge are installed by running their official installer. */
    val usesInstaller: Boolean = false,
) {
    VANILLA("Vanilla", null, emptyList()),
    OPTIFINE("OptiFine", null, emptyList()),
    FABRIC("Fabric", "https://meta.fabricmc.net/v2/versions/loader", listOf("fabric")),
    QUILT("Quilt", "https://meta.quiltmc.org/v3/versions/loader", listOf("quilt", "fabric")),
    FORGE("Forge", null, listOf("forge"), true),
    NEOFORGE("NeoForge", null, listOf("neoforge"), true);

    /** Fabric/Quilt: the loader's version profile JSON for Minecraft [mc], using the newest stable loader. */
    fun profileJson(mc: String, loaderVersion: String? = null): String {
        val base = meta ?: error("$label has no meta server")
        if (loaderVersion != null) return Http.getString("$base/$mc/$loaderVersion/profile/json")
        val list = JsonParser.parseString(Http.getString("$base/$mc")).asJsonArray
        if (list.size() == 0) throw java.io.IOException("$label has no build for Minecraft $mc")
        val versions = list.map { it.asJsonObject.obj("loader")!! }
        val pick = versions.firstOrNull { it.get("stable")?.asBoolean == true }
            ?: versions.firstOrNull { v -> val s = v.str("version") ?: ""; !s.contains("-") && !s.contains("beta") }
            ?: versions.first()
        return Http.getString("$base/$mc/${pick.str("version")!!}/profile/json")
    }
}
