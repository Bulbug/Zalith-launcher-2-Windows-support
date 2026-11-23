package launcher

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import launcher.ui.AppTheme
import launcher.ui.LauncherState
import launcher.ui.RailScreen
import launcher.ui.TopBar
import launcher.ui.screens.DownloadScreen
import launcher.ui.screens.HomeScreen
import launcher.ui.screens.InstancesScreen
import launcher.ui.screens.SettingsScreen
import launcher.ui.rememberAppTheme

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    val state = remember { LauncherState(scope) }
    val theme = rememberAppTheme()
    val snackbarHostState = remember { SnackbarHostState() }

    // Sync theme state from saved settings
    LaunchedEffect(Unit) {
        state.dark = theme.isDark
        state.colorTheme = theme.colorTheme
    }

    // Show toasts
    LaunchedEffect(state.toastMessage) {
        val msg = state.toastMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            state.toastMessage = null
        }
    }

    AppTheme(isDark = state.dark, colorTheme = state.colorTheme) {
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    TopBar(
                        currentScreen = state.screen,
                        onBack = { state.screen = RailScreen.HOME },
                        onHome = { state.screen = RailScreen.HOME },
                        onNavigate = { state.screen = it },
                        onTaskMenu = { },
                        onFileManager = { LauncherState.openFolder(launcher.Dirs.root) },
                        taskRunning = state.busy,
                        taskProgress = state.progress,
                    )
                    Crossfade(state.screen) { screen ->
                        when (screen) {
                            RailScreen.HOME -> HomeScreen(state, onNavigate = { state.screen = it }, scope = scope)
                            RailScreen.DOWNLOAD -> DownloadScreen(state)
                            RailScreen.INSTANCES -> InstancesScreen(state)
                            RailScreen.SETTINGS -> SettingsScreen(state)
                        }
                    }
                }
                SnackbarHost(snackbarHostState) { data ->
                    androidx.compose.material3.Snackbar(
                        data,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
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