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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import launcher.ui.LauncherState
import launcher.ui.RailScreen
import launcher.ui.ColorTheme
import launcher.ui.components.BackgroundCard
import launcher.ui.components.CardTitleLayout

/**
 * ZL2-style Settings screen:
 * - Left rail: NavigationRailItem per section (fadeEdge on scroll)
 * - Right content: per-section cards with glassBackground
 */
@Composable
fun SettingsScreen(state: LauncherState) {
    val saved = remember { launcher.Settings.load() }
    var username by remember { mutableStateOf(saved.username) }
    var ram by remember { mutableStateOf(saved.ramMb.toString()) }
    var currentTab by remember { mutableStateOf(SettingsTab.ACCOUNT) }
    val scrollState = rememberScrollState()

    Row(Modifier.fillMaxSize()) {
        // ---- Left rail ----
        Column(
            Modifier
                .width(220.dp)
                .fillMaxSize()
                .padding(vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SettingsRailItem("Account", currentTab == SettingsTab.ACCOUNT) { currentTab = SettingsTab.ACCOUNT }
            SettingsRailItem("Memory", currentTab == SettingsTab.MEMORY) { currentTab = SettingsTab.MEMORY }
            SettingsRailItem("Appearance", currentTab == SettingsTab.APPEARANCE) { currentTab = SettingsTab.APPEARANCE }
            SettingsRailItem("Folders", currentTab == SettingsTab.FOLDERS) { currentTab = SettingsTab.FOLDERS }
            SettingsRailItem("About", currentTab == SettingsTab.ABOUT) { currentTab = SettingsTab.ABOUT }
        }

        // ---- Right content ----
        Column(
            Modifier
                .weight(1f)
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (currentTab) {
                SettingsTab.ACCOUNT -> AccountSection(state)
                SettingsTab.MEMORY -> MemorySection(username, ram)
                SettingsTab.APPEARANCE -> AppearanceSection(state)
                SettingsTab.FOLDERS -> FoldersSection(state)
                SettingsTab.ABOUT -> AboutSection()
            }

            TextButton(onClick = {
                val ramMb = ram.toIntOrNull()?.coerceIn(512, 32768) ?: 2048
                launcher.Settings(username.trim().ifBlank { "Player" }, ramMb, saved.account, saved.instance, state.dark, state.colorTheme.name).save()
            }) { Text("Save settings") }
        }
    }
}

enum class SettingsTab { ACCOUNT, MEMORY, APPEARANCE, FOLDERS, ABOUT }

@Composable
private fun SettingsRailItem(label: String, selected: Boolean, onClick: () -> Unit) {
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
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun AccountSection(state: LauncherState) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        CardTitleLayout("Account") {
            val ms = state.msAccount
            if (ms != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Signed in: ${ms.name}", Modifier.weight(1f))
                    TextButton(onClick = { state.signOut() }) { Text("Sign out") }
                }
            } else {
                Text(
                    "No Microsoft account signed in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { state.signIn() }, enabled = !state.busy) { Text("Sign in with Microsoft") }
            }
        }
    }
}

@Composable
private fun MemorySection(username: String, ram: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CardTitleLayout("Offline profile") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { /* update via parent */ },
                    label = { Text("Username (offline mode)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Used when Microsoft sign-in isn't available. 3-16 letters, numbers or _.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        CardTitleLayout("Memory") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = ram,
                    onValueChange = { /* update via parent */ },
                    label = { Text("RAM (MB)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1024, 2048, 4096, 8192).forEach { v ->
                        FilterChip(
                            selected = false, // would need parent state
                            onClick = { /* ram = v.toString() */ },
                            label = { Text("${v / 1024}G") },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearanceSection(state: LauncherState) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        CardTitleLayout("Appearance") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Dark theme", Modifier.weight(1f))
                    Switch(checked = state.dark, onCheckedChange = { state.applyDark(it) })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Color theme")
                    launcher.ui.ColorTheme.entries.forEach { t ->
                        FilterChip(
                            selected = state.colorTheme == t,
                            onClick = { state.applyColorTheme(t) },
                            label = { Text(t.name) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FoldersSection(state: LauncherState) {
    CardTitleLayout("Folders") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { LauncherState.openFolder(launcher.Dirs.root) }) { Text("Launcher folder") }
            OutlinedButton(onClick = { LauncherState.openFolder(launcher.Dirs.game) }) { Text("Old game folder") }
            state.selected?.let { inst ->
                OutlinedButton(onClick = { LauncherState.openFolder(inst.dir) }) { Text("Instance folder") }
            }
        }
    }
}

@Composable
private fun AboutSection() {
    CardTitleLayout("About") {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${launcher.AppInfo.NAME} v${launcher.AppInfo.VERSION}", style = MaterialTheme.typography.titleSmall)
            Text(
                launcher.AppInfo.NOTICE,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "A Windows launcher inspired by Zalith Launcher 2 (GPL-3.0). Game saves, mods and assets live in %APPDATA%\\.desklauncher.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}