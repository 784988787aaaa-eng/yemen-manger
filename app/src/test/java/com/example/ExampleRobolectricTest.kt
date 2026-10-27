package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `app name matches Yemen Manager and dual pane file manager renders`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Yemen Manager", appName)

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("app_title_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dual_pane_file_manager").assertIsDisplayed()
        composeTestRule.onNodeWithTag("slim_bottom_toolbar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("drawer_open_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("top_bar_overflow_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("select_pane_a_chip", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("select_pane_b_chip", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("file_pane_A", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("file_pane_B", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `navigation across editors archives apk lab tasks and settings works with RTL and LTR toggle`() {
        composeTestRule.waitForIdle()

        // Toggle language between Arabic RTL and English LTR
        composeTestRule.onNodeWithTag("top_bar_language_toggle").performClick()
        composeTestRule.waitForIdle()

        // Navigate to Editors & Diff
        composeTestRule.onNodeWithTag("nav_editors").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("editors_and_diff_screen").assertIsDisplayed()

        // Switch to Hex Editor tab
        composeTestRule.onNodeWithTag("tab_hex_editor").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("hex_rows_list").assertIsDisplayed()

        // Switch to Diff Tool tab
        composeTestRule.onNodeWithTag("tab_diff_tool").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("diff_lines_list").assertIsDisplayed()

        // Navigate to Archive Lab
        composeTestRule.onNodeWithTag("nav_archives").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("archive_lab_screen").assertIsDisplayed()

        // Navigate to APK Lab
        composeTestRule.onNodeWithTag("nav_apk_lab").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("apk_lab_screen").assertIsDisplayed()

        // Navigate to Installed Apps
        composeTestRule.onNodeWithTag("nav_installed_apps").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("installed_apps_screen").assertIsDisplayed()

        // Navigate to Task Center
        composeTestRule.onNodeWithTag("nav_tasks").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("task_center_screen").assertIsDisplayed()

        // Navigate to Settings & Preferences
        composeTestRule.onNodeWithTag("nav_settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_and_capabilities_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("permissions_quick_banner_card", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `permissions and battery optimization center dialog opens and offline Moshi backup works`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val snapshot = com.example.engines.system.PermissionsAndSystemIntentsEngine.checkPermissionsSnapshot(context)
        assertEquals(6, snapshot.items.size)

        val repo = com.example.data.local.YemenPreferencesRepository(context)
        val sampleState = com.example.ui.viewmodel.UserPreferencesState(
            requestRootPermission = true,
            customSuCommand = "su -c",
            startupPathLeft = "/storage/emulated/0"
        )
        val exportedJson = repo.exportPreferencesToJson(sampleState)
        val restored = repo.importPreferencesFromJson(exportedJson)
        assertEquals("su -c", restored?.customSuCommand)
        assertEquals("/storage/emulated/0", restored?.startupPathLeft)

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("top_bar_permissions_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("permissions_and_battery_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("grant_all_permissions_button").assertIsDisplayed()
    }
}


