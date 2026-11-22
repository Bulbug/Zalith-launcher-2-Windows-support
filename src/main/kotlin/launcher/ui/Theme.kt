package launcher.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ==================== ZL2 COLOR THEMES (exact hex from ZL2 Color.kt) ====================

/** The 7 built-in ZL2 color themes, each with light & dark palettes. */
enum class ColorTheme(val displayName: String) {
    EMBERMIRE("Embermire"),
    VELVET_ROSE("Velvet Rose"),
    MISTWAVE("Mistwave"),
    GLACIER("Glacier"),
    VERDANT_FIELD("Verdant Field"),
    URBAN_ASH("Urban Ash"),
    VERDANT_DAWN("Verdant Dawn");

    // Light palettes
    val lightPrimary: Color
        get() = when (this) {
            EMBERMIRE -> Color(0xFFA63A17)
            VELVET_ROSE -> Color(0xFF723D57)
            MISTWAVE -> Color(0xFF426469)
            GLACIER -> Color(0xFF006684)
            VERDANT_FIELD -> Color(0xFF676014)
            URBAN_ASH -> Color(0xFF5E5E5F)
            VERDANT_DAWN -> Color(0xFF004814)
        }
    val lightSecondary: Color
        get() = when (this) {
            EMBERMIRE -> Color(0xFFC95A33)
            VELVET_ROSE -> Color(0xFF9C5C7C)
            MISTWAVE -> Color(0xFF5E8A8F)
            GLACIER -> Color(0xFF1E88A8)
            VERDANT_FIELD -> Color(0xFF8A832A)
            URBAN_ASH -> Color(0xFF7E7E7F)
            VERDANT_DAWN -> Color(0xFF1A6B2E)
        }
    val lightBackground: Color
        get() = when (this) {
            EMBERMIRE -> Color(0xFFFFF8F6)
            VELVET_ROSE -> Color(0xFFFFF8F8)
            MISTWAVE -> Color(0xFFFAF9F9)
            GLACIER -> Color(0xFFF6FAFD)
            VERDANT_FIELD -> Color(0xFFFEF9EB)
            URBAN_ASH -> Color(0xFFFCF8F8)
            VERDANT_DAWN -> Color(0xFFF7FBF2)
        }
    val lightSurface: Color
        get() = when (this) {
            EMBERMIRE -> Color(0xFFFFF8F6)
            VELVET_ROSE -> Color(0xFFFFF8F8)
            MISTWAVE -> Color(0xFFFAF9F9)
            GLACIER -> Color(0xFFF6FAFD)
            VERDANT_FIELD -> Color(0xFFFEF9EB)
            URBAN_ASH -> Color(0xFFFCF8F8)
            VERDANT_DAWN -> Color(0xFFF7FBF2)
        }
    val lightCard: Color
        get() = when (this) {
            EMBERMIRE -> Color(0xFFFFF0EB)
            VELVET_ROSE -> Color(0xFFFFF0F0)
            MISTWAVE -> Color(0xFFF5F4F4)
            GLACIER -> Color(0xFFEBF5FA)
            VERDANT_FIELD -> Color(0xFFFDF6E0)
            URBAN_ASH -> Color(0xFFF5F0F0)
            VERDANT_DAWN -> Color(0xFFEEF8EE)
        }

    // Dark palettes
    val darkPrimary: Color
        get() = when (this) {
            EMBERMIRE -> Color(0xFFFFB59F)
            VELVET_ROSE -> Color(0xFFF9B2D2)
            MISTWAVE -> Color(0xFFCEF3F9)
            GLACIER -> Color(0xFF73D2FB)
            VERDANT_FIELD -> Color(0xFFD2C972)
            URBAN_ASH -> Color(0xFFC7C6C6)
            VERDANT_DAWN -> Color(0xFF8ED88E)
        }
    val darkSecondary: Color
        get() = when (this) {
            EMBERMIRE -> Color(0xFFFF9A7A)
            VELVET_ROSE -> Color(0xFFF598BE)
            MISTWAVE -> Color(0xFFB0DADF)
            GLACIER -> Color(0xFF5AB9E8)
            VERDANT_FIELD -> Color(0xFFB8AF5A)
            URBAN_ASH -> Color(0xFFADACAD)
            VERDANT_DAWN -> Color(0xFF72C072)
        }
    val darkBackground: Color
        get() = when (this) {
            EMBERMIRE -> Color(0xFF1C110E)
            VELVET_ROSE -> Color(0xFF171214)
            MISTWAVE -> Color(0xFF121414)
            GLACIER -> Color(0xFF101416)
            VERDANT_FIELD -> Color(0xFF15140C)
            URBAN_ASH -> Color(0xFF141313)
            VERDANT_DAWN -> Color(0xFF101410)
        }
    val darkSurface: Color
        get() = when (this) {
            EMBERMIRE -> Color(0xFF2A1D18)
            VELVET_ROSE -> Color(0xFF241B1E)
            MISTWAVE -> Color(0xFF1E2121)
            GLACIER -> Color(0xFF1A1F21)
            VERDANT_FIELD -> Color(0xFF211F15)
            URBAN_ASH -> Color(0xFF1E1D1D)
            VERDANT_DAWN -> Color(0xFF1A1D1A)
        }
    val darkCard: Color
        get() = when (this) {
            EMBERMIRE -> Color(0xFF352520)
            VELVET_ROSE -> Color(0xFF2D2226)
            MISTWAVE -> Color(0xFF262A2A)
            GLACIER -> Color(0xFF22272A)
            VERDANT_FIELD -> Color(0xFF2A271D)
            URBAN_ASH -> Color(0xFF272525)
            VERDANT_DAWN -> Color(0xFF232722)
        }

    fun lightScheme(): ColorScheme = lightColorScheme(
        primary = lightPrimary,
        secondary = lightSecondary,
        background = lightBackground,
        surface = lightSurface,
        surfaceContainerLow = lightCard,
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color(0xFF1C110E),
        onSurface = Color(0xFF1C110E),
    )

    fun darkScheme(): ColorScheme = darkColorScheme(
        primary = darkPrimary,
        secondary = darkSecondary,
        background = darkBackground,
        surface = darkSurface,
        surfaceContainerLow = darkCard,
        onPrimary = Color(0xFF1C110E),
        onSecondary = Color(0xFF171214),
        onBackground = Color(0xFFFFF8F6),
        onSurface = Color(0xFFFFF8F6),
    )
}

enum class ThemeMode { LIGHT, DARK }

/** Custom shapes matching ZL2 specs. */
val ZL2Shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val TOP_BAR_HEIGHT = 40.dp
val RAIL_ITEM_SELECTED_HORIZONTAL_PADDING = 16.dp
val RAIL_ITEM_SELECTED_VERTICAL_PADDING = 4.dp
val RAIL_ITEM_UNSELECTED_PADDING = 8.dp
val TASK_MENU_WIDTH = 120.dp
val CARD_CORNER_RADIUS = 16.dp
val GLASS_HIGHLIGHT_BORDER = 0.5.dp

@Composable
fun glassBackground(
    modifier: Modifier = Modifier,
    isDark: Boolean,
): Modifier = modifier.then(
    androidx.compose.ui.Modifier.drawBehind {
        val cardColor = if (isDark) Color(0xCC2A1D18) else Color(0xCCFFF0EB)
        drawRect(
            color = cardColor,
            topLeft = Offset.Zero,
            size = Size(size.width, size.height),
            style = androidx.compose.ui.graphics.drawscope.Fill
        )
        drawRect(
            color = if (isDark) Color(0x33FFFFFF) else Color(0x33000000),
            topLeft = Offset.Zero,
            size = Size(size.width, GLASS_HIGHLIGHT_BORDER.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Fill
        )
        drawRect(
            color = if (isDark) Color(0x33FFFFFF) else Color(0x33000000),
            topLeft = Offset.Zero,
            size = Size(GLASS_HIGHLIGHT_BORDER.toPx(), size.height),
            style = androidx.compose.ui.graphics.drawscope.Fill
        )
    }
)

@Composable
fun AppTheme(
    isDark: Boolean,
    colorTheme: ColorTheme,
    content: @Composable () -> Unit,
) {
    val scheme = if (isDark) colorTheme.darkScheme() else colorTheme.lightScheme()
    MaterialTheme(
        colorScheme = scheme,
        shapes = ZL2Shapes,
        content = content,
    )
}

@Composable
fun AppTheme(
    isDark: ThemeMode,
    colorTheme: ColorTheme,
    content: @Composable () -> Unit,
) {
    val scheme = if (isDark == ThemeMode.DARK) colorTheme.darkScheme() else colorTheme.lightScheme()
    MaterialTheme(
        colorScheme = scheme,
        shapes = ZL2Shapes,
        content = content,
    )
}

@Composable
fun rememberAppTheme(): AppThemeState {
    val isDark by remember { mutableStateOf(true) }
    val colorTheme by remember { mutableStateOf(ColorTheme.VERDANT_DAWN) } // default = Verdant Dawn
    return AppThemeState(isDark, colorTheme)
}

data class AppThemeState(
    var isDark: Boolean,
    var colorTheme: ColorTheme,
)