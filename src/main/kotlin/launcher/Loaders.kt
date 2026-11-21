package launcher

import com.google.gson.JsonParser

enum class LoaderKind(val label: String, private val meta: String?) {
    VANILLA("Vanilla", null),
    FABRIC("Fabric", "https://meta.fabricmc.net/v2/versions/loader"),
    QUILT("Quilt", "https://meta.quiltmc.org/v3/versions/loader");

    /** The loader's version profile JSON for Minecraft [mc], using the newest stable loader. */
    fun profileJson(mc: String): String {
        val base = meta ?: error("Vanilla has no loader")
        val list = JsonParser.parseString(Http.getString("$base/$mc")).asJsonArray
        if (list.size() == 0) throw java.io.IOException("$label has no build for Minecraft $mc")
        val versions = list.map { it.asJsonObject.obj("loader")!! }
        val pick = versions.firstOrNull { it.get("stable")?.asBoolean == true }
            ?: versions.firstOrNull { v -> val s = v.str("version") ?: ""; !s.contains("-") && !s.contains("beta") }
            ?: versions.first()
        return Http.getString("$base/$mc/${pick.str("version")!!}/profile/json")
    }
}
