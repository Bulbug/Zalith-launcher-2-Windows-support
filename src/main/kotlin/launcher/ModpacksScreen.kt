package launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ModpacksScreen(onBack: () -> Unit, onInstalled: (Instance) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ModHit>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf<Float?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun search() {
        scope.launch {
            busy = true
            status = "Searching Modrinth…"
            try {
                val r = withContext(Dispatchers.IO) { Modrinth.searchPacks(query.trim()) }
                results = r
                status = if (r.isEmpty()) "No modpacks found" else "${r.size} modpacks (data from Modrinth)"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = "Search failed: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun install(hit: ModHit) {
        scope.launch {
            busy = true
            progress = null
            status = "Downloading ${hit.title}…"
            try {
                val inst = withContext(Dispatchers.IO) {
                    val file = Modrinth.downloadPack(hit.projectId, File(Dirs.root, "downloads"))
                    try {
                        ModpackInstaller.install(
                            file,
                            status = { s -> status = s },
                            progress = { d, t -> progress = if (t > 0) d.toFloat() / t else null },
                        )
                    } finally {
                        file.delete()
                    }
                }
                onInstalled(inst)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = "Install failed: ${e.message}"
            } finally {
                busy = false
                progress = null
            }
        }
    }

    LaunchedEffect(Unit) { search() }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Text("Modpacks (Modrinth)", style = MaterialTheme.typography.titleLarge)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search modpacks (empty = most popular)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { search() }, enabled = !busy) { Text("Search") }
        }
        val p = progress
        if (busy) {
            if (p != null) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Text(status, style = MaterialTheme.typography.bodyMedium)

        LazyColumn(Modifier.weight(1f)) {
            items(results) { hit ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(hit.title, fontWeight = FontWeight.Bold)
                            Text(
                                "by ${hit.author} - ${"%,d".format(hit.downloads)} downloads",
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                hit.description,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Button(onClick = { install(hit) }, enabled = !busy) { Text("Install") }
                    }
                }
            }
        }
    }
}
