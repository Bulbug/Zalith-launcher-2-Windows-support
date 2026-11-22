package launcher.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import launcher.Instance
import launcher.ModHit
import launcher.Modrinth
import launcher.ui.DownloadTab
import launcher.ui.LauncherState
import launcher.ui.WorkingIndicator
import java.io.File

/**
 * Download screen, ZL2-style: an inner rail with categories (Mods / Modpacks)
 * beside the category's content.
 */
@Composable
fun DownloadScreen(state: LauncherState) {
    var tab by remember { mutableStateOf(DownloadTab.MODS) }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(
            Modifier.width(120.dp).fillMaxSize().padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RailItem2("Mods", tab == DownloadTab.MODS) { tab = DownloadTab.MODS }
            RailItem2("Modpacks", tab == DownloadTab.MODPACKS) { tab = DownloadTab.MODPACKS }
        }
        when (tab) {
            DownloadTab.MODS -> ModsPane(state)
            DownloadTab.MODPACKS -> ModpacksPane(state)
        }
    }
}

// ---------------------------------------------------------------- mods

@Composable
private fun ModsPane(state: LauncherState) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ModHit>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var installed by remember { mutableStateOf(emptyList<File>()) }

    val inst = state.selected
    // refresh the installed list whenever the pane is (re)composed with a different instance
    remember(inst?.id) {
        installed = if (inst != null) Modrinth.listInstalled(inst.modsDir) else emptyList()
        inst
    }

    fun search() {
        if (inst == null) {
            status = "Select an instance first"
            return
        }
        scope.launch {
            busy = true
            status = "Searching Modrinth…"
            try {
                val r = withContext(Dispatchers.IO) { Modrinth.search(query.trim(), inst.mc, inst.loader.modrinth) }
                results = r
                status = if (r.isEmpty()) "No mods found for ${inst.mc} on ${inst.loader.label}" else "${r.size} mods (data from Modrinth)"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = "Search failed: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun install(hit: ModHit) {
        if (inst == null) return
        scope.launch {
            busy = true
            status = "Installing ${hit.title}…"
            try {
                val added = withContext(Dispatchers.IO) {
                    Modrinth.install(hit.projectId, inst.mc, inst.loader.modrinth, inst.modsDir) { status = it }
                }
                status = "Installed: ${added.joinToString()}"
                installed = Modrinth.listInstalled(inst.modsDir)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = "Install failed: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun remove(file: File) {
        if (inst == null) return
        file.delete()
        installed = Modrinth.listInstalled(inst.modsDir)
        status = "Removed ${file.name}"
    }

    Column(
        Modifier.fillMaxSize().padding(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (inst == null) {
            Note("Select an instance in Instances to browse mods for it.")
            return@Column
        }
        Text("Mods for ${inst.name}", style = MaterialTheme.typography.titleLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search Modrinth") },
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { search() }, enabled = !busy) { Text("Search") }
        }
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (busy) WorkingIndicator(status, null)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results) { hit ->
                ResultCard(
                    title = hit.title,
                    subtitle = "${hit.author} - ${hit.description}",
                    trailing = "${formatDownloads(hit.downloads)} downloads",
                    actionLabel = "Install",
                    actionEnabled = !busy,
                    onAction = { install(hit) },
                )
            }
            if (installed.isNotEmpty()) {
                item {
                    Text("Installed in this instance", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                items(installed) { f ->
                    ResultCard(
                        title = f.name,
                        subtitle = "",
                        trailing = "",
                        actionLabel = "Remove",
                        actionEnabled = !busy,
                        onAction = { remove(f) },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- modpacks

@Composable
private fun ModpacksPane(state: LauncherState) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ModHit>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf<Float?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun search() {
        scope.launch {
            busy = true
            status = "Searching Modrinth…"
            try {
                val r = withContext(Dispatchers.IO) { Modrinth.searchPacks(query.trim()) }
                results = r
                status = if (r.isEmpty()) "No modpacks found" else "${r.size} modpacks (data from Modrinth)"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = "Search failed: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun install(hit: ModHit) {
        scope.launch {
            busy = true
            progress = null
            status = "Downloading ${hit.title}…"
            try {
                val pack = withContext(Dispatchers.IO) {
                    val dir = File(launcher.Dirs.root, "modpacks").apply { mkdirs() }
                    Modrinth.downloadPack(hit.projectId, dir)
                }
                val inst = withContext(Dispatchers.IO) {
                    launcher.ModpackInstaller.install(
                        pack,
                        status = { status = it },
                        progress = { d, t -> progress = if (t > 0) d.toFloat() / t else null },
                    )
                }
                state.refreshInstances(inst.id)
                status = "Modpack \"${inst.name}\" installed. Press PLAY on Home to download Minecraft and start it."
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

    Column(
        Modifier.fillMaxSize().padding(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Modpacks", style = MaterialTheme.typography.titleLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search Modrinth modpacks") },
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { search() }, enabled = !busy) { Text("Search") }
        }
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (busy) WorkingIndicator(status, progress)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results) { hit ->
                ResultCard(
                    title = hit.title,
                    subtitle = "${hit.author} - ${hit.description}",
                    trailing = "${formatDownloads(hit.downloads)} downloads",
                    actionLabel = "Install",
                    actionEnabled = !busy,
                    onAction = { install(hit) },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- shared bits

@Composable
private fun ResultCard(
    title: String,
    subtitle: String,
    trailing: String,
    actionLabel: String,
    actionEnabled: Boolean,
    onAction: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (trailing.isNotBlank()) {
                    Text(trailing, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(onClick = onAction, enabled = actionEnabled) { Text(actionLabel) }
        }
    }
}

@Composable
private fun RailItem2(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun Note(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatDownloads(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000f)
    n >= 1_000 -> "%.1fk".format(n / 1_000f)
    else -> n.toString()
}
