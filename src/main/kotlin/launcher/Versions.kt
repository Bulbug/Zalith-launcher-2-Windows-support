package launcher

data class VersionEntry(val id: String, val type: String, val url: String, val sha1: String?)

object Manifest {
    private const val URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"

    fun load(): List<VersionEntry> {
        val o = parseJson(Http.getString(URL))
        return o.arr("versions")!!.map { it.asJsonObject }.map {
            VersionEntry(it.str("id")!!, it.str("type") ?: "release", it.str("url")!!, it.str("sha1"))
        }
    }
}
