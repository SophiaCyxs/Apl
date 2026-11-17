package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `app name matches BloxLua`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("BloxLua", appName)
  }

  @Test
  fun `cuj navigate editor terminal sandbox settings and toggle dark mode`() {
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("lua_code_editor").assertIsDisplayed()
    composeTestRule.onNodeWithTag("compile_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("run_button").assertIsDisplayed()

    // Navigate to Terminal and check Interactive GUI Canvas + mode toggle
    composeTestRule.onNodeWithTag("nav_terminal").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("terminal_frame_viewport").assertIsDisplayed()
    composeTestRule.onNodeWithTag("gui_preview_canvas").assertIsDisplayed()
    composeTestRule.onNodeWithTag("preview_mode_ansi").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("terminal_frame_viewport").assertIsDisplayed()
    composeTestRule.onNodeWithTag("preview_mode_canvas").performClick()
    composeTestRule.waitForIdle()

    // Navigate to Sandbox Inspector
    composeTestRule.onNodeWithTag("nav_sandbox").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("inspector_tab_BYTECODE").assertIsDisplayed()

    // Navigate to Settings, switch palette, and toggle Dark Mode
    composeTestRule.onNodeWithTag("nav_settings").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("setting_dark_mode_switch").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("palette_option_COBALT").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("palette_option_MONOCHROME").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("setting_execution_interval_slider").performScrollTo().assertIsDisplayed()
  }
}
