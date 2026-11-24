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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import launcher.CrashManager
import launcher.Instance
import launcher.ui.LauncherState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filed.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun CrashDiagnosticsScreen(state: LauncherState) {
    val scope = rememberCoroutineScope()
    val reports by remember { mutableStateOf<List<CrashManager.CrashReport>>(emptyList()) }
    val selectedReport by remember { mutableStateOf<CrashManager.CrashReport?>(null) }
    val showExportDialog by remember { mutableStateOf(false) }
    val exportPath by remember { mutableStateOf("") }

    fun loadReports() {
        scope.launch(Dispatchers.IO) {
            val r = CrashManager.getReports()
            scope.launch(Dispatchers.Main) { reports = r }
        }
    }

    // Load on first show
    LaunchedEffect(Unit) {
        loadReports()
    }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Header
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Crash & Diagnostics", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            Text("${reports.size} report(s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { loadReports() }) {
                androidx.compose.material3.Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        }

        if (reports.isEmpty()) {
            Text(
                "No crash reports yet. Crashes will appear here automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(reports) { report ->
                    CrashReportCard(
                        report = report,
                        selected = selectedReport == report,
                        onClick = { selectedReport = if (selectedReport == report) null else report },
                        onDelete = {
                            CrashManager.getReports() // Just reload
                            loadReports()
                        },
                    )
                }
            }
        }

        // Export section for selected instance
        state.selected?.let { inst ->
            Card(Modifier.fillMaxWidth(), modifier = Modifier.padding(top = 16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Export Diagnostic Package", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(onClick = { exportDiagnostics(inst) }) {
                            Text("Export for ${inst.name}")
                        }
                    }
                    Text(
                        "Creates a ZIP with game logs, crash reports, mod list, and system info for debugging.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CrashReportCard(
    report: CrashManager.CrashReport,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val statusColor = if (report.exitCode == 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error

    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = if (selected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(report.instanceName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("MC ${report.mcVersion} - ${report.loader}${report.loaderVersion?.let { " $it" } ?: ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column {
                    Text(report.timestamp.replace("_", " ").replace("-", "/"), style = MaterialTheme.typography.labelSmall)
                    Text(
                        "Exit: ${report.exitCode?.toString() ?: "N/A"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                IconButton(onClick = onDelete) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.filled.Delete, contentDescription = "Delete report")
                }
            }

            // Crash reason
            if (report.crashReason != null) {
                Text("Reason: ${report.crashReason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }

            // Expandable details
            if (selected) {
                androidx.compose.animation.AnimatedVisibility(visible = true) {
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // System info
                        if (report.systemInfo.entrySet().isNotEmpty()) {
                            Text("System Info", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                                Column(Modifier.padding(8.dp)) {
                                    report.systemInfo.entrySet().forEach { (k, v) ->
                                        Text("$k: ${v.asString}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }

                        // Mods
                        if (report.mods.isNotEmpty()) {
                            Text("Mods (${report.mods.size})", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                                Column(Modifier.padding(8.dp)) {
                                    report.mods.forEach { mod ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(mod.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                            Text("v${mod.version}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(mod.loader, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }

                        // Stack trace
                        if (report.stackTrace != null && report.stackTrace!!.isNotBlank()) {
                            Text("Stack Trace", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().padding(8.dp).height(200.dp)) {
                                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().padding(8.dp)) {
                                    androidx.compose.foundation.Text(
                                        text = report.stackTrace!!,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun exportDiagnostics(inst: Instance) {
    // This would be called from a coroutine scope
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        val outputFile = File(launcher.Dirs.root, "exports/diagnostic-${inst.name}-${System.currentTimeMillis()}.zip")
        try {
            launcher.CrashManager.exportDiagnosticPackage(inst, outputFile)
            // Show toast on main thread
            kotlinx.coroutines.Dispatchers.Main.launch {
                launcher.ui.LauncherState.showToast("Diagnostic package exported to ${outputFile.name}", launcher.ui.LauncherState.ToastType.SUCCESS)
            }
        } catch (e: Exception) {
            kotlinx.coroutines.Dispatchers.Main.launch {
                launcher.ui.LauncherState.showToast("Export failed: ${e.message}", launcher.ui.LauncherState.ToastType.ERROR)
            }
        }
    }
}