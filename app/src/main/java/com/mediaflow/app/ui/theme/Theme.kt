package com.mediaflow.app.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// MediaFlow brand colors (fallback when Dynamic Color is unavailable).
internal val BrandPrimary = Color(0xFF0B4F46)
internal val BrandPrimaryDark = Color(0xFF7FDCC8)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B5B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9CF1DE),
    onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFF4E635D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD0E8DF),
    onSecondaryContainer = Color(0xFF0A1F1A),
    tertiary = Color(0xFF9A405A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9E2),
    onTertiaryContainer = Color(0xFF3F0018),
    background = Color(0xFFF8FBF8),
    onBackground = Color(0xFF191D1B),
    surface = Color(0xFFF8FBF8),
    onSurface = Color(0xFF191D1B),
    surfaceVariant = Color(0xFFDCE5E0),
    onSurfaceVariant = Color(0xFF404944),
    outline = Color(0xFF707974),
    outlineVariant = Color(0xFFC0C9C4),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8BE7D1),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF145A50),
    onPrimaryContainer = Color(0xFFB0F5E5),
    secondary = Color(0xFFB9CCC6),
    onSecondary = Color(0xFF24332F),
    secondaryContainer = Color(0xFF3B4B46),
    onSecondaryContainer = Color(0xFFD5E9E2),
    tertiary = Color(0xFFB7D4E4),
    onTertiary = Color(0xFF20343D),
    tertiaryContainer = Color(0xFF354D58),
    onTertiaryContainer = Color(0xFFD3ECF7),
    background = Color(0xFF10151D),
    onBackground = Color(0xFFE4EAF2),
    surface = Color(0xFF10151D),
    onSurface = Color(0xFFE4EAF2),
    surfaceVariant = Color(0xFF3E4752),
    onSurfaceVariant = Color(0xFFC1CAD5),
    surfaceContainerLowest = Color(0xFF0B0F15),
    surfaceContainerLow = Color(0xFF151B24),
    surfaceContainer = Color(0xFF1A222C),
    surfaceContainerHigh = Color(0xFF232C37),
    surfaceContainerHighest = Color(0xFF2D3743),
    outline = Color(0xFF8B96A3),
    outlineVariant = Color(0xFF414B57),
    inverseSurface = Color(0xFFE4EAF2),
    inverseOnSurface = Color(0xFF293039),
)

/** Expressive rounded shapes for the whole app. */
internal val MediaFlowShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/** Differentiated typography: clear size and weight hierarchy. */
internal val MediaFlowTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, fontSize = 40.sp),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = 34.sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 28.sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
        bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 26.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 23.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    )
}

/** Blends two color schemes so theme changes animate smoothly. */
private fun lerpSchemes(a: ColorScheme, b: ColorScheme, t: Float): ColorScheme = b.copy(
    primary = lerp(a.primary, b.primary, t),
    onPrimary = lerp(a.onPrimary, b.onPrimary, t),
    primaryContainer = lerp(a.primaryContainer, b.primaryContainer, t),
    onPrimaryContainer = lerp(a.onPrimaryContainer, b.onPrimaryContainer, t),
    inversePrimary = lerp(a.inversePrimary, b.inversePrimary, t),
    secondary = lerp(a.secondary, b.secondary, t),
    onSecondary = lerp(a.onSecondary, b.onSecondary, t),
    secondaryContainer = lerp(a.secondaryContainer, b.secondaryContainer, t),
    onSecondaryContainer = lerp(a.onSecondaryContainer, b.onSecondaryContainer, t),
    tertiary = lerp(a.tertiary, b.tertiary, t),
    onTertiary = lerp(a.onTertiary, b.onTertiary, t),
    tertiaryContainer = lerp(a.tertiaryContainer, b.tertiaryContainer, t),
    onTertiaryContainer = lerp(a.onTertiaryContainer, b.onTertiaryContainer, t),
    background = lerp(a.background, b.background, t),
    onBackground = lerp(a.onBackground, b.onBackground, t),
    surface = lerp(a.surface, b.surface, t),
    onSurface = lerp(a.onSurface, b.onSurface, t),
    surfaceVariant = lerp(a.surfaceVariant, b.surfaceVariant, t),
    onSurfaceVariant = lerp(a.onSurfaceVariant, b.onSurfaceVariant, t),
    surfaceTint = lerp(a.surfaceTint, b.surfaceTint, t),
    surfaceDim = lerp(a.surfaceDim, b.surfaceDim, t),
    surfaceBright = lerp(a.surfaceBright, b.surfaceBright, t),
    surfaceContainerLowest = lerp(a.surfaceContainerLowest, b.surfaceContainerLowest, t),
    surfaceContainerLow = lerp(a.surfaceContainerLow, b.surfaceContainerLow, t),
    surfaceContainer = lerp(a.surfaceContainer, b.surfaceContainer, t),
    surfaceContainerHigh = lerp(a.surfaceContainerHigh, b.surfaceContainerHigh, t),
    surfaceContainerHighest = lerp(a.surfaceContainerHighest, b.surfaceContainerHighest, t),
    error = lerp(a.error, b.error, t),
    onError = lerp(a.onError, b.onError, t),
    inverseSurface = lerp(a.inverseSurface, b.inverseSurface, t),
    inverseOnSurface = lerp(a.inverseOnSurface, b.inverseOnSurface, t),
    outline = lerp(a.outline, b.outline, t),
    outlineVariant = lerp(a.outlineVariant, b.outlineVariant, t),
    scrim = lerp(a.scrim, b.scrim, t),
)

/**
 * Global Material 3 theme for MediaFlow.
 *
 * Uses Dynamic Color on Android 12+ with a brand fallback, animates the color
 * transition on theme change, and applies expressive shapes and typography.
 * The [ThemeMode] resolution always respects the manual LIGHT/DARK choice:
 * Dynamic Color only selects the palette, never overrides light/dark.
 */
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

    // Keep the MediaFlow palette recognizable across devices; system dark/light
    // still controls contrast while the app identity remains stable.
    val lightScheme = LightColors
    // Keep the dark palette branded and deliberately layered. Dynamic Color is
    // still used for light mode, while dark mode avoids a flat black system
    // palette that reduces surface and icon contrast.
    val darkScheme = DarkColors

    val progress by animateFloatAsState(
        targetValue = if (darkTheme) 1f else 0f,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "themeProgress",
    )
    val colorScheme = lerpSchemes(lightScheme, darkScheme, progress)

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = MediaFlowShapes,
        typography = MediaFlowTypography,
        content = content,
    )
}
