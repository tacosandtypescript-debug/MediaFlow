package com.mediaflow.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// -------------------------------------------------------------------------
// MediaFlow Canonical Palette Tokens
// -------------------------------------------------------------------------

// Base Oscura
val BackgroundDark = Color(0xFF0B0E15)
val SurfaceDark = Color(0xFF121621)
val SurfaceVariantDark = Color(0xFF1A1F2C)
val SurfaceElevatedDark = Color(0xFF202635)
val SurfaceSelectedDark = Color(0xFF322E4C)
val OutlineDark = Color(0xFF303747)
val OutlineSoftDark = Color(0xFF242A38)

// Morado Principal
val PrimaryPurple = Color(0xFF7C3AED)
val PrimaryBrightPurple = Color(0xFF8B5CF6)
val PrimaryPressedPurple = Color(0xFF6B3CD0)
val PrimaryDarkPurple = Color(0xFF5C2F9E)
val PrimaryContainerPurple = Color(0xFF322E4C)
val OnPrimaryPurple = Color(0xFFFFFFFF)

// Texto
val TextPrimaryDark = Color(0xFFF5F3F7)
val TextSecondaryDark = Color(0xFFA7A5AF)
val TextTertiaryDark = Color(0xFF777985)
val TextDisabledDark = Color(0xFF585B66)

// Favoritos
val FavoritePink = Color(0xFFD95B9B)
val FavoriteBrightPink = Color(0xFFEC6BAE)
val FavoriteInactiveDark = Color(0xFF8B8D98)

// X Spaces LIVE
val LiveRed = Color(0xFFF04455)
val LiveContainerDark = Color(0xFF542C2E)
val OnLiveColor = Color(0xFFFFFFFF)

// Eliminar / Errores
val ErrorRed = Color(0xFFEF4444)
val ErrorPressedRed = Color(0xFFC9363E)
val ErrorContainerDark = Color(0xFF3B1D23)
val OnErrorColor = Color(0xFFFFFFFF)

// Estados
val SuccessGreen = Color(0xFF4ADE80)
val WarningYellow = Color(0xFFF5B942)
val InfoBlue = Color(0xFF60A5FA)
val BufferingPurple = Color(0xFFA78BFA)

// Player Tokens
val PlayerBackgroundDark = Color(0xFF0B0E15)
val PlayerControlDark = Color(0xFFF5F3F7)
val PlayerControlSecondaryDark = Color(0xFFA7A5AF)
val ProgressTrackDark = Color(0xFF343947)
val ProgressPlayedPurple = Color(0xFF8B5CF6)
val ProgressThumbPurple = Color(0xFF8B5CF6)
val MiniPlayerBackgroundDark = Color(0xFF1A1825)
val MiniPlayerBorderDark = Color(0xFF403553)

// Biblioteca Tokens
val LibraryRowDark = Color(0xFF121621)
val LibraryRowPressedDark = Color(0xFF1A1F2C)
val LibraryRowPlayingDark = Color(0xFF211A32)
val LibraryRowPlayingBorderDark = Color(0xFF7345D8)
val ChipBackgroundDark = Color(0xFF1A1F2C)
val ChipSelectedBackgroundDark = Color(0xFF7C3AED)
val ChipTextDark = Color(0xFFA7A5AF)
val ChipSelectedTextDark = Color(0xFFFFFFFF)

// Bottom Navigation Tokens
val NavigationBackgroundDark = Color(0xFF0D111A)
val NavigationSelectedPurple = Color(0xFF8B5CF6)
val NavigationUnselectedDark = Color(0xFF777985)
val NavigationIndicatorDark = Color(0xFF2A203D)

// Diálogos y Bottom Sheets
val DialogBackgroundDark = Color(0xFF171B26)
val BottomSheetBackgroundDark = Color(0xFF141822)
val ScrimColor = Color(0x99000000) // 60% alpha

// -------------------------------------------------------------------------
// Light Mode Tokens
// -------------------------------------------------------------------------
val BackgroundLight = Color(0xFFF7F7FA)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFF0EEF5)
val OutlineLight = Color(0xFFDDD9E5)
val OutlineSoftLight = Color(0xFFE5E2EC)
val TextPrimaryLight = Color(0xFF17141D)
val TextSecondaryLight = Color(0xFF66616E)
val TextTertiaryLight = Color(0xFF8D8896)
val TextDisabledLight = Color(0xFFB5B1BE)
val PrimaryLight = Color(0xFF7138D8)
val PrimaryBrightLight = Color(0xFF8B5CF6)
val PrimaryPressedLight = Color(0xFF5C2F9E)
val PrimaryContainerLight = Color(0xFFEEE7FF)
val OnPrimaryLight = Color(0xFFFFFFFF)
val FavoriteLight = Color(0xFFD95B9B)
val FavoriteBrightLight = Color(0xFFEC6BAE)
val FavoriteInactiveLight = Color(0xFF9E9AA6)
val LiveLight = Color(0xFFF04455)
val LiveContainerLight = Color(0xFFFFDAD9)
val MiniPlayerBackgroundLight = Color(0xFFF3EDFD)
val MiniPlayerBorderLight = Color(0xFFD5C7F2)
val LibraryRowLight = Color(0xFFFFFFFF)
val LibraryRowPressedLight = Color(0xFFF0EEF5)
val LibraryRowPlayingLight = Color(0xFFEDE5FF)
val LibraryRowPlayingBorderLight = Color(0xFF8B5CF6)
val ChipBackgroundLight = Color(0xFFF0EEF5)
val ChipSelectedBackgroundLight = Color(0xFF7138D8)
val ChipTextLight = Color(0xFF66616E)
val ChipSelectedTextLight = Color(0xFFFFFFFF)
val NavigationBackgroundLight = Color(0xFFFAF9FD)
val NavigationSelectedLight = Color(0xFF7138D8)
val NavigationUnselectedLight = Color(0xFF8D8896)
val NavigationIndicatorLight = Color(0xFFE8DEFF)
val DialogBackgroundLight = Color(0xFFFFFFFF)
val BottomSheetBackgroundLight = Color(0xFFFFFFFF)

// -------------------------------------------------------------------------
// Extended Semantic Custom Colors for MediaFlow
// -------------------------------------------------------------------------
@Immutable
data class MediaFlowCustomColors(
    val favorite: Color,
    val favoriteBright: Color,
    val favoriteInactive: Color,
    val live: Color,
    val liveContainer: Color,
    val onLive: Color,
    val error: Color,
    val errorPressed: Color,
    val errorContainer: Color,
    val success: Color,
    val warning: Color,
    val info: Color,
    val buffering: Color,
    val primaryBright: Color,
    val primaryPressed: Color,
    val primaryDark: Color,
    val textTertiary: Color,
    val textDisabled: Color,
    val outlineSoft: Color,
    val surfaceElevated: Color,
    val surfaceSelected: Color,
    val dialogBackground: Color,
    val bottomSheetBackground: Color,
    val miniPlayerBackground: Color,
    val miniPlayerBorder: Color,
    val libraryRow: Color,
    val libraryRowPressed: Color,
    val libraryRowPlaying: Color,
    val libraryRowPlayingBorder: Color,
    val chipBackground: Color,
    val chipSelectedBackground: Color,
    val chipText: Color,
    val chipSelectedText: Color,
    val navigationBackground: Color,
    val navigationSelected: Color,
    val navigationUnselected: Color,
    val navigationIndicator: Color,
    val progressTrack: Color,
    val progressPlayed: Color,
    val progressThumb: Color,
) {
    val primaryGradient: Brush
        get() = Brush.linearGradient(listOf(primaryBright, primaryPressed))

    val favoriteGradient: Brush
        get() = Brush.linearGradient(listOf(favoriteBright, favorite))
}

val LocalMediaFlowColors = staticCompositionLocalOf {
    DarkCustomColors
}

val DarkCustomColors = MediaFlowCustomColors(
    favorite = FavoritePink,
    favoriteBright = FavoriteBrightPink,
    favoriteInactive = FavoriteInactiveDark,
    live = LiveRed,
    liveContainer = LiveContainerDark,
    onLive = OnLiveColor,
    error = ErrorRed,
    errorPressed = ErrorPressedRed,
    errorContainer = ErrorContainerDark,
    success = SuccessGreen,
    warning = WarningYellow,
    info = InfoBlue,
    buffering = BufferingPurple,
    primaryBright = PrimaryBrightPurple,
    primaryPressed = PrimaryPressedPurple,
    primaryDark = PrimaryDarkPurple,
    textTertiary = TextTertiaryDark,
    textDisabled = TextDisabledDark,
    outlineSoft = OutlineSoftDark,
    surfaceElevated = SurfaceElevatedDark,
    surfaceSelected = SurfaceSelectedDark,
    dialogBackground = DialogBackgroundDark,
    bottomSheetBackground = BottomSheetBackgroundDark,
    miniPlayerBackground = MiniPlayerBackgroundDark,
    miniPlayerBorder = MiniPlayerBorderDark,
    libraryRow = LibraryRowDark,
    libraryRowPressed = LibraryRowPressedDark,
    libraryRowPlaying = LibraryRowPlayingDark,
    libraryRowPlayingBorder = LibraryRowPlayingBorderDark,
    chipBackground = ChipBackgroundDark,
    chipSelectedBackground = ChipSelectedBackgroundDark,
    chipText = ChipTextDark,
    chipSelectedText = ChipSelectedTextDark,
    navigationBackground = NavigationBackgroundDark,
    navigationSelected = NavigationSelectedPurple,
    navigationUnselected = NavigationUnselectedDark,
    navigationIndicator = NavigationIndicatorDark,
    progressTrack = ProgressTrackDark,
    progressPlayed = ProgressPlayedPurple,
    progressThumb = ProgressThumbPurple,
)

val LightCustomColors = MediaFlowCustomColors(
    favorite = FavoriteLight,
    favoriteBright = FavoriteBrightLight,
    favoriteInactive = FavoriteInactiveLight,
    live = LiveLight,
    liveContainer = LiveContainerLight,
    onLive = OnLiveColor,
    error = ErrorRed,
    errorPressed = ErrorPressedRed,
    errorContainer = Color(0xFFFFDAD6),
    success = SuccessGreen,
    warning = WarningYellow,
    info = InfoBlue,
    buffering = BufferingPurple,
    primaryBright = PrimaryBrightLight,
    primaryPressed = PrimaryPressedLight,
    primaryDark = PrimaryPressedLight,
    textTertiary = TextTertiaryLight,
    textDisabled = TextDisabledLight,
    outlineSoft = OutlineSoftLight,
    surfaceElevated = SurfaceLight,
    surfaceSelected = PrimaryContainerLight,
    dialogBackground = DialogBackgroundLight,
    bottomSheetBackground = BottomSheetBackgroundLight,
    miniPlayerBackground = MiniPlayerBackgroundLight,
    miniPlayerBorder = MiniPlayerBorderLight,
    libraryRow = LibraryRowLight,
    libraryRowPressed = LibraryRowPressedLight,
    libraryRowPlaying = LibraryRowPlayingLight,
    libraryRowPlayingBorder = LibraryRowPlayingBorderLight,
    chipBackground = ChipBackgroundLight,
    chipSelectedBackground = ChipSelectedBackgroundLight,
    chipText = ChipTextLight,
    chipSelectedText = ChipSelectedTextLight,
    navigationBackground = NavigationBackgroundLight,
    navigationSelected = NavigationSelectedLight,
    navigationUnselected = NavigationUnselectedLight,
    navigationIndicator = NavigationIndicatorLight,
    progressTrack = OutlineLight,
    progressPlayed = PrimaryBrightLight,
    progressThumb = PrimaryBrightLight,
)
