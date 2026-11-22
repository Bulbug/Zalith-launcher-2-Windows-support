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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import launcher.Instance
import launcher.ui.LauncherState
import launcher.ui.SectionCard

/** Manage all instances: select, open folders, delete, create. */
@Composable
fun InstancesScreen(state: LauncherState) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Instances", style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { state.showNewInstance = true }, enabled = !state.busy) { Text("New instance") }
            OutlinedButton(
                onClick = { state.importModpack(LauncherState.pickMrpack()) {} },
                enabled = !state.busy,
            ) { Text("Import .mrpack") }
        }
        if (state.instances.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "No instances yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Create one with \"New instance\", or install a modpack from Download.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.instances, key = { it.id }) { inst ->
                    InstanceRow(state, inst)
                }
            }
        }
    }
}

@Composable
private fun InstanceRow(state: LauncherState, inst: Instance) {
    val isSel = inst.id == state.selectedId
    Surface(
        color = if (isSel) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().clickable(enabled = !state.busy) { state.selectInstance(inst.id) },
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.height(52.dp).width(52.dp)) { InstanceIcon(inst, Modifier.fillMaxSize()) }

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
            }
            TextButton(onClick = { LauncherState.openFolder(inst.dir) }) { Text("Folder") }
            TextButton(onClick = { LauncherState.openFolder(inst.modsDir) }) { Text("Mods") }
            TextButton(
                onClick = { state.deleteTarget = inst },
                enabled = !state.busy,
            ) { Text("Delete") }
        }
    }
}
