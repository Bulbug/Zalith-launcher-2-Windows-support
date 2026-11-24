package launcher.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import launcher.ui.screens.DownloadManagerScreen
import launcher.ui.screens.DownloadScreen
import launcher.ui.screens.HomeScreen
import launcher.ui.screens.InstancesScreen
import launcher.ui.screens.SettingsScreen

@Composable
fun ZL2Scaffold(
    state: LauncherState,
    scope: CoroutineScope,
    screenContent: @Composable (RailScreen) -> Unit,
    globalDialogs: @Composable () -> Unit,
) {
    var currentScreen by remember { mutableStateOf(RailScreen.HOME) }
    var taskRunning by remember { mutableStateOf(state.busy) }
    var taskProgress by remember { mutableStateOf(state.progress) }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            currentScreen = currentScreen,
            onBack = { currentScreen = RailScreen.HOME }, // simplified
            onHome = { currentScreen = RailScreen.HOME },
            onNavigate = { currentScreen = it },
            onTaskMenu = { /* open task drawer */ },
            onFileManager = { LauncherState.openFolder(launcher.Dirs.root) },
            taskRunning = taskRunning,
            taskProgress = taskProgress,
        )

        Crossfade(currentScreen) { screen ->
            when (screen) {
                RailScreen.HOME -> HomeScreen(state, { currentScreen = it }, scope)
                RailScreen.DOWNLOAD -> DownloadScreen(state)
                RailScreen.DOWNLOAD_MANAGER -> DownloadManagerScreen(state)
                RailScreen.INSTANCES -> InstancesScreen(state)
                RailScreen.SETTINGS -> SettingsScreen(state)
            }
        }
    }

    globalDialogs()
}