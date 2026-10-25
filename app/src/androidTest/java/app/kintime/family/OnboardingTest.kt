package app.kintime.family

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun localBuildShowsHonestEmptyStateAndFirebaseSetup() {
        compose.onNodeWithText("kintime").assertIsDisplayed()
        compose.onNodeWithText("Log", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Family", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Your family space is ready").assertIsDisplayed()
        compose.onNodeWithText("Tracking", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Screen-time tracking").performScrollTo().assertIsDisplayed()
    }
    @Test fun videoTrackingRequiresSeparateDisclosure() {
        compose.onNodeWithText("Tracking", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Reels & Shorts estimates").performScrollTo()
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        compose.onNodeWithText("Enable short-video estimates?").assertIsDisplayed()
        compose.onNodeWithText("Not now").performClick()
        compose.onAllNodes(isToggleable()).onFirst().assertIsOff()
    }
    @Test fun stepsHasSetupAndSeparateHealthDisclosure() {
        compose.onNodeWithText("Steps", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Your step goal").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Choose where steps come from").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Step privacy").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Log", useUnmergedTree = true).assertDoesNotExist()
    }
}
