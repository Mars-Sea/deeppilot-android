package dev.hailab.deeppilot.core

import dev.hailab.deeppilot.core.onboarding.OnboardingFlow
import dev.hailab.deeppilot.core.onboarding.OnboardingMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The onboarding timeline: curve endpoints, the "rest state is the final state" convention,
 * and each page's ordering. [OnboardingMotion.SETTLED] is the final state.
 */
class OnboardingMotionTest {
    private val settled = OnboardingMotion.SETTLED
    private val eps = 1e-9

    // Curves

    @Test
    fun progressClampsAndSettledIsComplete() {
        assertEquals(0.0, OnboardingMotion.progress(-1.0, 1.0, 2.0), eps)
        assertEquals(0.0, OnboardingMotion.progress(1.0, 1.0, 2.0), eps)
        assertEquals(0.5, OnboardingMotion.progress(2.0, 1.0, 2.0), eps)
        assertEquals(1.0, OnboardingMotion.progress(9.0, 1.0, 2.0), eps)
        assertEquals(1.0, OnboardingMotion.progress(settled, 1.0, 2.0), eps)
    }

    @Test
    fun easingCurvesHaveFixedEndpointsAndAreMonotonic() {
        for (curve in listOf(OnboardingMotion::easeOut, OnboardingMotion::easeInOut)) {
            assertEquals(0.0, curve(0.0), eps)
            assertEquals(1.0, curve(1.0), eps)
            var last = 0.0
            for (step in 1..20) {
                val value = curve(step / 20.0)
                assertTrue(value >= last)
                last = value
            }
        }
        // Overshoot starts at 0, ends at 1, and rises above 1 on the way.
        assertEquals(0.0, OnboardingMotion.overshoot(0.0), eps)
        assertEquals(1.0, OnboardingMotion.overshoot(1.0), eps)
        assertTrue((1..19).maxOf { OnboardingMotion.overshoot(it / 20.0) } > 1.0)
    }

    @Test
    fun riseStartsHiddenBelowAndEndsInPlace() {
        val start = OnboardingMotion.rise(0.0, 0.2)
        assertEquals(0.0, start.opacity, eps)
        assertEquals(OnboardingMotion.Copy.OFFSET, start.offset, eps)
        val end = OnboardingMotion.rise(settled, 0.2)
        assertEquals(1.0, end.opacity, eps)
        assertEquals(0.0, end.offset, eps)
    }

    @Test
    fun popEndsAtFullSizeAndOpacity() {
        val done = OnboardingMotion.pop(settled, 1.0, 0.5)
        assertEquals(1.0, done.opacity, eps)
        assertEquals(1.0, done.scale, eps)
        assertEquals(0.0, OnboardingMotion.pop(0.0, 1.0, 0.5).opacity, eps)
        // On the way it grows slightly past 1x.
        assertTrue((1..49).maxOf { OnboardingMotion.pop(1.0 + it * 0.01, 1.0, 0.5).scale } > 1.0)
    }

    // Typing and caret

    @Test
    fun typedCountFollowsStepsEndSemantics() {
        assertEquals(0, OnboardingMotion.typedCount(0.0, 1.0, 1.0, 10))
        assertEquals(0, OnboardingMotion.typedCount(1.0, 1.0, 1.0, 10))
        assertEquals(5, OnboardingMotion.typedCount(1.55, 1.0, 1.0, 10))
        assertEquals(10, OnboardingMotion.typedCount(2.0, 1.0, 1.0, 10))
        assertEquals(10, OnboardingMotion.typedCount(settled, 1.0, 1.0, 10))
        // The text length does not change the duration: a longer English line still finishes in time.
        assertEquals(41, OnboardingMotion.typedCount(settled, 1.0, 1.0, 41))
    }

    @Test
    fun caretBlinksOnlyAfterStartAndNeverInTheFinalState() {
        assertFalse(OnboardingMotion.caretOn(0.0, 2.0))
        assertTrue(OnboardingMotion.caretOn(2.1, 2.0))
        assertFalse(OnboardingMotion.caretOn(2 + OnboardingMotion.Session.CARET_PERIOD * 0.75, 2.0))
        assertTrue(OnboardingMotion.caretOn(2 + OnboardingMotion.Session.CARET_PERIOD * 1.1, 2.0))
        assertFalse("no caret left in the final state", OnboardingMotion.caretOn(settled, 2.0))
    }

    // Page 1

    @Test
    fun connectedFinalStateIsDrawnAndStill() {
        assertEquals(1.0, OnboardingMotion.stroke(settled, 0.5), eps)
        assertEquals(1.0, OnboardingMotion.linkOpacity(settled), eps)
        assertEquals("the final link does not flow", 0.0, OnboardingMotion.linkPhase(settled), eps)
        assertEquals("the scan frame is hidden at the end", 0.0, OnboardingMotion.bracket(settled).opacity, eps)
    }

    @Test
    fun connectedStartsEmptyAndTheLinkFlowsForward() {
        assertEquals(0.0, OnboardingMotion.stroke(0.0, OnboardingMotion.Connected.COMPUTER), eps)
        assertEquals(0.0, OnboardingMotion.linkOpacity(0.0), eps)
        assertTrue(OnboardingMotion.linkPhase(2.0) > OnboardingMotion.linkPhase(1.5))
    }

    @Test
    fun bracketAppearsHoldsThenFadesBeforeTheCheckPops() {
        val start = OnboardingMotion.Connected.BRACKET
        assertEquals(0.0, OnboardingMotion.bracket(start - 0.1).opacity, eps)
        assertEquals("fully visible mid-way", 1.0, OnboardingMotion.bracket(start + 0.75).opacity, eps)
        assertEquals(0.0, OnboardingMotion.bracket(start + OnboardingMotion.Connected.BRACKET_DURATION).opacity, eps)
        // By the time the check pops the frame is already fading, so they are never both fully on.
        assertTrue(OnboardingMotion.bracket(OnboardingMotion.Connected.CHECK + 0.01).opacity < 1.0)
    }

    // Page 2

    @Test
    fun spinnerOnlyBetweenItsStartAndTheToolDone() {
        assertEquals(0.0, OnboardingMotion.spinner(0.0).opacity, eps)
        assertEquals(1.0, OnboardingMotion.spinner(OnboardingMotion.Session.SPINNER + 0.5).opacity, eps)
        assertEquals(0.0, OnboardingMotion.spinner(OnboardingMotion.Session.SPINNER + OnboardingMotion.Session.SPINNER_DURATION).opacity, eps)
        assertEquals("only the check remains", 0.0, OnboardingMotion.spinner(settled).opacity, eps)
        // The check pops once the spinner is done.
        assertTrue(OnboardingMotion.Session.TOOL_DONE >= OnboardingMotion.Session.SPINNER + OnboardingMotion.Session.SPINNER_DURATION - 0.01)
    }

    // Page 3

    @Test
    fun approvalSheetSlidesInHoldsThenSlidesOut() {
        val a = OnboardingMotion.Approval
        assertEquals("starts outside the stage", 1.0, OnboardingMotion.approvalSheetOffset(0.0), eps)
        assertEquals(0.0, OnboardingMotion.approvalSheetOffset(a.SHEET_IN + a.SHEET_IN_DURATION + 0.01), 1e-6)
        assertEquals("held until Allow is pressed", 0.0, OnboardingMotion.approvalSheetOffset(2.5), 1e-6)
        assertEquals(1.0, OnboardingMotion.approvalSheetOffset(a.SHEET_OUT + a.SHEET_OUT_DURATION), 1e-6)
        assertEquals("gone in the final state", 1.0, OnboardingMotion.approvalSheetOffset(settled), 1e-6)
    }

    @Test
    fun approvalOrderingMatchesTheStory() {
        val a = OnboardingMotion.Approval
        // Slide in, press, confirm, then leave and swap the status to "Allowed".
        assertTrue(a.SHEET_IN + a.SHEET_IN_DURATION < a.PRESS)
        assertTrue(a.PRESS < a.BUTTONS_OUT)
        assertTrue(a.CONFIRMED_IN <= a.BUTTONS_OUT)
        assertTrue(a.BUTTONS_OUT < a.SHEET_OUT)
        assertTrue(a.WAIT_OUT <= a.ALLOWED_IN)
        assertTrue(a.SHEET_OUT + a.SHEET_OUT_DURATION <= a.TOTAL)
        assertTrue(a.ALLOWED_IN + a.ALLOWED_IN_DURATION <= a.TOTAL)
    }

    @Test
    fun approvalStatusSwapsFromWaitingToAllowed() {
        assertEquals(1.0, OnboardingMotion.waitOpacity(0.0), eps)
        assertEquals(0.0, OnboardingMotion.allowedOpacity(0.0), eps)
        assertEquals(0.0, OnboardingMotion.waitOpacity(settled), eps)
        assertEquals(1.0, OnboardingMotion.allowedOpacity(settled), eps)
        assertEquals(0.0, OnboardingMotion.buttonsOpacity(settled), eps)
        assertEquals(1.0, OnboardingMotion.confirmedOpacity(settled), eps)
    }

    @Test
    fun pressDipsThenReturnsToFullSize() {
        val a = OnboardingMotion.Approval
        assertEquals(1.0, OnboardingMotion.pressScale(0.0), eps)
        assertTrue(OnboardingMotion.pressScale(a.PRESS + a.PRESS_DURATION * 0.4) < 1.0)
        assertEquals(1.0, OnboardingMotion.pressScale(a.PRESS + a.PRESS_DURATION), eps)
        assertEquals(1.0, OnboardingMotion.pressScale(settled), eps)
    }

    @Test
    fun pulsePlaysExactlyTwiceThenStops() {
        val a = OnboardingMotion.Approval
        assertEquals(0.0, OnboardingMotion.pulse(0.0).opacity, eps)
        assertTrue(OnboardingMotion.pulse(a.PULSE + 0.1).opacity > 0)
        assertTrue("second pulse", OnboardingMotion.pulse(a.PULSE + a.PULSE_DURATION + 0.1).opacity > 0)
        assertEquals("no more after that", 0.0, OnboardingMotion.pulse(a.PULSE + a.PULSE_DURATION * a.PULSE_COUNT + 0.01).opacity, eps)
        assertEquals(0.0, OnboardingMotion.pulse(settled).opacity, eps)
    }

    // Page 4

    @Test
    fun notificationsDropInOrderAndEndInPlace() {
        val delays = OnboardingMotion.Notifications.DELAYS
        assertEquals(delays, delays.sorted())
        for (delay in delays) {
            assertEquals(0.0, OnboardingMotion.drop(0.0, delay).opacity, eps)
            val end = OnboardingMotion.drop(settled, delay)
            assertEquals(1.0, end.opacity, eps)
            assertEquals(0.0, end.offset, eps)
            assertEquals(1.0, end.scale, eps)
        }
        assertTrue(OnboardingMotion.Notifications.TOTAL >= delays.last() + OnboardingMotion.Notifications.DURATION - 0.05)
    }

    // Overall

    @Test
    fun everyPageDurationCoversItsLastEvent() {
        assertTrue(OnboardingMotion.Connected.TOTAL >= OnboardingMotion.Connected.CHIP + OnboardingMotion.Copy.DURATION - 0.05)
        assertTrue(OnboardingMotion.Connected.TOTAL >= OnboardingMotion.Connected.TICK + OnboardingMotion.Connected.TICK_DURATION)
        assertTrue(OnboardingMotion.Session.TOTAL >= OnboardingMotion.Session.LINE2 + OnboardingMotion.Session.LINE2_DURATION)
        assertTrue(OnboardingMotion.Approval.TOTAL >= OnboardingMotion.Approval.SHEET_OUT + OnboardingMotion.Approval.SHEET_OUT_DURATION)
        for (page in 0 until OnboardingFlow.PAGE_COUNT) {
            assertTrue(OnboardingMotion.duration(page) > OnboardingMotion.Copy.BODY + OnboardingMotion.Copy.DURATION)
        }
    }

    @Test
    fun onlyConnectedAndSessionPagesLoop() {
        assertTrue(OnboardingMotion.loops(0))
        assertTrue(OnboardingMotion.loops(1))
        assertFalse(OnboardingMotion.loops(2))
        assertFalse(OnboardingMotion.loops(3))
    }
}
