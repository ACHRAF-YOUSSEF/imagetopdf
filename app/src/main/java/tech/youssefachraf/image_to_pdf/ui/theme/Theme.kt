package tech.youssefachraf.image_to_pdf.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppDarkColorScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    primaryContainer = AccentBlue,
    onPrimaryContainer = Color.White,
    secondary = AccentBlue,
    onSecondary = Color.White,
    secondaryContainer = AccentBlue.copy(alpha = 0.2f),
    onSecondaryContainer = AccentBlue,
    background = DarkBackground,
    onBackground = PrimaryText,
    surface = DarkSurface,
    onSurface = PrimaryText,
    surfaceVariant = DarkCard,
    onSurfaceVariant = SecondaryText,
    outline = DarkBorder,
    error = PdfBadgeRed,
    onError = Color.White,
    inverseSurface = PrimaryText,
    inverseOnSurface = DarkBackground,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = Color(0xFF0F1419),
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = Color(0xFF1C2128),
    surfaceContainerHighest = Color(0xFF21262D),
)

@Composable
fun ImagetopdfTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AppDarkColorScheme,
        typography = Typography,
        content = content
    )
}