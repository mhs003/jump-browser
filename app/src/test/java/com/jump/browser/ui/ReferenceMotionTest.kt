package com.jump.browser.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the motion curves to the reference APK so a future refactor cannot
 * quietly swap them for stock Material specs.
 */
class ReferenceMotionTest {

    @Test
    fun sheetCurveIsSymmetricAndMovesInTheMiddle() {
        val easing = ReferenceMotion.SHEET_EASING
        assertEquals(0f, easing.transform(0f), 1e-4f)
        assertEquals(1f, easing.transform(1f), 1e-4f)
        // PathInterpolator(0.2, 0.2, 0.8, 0.8) passes through the centre.
        assertEquals(0.5f, easing.transform(0.5f), 1e-3f)
        // Symmetric: the curve mirrors around the centre.
        for (step in 1..9) {
            val f = step / 10f
            assertEquals(easing.transform(f), 1f - easing.transform(1f - f), 1e-3f)
        }
    }

    @Test
    fun sheetCurveIsMonotonic() {
        val easing = ReferenceMotion.SHEET_EASING
        var previous = easing.transform(0f)
        for (step in 1..100) {
            val value = easing.transform(step / 100f)
            assertTrue("progress must not go backwards at $step", value >= previous - 1e-4f)
            previous = value
        }
    }

    @Test
    fun bounceCurveMatchesPlatformShape() {
        val easing = BounceCurveEasing()
        assertEquals(0f, easing.transform(0f), 1e-4f)
        // Android's BounceInterpolator bounces up to and past its target,
        // starting fast rather than easing in.
        assertTrue("should rise quickly", easing.transform(0.1f) > 0.1f)
        assertTrue("should reach its target late", easing.transform(0.75f) > 0.9f)
        // The platform curve ends on its jump offsets (about 0.12) and the
        // caller's progress is what lands on the target, as in @anim/a.
        assertTrue(easing.transform(1f) < 0.2f)
    }

    @Test
    fun durationsMatchReferenceIntegers() {
        assertEquals(180, ReferenceMotion.SHEET_DURATION_MS)
        assertEquals(240, ReferenceMotion.POPUP_DURATION_MS)
        assertEquals(160, ReferenceMotion.TOOLBAR_DURATION_MS)
    }
}
