package com.mediaflow.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// -------------------------------------------------------------------------
// MediaFlow — paleta morada original
// -------------------------------------------------------------------------

val Ink = Color(0xFF0B0E15)
val Panel = Color(0xFF121621)
val PanelRaised = Color(0xFF1A1F2C)
val PrimaryPurple = Color(0xFF7C3AED)
val OnPrimaryPurple = Color(0xFFFFFFFF)
val PrimaryBrightPurple = Color(0xFF8B5CF6)
val PrimaryPressedPurple = Color(0xFF6B3CD0)
val TextDark = Color(0xFFF5F3F7)
val TextMutedDark = Color(0xFFA7A5AF)
val LineDark = Color(0xFF303747)

val Paper = Color(0xFFF7F7FA)
val Sheet = Color(0xFFFFFFFF)
val SheetMuted = Color(0xFFF0EEF5)
val PrimaryLight = Color(0xFF7138D8)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryBrightLight = Color(0xFF8B5CF6)
val TextLight = Color(0xFF17141D)
val TextMutedLight = Color(0xFF66616E)
val LineLight = Color(0xFFDDD9E5)

val CopperDark = PrimaryPurple
val OnCopperDark = OnPrimaryPurple
val CopperBrightDark = PrimaryBrightPurple
val CopperPressedDark = PrimaryPressedPurple
val CopperLight = PrimaryLight
val OnCopperLight = OnPrimaryLight
val CopperBrightLight = PrimaryBrightLight

val LiveRed = Color(0xFFF04455)
val OnLiveColor = Color(0xFFFFFFFF)
val FavoritePink = Color(0xFFD95B9B)
val FavoritePinkLight = Color(0xFFD95B9B)
val FavoriteBrightPink = Color(0xFFEC6BAE)
val ErrorRed = Color(0xFFE25555)
val ErrorPressedRed = Color(0xFFC44545)
val OnErrorColor = Color(0xFFFFFFFF)
val SuccessGreen = Color(0xFF3D9B7A)
val WarningYellow = Color(0xFFF5B942)
val InfoBlue = Color(0xFF60A5FA)
val ScrimColor = Color(0x99000000)

val BackgroundDark = Ink
val SurfaceDark = Panel
val SurfaceVariantDark = PanelRaised
val SurfaceElevatedDark = PanelRaised
val SurfaceSelectedDark = Color(0xFF322E4C)
val OutlineDark = LineDark
val OutlineSoftDark = Color(0xFF222C3A)
val TextPrimaryDark = TextDark
val TextSecondaryDark = TextMutedDark
val TextTertiaryDark = Color(0xFF7C8594)
val TextDisabledDark = Color(0xFF5C6573)
val FavoriteInactiveDark = Color(0xFF8B8D98)
val LiveContainerDark = Color(0xFF542C2E)
val ErrorContainerDark = Color(0xFF3B1D23)
val PrimaryCopper = PrimaryPurple
val PrimaryBrightCopper = PrimaryBrightPurple
val PrimaryPressedCopper = PrimaryPressedPurple
val PrimaryContainerCopper = Color(0xFF322E4C)
val OnPrimaryCopper = OnPrimaryPurple
val ProgressTrackDark = LineDark
val MiniPlayerBackgroundDark = Color(0xFF1A1825)
val MiniPlayerBorderDark = Color(0xFF403553)
val LibraryRowDark = Panel
val LibraryRowPressedDark = PanelRaised
val LibraryRowPlayingDark = Color(0xFF211A32)
val LibraryRowPlayingBorderDark = Color(0xFF7345D8)
val ChipBackgroundDark = PanelRaised
val ChipSelectedBackgroundDark = PrimaryPurple
val ChipTextDark = TextMutedDark
val ChipSelectedTextDark = OnPrimaryPurple
val NavigationBackgroundDark = Color(0xFF0D111A)
val NavigationSelectedCopper = PrimaryBrightPurple
val NavigationUnselectedDark = TextTertiaryDark
val NavigationIndicatorDark = Color(0xFF2A203D)
val DialogBackgroundDark = PanelRaised
val BottomSheetBackgroundDark = PanelRaised

val BackgroundLight = Paper
val SurfaceLight = Sheet
val SurfaceVariantLight = SheetMuted
val OutlineLight = LineLight
val OutlineSoftLight = Color(0xFFE4DFD6)
val TextPrimaryLight = TextLight
val TextSecondaryLight = TextMutedLight
val TextTertiaryLight = Color(0xFF7A8290)
val TextDisabledLight = Color(0xFFA8AEB8)
val PrimaryLightCopper = PrimaryLight
val PrimaryContainerLight = Color(0xFFEEE7FF)
val FavoriteInactiveLight = Color(0xFF9E9AA6)
val LiveContainerLight = Color(0xFFFFDAD9)
val MiniPlayerBackgroundLight = Color(0xFFF3EDFD)
val MiniPlayerBorderLight = Color(0xFFD5C7F2)
val LibraryRowLight = Sheet
val LibraryRowPressedLight = SheetMuted
val LibraryRowPlayingLight = Color(0xFFEDE5FF)
val LibraryRowPlayingBorderLight = PrimaryBrightPurple
val ChipBackgroundLight = SheetMuted
val ChipSelectedBackgroundLight = PrimaryLight
val ChipTextLight = TextMutedLight
val ChipSelectedTextLight = OnPrimaryLight
val NavigationBackgroundLight = Color(0xFFFAF9FD)
val NavigationSelectedLight = PrimaryLight
val NavigationUnselectedLight = TextTertiaryLight
val NavigationIndicatorLight = Color(0xFFE8DEFF)
val DialogBackgroundLight = Sheet
val BottomSheetBackgroundLight = Sheet

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
    buffering = CopperDark.copy(alpha = 0.6f),
    primaryBright = PrimaryBrightCopper,
    primaryPressed = PrimaryPressedCopper,
    primaryDark = PrimaryPressedCopper,
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
    navigationSelected = NavigationSelectedCopper,
    navigationUnselected = NavigationUnselectedDark,
    navigationIndicator = NavigationIndicatorDark,
    progressTrack = ProgressTrackDark,
    progressPlayed = CopperDark,
    progressThumb = CopperDark,
)

val LightCustomColors = MediaFlowCustomColors(
    favorite = FavoritePinkLight,
    favoriteBright = FavoriteBrightPink,
    favoriteInactive = FavoriteInactiveLight,
    live = LiveRed,
    liveContainer = LiveContainerLight,
    onLive = OnLiveColor,
    error = ErrorRed,
    errorPressed = ErrorPressedRed,
    errorContainer = Color(0xFFFFDAD6),
    success = SuccessGreen,
    warning = WarningYellow,
    info = InfoBlue,
    buffering = CopperLight.copy(alpha = 0.6f),
    primaryBright = CopperBrightLight,
    primaryPressed = CopperLight,
    primaryDark = CopperLight,
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
    progressPlayed = CopperLight,
    progressThumb = CopperLight,
)
