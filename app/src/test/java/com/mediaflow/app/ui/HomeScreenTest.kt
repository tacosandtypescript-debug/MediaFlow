package com.mediaflow.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mediaflow.app.ui.home.HomeScreen
import com.mediaflow.app.ui.theme.MediaFlowTheme
import com.mediaflow.app.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-normal-port")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setHome() {
        composeRule.setContent {
            MediaFlowTheme { HomeScreen() }
        }
    }

    @Test
    fun downloadButtonDisabledInitially() {
        setHome()
        composeRule.onNodeWithTag("download_button").assertIsNotEnabled()
    }

    @Test
    fun invalidTextShowsError() {
        setHome()
        composeRule.onNodeWithTag("url_input").performTextInput("hola mundo")
        composeRule.onNodeWithText("Introduce un enlace válido").assertIsDisplayed()
        composeRule.onNodeWithTag("download_button").assertIsNotEnabled()
    }

    @Test
    fun emptyFieldShowsPrompt() {
        setHome()
        composeRule.onNodeWithTag("url_input").performTextInput("x")
        composeRule.onNodeWithTag("url_input").performTextClearance()
        composeRule.onNodeWithText("Pega un enlace para continuar").assertIsDisplayed()
    }

    @Test
    fun httpUrlRejectedWithSecurityMessage() {
        setHome()
        composeRule.onNodeWithTag("url_input").performTextInput("http://example.com/video")
        composeRule.onNodeWithText("Por seguridad, utiliza un enlace HTTPS").assertIsDisplayed()
        composeRule.onNodeWithTag("download_button").assertIsNotEnabled()
    }

    @Test
    fun validHttpsEnablesButtonAndShowsInfo() {
        setHome()
        composeRule.onNodeWithTag("url_input").performTextInput("https://example.com/watch?v=1")
        composeRule.onNodeWithText(
            "Enlace válido. La fuente se analizará al iniciar la descarga",
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("download_button").assertIsEnabled()
    }

    @Test
    fun downloadNowShowsPendingMessageAndStartsNothing() {
        setHome()
        composeRule.onNodeWithTag("url_input").performTextInput("https://example.com/video")
        composeRule.onNodeWithTag("download_button").performScrollTo().performClick()
        composeRule.onNodeWithText("La descarga real se conectará en una fase posterior")
            .assertIsDisplayed()
    }

    @Test
    fun switchToAudioUpdatesSelectionAndQualityOptions() {
        setHome()
        composeRule.onNodeWithTag("media_type_audio").performClick()
        composeRule.onNodeWithTag("media_type_audio").assertIsSelected()
        composeRule.onNodeWithTag("media_type_video").assertIsNotSelected()
        composeRule.onNodeWithTag("quality_high").assertIsDisplayed()
    }

    @Test
    fun changeQualityUpdatesSelection() {
        setHome()
        composeRule.onNodeWithTag("quality_p1080").performScrollTo().performClick()
        composeRule.onNodeWithTag("quality_p1080").assertIsSelected()
    }

    @Test
    fun editFileName() {
        setHome()
        composeRule.onNodeWithTag("file_name_input").performScrollTo()
            .performTextInput("Mi video final")
        composeRule.onNodeWithTag("file_name_input").assertTextContains("Mi video final")
    }

    @Test
    fun clearButtonRemovesUrl() {
        setHome()
        composeRule.onNodeWithTag("url_input").performTextInput("https://example.com/v")
        composeRule.onNodeWithContentDescription("Limpiar enlace").performClick()
        composeRule.onNodeWithText("Pega un enlace para continuar").assertIsDisplayed()
        composeRule.onNodeWithTag("download_button").assertIsNotEnabled()
    }

    @Test
    fun clearButtonIsHiddenWhenUrlEmpty() {
        setHome()
        composeRule.onNodeWithContentDescription("Limpiar enlace").assertDoesNotExist()
    }

    @Test
    fun pasteFromClipboardUpdatesUrl() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clipboard =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("url", "https://example.com/paste"))

        setHome()
        composeRule.onNodeWithTag("paste_button").performClick()
        composeRule.onNodeWithText(
            "Enlace válido. La fuente se analizará al iniciar la descarga",
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("download_button").assertIsEnabled()
    }

    @Test
    fun lightThemeRendersWithoutError() {
        setHome()
        composeRule.onNodeWithText("MediaFlow").assertIsDisplayed()
    }

    @Test
    fun lightThemeModeRendersWithoutError() {
        composeRule.setContent {
            MediaFlowTheme(themeMode = ThemeMode.LIGHT) { HomeScreen() }
        }
        composeRule.onNodeWithText("MediaFlow").assertIsDisplayed()
    }

    @Test
    fun darkThemeModeRendersWithoutError() {
        composeRule.setContent {
            MediaFlowTheme(themeMode = ThemeMode.DARK) { HomeScreen() }
        }
        composeRule.onNodeWithText("MediaFlow").assertIsDisplayed()
    }
}
