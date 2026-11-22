package launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import launcher.Instance
import launcher.LoaderKind
import launcher.ui.ActionCard
import launcher.ui.LauncherState
import launcher.ui.RailScreen
import launcher.ui.SectionCard
import launcher.ui.WorkingIndicator

/** Hero card for the selected instance + quick actions + game log. */
@Composable
fun HomeScreen(state: LauncherState, onNavigate: (RailScreen) -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HeroCard(state, Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionCard("New instance", "Pick a version and loader", Modifier.weight(1f)) {
                state.showNewInstance = true
            }
            ActionCard("Browse modpacks", "Install from Modrinth", Modifier.weight(1f)) {
                onNavigate(RailScreen.DOWNLOAD)
            }
            ActionCard("Import .mrpack", "Install a downloaded pack", Modifier.weight(1f)) {
                state.importModpack(LauncherState.pickMrpack()) {}
            }
        }
        LogPanel(state, Modifier.weight(1.2f))
    }
}

@Composable
private fun HeroCard(state: LauncherState, modifier: Modifier = Modifier) {
    val inst = state.selected
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (inst == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No instance selected.\nCreate one to get started.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                InstanceIcon(inst, Modifier.size(72.dp))
                Column {
                    Text(inst.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        inst.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (inst.loader.usesInstaller) {
                Text(
                    "${inst.loader.label} is set up with its official installer. The first launch can take a few minutes.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(2.dp))
            Button(
                onClick = { state.play() },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(if (state.busy) "Working…" else "PLAY", fontSize = 18.sp)
            }
            if (state.busy) WorkingIndicator(state.status, state.progress)
            else Text(state.status, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Colored rounded square standing in for a version icon; hue derived from the version id. */
@Composable
fun InstanceIcon(inst: Instance, modifier: Modifier = Modifier) {
    val hue = (inst.mc.hashCode().mod(360)) / 360f
    val color = Color.hsv(hue, 0.55f, 0.75f)
    Box(
        modifier
            .background(color, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            inst.loader.label.take(1),
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun LogPanel(state: LauncherState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.log.size) {
        if (state.log.isNotEmpty()) listState.scrollToItem(state.log.size - 1)
    }
    SectionCard(title = "Game log", modifier) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().height(220.dp),
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(10.dp)) {
                items(state.log) { line ->
                    Text(line, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            }
        }
    }
}
