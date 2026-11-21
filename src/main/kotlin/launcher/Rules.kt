package launcher

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/** Evaluates Mojang's "rules" arrays for Windows. */
object Rules {
    private val archProp = System.getProperty("os.arch").lowercase()
    val is64: Boolean = archProp.contains("64")
    val isArm: Boolean = archProp.contains("aarch64") || archProp.contains("arm64")

    // Mojang's names: "x86", "arm64"; x64 machines match neither of those.
    private val myArch = when {
        isArm -> "arm64"
        is64 -> "x86_64"
        else -> "x86"
    }

    fun allowed(rules: JsonArray?): Boolean {
        if (rules == null || rules.size() == 0) return true
        var allowed = false
        for (e in rules) {
            val r = e.asJsonObject
            if (matches(r)) allowed = r.str("action") == "allow"
        }
        return allowed
    }

    private fun matches(r: JsonObject): Boolean {
        val os = r.obj("os")
        if (os != null) {
            val name = os.str("name")
            if (name != null && name != "windows") return false
            val arch = os.str("arch")
            if (arch != null && arch != myArch) return false
            val version = os.str("version")
            if (version != null && !Regex(version).containsMatchIn(System.getProperty("os.version"))) return false
        }
        // Feature rules (demo mode, custom resolution, quick play...) are all treated as "off".
        val features = r.obj("features")
        if (features != null && features.size() > 0) return false
        return true
    }
}
