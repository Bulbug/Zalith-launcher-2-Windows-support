package launcher

import java.io.File
import java.util.Properties

object AppInfo {
    // Change the name here. Per the Zalith Launcher 2 license terms, a modified version
    // must not contain "ZalithLauncher" or "ZL" in its name and must say it is an
    // "Unofficial Modified Version" on the main screen.
    const val NAME = "Zalith 2 Windows Support"
    const val VERSION = "0.5.0-beta"
    const val NOTICE = "Unofficial Modified Version"
}

object Dirs {
    val root: File = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), ".desklauncher")
    val versions = File(root, "versions")
    val libraries = File(root, "libraries")
    val assets = File(root, "assets")
    val runtimes = File(root, "runtimes")
    val game = File(root, "game")           // old shared folder (vanilla, before instances existed)
    val instances = File(root, "instances") // one folder per instance: saves, mods, config...
}

data class Settings(
    val username: String = "Player",
    val ramMb: Int = 2048,
    val account: String = "offline", // "offline" or "microsoft"
    val instance: String = "",       // id of the selected instance
    val dark: Boolean = true,        // dark or light theme
    val accent: String = "GREEN",    // Accent.name in ui/Theme.kt
) {
    fun save() {
        Dirs.root.mkdirs()
        val p = Properties()
        p.setProperty("username", username)
        p.setProperty("ram", ramMb.toString())
        p.setProperty("account", account)
        p.setProperty("instance", instance)
        p.setProperty("dark", dark.toString())
        p.setProperty("accent", accent)
        File(Dirs.root, "settings.properties").outputStream().use { p.store(it, null) }
    }

    companion object {
        fun load(): Settings {
            val f = File(Dirs.root, "settings.properties")
            if (!f.isFile) return Settings()
            val p = Properties()
            f.inputStream().use { p.load(it) }
            return Settings(
                username = p.getProperty("username", "Player"),
                ramMb = p.getProperty("ram", "2048").toIntOrNull() ?: 2048,
                account = p.getProperty("account", "offline"),
                instance = p.getProperty("instance", ""),
                dark = p.getProperty("dark", "true").toBooleanStrictOrNull() ?: true,
                accent = p.getProperty("accent", "GREEN"),
            )
        }
    }
}
