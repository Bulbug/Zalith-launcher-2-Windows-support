package launcher

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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var showAll by remember { mutableStateOf(false) }
    var versions by remember { mutableStateOf<List<VersionEntry>>(emptyList()) }
    var selectedId by remember { mutableStateOf(saved.version) }
    var status by remember { mutableStateOf("Loading versions…") }
    var progress by remember { mutableStateOf<Float?>(null) }
    var busy by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val log = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()

    fun addLog(line: String) {
        log.add(line)
        if (log.size > 3000) repeat(500) { log.removeAt(0) }
    }

    LaunchedEffect(Unit) {
        try {
            val list = withContext(Dispatchers.IO) { Manifest.load() }
            versions = list
            if (selectedId.isBlank()) {
                selectedId = list.firstOrNull { it.type == "release" }?.id ?: ""
            }
            status = "Ready"
        } catch (e: Exception) {
            status = "Couldn't load versions: ${e.message}"
        }
    }

    LaunchedEffect(log.size) {
        if (log.isNotEmpty()) listState.scrollToItem(log.size - 1)
    }

    val visible = remember(versions, showAll) {
        if (showAll) versions else versions.filter { it.type == "release" }
    }

    fun play() {
        val entry = versions.firstOrNull { it.id == selectedId } ?: return
        val name = username.trim()
        if (!Regex("[A-Za-z0-9_]{3,16}").matches(name)) {
            status = "Username must be 3-16 letters, numbers or _"
            return
        }
        val ramMb = ram.toIntOrNull()?.coerceIn(512, 32768) ?: 2048
        Settings(name, ramMb, entry.id).save()

        busy = true
        log.clear()
        scope.launch {
            try {
                val inst = GameInstaller.install(
                    entry,
                    status = { status = it },
                    progress = { d, t -> progress = if (t > 0) d.toFloat() / t else null },
                )
                progress = null
                status = "Checking Java…"
                val java = withContext(Dispatchers.IO) {
                    JavaRuntime.ensure(inst.javaMajor) { status = it }
                }
                status = "Launching…"
                val cmd = GameLauncher.buildCommand(inst, java, Account.offline(name), ramMb)
                val proc = withContext(Dispatchers.IO) {
                    GameLauncher.start(cmd) { line ->
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

    Row(Modifier.fillMaxSize().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        // ---- left: controls ----
        Column(Modifier.width(320.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(AppInfo.NAME, style = MaterialTheme.typography.headlineMedium)
            Text(
                "${AppInfo.NOTICE} - v${AppInfo.VERSION}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username (offline)") },
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )

            Column {
                OutlinedButton(
                    onClick = { menuOpen = true },
                    enabled = !busy && visible.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (selectedId.isBlank()) "Choose version" else "Version: $selectedId")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    visible.forEach { v ->
                        DropdownMenuItem(
                            text = { Text(v.id) },
                            onClick = {
                                selectedId = v.id
                                menuOpen = false
                            },
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = showAll, onCheckedChange = { showAll = it }, enabled = !busy)
                Text("Show snapshots and old versions")
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
                enabled = !busy && selectedId.isNotBlank(),
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
