package launcher

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import launcher.ui.Accent
import launcher.ui.AppTheme
import launcher.ui.LauncherState
import launcher.ui.NavRail
import launcher.ui.RailScreen
import launcher.ui.screens.DownloadScreen
import launcher.ui.screens.HomeScreen
import launcher.ui.screens.InstancesScreen
import launcher.ui.screens.SettingsScreen

@Composable
fun App() {
    val state = remember { LauncherState(CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
    var screen by remember { mutableStateOf(RailScreen.HOME) }

    AppTheme(dark = state.dark, accent = state.accent) {
        Surface(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize()) {
                NavRail(screen) { screen = it }
                Crossfade(screen) { s ->
                    when (s) {
                        RailScreen.HOME -> HomeScreen(state) { screen = it }
                        RailScreen.DOWNLOAD -> DownloadScreen(state)
                        RailScreen.INSTANCES -> InstancesScreen(state)
                        RailScreen.SETTINGS -> SettingsScreen(state)
                    }
                }
            }
        }

        GlobalDialogs(state)
    }
}

/** Dialogs that can appear over any screen. */
@Composable
private fun GlobalDialogs(state: LauncherState) {
    val dc = state.deviceCode
    if (dc != null) {
        AlertDialog(
            onDismissRequest = { state.cancelSignIn() },
            title = { Text("Sign in with Microsoft") },
            text = {
                Column2 {
                    Text("1. Open ${dc.verificationUri} (we tried to open it for you).")
                    Text("2. Enter this code:")
                    Text(dc.userCode, style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace)
                    Text("This window closes by itself when you're signed in.")
                }
            },
            confirmButton = { Button(onClick = { LauncherState.openBrowser(dc.verificationUri) }) { Text("Open page") } },
            dismissButton = { TextButton(onClick = { state.cancelSignIn() }) { Text("Cancel") } },
        )
    }

    if (state.showNewInstance) {
        NewInstanceDialog(
            versions = state.versions,
            onCreate = { n, mc, l, lv -> state.createInstance(n, mc, l, lv) },
            onDismiss = { state.showNewInstance = false },
        )
    }

    val del = state.deleteTarget
    if (del != null) {
        AlertDialog(
            onDismissRequest = { state.deleteTarget = null },
            title = { Text("Delete \"${del.name}\"?") },
            text = { Text("This permanently deletes the instance folder, including its worlds, mods and settings.") },
            confirmButton = {
                Button(onClick = { state.deleteInstance(del) }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { state.deleteTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Column2(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) { content() }
}
