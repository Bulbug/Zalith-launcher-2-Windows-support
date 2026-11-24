package launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    onCreate: (name: String, mc: String, loader: LoaderKind, loaderVersion: String?, optifineVersion: String?) -> Unit,
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

    // OptiFine build selection (available for any loader except VANILLA/OPTIFINE standalone)
    var ofMenuOpen by remember { mutableStateOf(false) }
    var ofBuilds by remember { mutableStateOf<List<OptiFineBuild>?>(null) }
    var ofBuild by remember { mutableStateOf<OptiFineBuild?>(null) }
    var useOptiFine by remember { mutableStateOf(false) }

    LaunchedEffect(useOptiFine, mc) {
        if (!useOptiFine || mc.isBlank()) {
            ofBuild = null
            ofBuilds = null
            return@LaunchedEffect
        }
        ofBuild = null
        ofBuilds = withContext(Dispatchers.IO) {
            runCatching { OptiFine.listFor(mc) }.getOrNull()
        }
    }
    val ofList = ofBuilds

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
                        rowKinds.forEach { k ->
                            val enabled = k != LoaderKind.OPTIFINE // OptiFine standalone is legacy
                            RadioRow(k.label, loader == k, enabled) { if (enabled) loader = k }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                // OptiFine checkbox (available when loader supports it)
                val canUseOptiFine = loader == LoaderKind.FORGE || loader == LoaderKind.NEOFORGE || loader == LoaderKind.FABRIC || loader == LoaderKind.QUILT || loader == LoaderKind.VANILLA
                if (canUseOptiFine) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = useOptiFine, onCheckedChange = { useOptiFine = it })
                        Text("Add OptiFine")
                    }
                    if (useOptiFine) {
                        val build = ofBuild
                        Column {
                            OutlinedButton(
                                onClick = { ofMenuOpen = true },
                                enabled = mc.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    when {
                                        ofList == null -> "Loading OptiFine builds…"
                                        ofList.isEmpty() -> "No OptiFine builds for $mc"
                                        build != null -> "OptiFine build: ${build.displayName}"
                                        else -> "OptiFine build…"
                                    }
                                )
                            }
                            DropdownMenu(expanded = ofMenuOpen, onDismissRequest = { ofMenuOpen = false }) {
                                ofList.orEmpty().filter { !it.isPreview }.forEach { b ->
                                    DropdownMenuItem(
                                        text = { Text(b.displayName) },
                                        onClick = {
                                            ofBuild = b
                                            ofMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val build = ofBuild
            Button(
                onClick = { onCreate(name, mc, loader, null, if (useOptiFine) build?.version else null) },
                enabled = mc.isNotBlank() && (!useOptiFine || build != null),
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
