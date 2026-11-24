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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import launcher.ModManager
import launcher.Instance
import launcher.ui.LauncherState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text

@Composable
fun ModManagementScreen(state: LauncherState) {
    val scope = rememberCoroutineScope()
    val inst = state.selected

    var modsTab by remember { mutableStateOf(ModsTab.MODS) }
    var mods by remember { mutableStateOf<List<ModManager.ModInfo>>(emptyList()) }
    var duplicates by remember { mutableStateOf<List<List<ModManager.ModInfo>>>(emptyList()) }
    var suspicious by remember { mutableStateOf<List<ModManager.SuspiciousMod>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var expandedMods by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun loadMods() {
        if (inst == null) return
        busy = true
        status = "Scanning mods…"
        scope.launch(Dispatchers.IO) {
            val m = ModManager.scanMods(inst)
            val d = ModManager.detectDuplicates(m)
            val s = ModManager.detectSuspicious(m)
            scope.launch(Dispatchers.Main) {
                mods = m
                duplicates = d
                suspicious = s
                busy = false
                status = if (m.isEmpty()) "No mods found" else "${m.size} mod(s) found"
            }
        }
    }

    fun loadResourcePacks() {
        if (inst == null) return
        busy = true
        status = "Scanning resource packs…"
        scope.launch(Dispatchers.IO) {
            val m = ModManager.scanResourcePacks(inst)
            scope.launch(Dispatchers.Main) {
                mods = m
                busy = false
                status = if (m.isEmpty()) "No resource packs found" else "${m.size} resource pack(s) found"
            }
        }
    }

    fun loadShaderPacks() {
        if (inst == null) return
        busy = true
        status = "Scanning shader packs…"
        scope.launch(Dispatchers.IO) {
            val m = ModManager.scanShaderPacks(inst)
            scope.launch(Dispatchers.Main) {
                mods = m
                busy = false
                status = if (m.isEmpty()) "No shader packs found" else "${m.size} shader pack(s) found"
            }
        }
    }

    // Load on first show or when instance changes
    LaunchedEffect(inst?.id) {
        loadMods()
    }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (inst == null) {
            Text(
                "Select an instance in Instances to manage mods for it.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxSize(),
            )
            return@Column
        }

        // Tab selector
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ModTabButton("Mods", modsTab == ModsTab.MODS) { modsTab = ModsTab.MODS; loadMods() }
            ModTabButton("Resource Packs", modsTab == ModsTab.RESOURCE_PACKS) { modsTab = ModsTab.RESOURCE_PACKS; loadResourcePacks() }
            ModTabButton("Shader Packs", modsTab == ModsTab.SHADER_PACKS) { modsTab = ModsTab.SHADER_PACKS; loadShaderPacks() }
        }

        // Status and actions
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { backupConfigs() }, enabled = !busy) {
                Text("Backup Configs")
            }
            OutlinedButton(onClick = { refresh() }, enabled = !busy) {
                Text("Refresh")
            }
        }

        if (busy) {
            androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        // Warnings
        if (duplicates.isNotEmpty()) {
            Card(
    Modifier.fillMaxWidth(),
    colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("⚠ Duplicate mods detected", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    duplicates.forEach { group ->
                        Text("${group.size} versions of ${group.first().name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }

        if (suspicious.isNotEmpty()) {
            Card(
    Modifier.fillMaxWidth(),
    colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("⚠ Suspicious files detected", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    suspicious.forEach { s ->
                        Text("${s.mod.name}: ${s.reason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }

        // Mods list
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(mods) { mod ->
                ModManagementCard(
                    mod = mod,
                    isExpanded = expandedMods.contains(mod.file.name),
                    onToggleExpand = {
                        if (expandedMods.contains(mod.file.name)) {
                            expandedMods -= mod.file.name
                        } else {
                            expandedMods += mod.file.name
                        }
                    },
                    onToggleEnable = {
                        val newState = ModManager.toggleMod(inst, mod.file)
                        // Reload to update UI
                        when (modsTab) {
                            ModsTab.MODS -> loadMods()
                            ModsTab.RESOURCE_PACKS -> loadResourcePacks()
                            ModsTab.SHADER_PACKS -> loadShaderPacks()
                        }
                    },
                    onOpenFolder = { LauncherState.openFolder(mod.file.parentFile) },
                    onBackupConfig = { backupModConfig(mod) },
                )
            }

            if (mods.isEmpty()) {
                item {
                    Text(
                        "No ${when (modsTab) { ModsTab.MODS -> "mods"; ModsTab.RESOURCE_PACKS -> "resource packs"; ModsTab.SHADER_PACKS -> "shader packs" }} found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

enum class ModsTab { MODS, RESOURCE_PACKS, SHADER_PACKS }

@Composable
private fun ModTabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun ModManagementCard(
    mod: ModManager.ModInfo,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleEnable: () -> Unit,
    onOpenFolder: () -> Unit,
    onBackupConfig: () -> Unit,
) {
    val enabled = mod.isEnabled
    val statusColor = if (enabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Main row
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleExpand),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Enable/disable switch
                Switch(
                    checked = enabled,
                    onCheckedChange = { onToggleEnable() },
                    modifier = Modifier.padding(end = 8.dp),
                )

                // Mod info
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(mod.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("v${mod.version}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(mod.loader, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(mod.file.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                // Status
                Text(
                    if (enabled) "Enabled" else "Disabled",
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // Expanded details
            androidx.compose.animation.AnimatedVisibility(visible = isExpanded) {
                Column(Modifier.padding(top = 8.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Details
                    if (mod.mcVersion != null) {
                        Text("Minecraft: ${mod.mcVersion}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (mod.dependencies.isNotEmpty()) {
                        Text("Dependencies: ${mod.dependencies.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Path: ${mod.file.absolutePath}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)

                    // Actions
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onOpenFolder) {
                            androidx.compose.material3.Icon(androidx.compose.material.icons.filled.Folder, contentDescription = "Open folder")
                            Text("Open Folder")
                        }
                        OutlinedButton(onClick = onBackupConfig) {
                            androidx.compose.material3.Icon(androidx.compose.material.icons.filed.ContentCopy, contentDescription = "Backup config")
                            Text("Backup Config")
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onToggleExpand) {
                            androidx.compose.material3.Icon(
                                if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun backupModConfig(mod: ModManager.ModInfo) {
    // This would be called from a coroutine scope
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        val inst = mod.file.parentFile.parentFile?.takeIf { it.name == "mods" }?.parentFile
            ?: mod.file.parentFile.parentFile?.takeIf { it.name == "resourcepacks" }?.parentFile
            ?: mod.file.parentFile.parentFile?.takeIf { it.name == "shaderpacks" }?.parentFile
        if (inst != null) {
            val instance = launcher.InstanceStore.list().firstOrNull { it.dir == inst }
            instance?.let { i ->
                val backupDir = File(launcher.Dirs.root, "backups/${i.name}/mod-configs").apply { mkdirs() }
                ModManager.backupConfigs(i, backupDir)
                kotlinx.coroutines.Dispatchers.Main.launch {
                    launcher.ui.LauncherState.showToast("Config backed up to ${backupDir.name}", launcher.ui.LauncherState.ToastType.SUCCESS)
                }
            }
        }
    }
}