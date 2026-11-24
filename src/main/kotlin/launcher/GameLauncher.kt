package launcher

import com.google.gson.JsonArray
import java.io.File

object GameLauncher {

    fun buildCommand(inst: Installed, javaExe: File, account: Account, ramMb: Int, extraJvmArgs: String = ""): List<String> {
        val json = inst.json
        val cp = (inst.libs.map { it.second } + inst.clientJar).joinToString(File.pathSeparator) { it.absolutePath }

        val vars = mapOf(
            "auth_player_name" to account.name,
            "version_name" to inst.id,
            "game_directory" to inst.gameDir.absolutePath,
            "assets_root" to Dirs.assets.absolutePath,
            "assets_index_name" to inst.assetsIndexId,
            "auth_uuid" to account.uuid,
            "auth_access_token" to account.accessToken,
            "auth_session" to account.accessToken,
            "clientid" to "",
            "auth_xuid" to "",
            "user_type" to account.userType,
            "version_type" to (json.str("type") ?: "release"),
            "user_properties" to "{}",
            "game_assets" to (inst.virtualAssetsDir ?: Dirs.assets).absolutePath,
            "natives_directory" to inst.nativesDir.absolutePath,
            "launcher_name" to AppInfo.NAME,
            "launcher_version" to AppInfo.VERSION,
            "classpath" to cp,
            "classpath_separator" to File.pathSeparator,
            "library_directory" to Dirs.libraries.absolutePath,
            "primary_jar" to inst.clientJar.absolutePath,
            "primary_jar_name" to inst.clientJar.name,
        )

        val args = json.obj("arguments")
        val jvmRaw: List<String> =
            if (args?.arr("jvm") != null) collect(args.arr("jvm"))
            else listOf("-Djava.library.path=\${natives_directory}", "-cp", "\${classpath}")
        val gameRaw: List<String> =
            if (args?.arr("game") != null) collect(args.arr("game"))
            else (json.str("minecraftArguments") ?: "").split(" ").filter { it.isNotBlank() }

        val cmd = mutableListOf(
            javaExe.absolutePath,
            "-Xmx${ramMb}M",
            "-Xms${minOf(ramMb, 512)}M",
        )
        // Add extra JVM args from instance settings
        if (extraJvmArgs.isNotBlank()) {
            cmd += extraJvmArgs.split(" ").filter { it.isNotBlank() }
        }
        val jvm = jvmRaw.map { sub(it, vars) }
        cmd += jvm
        if ("-cp" !in jvm && "-classpath" !in jvm) {
            cmd += listOf("-cp", cp)
        }
        if (inst.logArg != null && inst.logConfig != null) {
            cmd += inst.logArg.replace("\${path}", inst.logConfig.absolutePath)
        }
        cmd += json.str("mainClass") ?: error("This version has no main class")
        cmd += gameRaw.map { sub(it, vars) }
        return cmd
    }

    /** Starts the game. [onLine] is called for every line of game output (on a background thread). */
    fun start(cmd: List<String>, workDir: File, onLine: (String) -> Unit): Process {
        workDir.mkdirs()
        val p = ProcessBuilder(cmd)
            .directory(workDir)
            .redirectErrorStream(true)
            .start()
        Thread { p.inputStream.bufferedReader().forEachLine(onLine) }
            .apply { isDaemon = true }
            .start()
        return p
    }

    private fun collect(arr: JsonArray?): List<String> {
        val out = mutableListOf<String>()
        arr?.forEach { e ->
            if (e.isJsonPrimitive) {
                out += e.asString
            } else {
                val o = e.asJsonObject
                if (!Rules.allowed(o.arr("rules"))) return@forEach
                val v = o.get("value")
                if (v.isJsonArray) v.asJsonArray.forEach { out += it.asString } else out += v.asString
            }
        }
        return out
    }

    private val placeholder = Regex("""\$\{([a-zA-Z_]+)\}""")

    private fun sub(s: String, vars: Map<String, String>): String =
        placeholder.replace(s) { m -> vars[m.groupValues[1]] ?: m.value }
}
