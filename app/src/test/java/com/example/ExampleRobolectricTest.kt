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
    composeTestRule.onNodeWithTag("editor_select_all_btn").assertIsDisplayed()
    composeTestRule.onNodeWithTag("editor_copy_all_btn").assertIsDisplayed()
    composeTestRule.onNodeWithTag("editor_paste_btn").assertIsDisplayed()
    composeTestRule.onNodeWithTag("editor_clear_btn").assertIsDisplayed()

    // Clear and Undo restore flow
    composeTestRule.onNodeWithTag("editor_clear_btn").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("editor_clear_btn").performClick()
    composeTestRule.waitForIdle()

    // Explicit Compile button click & status banner check
    composeTestRule.onNodeWithTag("compile_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("compile_status_banner").assertIsDisplayed()

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

    // Navigate to Settings and verify Background/FX, MP4 video upload, Syntax Gradient, Palette, and Interval controls
    composeTestRule.onNodeWithTag("nav_settings").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("bg_mode_CUSTOM_VIDEO").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("upload_bg_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("upload_video_bg_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("setting_panel_shadow_slider").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("setting_dark_mode_switch").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("palette_option_COBALT").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("palette_option_MONOCHROME").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("setting_syntax_gradient_switch").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("setting_font_gradient_moving_switch").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("reset_syntax_colors_btn").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("setting_execution_interval_slider").performScrollTo().assertIsDisplayed()
  }
}
