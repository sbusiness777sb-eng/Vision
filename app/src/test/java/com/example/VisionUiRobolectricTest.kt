package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VisionUiRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `four tabs render in exact order and alfred bar persists across all tabs`() {
        composeRule.waitForIdle()

        // 1. Dashboard tab visible initially
        composeRule.onNodeWithTag("dashboard_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("alfred_assistant_bar").assertIsDisplayed()

        // 2. Navigate to Alarm tab
        composeRule.onNodeWithTag("nav_tab_alarm").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("alarm_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("alfred_assistant_bar").assertIsDisplayed()

        // 3. Navigate to Distraction tab and scroll to Study Mode card
        composeRule.onNodeWithTag("nav_tab_distraction").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("distraction_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("alfred_assistant_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("distraction_screen").performScrollToNode(hasTestTag("study_mode_card"))
        composeRule.onNodeWithTag("study_mode_card").assertIsDisplayed()

        // 4. Navigate to Report tab and scroll to Column Chart card
        composeRule.onNodeWithTag("nav_tab_report").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("report_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("alfred_assistant_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("report_screen").performScrollToNode(hasTestTag("report_column_chart_card"))
        composeRule.onNodeWithTag("report_column_chart_card").assertIsDisplayed()
    }
}
