package dev.hailab.deeppilot.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import dev.hailab.deeppilot.core.onboarding.OnboardingMotion
import dev.hailab.deeppilot.ui.palette

/**
 * The line art on onboarding page 1: a QR code on the computer screen, a dashed link to the phone,
 * and a check on the phone.
 *
 * It is drawn in the same 256x96 design space as the iOS `OnboardingConnectionArt`, scaled to the
 * canvas, so line widths and proportions match across platforms. Android's pairing screen has no
 * such diagram, so this is new here.
 *
 * [t] is the seconds since the page became current; [OnboardingMotion.SETTLED] draws the connected
 * final state: outlines complete, the link still, a check on the phone. The order of events is in
 * [OnboardingMotion.Connected].
 */
@Composable
fun OnboardingConnectionArt(t: Double = OnboardingMotion.SETTLED, modifier: Modifier = Modifier) {
    val ink = palette.foreground
    val link = palette.accent
    Canvas(modifier.size(DESIGN_WIDTH.dp * 300f / 256f, DESIGN_HEIGHT.dp * 300f / 256f)) {
        val s = size.width / DESIGN_WIDTH
        scale(s, s, pivot = Offset.Zero) { draw(t, ink, link) }
    }
}

private fun DrawScope.draw(t: Double, ink: Color, link: Color) {
    fun round(width: Float) = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

    // Computer: screen frame and base.
    strokeDrawn(roundRectPath(14f, 14f, 84f, 54f, 7f), OnboardingMotion.stroke(t, OnboardingMotion.Connected.COMPUTER), ink, round(2f))
    strokeDrawn(line(6f, 76f, 106f, 76f), OnboardingMotion.stroke(t, OnboardingMotion.Connected.BASE), ink, round(2f))

    // The QR code on the screen: a frame and a simplified pattern (four short pieces, because
    // PathMeasure measures one contour at a time).
    strokeDrawn(roundRectPath(43f, 26f, 26f, 26f, 3f), OnboardingMotion.stroke(t, OnboardingMotion.Connected.QR_FRAME), link, round(1.6f))
    val glyph = OnboardingMotion.stroke(t, OnboardingMotion.Connected.QR_GLYPH)
    strokeDrawn(Path().apply { addRect(Rect(Offset(49f, 32f), Size(6f, 6f))) }, glyph, link, round(1.6f))
    strokeDrawn(Path().apply { moveTo(58f, 32f); lineTo(63f, 32f); lineTo(63f, 37f) }, glyph, link, round(1.6f))
    strokeDrawn(line(49f, 43f, 54f, 43f), glyph, link, round(1.6f))
    strokeDrawn(Path().apply { moveTo(58f, 43f); lineTo(63f, 43f); lineTo(63f, 48f) }, glyph, link, round(1.6f))

    // The link: fades in, then flows from the computer to the phone; the final state is still.
    val linkAlpha = OnboardingMotion.linkOpacity(t).toFloat()
    if (linkAlpha > 0f) {
        val phase = OnboardingMotion.linkPhase(t).toFloat()
        // A dash pattern advances along the path when its phase decreases.
        val offset = ((-phase) % DASH_PERIOD + DASH_PERIOD) % DASH_PERIOD
        drawLine(
            link.copy(alpha = linkAlpha), Offset(114f, 46f), Offset(160f, 46f),
            strokeWidth = 2.4f, cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 12f), offset),
        )
    }

    // Phone: body and speaker.
    strokeDrawn(roundRectPath(178f, 6f, 44f, 84f, 9f), OnboardingMotion.stroke(t, OnboardingMotion.Connected.PHONE), ink, round(2f))
    strokeDrawn(line(193f, 14f, 207f, 14f), OnboardingMotion.stroke(t, OnboardingMotion.Connected.SPEAKER), ink, round(2f))

    // The scan frame in the middle of the phone screen: shrinks in, holds, fades; none in the final state.
    val bracket = OnboardingMotion.bracket(t)
    if (bracket.opacity > 0) {
        val color = link.copy(alpha = bracket.opacity.toFloat())
        scale(bracket.scale.toFloat(), bracket.scale.toFloat(), pivot = Offset(200f, 48f)) {
            val corners = listOf(
                Path().apply { moveTo(187f, 42f); lineTo(187f, 35f); lineTo(194f, 35f) },
                Path().apply { moveTo(206f, 35f); lineTo(213f, 35f); lineTo(213f, 42f) },
                Path().apply { moveTo(213f, 54f); lineTo(213f, 61f); lineTo(206f, 61f) },
                Path().apply { moveTo(194f, 61f); lineTo(187f, 61f); lineTo(187f, 54f) },
            )
            for (corner in corners) drawPath(corner, color, style = round(2f))
        }
    }

    // The connected check: the ring pops, then the tick is drawn. Success is the healthy default,
    // so it is neutral rather than brand blue.
    val pop = OnboardingMotion.pop(t, OnboardingMotion.Connected.CHECK, OnboardingMotion.Connected.CHECK_DURATION)
    if (pop.opacity > 0) {
        val color = ink.copy(alpha = pop.opacity.toFloat())
        scale(pop.scale.toFloat(), pop.scale.toFloat(), pivot = Offset(200f, 48f)) {
            drawCircle(color, radius = 13f, center = Offset(200f, 48f), style = round(2f))
            val tick = OnboardingMotion.easeOut(OnboardingMotion.progress(t, OnboardingMotion.Connected.TICK, OnboardingMotion.Connected.TICK_DURATION)).toFloat()
            val check = Path().apply { moveTo(193.5f, 48.5f); lineTo(198f, 53f); lineTo(206.5f, 44f) }
            strokeDrawn(check, tick.toDouble(), color, round(2f))
        }
    }
}

/**
 * Draws [path] up to [progress] (0 nothing, 1 all) of its length. Nothing is drawn at 0, so a
 * round cap does not leave a dot at the start. The path must be a single contour.
 */
private fun DrawScope.strokeDrawn(path: Path, progress: Double, color: Color, style: Stroke) {
    if (progress <= 0.001) return
    if (progress >= 1.0) {
        drawPath(path, color, style = style)
        return
    }
    val measure = PathMeasure().apply { setPath(path, false) }
    val partial = Path()
    measure.getSegment(0f, measure.length * progress.toFloat(), partial, true)
    drawPath(partial, color, style = style)
}

private fun roundRectPath(x: Float, y: Float, w: Float, h: Float, radius: Float) =
    Path().apply { addRoundRect(RoundRect(Rect(Offset(x, y), Size(w, h)), CornerRadius(radius))) }

private fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
    Path().apply { moveTo(x1, y1); lineTo(x2, y2) }

private const val DESIGN_WIDTH = 256f
private const val DESIGN_HEIGHT = 96f
private const val DASH_PERIOD = 16f
