package launcher.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import launcher.Account
import launcher.AccountStore
import launcher.AuthException
import launcher.CrashManager
import launcher.Dirs
import launcher.DeviceCode
import launcher.GameInstaller
import launcher.GameLauncher
import launcher.Instance
import launcher.InstanceExport
import launcher.InstanceStore
import launcher.JavaRuntime
import launcher.LoaderKind
import launcher.Manifest
import launcher.MicrosoftAuth
import launcher.ModpackInstaller
import launcher.Settings
import launcher.StoredAccount
import launcher.VersionEntry
import launcher.ui.ColorTheme
import launcher.ui.screens.InstanceFilter
import kotlinx.coroutines.CancellationException
import launcher.Instance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Navigation targets for the top bar rail. */
enum class RailScreen { HOME, DOWNLOAD, DOWNLOAD_MANAGER, CRASH_DIAGNOSTICS, MOD_MANAGEMENT, INSTANCES, SETTINGS }

/** Which category is open inside the Download screen. */
enum class DownloadTab { MODS, MODPACKS, GAME, RESOURCE_PACKS, SAVES, SHADERS, SEARCH_ID, FAVORITES, CURSEFORGE_MODS, CURSEFORGE_MODPACKS }

/**
 * All launcher state + actions, hoisted out of composables so the screens are thin views.
 * Created once in App(); survives rail navigation.
 */
class LauncherState(private val scope: CoroutineScope) {
    var screen by mutableStateOf(RailScreen.HOME)
    // ---- theme ----
    var dark by mutableStateOf(Settings.load().dark)
    var colorTheme by mutableStateOf(
        runCatching { ColorTheme.valueOf(Settings.load().colorTheme) }.getOrDefault(ColorTheme.VERDANT_DAWN)
    )

    fun applyDark(v: Boolean) {
        dark = v
        currentSettings { it.copy(dark = v) }
    }

    fun applyColorTheme(t: ColorTheme) {
        colorTheme = t
        currentSettings { it.copy(colorTheme = t.name) }
    }

    // ---- data ----
    var versions by mutableStateOf<List<VersionEntry>>(emptyList())
    var instances by mutableStateOf(InstanceStore.list())
    var filteredInstances by mutableStateOf<List<Instance>>(emptyList())
    var selectedId by mutableStateOf(Settings.load().instance)
    var msAccount by mutableStateOf(AccountStore.load())
    var currentFilter by mutableStateOf(InstanceFilter.ALL)

    val selected: Instance? get() = instances.firstOrNull { it.id == selectedId }

    // ---- run state ----
    var status by mutableStateOf("Loading versions…")
    var progress by mutableStateOf<Float?>(null)
    var busy by mutableStateOf(false)
    val log = mutableStateListOf<String>()

    // ---- toast/snackbar ----
    var toastMessage by mutableStateOf<String?>(null)
    var toastType by mutableStateOf<ToastType>(ToastType.INFO)

    fun showToast(message: String, type: ToastType = ToastType.INFO) {
        toastMessage = message
        toastType = type
        scope.launch {
            kotlinx.coroutines.delay(3000)
            if (toastMessage == message) toastMessage = null
        }
    }

    enum class ToastType { INFO, SUCCESS, WARNING, ERROR }

    // ---- dialogs ----
    var deviceCode by mutableStateOf<DeviceCode?>(null)
    var showNewInstance by mutableStateOf(false)
    fun showNewInstanceDialog() {
        showNewInstance = true
    }
    var deleteTarget by mutableStateOf<Instance?>(null)
    private var signInJob: Job? = null

    init {
        scope.launch {
            try {
                versions = withContext(Dispatchers.IO) { Manifest.load() }
                status = "Ready"
            } catch (e: Exception) {
                status = "Couldn't load versions: ${e.message}"
            }
        }
    }

    fun addLog(line: String) {
        log.add(line)
        if (log.size > 3000) repeat(500) { log.removeAt(0) }
    }

    fun refreshInstances(select: String? = null) {
        instances = InstanceStore.list()
        filterInstances(currentFilter)
        if (select != null) selectedId = select
        else if (instances.none { it.id == selectedId }) selectedId = instances.firstOrNull()?.id ?: ""
    }

    fun selectInstance(id: String) {
        if (!busy) selectedId = id
    }

    fun filterInstances(filter: InstanceFilter) {
        currentFilter = filter
        filteredInstances = instances.filter { inst ->
            when (filter) {
                InstanceFilter.ALL -> true
                InstanceFilter.VANILLA -> inst.loader == LoaderKind.VANILLA && !inst.isModpack
                InstanceFilter.MODLOADER -> inst.loader != LoaderKind.VANILLA && inst.loader != LoaderKind.OPTIFINE && !inst.isModpack
                InstanceFilter.MODPACK -> inst.isModpack
            }
        }
    }

    private inline fun currentSettings(update: (Settings) -> Settings) {
        val saved = Settings.load()
        update(saved).save()
    }

    // ---- account ----

    fun signIn() {
        if (signInJob?.isActive == true) return
        signInJob = scope.launch {
            try {
                status = "Contacting Microsoft…"
                val dc = withContext(Dispatchers.IO) { MicrosoftAuth.requestDeviceCode() }
                deviceCode = dc
                openBrowser(dc.verificationUri)
                val tokens = withContext(Dispatchers.IO) {
                    MicrosoftAuth.pollForToken(dc) { !isActive }
                }
                status = "Signing in to Xbox and Minecraft…"
                val session = withContext(Dispatchers.IO) { MicrosoftAuth.minecraftSession(tokens.access) }
                val stored = StoredAccount(session.name, session.uuid, tokens.refresh)
                AccountStore.save(stored)
                msAccount = stored
                status = "Signed in as ${session.name}"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = "Sign-in failed: ${e.message}"
            } finally {
                deviceCode = null
            }
        }
    }

    fun cancelSignIn() {
        signInJob?.cancel()
        deviceCode = null
        status = "Sign-in cancelled"
    }

    fun signOut() {
        AccountStore.clear()
        msAccount = null
    }

    // ---- instances ----

    fun createInstance(name: String, mc: String, loader: LoaderKind, loaderVersion: String?, optifineVersion: String?) {
        val inst = InstanceStore.create(name, mc, loader, loaderVersion, optifineVersion)
        refreshInstances(inst.id)
        showNewInstance = false
    }

    fun deleteInstance(inst: Instance) {
        InstanceStore.delete(inst)
        deleteTarget = null
        refreshInstances()
    }

    fun importModpack(file: File?, onInstalled: (Instance) -> Unit) {
        if (file == null) return
        busy = true
        progress = null
        status = "Installing modpack…"
        scope.launch {
            try {
                val inst = withContext(Dispatchers.IO) {
                    ModpackInstaller.install(
                        file,
                        status = { status = it },
                        progress = { d, t -> progress = if (t > 0) d.toFloat() / t else null },
                    )
                }
                // Update the instance to mark it as a modpack
                val modpackInst = InstanceStore.createModpack(inst.name, inst.mc, inst.loader, inst.loaderVersion)
                // Copy mods from the installed instance to the modpack instance
                if (inst.modsDir.exists()) {
                    copyDir(inst.modsDir, modpackInst.modsDir)
                }
                // Copy resource packs
                if (inst.resourcePacksDir.exists()) {
                    copyDir(inst.resourcePacksDir, modpackInst.resourcePacksDir)
                }
                // Copy shader packs
                if (inst.shadersDir.exists()) {
                    copyDir(inst.shadersDir, modpackInst.shadersDir)
                }
                // Delete the original non-modpack instance
                InstanceStore.delete(inst)
                refreshInstances(modpackInst.id)
                onInstalled(modpackInst)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = "Modpack install failed: ${e.message}"
            } finally {
                busy = false
                progress = null
            }
        }
    }

    private fun copyDir(source: File, target: File) {
        target.mkdirs()
        source.listFiles()?.forEach { f ->
            if (f.isDirectory) {
                copyDir(f, File(target, f.name))
            } else {
                f.copyTo(File(target, f.name), overwrite = true)
            }
        }
    }

    fun exportInstance(inst: Instance, file: File) {
        busy = true
        status = "Exporting instance…"
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    InstanceExport.export(inst, file) { status = it }
                }
                status = "Instance exported to ${file.name}"
                showToast("Instance exported", LauncherState.ToastType.SUCCESS)
            } catch (e: Exception) {
                status = "Export failed: ${e.message}"
                showToast("Export failed: ${e.message}", LauncherState.ToastType.ERROR)
            } finally {
                busy = false
            }
        }
    }

    fun importInstance(file: File) {
        busy = true
        status = "Importing instance…"
        scope.launch {
            try {
                val inst = withContext(Dispatchers.IO) {
                    InstanceExport.importPack(file) { status = it }
                }
                refreshInstances(inst.id)
                status = "Instance \"${inst.name}\" imported"
                showToast("Instance imported", LauncherState.ToastType.SUCCESS)
            } catch (e: Exception) {
                status = "Import failed: ${e.message}"
                showToast("Import failed: ${e.message}", LauncherState.ToastType.ERROR)
            } finally {
                busy = false
            }
        }
    }

    // ---- play ----

    fun play() {
        val inst = selected
        if (inst == null) {
            status = "Create or select an instance first"
            return
        }
        val entry = versions.firstOrNull { it.id == inst.mc }
            ?: File(Dirs.versions, "${inst.mc}/${inst.mc}.json").takeIf { it.isFile }
                ?.let { VersionEntry(inst.mc, "release", "", null) }
        if (entry == null) {
            status = "Minecraft ${inst.mc} isn't in the version list (check your internet connection)"
            return
        }
        val name = Settings.load().username.trim()
        val microsoft = Settings.load().account == "microsoft"
        if (microsoft) {
            if (AccountStore.load() == null) {
                status = "Sign in with Microsoft first"
                return
            }
        } else if (!Regex("[A-Za-z0-9_]{3,16}").matches(name)) {
            status = "Username must be 3-16 letters, numbers or _"
            return
        }
        val ramMb = Settings.load().ramMb
        Settings(name, ramMb, Settings.load().account, inst.id,
            dark, colorTheme.name).save()

        busy = true
        log.clear()
        scope.launch {
            try {
                val account = if (microsoft) {
                    status = "Refreshing Microsoft login…"
                    val stored = AccountStore.load() ?: throw AuthException("Sign in with Microsoft first")
                    withContext(Dispatchers.IO) { MicrosoftAuth.accountFromStored(stored) }
                } else {
                    Account.offline(name)
                }
                val installed = GameInstaller.install(
                    entry,
                    inst.loader,
                    inst.loaderVersion,
                    inst.dir,
                    status = { status = it },
                    progress = { d, t -> progress = if (t > 0) d.toFloat() / t else null },
                    optifineVersion = inst.optifineVersion,
                )
                progress = null
                status = "Checking Java…"
                val java = withContext(Dispatchers.IO) {
                    JavaRuntime.ensure(installed.javaMajor) { status = it }
                }
                status = "Launching…"
                val cmd = GameLauncher.buildCommand(installed, java, account, ramMb)
                val proc = withContext(Dispatchers.IO) {
                    GameLauncher.start(cmd, installed.gameDir) { line ->
                        scope.launch(Dispatchers.Main) { addLog(line) }
                    }
                }
                status = "Minecraft is running"
                val code = withContext(Dispatchers.IO) { proc.waitFor() }
                status = "Game closed (exit code $code)"
                // Create crash report if game crashed
                if (code != 0) {
                    val logFile = File(inst.dir, "latest.log").takeIf { it.isFile } ?: File(inst.dir, "logs/latest.log").takeIf { it.isFile }
                    CrashManager.createCrashReport(inst, code, logFile?.absolutePath)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = "Error: ${e.message}"
                addLog(e.stackTraceToString())
            } finally {
                busy = false
                progress = null
            }
        }
    }

    companion object {
        fun openBrowser(url: String) {
            try {
                if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().browse(java.net.URI(url))
            } catch (_: Exception) {
            }
        }

        fun openFolder(dir: File) {
            dir.mkdirs()
            try {
                if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(dir)
            } catch (_: Exception) {
            }
        }

        fun pickMrpack(): File? {
            val dlg = java.awt.FileDialog(null as java.awt.Frame?, "Choose a Modrinth modpack (.mrpack)", java.awt.FileDialog.LOAD)
            dlg.setFilenameFilter { _, name -> name.endsWith(".mrpack", ignoreCase = true) }
            dlg.isVisible = true // blocks until the dialog is closed
            val file = dlg.file ?: return null
            return File(dlg.directory, file)
        }
    }
}
