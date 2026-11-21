package launcher

import java.io.File
import java.util.Properties

object AppInfo {
    // Change the name here. Per the Zalith Launcher 2 license terms, a modified version
    // must not contain "ZalithLauncher" or "ZL" in its name and must say it is an
    // "Unofficial Modified Version" on the main screen.
    const val NAME = "Desk Launcher"
    const val VERSION = "0.1.0"
    const val NOTICE = "Unofficial Modified Version"
}

object Dirs {
    val root: File = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), ".desklauncher")
    val versions = File(root, "versions")
    val libraries = File(root, "libraries")
    val assets = File(root, "assets")
    val runtimes = File(root, "runtimes")
    val game = File(root, "game") // working directory: saves, mods, resourcepacks...
}

data class Settings(
    val username: String = "Player",
    val ramMb: Int = 2048,
    val version: String = "",
    val loader: String = "VANILLA",
    val account: String = "offline", // "offline" or "microsoft"
) {
    fun save() {
        Dirs.root.mkdirs()
        val p = Properties()
        p.setProperty("username", username)
        p.setProperty("ram", ramMb.toString())
        p.setProperty("version", version)
        p.setProperty("loader", loader)
        p.setProperty("account", account)
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
                version = p.getProperty("version", ""),
                loader = p.getProperty("loader", "VANILLA"),
                account = p.getProperty("account", "offline"),
            )
        }
    }
}
