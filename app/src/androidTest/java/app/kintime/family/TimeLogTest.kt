package app.kintime.family

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimeLogTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun timerStopsIntoHistoryAndSurvivesActivityRecreation() {
        compose.onNodeWithText("Log", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("timer-description").performTextInput("Read a book")
        compose.onNodeWithTag("timer-project").performTextInput("Family")
        compose.onNodeWithContentDescription("Start timer").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Stop timer").fetchSemanticsNodes().isNotEmpty() }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Log", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("Stop timer").assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Start timer").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Read a book").onLast().performScrollTo().assertIsDisplayed()
    }
    @Test fun manualEntryCanBeAddedWithoutUsageAccess() {
        compose.onNodeWithText("Log", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Log time").performScrollTo().performClick()
        compose.onNodeWithTag("entry-description").performTextInput("Evening walk")
        compose.onNodeWithText("Save entry").performClick()
        compose.onNodeWithText("Evening walk").performScrollTo().assertIsDisplayed()
    }
}
