package launcher

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipFile

/** Crash and diagnostics manager for game logs and crash reports. */
object CrashManager {
    data class CrashReport(
        val timestamp: String,
        val instanceId: String,
        val instanceName: String,
        val mcVersion: String,
        val loader: String,
        val loaderVersion: String?,
        val exitCode: Int?,
        val crashReason: String?,
        val stackTrace: String?,
        val gameLogPath: String?,
        val systemInfo: JsonObject,
        val mods: List<ModInfo>,
    )

    data class ModInfo(
        val name: String,
        val version: String,
        val loader: String,
        val path: String,
    )

    private val reportsDir = File(Dirs.root, "crash-reports")
    private val gson = Gson()

    init {
        reportsDir.mkdirs()
    }

    /** Analyzes a game log file and returns diagnostic info. */
    fun analyzeLog(logFile: File): DiagnosticResult {
        if (!logFile.isFile) return DiagnosticResult(false, "Log file not found", emptyList(), emptyList())

        val content = logFile.readText()
        val lines = content.lines().toList()

        val errors = mutableListOf<DiagnosticEntry>()
        val warnings = mutableListOf<DiagnosticEntry>()

        // Common error patterns
        val errorPatterns = listOf(
            "Exception" to "exception",
            "Error:" to "error",
            "FAILED" to "failed",
            "Could not" to "failed",
            "Caused by:" to "caused_by",
            "NoClassDefFoundError" to "missing_class",
            "ClassNotFoundException" to "missing_class",
            "OutOfMemoryError" to "oom",
            "StackOverflowError" to "stack_overflow",
        )

        val warningPatterns = listOf(
            "WARN" to "warning",
            "WARNING" to "warning",
            "deprecated" to "deprecated",
        )

        lines.forEachIndexed { index, line ->
            errorPatterns.forEach { (pattern, category) ->
                if (line.contains(pattern, ignoreCase = true)) {
                    errors += DiagnosticEntry(
                        lineNumber = index + 1,
                        category = category,
                        message = line.trim(),
                        context = getContext(lines, index, 2)
                    )
                }
            }
            warningPatterns.forEach { (pattern, category) ->
                if (line.contains(pattern, ignoreCase = true)) {
                    warnings += DiagnosticEntry(
                        lineNumber = index + 1,
                        category = category,
                        message = line.trim(),
                        context = getContext(lines, index, 2)
                    )
                }
            }
        }

        val hasErrors = errors.isNotEmpty()
        val summary = if (hasErrors) {
            val categories = errors.groupBy { it.category }.mapValues { it.value.size }
            "Found ${errors.size} error(s): ${categories.entries.joinToString(", ") { "${it.key}=${it.value}" }}"
        } else {
            "No obvious errors detected"
        }

        return DiagnosticResult(hasErrors, summary, errors, warnings)
    }

    private fun getContext(lines: List<String>, index: Int, radius: Int): List<String> {
        val start = maxOf(0, index - radius)
        val end = minOf(lines.size, index + radius + 1)
        return lines.subList(start, end).mapIndexed { i, l -> "${start + i + 1}: $l" }
    }

    /** Creates a crash report from a game exit. */
    fun createCrashReport(
        instance: Instance,
        exitCode: Int?,
        gameLogPath: String?,
        exception: Throwable? = null,
    ): CrashReport {
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        val logContent = gameLogPath?.let { File(it).readText() } ?: ""
        val diagnostic = gameLogPath?.let { analyzeLog(File(it)) }

        val crashReason = exception?.message ?: diagnostic?.errors?.firstOrNull()?.message ?: "Unknown crash (exit code $exitCode)"
        val stackTrace = exception?.stackTraceToString() ?: diagnostic?.errors?.firstOrNull()?.context?.joinToString("\n")

        // Collect installed mods
        val mods = mutableListOf<ModInfo>()
        listOf(instance.modsDir, instance.resourcePacksDir, instance.shadersDir).forEach { dir ->
            if (dir.exists()) {
                dir.listFiles()?.filter { it.extension in setOf("jar", "zip") }?.forEach { f ->
                    // Try to extract mod info from filename or embedded mcmod.info
                    val modInfo = extractModInfo(f)
                    mods += modInfo
                }
            }
        }

        val systemInfo = collectSystemInfo()

        val report = CrashReport(
            timestamp = timestamp,
            instanceId = instance.id,
            instanceName = instance.name,
            mcVersion = instance.mc,
            loader = instance.loader.name,
            loaderVersion = instance.loaderVersion,
            exitCode = exitCode,
            crashReason = crashReason,
            stackTrace = stackTrace,
            gameLogPath = gameLogPath,
            systemInfo = systemInfo,
            mods = mods,
        )

        // Save report
        val reportFile = File(reportsDir, "crash-${timestamp}-${instance.id}.json")
        reportFile.writeText(gson.toJson(report))

        return report
    }

    private fun extractModInfo(file: File): ModInfo {
        // Try to read mcmod.info or fabric.mod.json from the jar
        var name = file.nameWithoutExtension
        var version = "unknown"
        var loader = "unknown"

        try {
            ZipFile(file).use { zip ->
                val mcmod = zip.getEntry("mcmod.info") ?: zip.getEntry("fabric.mod.json") ?: zip.getEntry("META-INF/mods.toml")
                if (mcmod != null) {
                    val content = zip.getInputStream(mcmod).bufferedReader().use { it.readText() }
                    val json = JsonParser.parseString(content)
                    if (json.isJsonArray) {
                        val arr = json.asJsonArray
                        if (arr.size > 0) {
                            val obj = arr[0].asJsonObject
                            name = obj.get("name")?.asString ?: obj.get("modid")?.asString ?: name
                            version = obj.get("version")?.asString ?: version
                        }
                    } else if (json.isJsonObject) {
                        val obj = json.asJsonObject
                        name = obj.get("name")?.asString ?: obj.get("id")?.asString ?: name
                        version = obj.get("version")?.asString ?: version
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        // Guess loader from filename
        if (file.name.contains("fabric", ignoreCase = true)) loader = "fabric"
        else if (file.name.contains("forge", ignoreCase = true)) loader = "forge"
        else if (file.name.contains("neoforge", ignoreCase = true)) loader = "neoforge"
        else if (file.name.contains("quilt", ignoreCase = true)) loader = "quilt"
        else if (file.name.contains("optifine", ignoreCase = true)) loader = "optifine"

        return ModInfo(name, version, loader, file.absolutePath)
    }

    private fun collectSystemInfo(): JsonObject {
        return JsonObject().apply {
            addProperty("os", System.getProperty("os.name"))
            addProperty("osVersion", System.getProperty("os.version"))
            addProperty("arch", System.getProperty("os.arch"))
            addProperty("javaVersion", System.getProperty("java.version"))
            addProperty("javaVendor", System.getProperty("java.vendor"))
            addProperty("javaHome", System.getProperty("java.home"))
            addProperty("maxMemory", Runtime.getRuntime().maxMemory())
            addProperty("availableProcessors", Runtime.getRuntime().availableProcessors())
        }
    }

    /** Gets all crash reports. */
    fun getReports(): List<CrashReport> {
        return reportsDir.listFiles()?.filter { it.extension == "json" }
            ?.sortedByDescending { it.lastModified() }
            ?.mapNotNull { f ->
                try {
                    gson.fromJson(f.readText(), CrashReport::class.java)
                } catch (e: Exception) {
                    null
                }
            } ?: emptyList()
    }

    /** Exports a diagnostic package (zip with logs, reports, system info). */
    fun exportDiagnosticPackage(instance: Instance, outputFile: File): File {
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        val tempDir = File(Dirs.root, "temp_diag_$timestamp")
        tempDir.mkdirs()

        try {
            // Copy game logs
            val logsDir = File(tempDir, "logs")
            logsDir.mkdirs()
            instance.dir.listFiles()?.filter { it.name.endsWith(".log") }?.forEach { log ->
                log.copyTo(File(logsDir, log.name))
            }

            // Copy crash reports for this instance
            val reportsDir = File(tempDir, "crash-reports")
            reportsDir.mkdirs()
            getReports().filter { it.instanceId == instance.id }.forEach { report ->
                val reportFile = File(reportsDir, "crash-${report.timestamp}.json")
                reportFile.writeText(gson.toJson(report))
            }

            // Create system info file
            File(tempDir, "system-info.json").writeText(gson.toJson(collectSystemInfo()))

            // Create mod list
            val modList = mutableListOf<JsonObject>()
            listOf(instance.modsDir, instance.resourcePacksDir, instance.shadersDir).forEach { dir ->
                dir.listFiles()?.filter { it.extension in setOf("jar", "zip") }?.forEach { f ->
                    val info = extractModInfo(f)
                    modList += JsonObject().apply {
                        addProperty("name", info.name)
                        addProperty("version", info.version)
                        addProperty("loader", info.loader)
                        addProperty("path", info.path)
                    }
                }
            }
            File(tempDir, "mods.json").writeText(gson.toJson(modList))

            // Create instance info
            File(tempDir, "instance.json").writeText(gson.toJson(JsonObject().apply {
                addProperty("id", instance.id)
                addProperty("name", instance.name)
                addProperty("mc", instance.mc)
                addProperty("loader", instance.loader.name)
                addProperty("loaderVersion", instance.loaderVersion)
                addProperty("optifineVersion", instance.optifineVersion)
            }))

            // Zip it
            outputFile.parentFile?.mkdirs()
            InstanceExport.exportDirectory(tempDir, outputFile)

            return outputFile
        } finally {
            tempDir.deleteRecursively()
        }
    }

    data class DiagnosticResult(
        val hasErrors: Boolean,
        val summary: String,
        val errors: List<DiagnosticEntry>,
        val warnings: List<DiagnosticEntry>,
    )

    data class DiagnosticEntry(
        val lineNumber: Int,
        val category: String,
        val message: String,
        val context: List<String>,
    )
}

// Extension to InstanceExport for exporting directories
fun InstanceExport.exportDirectory(source: File, dest: File) {
    dest.parentFile?.mkdirs()
    java.util.zip.ZipOutputStream(java.io.BufferedOutputStream(java.io.FileOutputStream(dest))).use { zip ->
        zip.setLevel(6)
        fun addToZip(file: File, basePath: String) {
            val path = if (basePath.isEmpty()) file.name else "$basePath/${file.name}"
            if (file.isDirectory) {
                zip.putNextEntry(java.util.zip.ZipEntry("$path/"))
                zip.closeEntry()
                file.listFiles()?.forEach { addToZip(it, path) }
            } else {
                zip.putNextEntry(java.util.zip.ZipEntry(path))
                file.inputStream().use { zip.write(it.readAllBytes()) }
                zip.closeEntry()
            }
        }
        addToZip(source, "")
    }
}