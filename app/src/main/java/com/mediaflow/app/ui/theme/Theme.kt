package com.mediaflow.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = PrimaryLightCopper,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = Color(0xFF260058),
    inversePrimary = PrimaryBrightLight,
    secondary = PrimaryBrightLight,
    onSecondary = OnPrimaryLight,
    secondaryContainer = Color(0xFFE8DDFF),
    onSecondaryContainer = TextLight,
    tertiary = FavoritePinkLight,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF3B071D),
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Paper,
    surfaceContainer = Sheet,
    surfaceContainerHigh = SheetMuted,
    surfaceContainerHighest = Color(0xFFDDD8CE),
    outline = OutlineLight,
    outlineVariant = OutlineSoftLight,
    error = ErrorRed,
    onError = OnErrorColor,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    scrim = ScrimColor,
)

private val DarkColors = darkColorScheme(
    primary = PrimaryCopper,
    onPrimary = OnPrimaryCopper,
    primaryContainer = PrimaryContainerCopper,
    onPrimaryContainer = TextPrimaryDark,
    inversePrimary = PrimaryBrightCopper,
    secondary = PrimaryBrightCopper,
    onSecondary = OnPrimaryCopper,
    secondaryContainer = PrimaryContainerCopper,
    onSecondaryContainer = TextPrimaryDark,
    tertiary = FavoritePink,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = LiveContainerDark,
    onTertiaryContainer = Color(0xFFFFD9E2),
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = Color(0xFF07090E),
    surfaceContainerLow = Color(0xFF101820),
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = SurfaceElevatedDark,
    surfaceContainerHighest = SurfaceSelectedDark,
    outline = OutlineDark,
    outlineVariant = OutlineSoftDark,
    error = ErrorRed,
    onError = OnErrorColor,
    errorContainer = ErrorContainerDark,
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = TextPrimaryDark,
    inverseOnSurface = BackgroundDark,
    scrim = ScrimColor,
)

internal val MediaFlowShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

internal val MediaFlowTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
        bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
        labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium, fontSize = 13.sp),
        labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp),
    )
}

val MaterialTheme.customColors: MediaFlowCustomColors
    @Composable
    @ReadOnlyComposable
    get() = LocalMediaFlowColors.current

@Composable
fun MediaFlowTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (darkTheme) DarkColors else LightColors
    val customColors = if (darkTheme) DarkCustomColors else LightCustomColors

    CompositionLocalProvider(LocalMediaFlowColors provides customColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = MediaFlowShapes,
            typography = MediaFlowTypography,
            content = content,
        )
    }
}
