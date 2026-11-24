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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import launcher.WorldManager
import launcher.Instance
import launcher.ui.LauncherState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filed.CloudDownload
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun WorldManagerScreen(state: LauncherState) {
    val scope = rememberCoroutineScope()
    val inst = state.selected

    var worlds by remember { mutableStateOf<List<WorldManager.WorldInfo>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renamingWorld by remember { mutableStateOf<WorldManager.WorldInfo?>(null) }
    var newName by remember { mutableStateOf("") }

    fun loadWorlds() {
        if (inst == null) return
        busy = true
        status = "Loading worlds…"
        scope.launch(Dispatchers.IO) {
            val w = WorldManager.scanWorlds(inst)
            scope.launch(Dispatchers.Main) {
                worlds = w
                busy = false
                status = if (w.isEmpty()) "No worlds found" else "${w.size} world(s)"
            }
        }
    }

    // Load on first show or when instance changes
    LaunchedEffect(inst?.id) {
        loadWorlds()
    }

    fun renameWorld(world: WorldManager.WorldInfo) {
        renamingWorld = world
        newName = world.name
        showRenameDialog = true
    }

    fun saveRename() {
        renamingWorld?.let { world ->
            if (newName.trim().isNotBlank()) {
                scope.launch(Dispatchers.IO) {
                    WorldManager.renameWorld(world, newName.trim())
                    scope.launch(Dispatchers.Main) { loadWorlds() }
                }
            }
            showRenameDialog = false
            renamingWorld = null
        }
    }

    fun duplicateWorld(world: WorldManager.WorldInfo) {
        scope.launch(Dispatchers.IO) {
            WorldManager.duplicateWorld(world)
            scope.launch(Dispatchers.Main) { loadWorlds() }
        }
    }

    fun backupWorld(world: WorldManager.WorldInfo) {
        scope.launch(Dispatchers.IO) {
            val backupDir = File(launcher.Dirs.root, "backups/worlds").apply { mkdirs() }
            WorldManager.backupWorld(world, backupDir)
            scope.launch(Dispatchers.Main) {
                status = "World backed up to ${backupDir.name}"
                state.showToast("World backup created", LauncherState.ToastType.SUCCESS)
            }
        }
    }

    fun deleteWorld(world: WorldManager.WorldInfo) {
        scope.launch(Dispatchers.IO) {
            WorldManager.deleteWorld(world)
            scope.launch(Dispatchers.Main) { loadWorlds() }
        }
    }

    fun openWorldFolder(world: WorldManager.WorldInfo) {
        LauncherState.openFolder(world.path)
    }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (inst == null) {
            Text(
                "Select an instance in Instances to manage worlds for it.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxSize(),
            )
            return@Column
        }

        // Header
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("World Manager: ${inst.name}", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            Text("${worlds.size} world(s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { loadWorlds() }, enabled = !busy) { Text("Refresh") }
        }

        if (status.isNotBlank()) {
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (busy) {
            androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (worlds.isEmpty()) {
            Text(
                "No worlds yet. Create a world in-game to see it here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(worlds) { world ->
                    WorldCard(
                        world = world,
                        onOpenFolder = { openWorldFolder(world) },
                        onRename = { renameWorld(world) },
                        onDuplicate = { duplicateWorld(world) },
                        onBackup = { backupWorld(world) },
                        onDelete = { deleteWorld(world) },
                    )
                }
            }
        }
    }

    // Rename dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false; renamingWorld = null },
            title = { Text("Rename World") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New Name") },
                    singleLine = true,
                    modifier = Modifier.padding(16.dp).width(400.dp),
                )
            },
            confirmButton = {
                Button(onClick = { saveRename() }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false; renamingWorld = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun WorldCard(
    world: WorldManager.WorldInfo,
    onOpenFolder: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onBackup: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(world.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (world.isHardcore) {
                            Text("HARDCORE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        world.version?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        world.gameMode?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                        val date = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(world.lastPlayed))
                        Text("Last played: $date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatSize(world.sizeBytes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Actions
                OutlinedButton(onClick = onOpenFolder) {
                    Icon(Icons.Filled.Folder, contentDescription = "Open folder")
                    Text("Open")
                }
                IconButton(onClick = onRename) {
                    Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = "Rename")
                }
                IconButton(onClick = onDuplicate) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate")
                }
                IconButton(onClick = onBackup) {
                    Icon(Icons.Filed.CloudDownload, contentDescription = "Backup")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.1f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }
}