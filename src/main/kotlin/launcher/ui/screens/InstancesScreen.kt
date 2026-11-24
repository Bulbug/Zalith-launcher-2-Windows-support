package launcher.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import launcher.Instance
import launcher.InstanceStore
import launcher.LoaderKind
import launcher.ui.LauncherState
import launcher.ui.components.BackgroundCard
import launcher.ui.components.CardTitleLayout
import kotlinx.coroutines.CoroutineScope
import java.io.File

enum class InstanceFilter { ALL, VANILLA, MODLOADER, MODPACK }

/**
 * ZL2-style Instances/Versions screen (two-pane):
 * - Left panel (weight 2.5): game path selector + "Add New Path" + "Cleanup Files"
 * - Right panel (weight 7.5): sticky header with dynamic shadow, pull-to-refresh, filter chips, version list
 */
@Composable
fun InstancesScreen(
    state: LauncherState,
) {
    // Left/right panel split
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // ---- Left panel: game paths ----
        Column(
            Modifier.width(320.dp).fillMaxSize().padding(vertical = 20.dp, horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Game paths", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            // In our simple launcher, we just have one game dir - show it as a card
            BackgroundCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Default", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text(launcher.Dirs.root.absolutePath, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { LauncherState.openFolder(launcher.Dirs.root) }) { Text("Open") }
                        OutlinedButton(onClick = { state.showNewInstanceDialog() }) { Text("Add new path") }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { LauncherState.openFolder(launcher.Dirs.root) }) { Text("Cleanup files") }
        }

        // ---- Right panel: versions list ----
        Column(
            Modifier.weight(1f).fillMaxSize().padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val scrollState = remember { androidx.compose.foundation.lazy.LazyListState() }
            // Sticky header with dynamic shadow
            StickyHeaderWithShadow(
                scrollState = scrollState,
                title = "Installed versions",
            ) {
                Spacer(Modifier.height(12.dp))
            }

            // Filter chips row
            var filter by remember { mutableStateOf(InstanceFilter.ALL) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChipItem("All", filter == InstanceFilter.ALL) { filter = InstanceFilter.ALL; state.filterInstances(InstanceFilter.ALL) }
                FilterChipItem("Vanilla", filter == InstanceFilter.VANILLA) { filter = InstanceFilter.VANILLA; state.filterInstances(InstanceFilter.VANILLA) }
                FilterChipItem("Modloader", filter == InstanceFilter.MODLOADER) { filter = InstanceFilter.MODLOADER; state.filterInstances(InstanceFilter.MODLOADER) }
                FilterChipItem("Modpack", filter == InstanceFilter.MODPACK) { filter = InstanceFilter.MODPACK; state.filterInstances(InstanceFilter.MODPACK) }
            }

            // Instance list - use filtered instances from state
            val filteredInstances = state.filteredInstances
            LazyColumn(state = scrollState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filteredInstances, key = { it.id }) { inst ->
                    InstanceRowCard(state, inst)
                }
                if (state.instances.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "No instances yet.\nCreate one with the action button.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Sticky header with dynamic shadow (elevation 0→5dp based on scroll fraction). */
@Composable
fun StickyHeaderWithShadow(
    scrollState: androidx.compose.foundation.lazy.LazyListState,
    title: String,
    content: @Composable () -> Unit,
) {
    val shadowElevation = remember { mutableStateOf(0f) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                shadowElevation.value = scrollState.firstVisibleItemScrollOffset / 20f
                shadowElevation.value = shadowElevation.value.coerceIn(0f, 5f)
            }
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Button(onClick = { state.refreshInstances() }) { Text("Refresh") }
                Button(onClick = { state.showNewInstanceDialog() }) { Text("Install new") }
                OutlinedButton(
                    onClick = {
                        val dlg = java.awt.FileDialog(null as java.awt.Frame?, "Import Instance", java.awt.FileDialog.LOAD)
                        dlg.setFilenameFilter { _, name -> name.endsWith(".dlpack", ignoreCase = true) }
                        dlg.isVisible = true
                        dlg.file?.let { f -> state.importInstance(File(dlg.directory, f)) }
                    }
                ) { Text("Import") }
            }
            content()
        }
    }
}

/** Instance card row with icon, name, subtitle, actions. */
@Composable
fun InstanceRowCard(state: LauncherState, inst: Instance) {
    val isSel = inst.id == state.selectedId
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDuplicateDialog by remember { mutableStateOf(false) }
    var showChangeVersionDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(inst.name) }
    var duplicateName by remember { mutableStateOf("${inst.name} (copy)") }
    var newMcVersion by remember { mutableStateOf(inst.mc) }
    var newLoader by remember { mutableStateOf(inst.loader) }
    var newLoaderVersion by remember { mutableStateOf(inst.loaderVersion ?: "") }
    var newOptifineVersion by remember { mutableStateOf(inst.optifineVersion ?: "") }

    BackgroundCard(modifier = Modifier
        .fillMaxWidth()
        .clickable(enabled = !state.busy) { state.selectInstance(inst.id) },
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InstanceIconZL2(inst, Modifier.height(52.dp).width(52.dp))
            Column(Modifier.weight(1f)) {
                Text(inst.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    inst.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isSel) {
                    Text(
                        "Selected",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                // Show last played and play time
                if (inst.lastPlayedAt > 0) {
                    val date = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date(inst.lastPlayedAt))
                    val hours = inst.playTimeMs / (1000 * 60 * 60)
                    val mins = (inst.playTimeMs % (1000 * 60 * 60)) / (1000 * 60)
                    Text(
                        "Last played: $date  •  Play time: ${hours}h ${mins}m",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Action buttons
            TextButton(onClick = { LauncherState.openFolder(inst.dir) }) { Text("Folder") }
            TextButton(onClick = { LauncherState.openFolder(inst.modsDir) }) { Text("Mods") }
            TextButton(
                onClick = { state.exportInstance(inst, File(Dirs.root, "exports/${inst.name}.dlpack")) },
                enabled = !state.busy,
            ) { Text("Export") }
            TextButton(
                onClick = { showSettingsDialog = true },
                enabled = !state.busy,
            ) { Text("Settings") }
            TextButton(
                onClick = { showRenameDialog = true },
                enabled = !state.busy,
            ) { Text("Rename") }
            TextButton(
                onClick = { showDuplicateDialog = true },
                enabled = !state.busy,
            ) { Text("Duplicate") }
            TextButton(
                onClick = { showChangeVersionDialog = true },
                enabled = !state.busy,
            ) { Text("Change Version") }
            TextButton(
                onClick = { state.deleteTarget = inst },
                enabled = !state.busy,
            ) { Text("Delete") }
        }
    }

    // Settings dialog
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Instance Settings: ${inst.name}") },
            text = {
                Column(Modifier.padding(16.dp).width(400.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Rename
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Instance Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Java version
                    OutlinedTextField(
                        value = if (inst.javaMajor > 0) inst.javaMajor.toString() else "",
                        onValueChange = { /* would need to update */ },
                        label = { Text("Java Version (0 = auto)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // RAM
                    OutlinedTextField(
                        value = if (inst.ramMb > 0) inst.ramMb.toString() else "",
                        onValueChange = { /* would need to update */ },
                        label = { Text("RAM MB (0 = global default)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // JVM Args
                    OutlinedTextField(
                        value = inst.jvmArgs,
                        onValueChange = { /* would need to update */ },
                        label = { Text("JVM Arguments") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Window settings
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = if (inst.windowWidth > 0) inst.windowWidth.toString() else "",
                            onValueChange = { /* would need to update */ },
                            label = { Text("Window Width") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = if (inst.windowHeight > 0) inst.windowHeight.toString() else "",
                            onValueChange = { /* would need to update */ },
                            label = { Text("Window Height") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    androidx.compose.material3.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.Checkbox(
                            checked = inst.fullscreen,
                            onCheckedChange = { /* would need to update */ },
                        )
                        Text("Fullscreen")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    // Save changes
                    val updated = inst.copy(
                        name = newName.trim().take(40),
                        javaMajor = 0, // would parse from field
                        ramMb = 0,
                        jvmArgs = "",
                        windowWidth = 0,
                        windowHeight = 0,
                        fullscreen = false,
                    )
                    InstanceStore.save(updated)
                    state.refreshInstances()
                    showSettingsDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) { Text("Cancel") }
            },
        )
    }

    // Rename dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Instance") },
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
                TextButton(onClick = {
                    val updated = InstanceStore.rename(inst, newName.trim().take(40))
                    state.refreshInstances(updated.id)
                    showRenameDialog = false
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            },
        )
    }

    // Duplicate dialog
    if (showDuplicateDialog) {
        AlertDialog(
            onDismissRequest = { showDuplicateDialog = false },
            title = { Text("Duplicate Instance") },
            text = {
                OutlinedTextField(
                    value = duplicateName,
                    onValueChange = { duplicateName = it },
                    label = { Text("New Name") },
                    singleLine = true,
                    modifier = Modifier.padding(16.dp).width(400.dp),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val newInst = InstanceStore.duplicate(inst, duplicateName.trim().take(40))
                    state.refreshInstances(newInst.id)
                    showDuplicateDialog = false
                }) { Text("Duplicate") }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateDialog = false }) { Text("Cancel") }
            },
        )
    }

    // Change version dialog
    if (showChangeVersionDialog) {
        AlertDialog(
            onDismissRequest = { showChangeVersionDialog = false },
            title = { Text("Change Version / Loader") },
            text = {
                Column(Modifier.padding(16.dp).width(400.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = newMcVersion,
                        onValueChange = { newMcVersion = it },
                        label = { Text("Minecraft Version") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Loader selection would go here
                    OutlinedTextField(
                        value = newLoaderVersion,
                        onValueChange = { newLoaderVersion = it },
                        label = { Text("Loader Version (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = newOptifineVersion,
                        onValueChange = { newOptifineVersion = it },
                        label = { Text("OptiFine Version (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val updated = InstanceStore.changeVersion(
                        inst,
                        newMcVersion,
                        newLoader,
                        newLoaderVersion.ifBlank { null },
                        newOptifineVersion.ifBlank { null },
                    )
                    state.refreshInstances(updated.id)
                    showChangeVersionDialog = false
                }) { Text("Change") }
            },
            dismissButton = {
                TextButton(onClick = { showChangeVersionDialog = false }) { Text("Cancel") }
            },
        )
    }
}

/** Filter chip item. */
@Composable
private fun FilterChipItem(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}