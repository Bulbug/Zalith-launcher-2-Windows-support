package launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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

@Composable
fun RadioRow(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Text(label)
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
fun NewInstanceDialog(
    versions: List<VersionEntry>,
    onCreate: (name: String, mc: String, loader: LoaderKind) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var showAll by remember { mutableStateOf(false) }
    var mc by remember { mutableStateOf(versions.firstOrNull { it.type == "release" }?.id ?: "") }
    var loader by remember { mutableStateOf(LoaderKind.VANILLA) }
    var menuOpen by remember { mutableStateOf(false) }
    val visible = remember(versions, showAll) {
        if (showAll) versions else versions.filter { it.type == "release" }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New instance") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column {
                    OutlinedButton(
                        onClick = { menuOpen = true },
                        enabled = visible.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (mc.isBlank()) "Version list not loaded" else "Minecraft version: $mc")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        visible.forEach { v ->
                            DropdownMenuItem(
                                text = { Text(v.id) },
                                onClick = {
                                    mc = v.id
                                    menuOpen = false
                                },
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = showAll, onCheckedChange = { showAll = it })
                    Text("Show snapshots and old versions")
                }
                Text("Mod loader", style = MaterialTheme.typography.labelLarge)
                LoaderKind.values().toList().chunked(3).forEach { rowKinds ->
                    Row {
                        rowKinds.forEach { k -> RadioRow(k.label, loader == k, true) { loader = k } }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(name, mc, loader) }, enabled = mc.isNotBlank()) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
