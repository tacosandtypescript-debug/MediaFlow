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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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

private fun lerpCustomColors(
    a: MediaFlowCustomColors,
    b: MediaFlowCustomColors,
    t: Float,
): MediaFlowCustomColors = MediaFlowCustomColors(
    favorite = lerp(a.favorite, b.favorite, t),
    favoriteBright = lerp(a.favoriteBright, b.favoriteBright, t),
    favoriteInactive = lerp(a.favoriteInactive, b.favoriteInactive, t),
    live = lerp(a.live, b.live, t),
    liveContainer = lerp(a.liveContainer, b.liveContainer, t),
    onLive = lerp(a.onLive, b.onLive, t),
    error = lerp(a.error, b.error, t),
    errorPressed = lerp(a.errorPressed, b.errorPressed, t),
    errorContainer = lerp(a.errorContainer, b.errorContainer, t),
    success = lerp(a.success, b.success, t),
    warning = lerp(a.warning, b.warning, t),
    info = lerp(a.info, b.info, t),
    buffering = lerp(a.buffering, b.buffering, t),
    primaryBright = lerp(a.primaryBright, b.primaryBright, t),
    primaryPressed = lerp(a.primaryPressed, b.primaryPressed, t),
    primaryDark = lerp(a.primaryDark, b.primaryDark, t),
    textTertiary = lerp(a.textTertiary, b.textTertiary, t),
    textDisabled = lerp(a.textDisabled, b.textDisabled, t),
    outlineSoft = lerp(a.outlineSoft, b.outlineSoft, t),
    surfaceElevated = lerp(a.surfaceElevated, b.surfaceElevated, t),
    surfaceSelected = lerp(a.surfaceSelected, b.surfaceSelected, t),
    dialogBackground = lerp(a.dialogBackground, b.dialogBackground, t),
    bottomSheetBackground = lerp(a.bottomSheetBackground, b.bottomSheetBackground, t),
    miniPlayerBackground = lerp(a.miniPlayerBackground, b.miniPlayerBackground, t),
    miniPlayerBorder = lerp(a.miniPlayerBorder, b.miniPlayerBorder, t),
    libraryRow = lerp(a.libraryRow, b.libraryRow, t),
    libraryRowPressed = lerp(a.libraryRowPressed, b.libraryRowPressed, t),
    libraryRowPlaying = lerp(a.libraryRowPlaying, b.libraryRowPlaying, t),
    libraryRowPlayingBorder = lerp(a.libraryRowPlayingBorder, b.libraryRowPlayingBorder, t),
    chipBackground = lerp(a.chipBackground, b.chipBackground, t),
    chipSelectedBackground = lerp(a.chipSelectedBackground, b.chipSelectedBackground, t),
    chipText = lerp(a.chipText, b.chipText, t),
    chipSelectedText = lerp(a.chipSelectedText, b.chipSelectedText, t),
    navigationBackground = lerp(a.navigationBackground, b.navigationBackground, t),
    navigationSelected = lerp(a.navigationSelected, b.navigationSelected, t),
    navigationUnselected = lerp(a.navigationUnselected, b.navigationUnselected, t),
    navigationIndicator = lerp(a.navigationIndicator, b.navigationIndicator, t),
    progressTrack = lerp(a.progressTrack, b.progressTrack, t),
    progressPlayed = lerp(a.progressPlayed, b.progressPlayed, t),
    progressThumb = lerp(a.progressThumb, b.progressThumb, t),
)

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

    val lightScheme = LightColors
    val darkScheme = DarkColors

    val progress by animateFloatAsState(
        targetValue = if (darkTheme) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "themeProgress",
    )
    val colorScheme = lerpSchemes(lightScheme, darkScheme, progress)
    val customColors = lerpCustomColors(LightCustomColors, DarkCustomColors, progress)

    CompositionLocalProvider(LocalMediaFlowColors provides customColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = MediaFlowShapes,
            typography = MediaFlowTypography,
            content = content,
        )
    }
}
