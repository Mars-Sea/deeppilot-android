package dev.hailab.deeppilot.core.onboarding

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The onboarding entrance timeline as pure functions (iOS `OnboardingMotion`); the constants
 * are the same as iOS so both apps share one timing table.
 *
 * `t` is the seconds since the page became the current one. [SETTLED] (positive infinity)
 * means "finished or not played": every function returns its final state for it. The rest
 * state is the final state, so a page that is off screen, finished, or shown with animations
 * removed looks complete, and leaving a page never snaps it back to its start.
 */
object OnboardingMotion {
    const val SETTLED = Double.POSITIVE_INFINITY

    /** Title and body rise in on every page. */
    object Copy {
        const val TITLE = 0.15
        const val BODY = 0.28
        const val DURATION = 0.55
        const val OFFSET = 14.0
    }

    /** Page 1: outlines draw in, the link flows, the scan frame flashes, the check pops, the chip rises. */
    object Connected {
        const val COMPUTER = 0.0
        const val BASE = 0.25
        const val PHONE = 0.15
        const val QR_FRAME = 0.5
        const val QR_GLYPH = 0.7
        const val SPEAKER = 0.7
        const val DRAW = 0.8
        const val LINK = 0.8
        const val LINK_FADE = 0.4

        /** Design-space units the dashed link advances per second (one "4 on, 12 off" period is 16). */
        const val LINK_SPEED = 16.0
        const val BRACKET = 0.9
        const val BRACKET_DURATION = 1.5
        const val CHECK = 2.3
        const val CHECK_DURATION = 0.6
        const val TICK = 2.55
        const val TICK_DURATION = 0.5
        const val CHIP = 2.5
        const val TOTAL = 3.1
    }

    /** Page 2: the bubble, a tool row that spins then checks, and the reply typing out. */
    object Session {
        const val BUBBLE = 0.2
        const val TOOL = 0.75
        const val SPINNER = 0.9
        const val SPINNER_DURATION = 1.5
        const val TOOL_DONE = 2.4
        const val LINE1 = 1.5
        const val LINE1_DURATION = 1.2
        const val LINE2 = 2.8
        const val LINE2_DURATION = 1.1
        const val CARET_PERIOD = 1.06
        const val TOTAL = 3.95
    }

    /** Page 3: the pending row, the sheet sliding in, "Allow" pressed, the sheet leaving. */
    object Approval {
        const val ASSISTANT = 0.2
        const val TOOL = 0.4
        const val SHEET_IN = 0.8
        const val SHEET_IN_DURATION = 0.54
        const val PULSE = 1.4
        const val PULSE_DURATION = 1.1
        const val PULSE_COUNT = 2.0
        const val PRESS = 2.8
        const val PRESS_DURATION = 0.35
        const val BUTTONS_OUT = 3.05
        const val BUTTONS_OUT_DURATION = 0.3
        const val CONFIRMED_IN = 3.05
        const val CONFIRMED_IN_DURATION = 0.35
        const val SHEET_OUT = 3.39
        const val SHEET_OUT_DURATION = 0.58
        const val WAIT_OUT = 3.4
        const val WAIT_OUT_DURATION = 0.3
        const val ALLOWED_IN = 3.5
        const val ALLOWED_IN_DURATION = 0.35
        const val TOTAL = 4.0
    }

    /** Page 4: three notifications drop in one after another. */
    object Notifications {
        val DELAYS = listOf(0.2, 0.55, 0.9)
        const val DURATION = 0.65
        const val TOTAL = 1.6
    }

    fun duration(page: Int): Double = when (page) {
        0 -> Connected.TOTAL
        1 -> Session.TOTAL
        2 -> Approval.TOTAL
        else -> Notifications.TOTAL
    }

    /** Pages that keep drawing while visible: the link flow on page 1, the caret blink on page 2. */
    fun loops(page: Int): Boolean = page < 2

    // Curves

    /** Linear progress clamped to 0..1; 1 for [SETTLED]. */
    fun progress(t: Double, delay: Double, duration: Double): Double {
        if (duration <= 0) return if (t >= delay) 1.0 else 0.0
        return min(max((t - delay) / duration, 0.0), 1.0)
    }

    fun easeOut(p: Double): Double = 1 - (1 - p).pow(3)

    fun easeInOut(p: Double): Double =
        if (p < 0.5) 4 * p * p * p else 1 - (-2 * p + 2).pow(3) / 2

    /** Settles with a slight overshoot, for the notification cards. Ends at 1. */
    fun overshoot(p: Double): Double {
        val c1 = 1.0
        val c3 = c1 + 1
        return 1 + c3 * (p - 1).pow(3) + c1 * (p - 1).pow(2)
    }

    // Shared effects

    data class Rise(val opacity: Double, val offset: Double)

    /** Fade in while rising from below. */
    fun rise(t: Double, delay: Double, duration: Double = Copy.DURATION): Rise {
        val p = easeOut(progress(t, delay, duration))
        return Rise(p, Copy.OFFSET * (1 - p))
    }

    data class Pop(val opacity: Double, val scale: Double)

    /** Grows from 0.6x past 1x and settles. */
    fun pop(t: Double, delay: Double, duration: Double): Pop {
        val p = progress(t, delay, duration)
        val scale = if (p < 0.6) 0.6 + 0.48 * easeOut(p / 0.6) else 1.08 - 0.08 * ((p - 0.6) / 0.4)
        return Pop(min(1.0, p / 0.6), scale)
    }

    /** Characters shown so far, with `steps(total, end)` semantics: 0 at the start, [total] at the end. */
    fun typedCount(t: Double, delay: Double, duration: Double, total: Int): Int {
        val p = progress(t, delay, duration)
        if (p >= 1) return total
        return min(total, floor(p * total).toInt())
    }

    /** Blinks from [since]: on for the first half of each period. Never shown in the final state. */
    fun caretOn(t: Double, since: Double, period: Double = Session.CARET_PERIOD): Boolean {
        if (!t.isFinite() || t < since) return false
        return (t - since) % period < period / 2
    }

    // Page 1

    /** Stroke-draw progress: 0 not drawn, 1 fully drawn. */
    fun stroke(t: Double, delay: Double): Double = easeOut(progress(t, delay, Connected.DRAW))

    fun linkOpacity(t: Double): Double = progress(t, Connected.LINK, Connected.LINK_FADE)

    /** The link's dash phase; the final state does not flow. */
    fun linkPhase(t: Double): Double =
        if (t.isFinite()) max(0.0, t - Connected.LINK) * Connected.LINK_SPEED else 0.0

    data class Bracket(val opacity: Double, val scale: Double)

    /** The scan frame on the phone: shrinks in and fades up, holds, then fades; hidden in the final state. */
    fun bracket(t: Double): Bracket {
        val p = progress(t, Connected.BRACKET, Connected.BRACKET_DURATION)
        return when {
            p >= 1 -> Bracket(0.0, 1.0)
            p < 0.2 -> {
                val q = easeOut(p / 0.2)
                Bracket(q, 1.35 - 0.35 * q)
            }
            p < 0.8 -> Bracket(1.0, 1.0)
            else -> Bracket(1 - (p - 0.8) / 0.2, 1.0)
        }
    }

    // Page 2

    data class Spinner(val opacity: Double, val degrees: Double)

    /** The tool row's spinner: two linear turns, fading near the end; absent in the final state. */
    fun spinner(t: Double): Spinner {
        if (!t.isFinite() || t < Session.SPINNER) return Spinner(0.0, 0.0)
        val p = progress(t, Session.SPINNER, Session.SPINNER_DURATION)
        if (p >= 1) return Spinner(0.0, 720.0)
        return Spinner(if (p < 0.95) 1.0 else 1 - (p - 0.95) / 0.05, 720 * p)
    }

    // Page 3

    /** The approval sheet's position: 0 fully shown, 1 fully gone. */
    fun approvalSheetOffset(t: Double): Double {
        if (t < Approval.SHEET_OUT) {
            return 1 - easeOut(progress(t, Approval.SHEET_IN, Approval.SHEET_IN_DURATION))
        }
        return easeInOut(progress(t, Approval.SHEET_OUT, Approval.SHEET_OUT_DURATION))
    }

    /** "Allow" dips to 0.94x and comes back. */
    fun pressScale(t: Double): Double {
        val p = progress(t, Approval.PRESS, Approval.PRESS_DURATION)
        if (p >= 1 || p <= 0) return 1.0
        return if (p < 0.4) 1 - 0.06 * (p / 0.4) else 0.94 + 0.06 * ((p - 0.4) / 0.6)
    }

    /** The Deny / Allow row fades out after the press, making room for "Allowed". */
    fun buttonsOpacity(t: Double): Double =
        1 - progress(t, Approval.BUTTONS_OUT, Approval.BUTTONS_OUT_DURATION)

    fun confirmedOpacity(t: Double): Double =
        progress(t, Approval.CONFIRMED_IN, Approval.CONFIRMED_IN_DURATION)

    /** The "Awaiting approval" status in the tool row. */
    fun waitOpacity(t: Double): Double =
        1 - progress(t, Approval.WAIT_OUT, Approval.WAIT_OUT_DURATION)

    /** The "Allowed" status that replaces it. */
    fun allowedOpacity(t: Double): Double =
        progress(t, Approval.ALLOWED_IN, Approval.ALLOWED_IN_DURATION)

    data class Pulse(val scale: Double, val opacity: Double)

    /** The orange dot's ring pulses [Approval.PULSE_COUNT] times, never forever. */
    fun pulse(t: Double): Pulse {
        val e = t - Approval.PULSE
        if (!t.isFinite() || e < 0 || e >= Approval.PULSE_DURATION * Approval.PULSE_COUNT) return Pulse(1.0, 0.0)
        val cycle = (e % Approval.PULSE_DURATION) / Approval.PULSE_DURATION
        return Pulse(1 + 1.6 * easeOut(cycle), 0.5 * (1 - cycle))
    }

    // Page 4

    data class Drop(val opacity: Double, val offset: Double, val scale: Double)

    /** A notification card falls from 26 above at 0.96x into place, with a slight overshoot. */
    fun drop(t: Double, delay: Double): Drop {
        val p = progress(t, delay, Notifications.DURATION)
        val e = overshoot(p)
        return Drop(min(1.0, e), -26 * (1 - e), 0.96 + 0.04 * e)
    }
}
