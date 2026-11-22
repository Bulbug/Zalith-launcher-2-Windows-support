package launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.foundation.layout.Arrangement as FArrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import launcher.AppInfo
import launcher.ui.RailScreen
import kotlinx.coroutines.CoroutineScope

@Composable
fun TopBar(
    currentScreen: RailScreen,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onNavigate: (RailScreen) -> Unit,
    onTaskMenu: () -> Unit,
    onFileManager: () -> Unit,
    taskRunning: Boolean,
    taskProgress: Float?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(TOP_BAR_HEIGHT)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: back + home + title
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                AnimatedVisibility(visible = currentScreen != RailScreen.HOME) {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                }
                IconButton(onClick = onHome) { Icon(Icons.Filled.Home, contentDescription = "Home") }
                Text(
                    when (currentScreen) {
                        RailScreen.HOME -> AppInfo.NAME
                        RailScreen.DOWNLOAD -> "Download"
                        RailScreen.INSTANCES -> "Instances"
                        RailScreen.SETTINGS -> "Settings"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Spacer(Modifier.weight(1f))

            // Right: task menu + file manager + rail items
            Box(
                modifier = Modifier
                    .width(TASK_MENU_WIDTH)
                    .height(TOP_BAR_HEIGHT - 8.dp)
                    .padding(all = 4.dp)
                    .clickable(onClick = onTaskMenu)
                    .background(
                        if (taskRunning) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = MaterialTheme.shapes.large,
                    ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp).fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (taskRunning) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), progress = { taskProgress?.coerceIn(0f, 1f) ?: 0f })
                    } else {
                        Spacer(Modifier.fillMaxWidth())
                    }
                    Icon(Icons.Filled.Assignment, contentDescription = "Task menu", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }

            IconButton(onClick = onFileManager) { Icon(Icons.Filled.Folder, contentDescription = "Open folder") }

            TopBarRailItem(Icons.Filled.Group, "Multiplayer", currentScreen == RailScreen.INSTANCES) { onNavigate(RailScreen.INSTANCES) }
            TopBarRailItem(Icons.Filled.Download, "Download", currentScreen == RailScreen.DOWNLOAD) { onNavigate(RailScreen.DOWNLOAD) }
            TopBarRailItem(Icons.Filled.Settings, "Settings", currentScreen == RailScreen.SETTINGS) { onNavigate(RailScreen.SETTINGS) }
        }
    }
}

@Composable
fun TopBarRailItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(
                horizontal = if (selected) RAIL_ITEM_SELECTED_HORIZONTAL_PADDING else RAIL_ITEM_UNSELECTED_PADDING,
                vertical = if (selected) RAIL_ITEM_SELECTED_VERTICAL_PADDING else RAIL_ITEM_UNSELECTED_PADDING,
            )
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurface)
            AnimatedVisibility(visible = selected) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Medium)
            }
        }
    }
}