package launcher.ui.screens

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
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import launcher.Instance
import launcher.ModHit
import launcher.Modrinth
import launcher.ui.DownloadTab
import launcher.ui.LauncherState
import launcher.ui.RailScreen
import launcher.ui.WorkingIndicator
import launcher.ui.components.BackgroundCard
import java.io.File

/**
 * ZL2-style Download screen:
 * - Left TabMenu rail with categories (Game, Modpack, Mod, Resource Pack, Saves, Shaders, Search by ID, Favorites)
 * - Right content with nested nav (Mods / Modpacks panes)
 */
@Composable
fun DownloadScreen(
    state: LauncherState,
) {
    var tab by remember { mutableStateOf(DownloadTab.MODS) }
    Row(Modifier.fillMaxSize()) {
        // ---- Left TabMenu rail ----
        Column(
            Modifier
                .width(180.dp)
                .fillMaxSize()
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Categories with section dividers
            TabMenuCategory("Game", tab == DownloadTab.GAME) { tab = DownloadTab.GAME }
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth(0.4f).padding(start = 8.dp),
            )
            Spacer(Modifier.height(12.dp))
            TabMenuCategory("Modpack", tab == DownloadTab.MODPACKS) { tab = DownloadTab.MODPACKS }
            TabMenuCategory("Mod", tab == DownloadTab.MODS) { tab = DownloadTab.MODS }
            TabMenuCategory("Resource Pack", tab == DownloadTab.RESOURCE_PACKS) { tab = DownloadTab.RESOURCE_PACKS }
            TabMenuCategory("Saves", tab == DownloadTab.SAVES) { tab = DownloadTab.SAVES }
            TabMenuCategory("Shaders", tab == DownloadTab.SHADERS) { tab = DownloadTab.SHADERS }
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth(0.4f).padding(start = 8.dp),
            )
            Spacer(Modifier.height(12.dp))
            TabMenuCategory("Search by ID", tab == DownloadTab.SEARCH_ID) { tab = DownloadTab.SEARCH_ID }
            TabMenuCategory("Favorites", tab == DownloadTab.FAVORITES) { tab = DownloadTab.FAVORITES }
        }

        // ---- Right content ----
        when (tab) {
            DownloadTab.MODS -> ModsPane(state)
            DownloadTab.MODPACKS -> ModpacksPane(state)
            DownloadTab.GAME -> Box(Modifier.fillMaxSize()) { Text("Game tab - TODO", style = MaterialTheme.typography.titleMedium) }
            DownloadTab.RESOURCE_PACKS -> ResourcePacksPane(state)
            DownloadTab.SAVES -> SavesPane(state)
            DownloadTab.SHADERS -> ShadersPane(state)
            DownloadTab.SEARCH_ID -> SearchByIdPane(state)
            DownloadTab.FAVORITES -> FavoritesPane(state)
        }
    }
}

/** TabMenu category item: selected = secondaryContainer + label visible, unselected = surface. */
@Composable
private fun TabMenuCategory(
    label: String,
    selected: Boolean = false,
    onClick: () -> Unit = {},
) {
    androidx.compose.material3.Surface(
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

// ---------------------------------------------------------------- Mods pane

@Composable
private fun ModsPane(state: LauncherState) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ModHit>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var installed by remember { mutableStateOf(emptyList<File>()) }

    val inst = state.selected
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
                state.showToast("Mod \"${hit.title}\" installed", LauncherState.ToastType.SUCCESS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = "Install failed: ${e.message}"
                state.showToast("Failed to install ${hit.title}: ${e.message}", LauncherState.ToastType.ERROR)
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
        state.showToast("Mod removed", LauncherState.ToastType.INFO)
    }

    Box(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (inst == null) {
                Text(
                    "Select an instance in Instances to browse mods for it.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxSize(),
                )
                return@Box
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
                    ModResultCard(
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
                        ModResultCard(
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
}

// ---------------------------------------------------------------- Modpacks pane

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
                state.showToast("Modpack \"${inst.name}\" installed", LauncherState.ToastType.SUCCESS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = "Modpack install failed: ${e.message}"
                state.showToast("Failed to install modpack: ${e.message}", LauncherState.ToastType.ERROR)
            } finally {
                busy = false
                progress = null
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
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
                    ModResultCard(
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
}

// ---------------------------------------------------------------- shared

@Composable
private fun ModResultCard(
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

private fun formatDownloads(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000f)
    n >= 1_000 -> "%.1fk".format(n / 1_000f)
    else -> n.toString()
}

// ---------------------------------------------------------------- Resource Packs pane

@Composable
private fun ResourcePacksPane(state: LauncherState) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ModHit>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var installed by remember { mutableStateOf(emptyList<File>()) }

    val inst = state.selected
    remember(inst?.id) {
        installed = if (inst != null) Modrinth.listInstalled(inst.resourcePacksDir) else emptyList()
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
                val r = withContext(Dispatchers.IO) { Modrinth.searchResourcePacks(query.trim(), inst.mc) }
                results = r
                status = if (r.isEmpty()) "No resource packs found for ${inst.mc}" else "${r.size} resource packs (data from Modrinth)"
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
                    Modrinth.install(hit.projectId, inst.mc, emptyList(), inst.resourcePacksDir) { status = it }
                }
                status = "Installed: ${added.joinToString()}"
                installed = Modrinth.listInstalled(inst.resourcePacksDir)
                state.showToast("Resource pack \"${hit.title}\" installed", LauncherState.ToastType.SUCCESS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = "Install failed: ${e.message}"
                state.showToast("Failed to install resource pack: ${e.message}", LauncherState.ToastType.ERROR)
            } finally {
                busy = false
            }
        }
    }

    fun remove(file: File) {
        if (inst == null) return
        file.delete()
        installed = Modrinth.listInstalled(inst.resourcePacksDir)
        status = "Removed ${file.name}"
        state.showToast("Resource pack removed", LauncherState.ToastType.INFO)
    }

    Box(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (inst == null) {
                Text(
                    "Select an instance in Instances to browse resource packs for it.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxSize(),
                )
                return@Box
            }
            Text("Resource Packs for ${inst.name}", style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search Modrinth resource packs") },
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
                    ModResultCard(
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
                        ModResultCard(
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
}

// ---------------------------------------------------------------- Saves pane

@Composable
private fun SavesPane(state: LauncherState) {
    val scope = rememberCoroutineScope()
    var saves by remember { mutableStateOf(emptyList<File>()) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    val inst = state.selected

    fun loadSaves() {
        if (inst == null) {
            status = "Select an instance first"
            return
        }
        busy = true
        status = "Loading saves…"
        scope.launch {
            try {
                val dir = inst.savesDir
                dir.mkdirs()
                val list = dir.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name.lowercase() } ?: emptyList()
                saves = list
                status = if (list.isEmpty()) "No saves found" else "${list.size} world(s) found"
            } catch (e: Exception) {
                status = "Failed to load: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun openSave(save: File) {
        LauncherState.openFolder(save)
    }

    fun deleteSave(save: File) {
        save.deleteRecursively()
        loadSaves()
        state.showToast("World save deleted", LauncherState.ToastType.INFO)
    }

    // Load on first show or when instance changes
    remember(inst?.id) {
        loadSaves()
        inst
    }

    Box(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (inst == null) {
                Text(
                    "Select an instance in Instances to manage saves for it.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxSize(),
                )
                return@Box
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Saves for ${inst.name}", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                Button(onClick = { loadSaves() }, enabled = !busy) { Text("Refresh") }
                Button(onClick = { LauncherState.openFolder(inst.savesDir) }, enabled = !busy) { Text("Open Folder") }
            }
            if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (busy) WorkingIndicator(status, null)

            if (saves.isEmpty()) {
                Text(
                    "No world saves yet. Create a world in-game to see it here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(saves) { save ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(save.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                }
                                Button(onClick = { openSave(save) }) { Text("Open") }
                                Button(onClick = { deleteSave(save) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Text("Delete") }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Shaders pane

@Composable
private fun ShadersPane(state: LauncherState) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ModHit>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var installed by remember { mutableStateOf(emptyList<File>()) }

    val inst = state.selected
    remember(inst?.id) {
        installed = if (inst != null) Modrinth.listInstalled(inst.shadersDir) else emptyList()
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
                val r = withContext(Dispatchers.IO) { Modrinth.searchShaders(query.trim(), inst.mc) }
                results = r
                status = if (r.isEmpty()) "No shader packs found for ${inst.mc}" else "${r.size} shader packs (data from Modrinth)"
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
                    Modrinth.install(hit.projectId, inst.mc, emptyList(), inst.shadersDir) { status = it }
                }
                status = "Installed: ${added.joinToString()}"
                installed = Modrinth.listInstalled(inst.shadersDir)
                state.showToast("Shader pack \"${hit.title}\" installed", LauncherState.ToastType.SUCCESS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = "Install failed: ${e.message}"
                state.showToast("Failed to install shader pack: ${e.message}", LauncherState.ToastType.ERROR)
            } finally {
                busy = false
            }
        }
    }

    fun remove(file: File) {
        if (inst == null) return
        file.delete()
        installed = Modrinth.listInstalled(inst.shadersDir)
        status = "Removed ${file.name}"
        state.showToast("Shader pack removed", LauncherState.ToastType.INFO)
    }

    Box(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (inst == null) {
                Text(
                    "Select an instance in Instances to browse shader packs for it.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxSize(),
                )
                return@Box
            }
            Text("Shader Packs for ${inst.name}", style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search Modrinth shader packs") },
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
                    ModResultCard(
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
                        ModResultCard(
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
}

// ---------------------------------------------------------------- Search by ID pane

@Composable
private fun SearchByIdPane(state: LauncherState) {
    val scope = rememberCoroutineScope()
    var projectId by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<ModHit?>(null) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun search() {
        if (projectId.trim().isBlank()) {
            status = "Enter a Modrinth project ID"
            return
        }
        scope.launch {
            busy = true
            status = "Looking up project…"
            try {
                val r = withContext(Dispatchers.IO) { Modrinth.getProject(projectId.trim()) }
                result = r
                status = if (r != null) "Found: ${r.title}" else "Project not found"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = "Lookup failed: ${e.message}"
                result = null
            } finally {
                busy = false
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Search by Project ID", style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = projectId,
                    onValueChange = { projectId = it },
                    label = { Text("Modrinth Project ID (e.g. 'sodium')") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { search() }, enabled = !busy) { Text("Search") }
            }
            if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (busy) WorkingIndicator(status, null)

            result?.let { hit ->
                ModResultCard(
                    title = hit.title,
                    subtitle = "${hit.author} - ${hit.description}",
                    trailing = "${formatDownloads(hit.downloads)} downloads",
                    actionLabel = "Install (requires instance)",
                    actionEnabled = false,
                    onAction = { },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Favorites pane

@Composable
private fun FavoritesPane(state: LauncherState) {
    val scope = rememberCoroutineScope()
    var favorites by remember { mutableStateOf<List<ModHit>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun loadFavorites() {
        scope.launch {
            busy = true
            status = "Loading favorites…"
            try {
                // For now, just show empty - would need persistent storage
                favorites = emptyList()
                status = "No favorites saved yet"
            } catch (e: Exception) {
                status = "Failed to load: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Favorites", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                Button(onClick = { loadFavorites() }, enabled = !busy) { Text("Refresh") }
            }
            if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (busy) WorkingIndicator(status, null)

            if (favorites.isEmpty()) {
                Text(
                    "No favorites yet. Favorite items from other tabs to see them here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(favorites) { hit ->
                        ModResultCard(
                            title = hit.title,
                            subtitle = "${hit.author} - ${hit.description}",
                            trailing = "${formatDownloads(hit.downloads)} downloads",
                            actionLabel = "View",
                            actionEnabled = !busy,
                            onAction = { /* open details */ },
                        )
                    }
                }
            }
        }
    }
}