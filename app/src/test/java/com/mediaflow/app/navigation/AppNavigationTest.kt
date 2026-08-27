package com.mediaflow.app.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mediaflow.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Verifies the bottom navigation bar and navigation between the four main
 * destinations through the real [MainActivity].
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-normal-port")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun initialDestinationIsHome() {
        composeRule.onNodeWithText("MediaFlow").assertIsDisplayed()
        composeRule.onNodeWithText("Descarga y organiza tus archivos multimedia")
            .assertIsDisplayed()
    }

    @Test
    fun bottomBarIsVisible() {
        composeRule.onNodeWithText("Inicio").assertIsDisplayed()
        composeRule.onNodeWithText("Descargas").assertIsDisplayed()
        composeRule.onNodeWithText("Galería").assertIsDisplayed()
        composeRule.onNodeWithText("Ajustes").assertIsDisplayed()
    }

    @Test
    fun canNavigateToDownloads() {
        composeRule.onNodeWithTag("tab_downloads").performClick()
        composeRule.onNodeWithText("Todavía no tienes descargas").assertIsDisplayed()
        composeRule.onNodeWithText("Volver a Inicio").assertIsDisplayed()
    }

    @Test
    fun canNavigateToGallery() {
        composeRule.onNodeWithTag("tab_gallery").performClick()
        // Android Q+ uses the app-owned MediaStore path and does not request
        // broad media permissions. An empty owned collection is truthful.
        composeRule.onNodeWithText("Tu galería aparecerá aquí").assertIsDisplayed()
    }

    @Test
    fun canNavigateToSettings() {
        composeRule.onNodeWithTag("tab_settings").performClick()
        composeRule.onNodeWithText("Apariencia").assertIsDisplayed()
        composeRule.onNodeWithText("Acerca de").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun downloadsCanReturnHome() {
        composeRule.onNodeWithTag("tab_downloads").performClick()
        composeRule.onNodeWithText("Volver a Inicio").performClick()
        composeRule.onNodeWithText("Descarga y organiza tus archivos multimedia")
            .assertIsDisplayed()
    }

    @Test
    fun sameTabTapDoesNotCrash() {
        composeRule.onNodeWithTag("tab_downloads").performClick()
        composeRule.onNodeWithText("Todavía no tienes descargas").assertIsDisplayed()
        composeRule.onNodeWithTag("tab_downloads").performClick()
        composeRule.onNodeWithText("Todavía no tienes descargas").assertIsDisplayed()
    }
}
