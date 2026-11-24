package launcher.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Fade edge modifier: adds gradient fade at scroll container edges.
 * Draws a gradient overlay at the top/bottom of a scrollable container
 * to indicate more content is available.
 */
fun Modifier.fadeEdge(
    scrollState: androidx.compose.foundation.lazy.LazyListState,
    top: Boolean = true,
    bottom: Boolean = true,
    fadeSize: androidx.compose.ui.unit.Dp = 32.dp,
): Modifier = this.drawBehind {
    val density = this@drawBehind.density
    val fadePx = fadeSize.roundToPx()
    val width = size.width
    val height = size.height

    // Calculate scroll progress to show/hide fade edges
    val maxScroll = scrollState.maxScroll
    val currentScroll = scrollState.firstVisibleItemScrollOffset + scrollState.layoutInfo.visibleItemsInfo.sumOf { it.size * it.index.toFloat() }.toInt()

    // Show top fade if not at top
    val showTop = top && currentScroll > 0
    // Show bottom fade if not at bottom
    val showBottom = bottom && currentScroll < maxScroll

    if (showTop) {
        val gradient = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            ),
            startY = 0f,
            endY = fadePx.toFloat()
        )
        drawRect(
            brush = gradient,
            topLeft = Offset.Zero,
            size = Size(width, fadePx.toFloat())
        )
    }

    if (showBottom) {
        val gradient = Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                Color.Transparent
            ),
            startY = height - fadePx.toFloat(),
            endY = height
        )
        drawRect(
            brush = gradient,
            topLeft = Offset(0f, height - fadePx.toFloat()),
            size = Size(width, fadePx.toFloat())
        )
    }
}