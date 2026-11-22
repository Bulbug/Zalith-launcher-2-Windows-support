package launcher.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import launcher.Instance
import launcher.LoaderKind
import launcher.ui.ActionCard
import launcher.ui.LauncherState
import launcher.ui.RailScreen
import launcher.ui.components.BackgroundCard
import kotlinx.coroutines.CoroutineScope

/**
 * ZL2-style Home screen with responsive version card layouts.
 */
@Composable
fun HomeScreen(
    state: LauncherState,
    onNavigate: (RailScreen) -> Unit,
    scope: CoroutineScope,
) {
    val inst = state.selected
    val scrollState = rememberLazyListState()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---- Hero card for selected instance ----
        if (inst != null) {
            VersionCard(state, inst, scope, Modifier.fillMaxWidth())
        } else {
            Box(
                modifier = Modifier.weight(1f).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No instance selected.\nCreate one to get started.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // ---- Quick actions ----
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionCard("New instance", "Pick a version and loader", Modifier.weight(1f)) {
                state.showNewInstance = true
            }
            ActionCard("Browse modpacks", "Install from Modrinth", Modifier.weight(1f)) {
                // onNavigate(RailScreen.DOWNLOAD)
            }
            ActionCard("Import .mrpack", "Install a downloaded pack", Modifier.weight(1f)) {
                state.importModpack(LauncherState.pickMrpack()) {}
            }
        }

        // ---- Game log ----
        LogPanel(state, Modifier.height(220.dp))
    }
}

/** Responsive version card: adapts to available space (Tall/Row/Compact). */
@Composable
fun VersionCard(
    state: LauncherState,
    inst: Instance,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val widthDp = with(LocalDensity.current) { constraints.maxWidth.toDp() }
        val heightDp = with(LocalDensity.current) { constraints.maxHeight.toDp() }

        // Determine layout based on available space (ZL2-style CardSizeClass logic)
        val isTall = widthDp >= 500.dp && heightDp >= 300.dp
        val isRow = widthDp >= 400.dp && !isTall
        // else Compact

        if (isTall) {
            VersionCardTall(state, inst, scope, Modifier.fillMaxSize())
        } else if (isRow) {
            VersionCardRow(state, inst, scope, Modifier.fillMaxSize())
        } else {
            VersionCardCompact(state, inst, scope, Modifier.fillMaxSize())
        }
    }
}

/** Tall layout: icon(72dp) + name + subtitle + loader hint + PLAY button + progress. */
@Composable
fun VersionCardTall(
    state: LauncherState,
    inst: Instance,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    BackgroundCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                InstanceIconZL2(inst, Modifier.size(72.dp))
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

/** Row layout: icon(52dp) + texts + text launch button. */
@Composable
fun VersionCardRow(
    state: LauncherState,
    inst: Instance,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    BackgroundCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            InstanceIconZL2(inst, Modifier.size(52.dp))
            Column(Modifier.weight(1f)) {
                Text(inst.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(inst.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = { state.play() }, enabled = !state.busy) { Text("PLAY") }
        }
    }
}

/** Compact layout: icon + name + small play button. */
@Composable
fun VersionCardCompact(
    state: LauncherState,
    inst: Instance,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    BackgroundCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InstanceIconZL2(inst, Modifier.size(40.dp))
            Text(inst.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Button(onClick = { state.play() }, enabled = !state.busy) { Text("▶") }
        }
    }
}

/** ZL2-style instance icon: colored rounded square (14dp radius) with loader initial. */
@Composable
fun InstanceIconZL2(inst: Instance, modifier: Modifier = Modifier) {
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
    BackgroundCard(modifier) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text("Game log", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
            }
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().height(180.dp),
            ) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(10.dp)) {
                    items(state.log) { line ->
                        Text(line, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkingIndicator(status: String, progress: Float?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (progress != null) {
            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Text(status, style = MaterialTheme.typography.bodyMedium)
    }
}