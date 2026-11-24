package dev.lunikodevelopment.ludoform.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily

// Board colours override the original dark concept tokens, as specified in BUILD-PROMPT.
private val DarkColors =
    darkColorScheme(
        primary = Color(0xFFCDB6FF), onPrimary = Color(0xFF2A1260),
        primaryContainer = Color(0xFF4A3880), onPrimaryContainer = Color(0xFFEEE7F4),
        tertiary = Color(0xFFF5A576), onTertiary = Color(0xFF4A1F06),
        background = Color(0xFF16131B), onBackground = Color(0xFFEEE7F4),
        surface = Color(0xFF1F1B24), onSurface = Color(0xFFEEE7F4),
        surfaceVariant = Color(0xFF35303D), onSurfaceVariant = Color(0xFFC9C1D3),
        surfaceContainer = Color(0xFF2A2631), error = Color(0xFFF2B8B5),
    )

private val LightColors =
    lightColorScheme(
        primary = Color(0xFF6750A4),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFEADDFF),
        onPrimaryContainer = Color(0xFF21005D),
        secondary = Color(0xFF625B71),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE8DEF8),
        onSecondaryContainer = Color(0xFF1D192B),
        tertiary = Color(0xFF874C2D),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFDBCA),
        onTertiaryContainer = Color(0xFF321203),
        error = Color(0xFFB3261E),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
        background = Color(0xFFFEF7FF),
        onBackground = Color(0xFF1D1B20),
        surface = Color(0xFFFEF7FF),
        onSurface = Color(0xFF1D1B20),
        surfaceVariant = Color(0xFFE7E0EC),
        onSurfaceVariant = Color(0xFF49454F),
        outline = Color(0xFF79747E),
        outlineVariant = Color(0xFFCAC4D0),
        inverseSurface = Color(0xFF322F35),
        inverseOnSurface = Color(0xFFF5EFF7),
        inversePrimary = Color(0xFFD0BCFF),
        surfaceTint = Color(0xFF6750A4),
        scrim = Color(0xFF000000),
        surfaceDim = Color(0xFFDED8E1),
        surfaceBright = Color(0xFFFEF7FF),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF7F2FA),
        surfaceContainer = Color(0xFFF3EDF7),
        surfaceContainerHigh = Color(0xFFECE6F0),
        surfaceContainerHighest = Color(0xFFE6E0E9),
    )

private val RobotoFlex = FontFamily(Font(R.font.roboto_flex))
private val DefaultType = Typography()
private val LudoformTypography =
    Typography(
        displayLarge = DefaultType.displayLarge.copy(fontFamily = RobotoFlex),
        displayMedium = DefaultType.displayMedium.copy(fontFamily = RobotoFlex),
        displaySmall = DefaultType.displaySmall.copy(fontFamily = RobotoFlex),
        headlineLarge = DefaultType.headlineLarge.copy(fontFamily = RobotoFlex),
        headlineMedium = DefaultType.headlineMedium.copy(fontFamily = RobotoFlex),
        headlineSmall = DefaultType.headlineSmall.copy(fontFamily = RobotoFlex),
        titleLarge = DefaultType.titleLarge.copy(fontFamily = RobotoFlex),
        titleMedium = DefaultType.titleMedium.copy(fontFamily = RobotoFlex),
        titleSmall = DefaultType.titleSmall.copy(fontFamily = RobotoFlex),
        bodyLarge = DefaultType.bodyLarge.copy(fontFamily = RobotoFlex),
        bodyMedium = DefaultType.bodyMedium.copy(fontFamily = RobotoFlex),
        bodySmall = DefaultType.bodySmall.copy(fontFamily = RobotoFlex),
        labelLarge = DefaultType.labelLarge.copy(fontFamily = RobotoFlex),
        labelMedium = DefaultType.labelMedium.copy(fontFamily = RobotoFlex),
        labelSmall = DefaultType.labelSmall.copy(fontFamily = RobotoFlex),
    )

@Composable
fun LudoformTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = LudoformTypography, content = content)
}
