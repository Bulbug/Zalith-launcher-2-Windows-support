package launcher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private fun openBrowser(url: String) {
    try {
        if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().browse(java.net.URI(url))
    } catch (_: Exception) {
    }
}

private fun openFolder(dir: File) {
    dir.mkdirs()
    try {
        if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(dir)
    } catch (_: Exception) {
    }
}

private fun pickMrpack(): File? {
    val dlg = java.awt.FileDialog(null as java.awt.Frame?, "Choose a Modrinth modpack (.mrpack)", java.awt.FileDialog.LOAD)
    dlg.setFilenameFilter { _, name -> name.endsWith(".mrpack", ignoreCase = true) }
    dlg.isVisible = true // blocks until the dialog is closed
    val file = dlg.file ?: return null
    return File(dlg.directory, file)
}

@Composable
fun App() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            LauncherScreen()
        }
    }
}

@Composable
fun LauncherScreen() {
    val scope = rememberCoroutineScope()
    val saved = remember { Settings.load() }

    var username by remember { mutableStateOf(saved.username) }
    var ram by remember { mutableStateOf(saved.ramMb.toString()) }
    var accountMode by remember { mutableStateOf(saved.account) }
    var msAccount by remember { mutableStateOf(AccountStore.load()) }
    var deviceCode by remember { mutableStateOf<DeviceCode?>(null) }
    var signInJob by remember { mutableStateOf<Job?>(null) }

    var versions by remember { mutableStateOf<List<VersionEntry>>(emptyList()) }
    var instances by remember { mutableStateOf(InstanceStore.list()) }
    var selectedId by remember { mutableStateOf(saved.instance) }
    var showNew by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Instance?>(null) }
    var screen by remember { mutableStateOf("main") }

    var status by remember { mutableStateOf("Loading versions…") }
    var progress by remember { mutableStateOf<Float?>(null) }
    var busy by remember { mutableStateOf(false) }
    val log = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()

    val selected = instances.firstOrNull { it.id == selectedId }

    fun addLog(line: String) {
        log.add(line)
        if (log.size > 3000) repeat(500) { log.removeAt(0) }
    }

    fun refreshInstances(select: String? = null) {
        instances = InstanceStore.list()
        if (select != null) selectedId = select
        else if (instances.none { it.id == selectedId }) selectedId = instances.firstOrNull()?.id ?: ""
    }

    LaunchedEffect(Unit) {
        try {
            versions = withContext(Dispatchers.IO) { Manifest.load() }
            status = "Ready"
        } catch (e: Exception) {
            status = "Couldn't load versions: ${e.message}"
        }
    }

    LaunchedEffect(log.size) {
        if (log.isNotEmpty()) listState.scrollToItem(log.size - 1)
    }

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

    fun importModpack() {
        val file = pickMrpack() ?: return
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
                refreshInstances(inst.id)
                status = "Modpack \"${inst.name}\" installed. Press PLAY to download Minecraft and start it."
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
        val name = username.trim()
        val microsoft = accountMode == "microsoft"
        if (microsoft) {
            if (AccountStore.load() == null) {
                status = "Sign in with Microsoft first"
                return
            }
        } else if (!Regex("[A-Za-z0-9_]{3,16}").matches(name)) {
            status = "Username must be 3-16 letters, numbers or _"
            return
        }
        val ramMb = ram.toIntOrNull()?.coerceIn(512, 32768) ?: 2048
        Settings(name, ramMb, accountMode, inst.id).save()

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

    // ---- dialogs ----
    val dc = deviceCode
    if (dc != null) {
        AlertDialog(
            onDismissRequest = { cancelSignIn() },
            title = { Text("Sign in with Microsoft") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1. Open ${dc.verificationUri} (we tried to open it for you).")
                    Text("2. Enter this code:")
                    Text(dc.userCode, style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace)
                    Text("This window closes by itself when you're signed in.")
                }
            },
            confirmButton = { Button(onClick = { openBrowser(dc.verificationUri) }) { Text("Open page") } },
            dismissButton = { TextButton(onClick = { cancelSignIn() }) { Text("Cancel") } },
        )
    }

    if (showNew) {
        NewInstanceDialog(
            versions = versions,
            onCreate = { n, mc, l ->
                val inst = InstanceStore.create(n, mc, l, null)
                refreshInstances(inst.id)
                showNew = false
            },
            onDismiss = { showNew = false },
        )
    }

    val del = deleteTarget
    if (del != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete \"${del.name}\"?") },
            text = { Text("This permanently deletes the instance folder, including its worlds, mods and settings.") },
            confirmButton = {
                Button(onClick = {
                    InstanceStore.delete(del)
                    deleteTarget = null
                    refreshInstances()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }

    // ---- other screens ----
    if (screen == "mods" && selected != null) {
        ModsScreen(
            mc = selected.mc,
            loader = selected.loader,
            modsDir = selected.modsDir,
            onBack = { screen = "main" },
        )
        return
    }
    if (screen == "packs") {
        ModpacksScreen(
            onBack = { screen = "main" },
            onInstalled = { inst ->
                refreshInstances(inst.id)
                screen = "main"
                status = "Modpack \"${inst.name}\" installed. Press PLAY to download Minecraft and start it."
            },
        )
        return
    }

    // ---- main screen ----
    Row(Modifier.fillMaxSize().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        // ---- left: controls ----
        Column(
            Modifier.width(340.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(AppInfo.NAME, style = MaterialTheme.typography.headlineMedium)
            Text(
                "${AppInfo.NOTICE} - v${AppInfo.VERSION}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))

            // account
            Row {
                RadioRow("Offline", accountMode == "offline", !busy) { accountMode = "offline" }
                RadioRow("Microsoft", accountMode == "microsoft", !busy) { accountMode = "microsoft" }
            }
            if (accountMode == "offline") {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                val ms = msAccount
                if (ms != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Signed in: ${ms.name}", Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                AccountStore.clear()
                                msAccount = null
                            },
                            enabled = !busy,
                        ) { Text("Sign out") }
                    }
                } else {
                    Button(onClick = { signIn() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text("Sign in with Microsoft")
                    }
                }
            }

            // instances
            Spacer(Modifier.height(4.dp))
            Text("Instances", style = MaterialTheme.typography.titleMedium)
            if (instances.isEmpty()) {
                Text(
                    "No instances yet. Create one, or import / browse a modpack.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            instances.forEach { inst ->
                val isSel = inst.id == selectedId
                Surface(
                    color = if (isSel) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) { selectedId = inst.id },
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(inst.name, style = MaterialTheme.typography.titleSmall)
                        Text(inst.subtitle, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showNew = true }, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("New")
                }
                OutlinedButton(
                    onClick = { deleteTarget = selected },
                    enabled = !busy && selected != null,
                    modifier = Modifier.weight(1f),
                ) { Text("Delete") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { importModpack() }, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("Import .mrpack")
                }
                OutlinedButton(onClick = { screen = "packs" }, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("Browse packs")
                }
            }
            if (selected != null) {
                OutlinedButton(
                    onClick = { screen = "mods" },
                    enabled = !busy && selected.loader != LoaderKind.VANILLA,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (selected.loader == LoaderKind.VANILLA) "Mods (this instance has no mod loader)"
                        else "Browse and install mods"
                    )
                }
                if (selected.loader.usesInstaller) {
                    Text(
                        "${selected.loader.label} is set up with its official installer. The first launch can take a few minutes.",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            OutlinedTextField(
                value = ram,
                onValueChange = { ram = it.filter { c -> c.isDigit() } },
                label = { Text("RAM (MB)") },
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = { play() },
                enabled = !busy && selected != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(if (busy) "Working…" else "PLAY", fontSize = 18.sp)
            }

            val p = progress
            if (busy) {
                if (p != null) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Text(status, style = MaterialTheme.typography.bodyMedium)

            if (selected != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { openFolder(selected.dir) }) { Text("Instance folder") }
                    OutlinedButton(onClick = { openFolder(selected.modsDir) }) { Text("Mods folder") }
                }
            }
        }

        // ---- right: game log ----
        Column(Modifier.weight(1f)) {
            Text("Game log", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Surface(
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(state = listState, modifier = Modifier.padding(10.dp)) {
                    items(log) { line ->
                        Text(line, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
