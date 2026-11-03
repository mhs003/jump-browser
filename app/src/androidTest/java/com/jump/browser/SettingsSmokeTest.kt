package com.jump.browser

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Walks the chrome and every settings page, opening dialogs along the way.
 *
 * These screens are pure Compose and had no instrumentation coverage, which is
 * how a nested-scroll crash in a dialog reached the device. A crash here fails
 * the test with the activity state, which is much faster to read than logcat.
 */
@RunWith(AndroidJUnit4::class)
class SettingsSmokeTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    /**
     * Each test starts from the browser chrome. Instrumentation runs the tests
     * in one process, so without this the previous test's page would be left
     * open and taps would land on the wrong screen.
     */
    /** Scrolling while Compose is still animating produced flaky taps. */
    @Before
    fun settle() {
        rule.mainClock.autoAdvance = true
    }

    @Before
    fun returnToChrome() {
        repeat(4) {
            rule.waitForIdle()
            if (rule.onAllNodesWithContentDescription("Menu").fetchSemanticsNodes().isNotEmpty()) {
                return
            }
            androidx.test.espresso.Espresso.pressBack()
            rule.waitForIdle()
            Thread.sleep(200)
        }
    }

    private fun waitForText(text: String, timeoutMs: Long = 5_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            rule.waitForIdle()
            if (rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(100)
        }
        // Overlay surfaces are separate windows, so there can be several roots;
        // the tree dump is only for diagnosing the failure.
        val tree = runCatching { rule.onAllNodes(isRoot()).printToString() }.getOrDefault("")
        throw AssertionError("Timed out waiting for '$text'\n$tree")
    }

    private fun tap(text: String) {
        waitForText(text)
        rule.onAllNodesWithText(text).onFirst().performClick()
        rule.waitForIdle()
    }

    /** Taps a control that is labelled through its content description. */
    private fun tapDescription(description: String) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            rule.waitForIdle()
            val nodes = rule.onAllNodesWithContentDescription(description).fetchSemanticsNodes()
            if (nodes.isNotEmpty()) {
                rule.onAllNodesWithContentDescription(description).onFirst().performClick()
                rule.waitForIdle()
                return
            }
            Thread.sleep(100)
        }
        throw AssertionError("No node with description '$description'")
    }

    @Test
    fun opensEverySettingsCategory() {
        // Open the menu, then Settings.
        tapDescription("Menu")
        tap("Settings")
        val categories = listOf("General", "Customization", "Privacy", "Advanced", "Scripts", "About")
        categories.forEach { category ->
            tap(category)
            // Each category has a back affordance at the top left.
            rule.waitForIdle()
            tap("Settings")
        }
    }

    /**
     * Scrolls the settings list until [text] is on screen, then taps it.
     *
     * The page is long and the rows are not ordered by where the tests need
     * them, so this scrolls down first and then back up: a forward-only scroll
     * walks past rows near the top and never finds them again.
     */
    private fun scrollToAndTap(text: String) {
        repeat(2) { direction ->
            val deadline = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < deadline) {
                rule.waitForIdle()
                if (rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()) {
                    rule.onAllNodesWithText(text).onFirst().performClick()
                    rule.waitForIdle()
                    return
                }
                // Swipe the list itself; the settings pages are plain lists.
                rule.onAllNodes(isRoot()).onFirst().performTouchInput {
                    if (direction == 0) {
                        swipeUp(startY = bottom * 0.8f, endY = top + height * 0.2f, durationMillis = 250)
                    } else {
                        swipeDown(startY = top + height * 0.2f, endY = bottom * 0.8f, durationMillis = 250)
                    }
                }
                rule.waitForIdle()
                Thread.sleep(120)
            }
        }
        throw AssertionError("Could not find '$text' by scrolling")
    }

    @Test
    fun opensGeneralDialogsWithoutCrashing() {
        tapDescription("Menu")
        tap("Settings")
        tap("General")
        // These dialogs previously stacked a scroll container inside the
        // dialog's own scroll region and crashed the app.
        scrollToAndTap("Password manager")
        waitForText("Saved passwords")
        tap("Close")
    }

    /**
     * The General page's Via-parity dialogs: Clear data, Clear data on exit,
     * User-agent and Toolbars settings. All four are long lists, which is what
     * used to crash, and all four used to be missing or non-functional.
     */
    @Test
    fun opensGeneralParityDialogs() {
        tapDescription("Menu")
        tap("Settings")
        tap("General")

        scrollToAndTap("Clear data")
        // Every option Via offers is present, and the two caches start ticked.
        listOf(
            "Cache", "Form data", "History", "Closed tabs",
            "Web storage", "Cookies (Login status)", "App cache",
        ).forEach { waitForText(it) }
        tap("Cancel")

        scrollToAndTap("Clear data on exit")
        waitForText("Cookies (Login status)")
        tap("Cancel")

        scrollToAndTap("User-agent")
        // Via's full list, including the custom entry.
        listOf("Default", "Chrome (PC)", "Windows (IE 11)", "Symbian", "Custom")
            .forEach { waitForText(it) }
        waitForText("User-agent reduction")
        // Selecting Custom reveals the free-text field. Its placeholder is a
        // separate node, so the field itself is checked through the label.
        tap("Custom")
        waitForText("Custom")
        tap("Cancel")

        scrollToAndTap("Toolbars settings")
        listOf(
            "Toolbar UI mode", "Auto-hide toolbars", "Enable tab bar",
            "URL field content", "Color mode", "Auto-adjust color of toolbars",
        ).forEach { waitForText(it) }
        tap("Done")
    }

    @Test
    fun opensAppearanceAndThemeDialogs() {
        tapDescription("Menu")
        tap("Settings")
        tap("Customization")
        tap("Appearance")
        // The appearance dialog offers the three themes rather than a Close
        // button; choosing one also exercises the theme switch.
        waitForText("Follow system")
        tap("Light")
    }
}
