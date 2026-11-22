package launcher.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import launcher.ui.Accent
import launcher.ui.LauncherState
import launcher.ui.SectionCard

/** Settings: account, memory, appearance, folders, about. */
@Composable
fun SettingsScreen(state: LauncherState) {
    val saved = remember { launcher.Settings.load() }
    var username by remember { mutableStateOf(saved.username) }
    var ram by remember { mutableStateOf(saved.ramMb.toString()) }

    fun persist() {
        val ramMb = ram.toIntOrNull()?.coerceIn(512, 32768) ?: 2048
        launcher.Settings(username.trim().ifBlank { "Player" }, ramMb, saved.account, saved.instance, state.dark, state.accent.name).save()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        // ---- account ----
        SectionCard("Account") {
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

        // ---- offline profile ----
        SectionCard("Offline profile") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
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

        // ---- memory ----
        SectionCard("Memory") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = ram,
                    onValueChange = { ram = it.filter { c -> c.isDigit() } },
                    label = { Text("RAM (MB)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1024, 2048, 4096, 8192).forEach { v ->
                        FilterChip(
                            selected = ram == v.toString(),
                            onClick = { ram = v.toString() },
                            label = { Text("${v / 1024}G") },
                        )
                    }
                }
            }
        }

        // ---- appearance ----
        SectionCard("Appearance") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Dark theme", Modifier.weight(1f))
                    Switch(checked = state.dark, onCheckedChange = { state.applyDark(it) })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Accent")
                    Accent.entries.forEach { a ->
                        FilterChip(
                            selected = state.accent == a,
                            onClick = { state.applyAccent(a) },
                            label = { Text(a.label) },
                        )
                    }
                }
            }
        }

        // ---- folders ----
        SectionCard("Folders") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { LauncherState.openFolder(launcher.Dirs.root) }) { Text("Launcher folder") }
                OutlinedButton(onClick = { LauncherState.openFolder(launcher.Dirs.game) }) { Text("Old game folder") }
                state.selected?.let { inst ->
                    OutlinedButton(onClick = { LauncherState.openFolder(inst.dir) }) { Text("Instance folder") }
                }
            }
        }

        // ---- about ----
        SectionCard("About") {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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

        TextButton(onClick = { persist() }) { Text("Save profile settings") }
    }
}
