package launcher

import java.io.File

/** Finds a matching Java on the PC, or downloads a JRE from Adoptium into our own folder. */
object JavaRuntime {

    /** Returns the path to java.exe for the given Java major version. Blocking: call from a background thread. */
    fun ensure(requiredMajor: Int, status: (String) -> Unit): File {
        // 1.17 / 1.17.1 ask for Java 16, which is end-of-life; 17 runs them fine.
        val major = if (requiredMajor == 16) 17 else requiredMajor

        javaExe(File(Dirs.runtimes, "jre-$major"))?.let { return it }

        for (home in candidates()) {
            val exe = javaExe(home) ?: continue
            if (majorOf(home) == major) return exe
        }

        status("Downloading Java $major (one time)…")
        return download(major)
    }

    private fun javaExe(home: File): File? = File(home, "bin/java.exe").takeIf { it.isFile }

    private fun candidates(): List<File> {
        val out = mutableListOf<File>()
        System.getenv("JAVA_HOME")?.let { out += File(it) }
        val bases = listOfNotNull(
            System.getenv("ProgramFiles"),
            System.getenv("ProgramFiles(x86)"),
            System.getenv("ProgramW6432"),
        ).distinct()
        val vendors = listOf(
            "Java", "Eclipse Adoptium", "Eclipse Foundation", "Microsoft", "Zulu",
            "Amazon Corretto", "BellSoft", "Semeru", "AdoptOpenJDK",
        )
        for (base in bases) for (v in vendors) {
            File(base, v).listFiles()?.filter { it.isDirectory }?.let { out += it }
        }
        return out
    }

    private fun majorOf(home: File): Int? {
        val rel = File(home, "release")
        if (!rel.isFile) return null
        val line = rel.readLines().firstOrNull { it.startsWith("JAVA_VERSION=") } ?: return null
        return parseMajor(line.substringAfter("=").trim().trim('"'))
    }

    fun parseMajor(v: String): Int? {
        val parts = v.split('.', '-', '+', '_')
        val first = parts.getOrNull(0)?.toIntOrNull() ?: return null
        return if (first == 1) parts.getOrNull(1)?.toIntOrNull() else first
    }

    private fun download(major: Int): File {
        val arch = if (Rules.isArm) "aarch64" else "x64"
        val url = "https://api.adoptium.net/v3/binary/latest/$major/ga/windows/$arch/jre/hotspot/normal/eclipse"
        Dirs.runtimes.mkdirs()
        val zip = File(Dirs.runtimes, "jre-$major.zip")
        zip.delete()
        Http.download(url, zip, null)

        val tmp = File(Dirs.runtimes, "jre-$major.tmp")
        tmp.deleteRecursively()
        unzip(zip, tmp)
        val home = tmp.walkTopDown().firstOrNull { File(it, "bin/java.exe").isFile }
            ?: error("The downloaded Java looks invalid")

        val target = File(Dirs.runtimes, "jre-$major")
        target.deleteRecursively()
        if (!home.renameTo(target)) {
            home.copyRecursively(target, overwrite = true)
        }
        tmp.deleteRecursively()
        zip.delete()
        return File(target, "bin/java.exe")
    }
}
