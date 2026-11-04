package dev.hailab.deeppilot.ui.onboarding

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.hailab.deeppilot.core.onboarding.OnboardingMotion

/**
 * Seconds since this page became the current one, for the entrance timeline (iOS
 * `OnboardingTimelineHost`).
 *
 * The rest state is the final state: an inactive page, a finished page, and any page while
 * animations are off report [OnboardingMotion.SETTLED], which every motion function maps to the
 * final picture. So leaving a page never snaps it back to its start.
 *
 * It ticks once per frame, and only while it has something to draw:
 * - a page that is not current does not tick;
 * - once the timeline is over, a page without a looping effect stops ticking and reports settled;
 * - a page with a looping effect (the link flow on page 1, the caret blink on page 2) keeps
 *   ticking while it is current, and stops as soon as the user leaves.
 *
 * @param active whether this is the current page
 * @param animationsOn false when the system animation scale is 0 ("remove animations")
 */
@Composable
fun rememberOnboardingTime(active: Boolean, animationsOn: Boolean, duration: Double, loops: Boolean): Double {
    val playing = active && animationsOn
    // Keyed on [playing] so the first frame after activation already starts at 0: a page that
    // briefly showed its final state and then jumped back to the start would flash.
    var time by remember(playing) { mutableDoubleStateOf(if (playing) 0.0 else OnboardingMotion.SETTLED) }
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) {
            val elapsed = (withFrameNanos { it } - start) / NANOS
            if (!loops && elapsed >= duration) {
                time = OnboardingMotion.SETTLED
                break
            }
            time = elapsed
        }
    }
    return time
}

/**
 * Android's counterpart of iOS "Reduce Motion": the system animation scale. "Remove animations"
 * in Accessibility (and Developer options) sets it to 0, which is the signal to show final frames.
 */
fun animationsEnabled(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f

/** Rise from below while fading in, for titles, body text and small chips. */
fun Modifier.onboardingRise(t: Double, delay: Double): Modifier = graphicsLayer {
    val rise = OnboardingMotion.rise(t, delay)
    alpha = rise.opacity.toFloat()
    translationY = rise.offset.toFloat().dp.toPx()
}

private const val NANOS = 1_000_000_000.0
