package hu.rmz.prefiscore.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

private val Light = lightColorScheme(
    primary = Color(0xFF1F6F4A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB6EFCF),
    onPrimaryContainer = Color(0xFF002112),
    secondary = Color(0xFFB3261E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDAD5),
    onSecondaryContainer = Color(0xFF410001),
    tertiary = Color(0xFF7A5900),
    tertiaryContainer = Color(0xFFFFDF9E),
    onTertiaryContainer = Color(0xFF261A00),
    background = Color(0xFFF6F4EE),
    onBackground = Color(0xFF1D2421),
    surface = Color(0xFFF6F4EE),
    onSurface = Color(0xFF1D2421),
    surfaceVariant = Color(0xFFE2E3DA),
    onSurfaceVariant = Color(0xFF545F58),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBFAF5),
    surfaceContainer = Color(0xFFEFEDE6),
    surfaceContainerHigh = Color(0xFFE9E7E0),
    surfaceContainerHighest = Color(0xFFE3E1DA),
    outline = Color(0xFF8A938D),
    outlineVariant = Color(0xFFDCD8CC),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8FD8B0),
    onPrimary = Color(0xFF003822),
    primaryContainer = Color(0xFF005234),
    onPrimaryContainer = Color(0xFFB6EFCF),
    secondary = Color(0xFFFFB4AA),
    onSecondary = Color(0xFF690004),
    secondaryContainer = Color(0xFF8C1D18),
    onSecondaryContainer = Color(0xFFFFDAD5),
    tertiary = Color(0xFFF0C048),
    tertiaryContainer = Color(0xFF5C4200),
    onTertiaryContainer = Color(0xFFFFDF9E),
    background = Color(0xFF111613),
    onBackground = Color(0xFFE1E5E1),
    surface = Color(0xFF111613),
    onSurface = Color(0xFFE1E5E1),
    surfaceVariant = Color(0xFF3F4943),
    onSurfaceVariant = Color(0xFFBEC9C1),
    surfaceContainerLowest = Color(0xFF0C110E),
    surfaceContainerLow = Color(0xFF19201C),
    surfaceContainer = Color(0xFF1D2420),
    surfaceContainerHigh = Color(0xFF272E2A),
    surfaceContainerHighest = Color(0xFF323935),
    outline = Color(0xFF89938C),
    outlineVariant = Color(0xFF3F4943),
)

@Composable
fun PrefiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

object ScoreColors {
    val positive: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary
    val negative: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.secondary

    @Composable
    @ReadOnlyComposable
    fun of(v: Int): Color = when {
        v > 0 -> positive
        v < 0 -> negative
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/** Material ikonok útvonalai (az ikonkönyvtár függősége nélkül). */
object AppIcons {
    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(PathParser().parsePathString(path).toNodes(), fill = androidx.compose.ui.graphics.SolidColor(Color.Black))
            .build()

    val Back = icon("back", "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z")
    val More = icon("more", "M12,8c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM12,16c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z")
    val Undo = icon("undo", "M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,7v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88 3.54,0 6.55,2.31 7.6,5.5l2.37,-0.78C21.08,11.03 17.15,8 12.5,8z")
    val Redo = icon("redo", "M18.4,10.6C16.55,8.99 14.15,8 11.5,8c-4.65,0 -8.58,3.03 -9.96,7.22L3.9,16c1.05,-3.19 4.05,-5.5 7.6,-5.5 1.95,0 3.73,0.72 5.12,1.88L13,16h9V7l-3.6,3.6z")
    val Up = icon("up", "M7.41,15.41L12,10.83l4.59,4.58L18,14l-6,-6 -6,6z")
    val Down = icon("down", "M7.41,8.59L12,13.17l4.59,-4.58L18,10l-6,6 -6,-6 1.41,-1.41z")
    val Close = icon("close", "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z")
    val Play = icon("play", "M8,5v14l11,-7z")
    val Cards = icon("cards", "M21.47,4.35l-1.34,-0.56v9.03l2.43,-5.86c0.41,-1.02 -0.06,-2.19 -1.09,-2.61zM1.97,8.05l4.96,11.96c0.31,0.76 1.05,1.22 1.83,1.23 0.26,0 0.53,-0.05 0.78,-0.15l7.37,-3.05c0.75,-0.31 1.21,-1.05 1.23,-1.79 0.01,-0.26 -0.04,-0.55 -0.13,-0.81L13,3.5c-0.3,-0.77 -1.05,-1.23 -1.84,-1.24 -0.26,0 -0.52,0.06 -0.77,0.15L3.03,5.46c-1.02,0.42 -1.51,1.59 -1.06,2.59zM18.12,4.25c0,-1.1 -0.9,-2 -2,-2h-1.45l3.45,8.34v-6.34z")
    val Scores = icon("scores", "M5,9.2h3V19H5zM10.6,5h2.8v14h-2.8zM16.2,13H19v6h-2.8z")
    val List = icon("list", "M3,13h2v-2H3v2zm0,4h2v-2H3v2zm0,-8h2V7H3v2zm4,4h14v-2H7v2zm0,4h14v-2H7v2zM7,7v2h14V7H7z")
}
