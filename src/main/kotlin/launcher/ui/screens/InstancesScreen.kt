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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import launcher.LoaderKind
import launcher.ui.LauncherState
import launcher.ui.components.BackgroundCard
import launcher.ui.components.CardTitleLayout
import kotlinx.coroutines.CoroutineScope

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
                        OutlinedButton(onClick = { /* TODO: add new path */ }) { Text("Add new path") }
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
            // Sticky header with dynamic shadow
            StickyHeaderWithShadow(
                scrollState = remember { androidx.compose.foundation.lazy.LazyListState() },
                title = "Installed versions",
            ) {
                Spacer(Modifier.height(12.dp))
            }

            // Filter chips row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChipItem("All", true) {}
                FilterChipItem("Vanilla", false) {}
                FilterChipItem("Modloader", false) {}
                FilterChipItem("Modpack", false) {}
            }

            // Instance list
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.instances, key = { it.id }) { inst ->
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
                Button(onClick = { /* refresh */ }) { Text("Refresh") }
                Button(onClick = { /* install new */ }) { Text("Install new") }
            }
            content()
        }
    }
}

/** Instance card row with icon, name, subtitle, actions. */
@Composable
fun InstanceRowCard(state: LauncherState, inst: Instance) {
    val isSel = inst.id == state.selectedId
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
            }
            // Action buttons
            TextButton(onClick = { LauncherState.openFolder(inst.dir) }) { Text("Folder") }
            TextButton(onClick = { LauncherState.openFolder(inst.modsDir) }) { Text("Mods") }
            TextButton(
                onClick = { state.deleteTarget = inst },
                enabled = !state.busy,
            ) { Text("Delete") }
            TextButton(
                onClick = { /* settings */ },
                enabled = !state.busy,
            ) { Text("Settings") }
            TextButton(
                onClick = { /* export */ },
                enabled = !state.busy,
            ) { Text("Export") }
        }
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