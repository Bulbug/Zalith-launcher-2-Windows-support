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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import launcher.ServerManager
import launcher.ui.LauncherState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filed.ContentCopy
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun ServerManagerScreen(state: LauncherState) {
    val scope = rememberCoroutineScope()
    var servers by remember { mutableStateOf<List<ServerManager.ServerEntry>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingServer by remember { mutableStateOf<ServerManager.ServerEntry?>(null) }
    var newName by remember { mutableStateOf("") }
    var newAddress by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf("default") }
    var newHidden by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    fun loadServers() {
        scope.launch(Dispatchers.IO) {
            val s = ServerManager.loadServers()
            scope.launch(Dispatchers.Main) { servers = s }
        }
    }

    // Load on first show
    LaunchedEffect(Unit) {
        loadServers()
    }

    fun addServer() {
        newName = ""
        newAddress = ""
        newCategory = "default"
        newHidden = false
        showAddDialog = true
    }

    fun editServer(server: ServerManager.ServerEntry) {
        editingServer = server
        newName = server.name
        newAddress = server.address
        newCategory = server.category
        newHidden = server.hidden
        showEditDialog = true
    }

    fun deleteServer(id: String) {
        scope.launch(Dispatchers.IO) {
            ServerManager.removeServer(id)
            scope.launch(Dispatchers.Main) { loadServers() }
        }
    }

    fun saveNewServer() {
        if (newName.trim().isBlank() || newAddress.trim().isBlank()) {
            status = "Name and address are required"
            return
        }
        val server = ServerManager.ServerEntry(
            id = ServerManager.generateId(newName.trim()),
            name = newName.trim(),
            address = newAddress.trim(),
            category = newCategory,
            hidden = newHidden,
        )
        scope.launch(Dispatchers.IO) {
            ServerManager.addServer(server)
            scope.launch(Dispatchers.Main) {
                loadServers()
                showAddDialog = false
            }
        }
    }

    fun saveEditedServer() {
        if (newName.trim().isBlank() || newAddress.trim().isBlank()) {
            status = "Name and address are required"
            return
        }
        editingServer?.let { server ->
            val updated = server.copy(
                name = newName.trim(),
                address = newAddress.trim(),
                category = newCategory,
                hidden = newHidden,
            )
            scope.launch(Dispatchers.IO) {
                ServerManager.updateServer(updated)
                scope.launch(Dispatchers.Main) {
                    loadServers()
                    showEditDialog = false
                    editingServer = null
                }
            }
        }
    }

    fun connectToServer(server: ServerManager.ServerEntry) {
        // This would connect to the server - for now just update last connected
        ServerManager.updateLastConnected(server.id)
        scope.launch(Dispatchers.Main) { loadServers() }
        state.showToast("Connecting to ${server.name}...", LauncherState.ToastType.INFO)
    }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Header
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Server Manager", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            Text("${servers.size} server(s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { addServer() }) {
                Icon(Icons.Filled.Add, contentDescription = "Add server")
                Text("Add Server")
            }
        }

        if (status.isNotBlank()) {
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        if (servers.isEmpty()) {
            Text(
                "No servers saved yet. Add a server to get started.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(servers) { server ->
                    ServerCard(
                        server = server,
                        onConnect = { connectToServer(server) },
                        onEdit = { editServer(server) },
                        onDelete = { deleteServer(server.id) },
                    )
                }
            }
        }
    }

    // Add server dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Server") },
            text = {
                Column(Modifier.padding(16.dp).width(400.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Server Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = newAddress,
                        onValueChange = { newAddress = it },
                        label = { Text("Address (host:port)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = newCategory,
                        onValueChange = { newCategory = it },
                        label = { Text("Category") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Switch(checked = newHidden, onCheckedChange = { newHidden = it })
                        Text("Hidden")
                    }
                }
            },
            confirmButton = {
                Button(onClick = { saveNewServer() }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            },
        )
    }

    // Edit server dialog
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false; editingServer = null },
            title = { Text("Edit Server") },
            text = {
                Column(Modifier.padding(16.dp).width(400.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Server Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = newAddress,
                        onValueChange = { newAddress = it },
                        label = { Text("Address (host:port)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = newCategory,
                        onValueChange = { newCategory = it },
                        label = { Text("Category") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Switch(checked = newHidden, onCheckedChange = { newHidden = it })
                        Text("Hidden")
                    }
                    if (status.isNotBlank()) {
                        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { saveEditedServer() }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false; editingServer = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ServerCard(
    server: ServerManager.ServerEntry,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val (host, port) = ServerManager.parseAddress(server.address)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(server.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("$host:$port", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                    if (server.category != "default") {
                        Text("Category: ${server.category}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    if (server.lastConnected > 0) {
                        val date = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(server.lastConnected))
                        Text("Last connected: $date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // Actions
                Button(onClick = onConnect) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Connect")
                    Text("Connect")
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}