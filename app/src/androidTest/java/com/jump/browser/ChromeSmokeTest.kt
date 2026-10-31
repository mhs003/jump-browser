package com.jump.browser

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChromeSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun chromeIsPresent() {
        composeRule.onNodeWithContentDescription("Menu").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Home").assertIsDisplayed()
        composeRule.onNodeWithText("Homepage").assertIsDisplayed()
    }
}
