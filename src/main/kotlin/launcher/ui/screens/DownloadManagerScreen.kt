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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import launcher.DownloadManager
import launcher.ui.LauncherState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun DownloadManagerScreen(state: LauncherState) {
    val scope = rememberCoroutineScope()
    val tasks by remember { mutableStateOf<List<DownloadManager.DownloadTask>>(emptyList()) }
    val listenerId by remember { DownloadManager.addListener { task ->
        // Update state on main thread
        scope.launch(Dispatchers.Main) {
            // Update the specific task in the list
        }
    } }

    // Reload tasks when state changes
    LaunchedEffect(Unit) {
        DownloadManager.loadState(scope)
        reloadTasks()
    }

    fun reloadTasks() {
        tasks = DownloadManager.getAll()
    }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Header
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Download Manager", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            Text("${tasks.size} tasks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = { DownloadManager.clearCompleted(); reloadTasks() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Clear completed")
            }
        }

        if (tasks.isEmpty()) {
            Text(
                "No downloads yet. Start a download from the Download screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tasks) { task ->
                    DownloadTaskCard(task, scope, onRemove = { reloadTasks() })
                }
            }
        }
    }
}

@Composable
private fun DownloadTaskCard(
    task: DownloadManager.DownloadTask,
    scope: kotlinx.coroutines.CoroutineScope,
    onRemove: () -> Unit,
) {
    val statusColor = when (task.status) {
        DownloadManager.Status.DOWNLOADING -> MaterialTheme.colorScheme.primary
        DownloadManager.Status.COMPLETED -> MaterialTheme.colorScheme.secondary
        DownloadManager.Status.FAILED -> MaterialTheme.colorScheme.error
        DownloadManager.Status.PAUSED -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(task.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        task.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(task.status.name, style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.SemiBold)
            }

            if (task.status == DownloadManager.Status.DOWNLOADING || task.status == DownloadManager.Status.PAUSED) {
                LinearProgressIndicator(progress = task.progress.coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(formatBytes(task.downloadedBytes) + " / " + formatBytes(task.totalBytes), style = MaterialTheme.typography.labelSmall)
                    Text(formatSpeed(task.speedBytesPerSec), style = MaterialTheme.typography.labelSmall)
                    if (task.status == DownloadManager.Status.DOWNLOADING) {
                        IconButton(onClick = { DownloadManager.pause(task.id); onRemove() }) {
                            Icon(Icons.Filled.Pause, contentDescription = "Pause")
                        }
                    } else {
                        IconButton(onClick = { DownloadManager.start(task.id, scope); onRemove() }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Resume")
                        }
                    }
                }
            }

            if (task.status == DownloadManager.Status.FAILED && task.error != null) {
                Text("Error: ${task.error}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            if (task.status == DownloadManager.Status.COMPLETED || task.status == DownloadManager.Status.FAILED || task.status == DownloadManager.Status.CANCELLED) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { DownloadManager.remove(task.id); onRemove() }) {
                        Text("Remove")
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.1f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0) return ""
    return when {
        bytesPerSec >= 1_000_000 -> "%.1f MB/s".format(bytesPerSec / 1_000_000.0)
        bytesPerSec >= 1_000 -> "%.1f KB/s".format(bytesPerSec / 1_000.0)
        else -> "$bytesPerSec B/s"
    }
}