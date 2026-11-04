package dev.hailab.deeppilot.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import dev.hailab.deeppilot.R
import dev.hailab.deeppilot.core.onboarding.OnboardingMotion
import dev.hailab.deeppilot.ui.AppRadius
import dev.hailab.deeppilot.ui.palette

// The "stage" above each onboarding page: a small picture drawn from DeepPilot's own interface
// elements. It is an illustration, not a working screen: the page exposes one description to
// TalkBack instead of its parts. Text inside is a fixed size (see StageText), because it is part
// of the picture and has to scale with it; the page title and body follow the user's text size.
// Brand blue appears only on the primary action and the link, orange only for "waiting on you",
// red only for errors.
//
// Every stage takes `t`, the seconds since the page became current. OnboardingMotion.SETTLED means
// the final picture: that is what an inactive page, a finished page, and a page with animations
// off all show. The timeline itself is in OnboardingMotion.

/** Card color on the stage: white in light mode, the grouped surface in dark mode. */
private val stageCard: Color
    @Composable get() = if (palette.dark) palette.surface else palette.background

/** A fixed on-screen size that ignores the user's font scale (iOS fixed-size system fonts). */
@Composable
private fun fixedSp(size: Int): TextUnit = with(LocalDensity.current) { size.dp.toSp() }

@Composable
private fun StageText(
    text: String,
    size: Int,
    modifier: Modifier = Modifier,
    color: Color = palette.foreground,
    weight: FontWeight? = null,
    mono: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fixedSp(size),
        lineHeight = fixedSp((size * 1.35f).toInt()),
        fontWeight = weight,
        fontFamily = if (mono) FontFamily.Monospace else null,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The shared stage container: one panel radius and height, content lays itself out. */
@Composable
fun OnboardingStage(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .height(STAGE_HEIGHT)
            .clip(RoundedCornerShape(AppRadius.panel))
            .background(palette.mutedSurface),
        content = content,
    )
}

private val STAGE_HEIGHT = 320.dp

// Page 1: connected

@Composable
fun OnboardingConnectedStage(t: Double = OnboardingMotion.SETTLED) {
    OnboardingStage {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OnboardingConnectionArt(t)
            Row(
                Modifier
                    .onboardingRise(t, OnboardingMotion.Connected.CHIP)
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(stageCard)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Outlined.VerifiedUser, null, Modifier.size(16.dp), tint = palette.foreground)
                StageText(stringResource(R.string.onboarding_p1_chip), 14, weight = FontWeight.Medium)
            }
        }
    }
}

// Page 2: a live session

@Composable
fun OnboardingSessionStage(t: Double = OnboardingMotion.SETTLED) {
    val s = OnboardingMotion.Session
    OnboardingStage {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // The user's message: a soft bubble, right aligned.
            Row(Modifier.fillMaxWidth().onboardingRise(t, s.BUBBLE), horizontalArrangement = Arrangement.End) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(AppRadius.large))
                        .background(stageCard)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) { StageText(stringResource(R.string.onboarding_p2_user), 15) }
            }
            ToolRow(t)
            // The assistant's reply has no bubble: it sits directly on the stage and types out. The
            // first line's caret goes away when it is written, the second line's keeps blinking.
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TypedText(stringResource(R.string.onboarding_p2_line1), t, s.LINE1, s.LINE1_DURATION, blinking = false)
                TypedText(stringResource(R.string.onboarding_p2_line2), t, s.LINE2, s.LINE2_DURATION, blinking = true)
            }
            Spacer(Modifier.weight(1f))
            // The composer: only its outline, at the bottom of the stage.
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(AppRadius.large))
                    .background(stageCard)
                    .padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StageText(stringResource(R.string.onboarding_p2_composer), 15, Modifier.weight(1f), color = palette.muted)
                Box(Modifier.size(36.dp).clip(CircleShape).background(palette.mutedSurface), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.ArrowUpward, null, Modifier.size(14.dp), tint = palette.muted)
                }
            }
        }
    }
}

/** The tool call: it spins first, and the spinner turns into a check when it is done. */
@Composable
private fun ToolRow(t: Double) {
    val s = OnboardingMotion.Session
    val spin = OnboardingMotion.spinner(t)
    val done = OnboardingMotion.pop(t, s.TOOL_DONE, 0.55)
    val muted = palette.muted
    Row(
        Modifier
            .onboardingRise(t, s.TOOL)
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(AppRadius.medium))
            .background(stageCard)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.Description, null, Modifier.size(16.dp), tint = palette.foreground)
        StageText(stringResource(R.string.onboarding_p2_tool), 14, Modifier.weight(1f), maxLines = 1)
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier
                    .size(16.dp)
                    .graphicsLayer {
                        alpha = spin.opacity.toFloat()
                        rotationZ = spin.degrees.toFloat()
                    },
            ) {
                val stroke = 1.8.dp.toPx()
                drawArc(
                    muted, startAngle = -90f, sweepAngle = 90f, useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            Icon(
                Icons.Filled.Check, null,
                Modifier.size(14.dp).graphicsLayer {
                    alpha = done.opacity.toFloat()
                    scaleX = done.scale.toFloat()
                    scaleY = done.scale.toFloat()
                },
                tint = muted,
            )
        }
    }
}

/**
 * One line of the reply, typed character by character. The full text, plus the caret, is laid out
 * invisibly first so the line never re-wraps while it types and the caret never pushes the last
 * word off the line. The caret is the text cursor character, not an icon; it is shown or hidden by
 * color so its width stays put.
 */
@Composable
private fun TypedText(text: String, t: Double, delay: Double, duration: Double, blinking: Boolean) {
    val count = OnboardingMotion.typedCount(t, delay, duration, text.length)
    val caretVisible = t.isFinite() && t >= delay &&
        if (blinking) OnboardingMotion.caretOn(t, delay) else t < delay + duration
    val ink = palette.foreground
    val size = fixedSp(15)
    val lineHeight = fixedSp(20)
    Box {
        Text(text + CARET, color = Color.Transparent, fontSize = size, lineHeight = lineHeight)
        Text(
            buildAnnotatedString {
                append(text.take(count))
                withStyle(SpanStyle(color = if (caretVisible) ink else Color.Transparent)) { append(CARET) }
            },
            color = ink, fontSize = size, lineHeight = lineHeight,
        )
    }
}

private const val CARET = "|"

// Page 3: an approval

/**
 * The approval: the pending row, the sheet sliding in, "Allow" pressed, the sheet leaving and the
 * status turning to "Allowed".
 *
 * With animations off, the final frame would be "approved, sheet gone", which no longer explains
 * the page. So [staticPending] shows the pending sheet still instead (the one exception to "rest
 * state is the final state").
 */
@Composable
fun OnboardingApprovalStage(t: Double = OnboardingMotion.SETTLED, staticPending: Boolean = false) {
    val a = OnboardingMotion.Approval
    val sheetOffset = if (staticPending) 0.0 else OnboardingMotion.approvalSheetOffset(t)
    val waitOpacity = if (staticPending) 1.0 else OnboardingMotion.waitOpacity(t)
    val allowedOpacity = if (staticPending) 0.0 else OnboardingMotion.allowedOpacity(t)
    OnboardingStage {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StageText(stringResource(R.string.onboarding_p3_assistant), 15, Modifier.onboardingRise(t, a.ASSISTANT))
            Row(
                Modifier
                    .onboardingRise(t, a.TOOL)
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(AppRadius.medium))
                    .background(stageCard)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Outlined.Terminal, null, Modifier.size(16.dp), tint = palette.foreground)
                StageText(COMMAND, 13, Modifier.weight(1f), mono = true, maxLines = 1)
                // The status swaps from "Awaiting approval" to "Allowed" in the same place.
                Box(contentAlignment = Alignment.CenterEnd) {
                    Row(
                        Modifier.graphicsLayer { alpha = waitOpacity.toFloat() },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.size(8.dp).background(palette.warning, CircleShape))
                        StageText(stringResource(R.string.onboarding_p3_status), 13, color = palette.muted)
                    }
                    Row(
                        Modifier.graphicsLayer { alpha = allowedOpacity.toFloat() },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Filled.Check, null, Modifier.size(12.dp), tint = palette.foreground)
                        StageText(stringResource(R.string.onboarding_p3_allowed), 13)
                    }
                }
            }
        }
        ApprovalSheet(t, staticPending, sheetOffset)
    }
}

/** The sheet mirrors the real approval card: label, title, command, Deny / Allow. */
@Composable
private fun BoxScope.ApprovalSheet(t: Double, staticPending: Boolean, offset: Double) {
    val pulse = OnboardingMotion.pulse(t)
    val buttonsOpacity = if (staticPending) 1.0 else OnboardingMotion.buttonsOpacity(t)
    val confirmedOpacity = if (staticPending) 0.0 else OnboardingMotion.confirmedOpacity(t)
    val press = if (staticPending) 1.0 else OnboardingMotion.pressScale(t)
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .graphicsLayer { translationY = (offset * SHEET_TRAVEL.dp.toPx()).toFloat() }
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(stageCard)
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 20.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .width(36.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(palette.foreground.copy(alpha = 0.16f)),
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp)) {
                // The ring pulses twice, never forever.
                Box(
                    Modifier
                        .size(8.dp)
                        .graphicsLayer {
                            scaleX = if (staticPending) 1f else pulse.scale.toFloat()
                            scaleY = scaleX
                            alpha = if (staticPending) 0f else pulse.opacity.toFloat()
                        }
                        .background(palette.warning, CircleShape),
                )
                Box(Modifier.size(8.dp).background(palette.warning, CircleShape))
            }
            StageText(stringResource(R.string.onboarding_p3_sheet_kind), 12, color = palette.muted)
        }
        StageText(
            stringResource(R.string.onboarding_p3_sheet_title), 17,
            Modifier.padding(top = 6.dp), weight = FontWeight.SemiBold,
        )
        Box(
            Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppRadius.small))
                .background(palette.mutedSurface)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) { StageText(COMMAND, 13, mono = true) }
        Box(Modifier.padding(top = 14.dp).fillMaxWidth().height(44.dp)) {
            Row(
                Modifier.fillMaxSize().graphicsLayer { alpha = buttonsOpacity.toFloat() },
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StageButton(stringResource(R.string.onboarding_p3_deny), palette.mutedSurface, palette.foreground, Modifier.weight(1f))
                StageButton(
                    stringResource(R.string.onboarding_p3_allow), palette.accent, Color.White,
                    Modifier.weight(1f).graphicsLayer {
                        scaleX = press.toFloat()
                        scaleY = press.toFloat()
                    },
                )
            }
            // After the press the buttons give way to "Allowed".
            Row(
                Modifier.fillMaxSize().graphicsLayer { alpha = confirmedOpacity.toFloat() },
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Check, null, Modifier.size(18.dp), tint = palette.foreground)
                StageText(stringResource(R.string.onboarding_p3_allowed), 16, weight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun StageButton(text: String, fill: Color, textColor: Color, modifier: Modifier) {
    Box(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(AppRadius.medium))
            .background(fill),
        contentAlignment = Alignment.Center,
    ) { StageText(text, 16, color = textColor, weight = FontWeight.SemiBold) }
}

/** The sample command shown in the approval illustration; not localized, it is code. */
private const val COMMAND = "npm test -- auth"

/** How far the sheet travels to leave the stage: a little more than its height. */
private const val SHEET_TRAVEL = 230

// Page 4: notifications

@Composable
fun OnboardingNotificationStage(t: Double = OnboardingMotion.SETTLED) {
    val delays = OnboardingMotion.Notifications.DELAYS
    OnboardingStage {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        ) {
            NotificationCard(
                Icons.Outlined.CheckCircle, palette.foreground,
                stringResource(R.string.onboarding_p4_n1_title), stringResource(R.string.onboarding_p4_n1_body),
                stringResource(R.string.onboarding_p4_n1_time), t, delays[0],
            )
            NotificationCard(
                Icons.Outlined.Schedule, palette.warning,
                stringResource(R.string.onboarding_p4_n2_title), stringResource(R.string.onboarding_p4_n2_body),
                stringResource(R.string.onboarding_p4_n2_time), t, delays[1],
            )
            NotificationCard(
                Icons.Outlined.WarningAmber, palette.danger,
                stringResource(R.string.onboarding_p4_n3_title), stringResource(R.string.onboarding_p4_n3_body),
                stringResource(R.string.onboarding_p4_n3_time), t, delays[2],
            )
        }
    }
}

/** The three notifications differ by icon shape and words; color only helps, it never carries the meaning alone. */
@Composable
private fun NotificationCard(
    icon: ImageVector, tint: Color, title: String, body: String, time: String, t: Double, delay: Double,
) {
    val drop = OnboardingMotion.drop(t, delay)
    Row(
        Modifier
            .graphicsLayer {
                alpha = drop.opacity.toFloat()
                translationY = drop.offset.toFloat().dp.toPx()
                scaleX = drop.scale.toFloat()
                scaleY = drop.scale.toFloat()
            }
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.large))
            .background(stageCard)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(palette.mutedSurface),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(20.dp), tint = tint) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                StageText(title, 14, weight = FontWeight.SemiBold)
                StageText(time, 12, color = palette.muted)
            }
            StageText(body, 13, color = palette.muted, maxLines = 2)
        }
    }
}
