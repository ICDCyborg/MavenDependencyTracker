package net.icdcyborg.mavenDependencyTracker

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun clickingResolveButton_callsViewModel() {
        val viewModel = mockk<MainViewModel>(relaxed = true)
        composeTestRule.setContent {
            MainScreen(viewModel)
        }

        composeTestRule.onNodeWithText("Resolve").performClick()

        verify { viewModel.startResolution(any()) }
    }
}
