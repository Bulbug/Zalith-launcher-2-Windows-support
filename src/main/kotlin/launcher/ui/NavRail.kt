package launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val railWidth = 96.dp

/** Left navigation rail, ZL2-style: icon buttons that reveal their label when selected. */
@Composable
fun NavRail(current: RailScreen, onSelect: (RailScreen) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.width(railWidth).fillMaxHeight()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 14.dp),
        ) {
            Text(
                "DL",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                launcher.AppInfo.VERSION,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.padding(vertical = 6.dp))

            RailItem(Icons.Filled.Home, "Home", current == RailScreen.HOME) { onSelect(RailScreen.HOME) }
            RailItem(Icons.Filled.Home, "Download", current == RailScreen.DOWNLOAD) { onSelect(RailScreen.DOWNLOAD) }
            RailItem(Icons.Filled.Home, "Instances", current == RailScreen.INSTANCES) { onSelect(RailScreen.INSTANCES) }
            RailItem(Icons.Filled.Settings, "Settings", current == RailScreen.SETTINGS) { onSelect(RailScreen.SETTINGS) }
        }
    }
}
