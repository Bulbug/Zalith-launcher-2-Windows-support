package launcher.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Accent color presets, ZL2-style multiple themes. */
enum class Accent(val label: String, val hex: String) {
    GREEN("Grass", "0xFF4CAF50"),
    BLUE("Ocean", "0xFF42A5F5"),
    PURPLE("Amethyst", "0xFF9575CD"),
    AMBER("Amber", "0xFFFFB300");

    val color: Color get() = Color(hex.toLong().ushr(32).toInt() or 0xFF000000.toInt())
}

@Composable
fun appColorScheme(dark: Boolean, accent: Accent): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent.color,
        secondary = accent.color.copy(alpha = 0.7f),
        tertiary = accent.color.copy(alpha = 0.5f),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun AppTheme(dark: Boolean, accent: Accent, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = appColorScheme(dark, accent),
        shapes = AppShapes,
        content = content,
    )
}
