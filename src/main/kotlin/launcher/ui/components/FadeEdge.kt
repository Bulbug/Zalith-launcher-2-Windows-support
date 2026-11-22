package launcher.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Fade edge modifier: adds gradient fade at scroll container edges.
 * Simplified version - just a no-op for now.
 */
fun Modifier.fadeEdge(
    scrollState: androidx.compose.foundation.lazy.LazyListState,
    top: Boolean = true,
    bottom: Boolean = true,
): Modifier = this