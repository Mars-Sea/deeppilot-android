package dev.hailab.deeppilot.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.hailab.deeppilot.R
import dev.hailab.deeppilot.core.onboarding.NotifyState
import dev.hailab.deeppilot.core.onboarding.OnboardingAction
import dev.hailab.deeppilot.core.onboarding.OnboardingFlow
import dev.hailab.deeppilot.core.onboarding.OnboardingMode
import dev.hailab.deeppilot.core.onboarding.OnboardingMotion
import dev.hailab.deeppilot.graph
import dev.hailab.deeppilot.ui.AppLayout
import dev.hailab.deeppilot.ui.AppSpacing
import dev.hailab.deeppilot.ui.AppType
import dev.hailab.deeppilot.ui.components.BarIconButton
import dev.hailab.deeppilot.ui.components.ProminentButton
import dev.hailab.deeppilot.ui.components.plainClickable
import dev.hailab.deeppilot.ui.palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The one-time tour after pairing: four full-screen pages in a pager (iOS `OnboardingView`).
 * Layered over the shell by `AppRoot`; Settings opens it again in [OnboardingMode.Replay].
 *
 * Finishing a first run calls [onComplete] at once, shows "all set" for about 1.2 seconds, then
 * calls [onDismiss]; completion is written first so killing the app in that moment does not show
 * the tour again. A replay records nothing, asks for nothing, and just calls [onDismiss].
 *
 * @param notifyOverride shows a fixed notification state instead of the device's; debug preview only.
 */
@Composable
fun OnboardingScreen(
    mode: OnboardingMode,
    onComplete: () -> Unit,
    onDismiss: () -> Unit,
    notifyOverride: NotifyState? = null,
) {
    val context = LocalContext.current
    val replay = mode == OnboardingMode.Replay
    val pager = rememberPagerState(pageCount = { OnboardingFlow.PAGE_COUNT })
    val scope = rememberCoroutineScope()
    val flow = OnboardingFlow(pager.currentPage)

    var deviceNotify by remember { mutableStateOf(readNotifyState(context)) }
    // The system animation scale is 0 when the user removes animations: then every page shows its
    // final frame, and the buttons switch pages without sliding.
    var animationsOn by remember { mutableStateOf(animationsEnabled(context)) }
    val notify = notifyOverride ?: deviceNotify
    var requesting by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    // The state changes outside the app (system settings), so read it again when coming back.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                deviceNotify = readNotifyState(context)
                animationsOn = animationsEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun finish() {
        if (finished) return
        if (replay) {
            onDismiss()
            return
        }
        onComplete()
        finished = true
    }
    LaunchedEffect(finished) {
        if (!finished) return@LaunchedEffect
        delay(1200)
        onDismiss()
    }

    fun go(index: Int) {
        val target = OnboardingFlow(index).index
        scope.launch { if (animationsOn) pager.animateScrollToPage(target) else pager.scrollToPage(target) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        deviceNotify = readNotifyState(context)
        requesting = false
        finish()
    }

    fun perform(action: OnboardingAction) {
        when (action) {
            OnboardingAction.Next -> go(flow.index + 1)
            OnboardingAction.RequestNotifications -> {
                requesting = true
                // Remember that the dialog was shown: after a refusal the system may not show it again.
                context.graph.onboardingPrefs.notificationAsked = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    requesting = false
                    finish()
                }
            }
            // Stay on the last page: coming back from the settings refreshes the state, and the
            // main button then becomes "Done".
            OnboardingAction.OpenSystemSettings -> openNotificationSettings(context)
            OnboardingAction.Finish -> finish()
        }
    }

    // Back goes to the previous page; on the first page the system default applies (leave the app,
    // or pop the replay route).
    BackHandler(enabled = flow.index > 0 && !finished) { go(flow.index - 1) }

    val p = palette
    Box(
        Modifier
            .fillMaxSize()
            .background(p.background)
            // Swallow touches so the shell underneath cannot be tapped through the overlay.
            .plainClickable(enabled = true) {},
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.widthIn(max = COLUMN_WIDTH).fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Header(flow, onBack = { go(flow.index - 1) }, onSkip = ::finish)
            HorizontalPager(pager, Modifier.weight(1f)) { index ->
                // targetPage, not currentPage: it already points at the destination when a button
                // starts a scroll, so the entrance starts with the slide instead of half-way through.
                OnboardingPage(index, notify, active = pager.targetPage == index, animationsOn = animationsOn)
            }
            Controls(
                flow = flow,
                notify = notify,
                replay = replay,
                requesting = requesting,
                onPage = ::go,
                onPrimary = { perform(flow.primaryAction(notify, replay)) },
                onLater = ::finish,
            )
        }
        if (finished) FinishedState()
    }
}

/** Back and Skip keep their place; when not applicable they are replaced by an empty spacer of the same size. */
@Composable
private fun Header(flow: OnboardingFlow, onBack: () -> Unit, onSkip: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = AppSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (flow.showsBack) {
            BarIconButton(
                Icons.AutoMirrored.Filled.ArrowBackIos,
                stringResource(R.string.onboarding_back),
                onBack,
                iconSize = 20.dp,
            )
        } else {
            Spacer(Modifier.size(44.dp))
        }
        if (flow.showsSkip) {
            Box(
                Modifier
                    .heightIn(min = 44.dp)
                    .plainClickable(onClick = onSkip)
                    .padding(horizontal = AppSpacing.regular),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.onboarding_skip),
                    style = AppType.subheadline.copy(fontWeight = FontWeight.Medium),
                    color = palette.muted,
                )
            }
        } else {
            Spacer(Modifier.heightIn(min = 44.dp))
        }
    }
}

@Composable
private fun Controls(
    flow: OnboardingFlow,
    notify: NotifyState,
    replay: Boolean,
    requesting: Boolean,
    onPage: (Int) -> Unit,
    onPrimary: () -> Unit,
    onLater: () -> Unit,
) {
    val p = palette
    Column(
        Modifier.fillMaxWidth().padding(horizontal = AppSpacing.section).padding(bottom = AppSpacing.small),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Page dots: the current one is longer and brand blue. Each dot is a 28x44 tap target;
        // for TalkBack the row is one element reading "Page n of 4".
        val indicatorText = stringResource(R.string.onboarding_page_indicator, flow.index + 1, OnboardingFlow.PAGE_COUNT)
        val nextLabel = stringResource(R.string.onboarding_next_page)
        val previousLabel = stringResource(R.string.onboarding_back)
        // TalkBack gets the page change as actions on that one element, the way iOS offers it as
        // an adjustable element; the individual dots stay out of the accessibility tree.
        Row(
            Modifier.clearAndSetSemantics {
                contentDescription = indicatorText
                customActions = listOf(
                    CustomAccessibilityAction(nextLabel) { onPage(flow.index + 1); true },
                    CustomAccessibilityAction(previousLabel) { onPage(flow.index - 1); true },
                )
            },
        ) {
            for (index in 0 until OnboardingFlow.PAGE_COUNT) {
                Box(
                    Modifier.size(width = 28.dp, height = 44.dp).plainClickable { onPage(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(width = if (index == flow.index) 22.dp else 8.dp, height = 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (index == flow.index) p.accent else p.foreground.copy(alpha = 0.16f)),
                    )
                }
            }
        }

        ProminentButton(
            text = primaryTitle(flow.primaryAction(notify, replay)),
            onClick = onPrimary,
            modifier = Modifier.fillMaxWidth(),
            enabled = !requesting,
            busy = requesting,
            radius = (AppLayout.largeControlHeight / 2),
            height = AppLayout.largeControlHeight,
        )

        // "Not now" keeps its place: it shows only on the last page while notifications are off,
        // otherwise an empty slot of the same minimum height keeps the main button from jumping. The
        // height is a minimum, not fixed: at large font scales the label is taller and must not be clipped.
        Box(Modifier.fillMaxWidth().heightIn(min = 44.dp), contentAlignment = Alignment.Center) {
            if (flow.showsLater(notify, replay)) {
                Text(
                    stringResource(R.string.onboarding_later),
                    style = AppType.subheadline.copy(fontWeight = FontWeight.Medium),
                    color = p.muted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .plainClickable(enabled = !requesting, onClick = onLater)
                        .padding(vertical = 12.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun primaryTitle(action: OnboardingAction): String = stringResource(
    when (action) {
        OnboardingAction.Next -> R.string.onboarding_next
        OnboardingAction.RequestNotifications -> R.string.onboarding_enable_notifications
        OnboardingAction.OpenSystemSettings -> R.string.onboarding_open_settings
        OnboardingAction.Finish -> R.string.onboarding_done
    },
)

/** One page: the stage above, then the title and body. A tall page (large text) scrolls as a whole. */
@Composable
private fun OnboardingPage(index: Int, notify: NotifyState, active: Boolean, animationsOn: Boolean) {
    val t = rememberOnboardingTime(
        active = active,
        animationsOn = animationsOn,
        duration = OnboardingMotion.duration(index),
        loops = OnboardingMotion.loops(index),
    )
    // At large font scales the stage is dropped: its text is a fixed size and would be tiny next to
    // the page text, or fill the screen if scaled up; the title and body already say it all.
    val showsStage = LocalDensity.current.fontScale < HIDE_STAGE_FONT_SCALE
    val title = stringResource(
        when (index) {
            0 -> R.string.onboarding_p1_title
            1 -> R.string.onboarding_p2_title
            2 -> R.string.onboarding_p3_title
            else -> R.string.onboarding_p4_title
        },
    )
    val body = stringResource(
        when (index) {
            0 -> R.string.onboarding_p1_body
            1 -> R.string.onboarding_p2_body
            2 -> R.string.onboarding_p3_body
            else -> when (notify) {
                NotifyState.Undecided -> R.string.onboarding_p4_body_undecided
                NotifyState.Granted -> R.string.onboarding_p4_body_granted
                NotifyState.Denied -> R.string.onboarding_p4_body_denied
            }
        },
    )
    val stageDescription = stringResource(
        when (index) {
            0 -> R.string.onboarding_p1_a11y
            1 -> R.string.onboarding_p2_a11y
            2 -> R.string.onboarding_p3_a11y
            else -> R.string.onboarding_p4_a11y
        },
    )
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.section)
            .padding(top = AppSpacing.compact, bottom = AppSpacing.regular),
    ) {
        if (showsStage) {
            Box(Modifier.clearAndSetSemantics { contentDescription = stageDescription }) {
                when (index) {
                    0 -> OnboardingConnectedStage(t)
                    1 -> OnboardingSessionStage(t)
                    // With animations off the approval page shows its pending sheet still.
                    2 -> OnboardingApprovalStage(t, staticPending = !animationsOn)
                    else -> OnboardingNotificationStage(t)
                }
            }
        }
        Text(
            title,
            style = AppType.title.copy(fontWeight = FontWeight.Bold),
            color = palette.foreground,
            modifier = Modifier
                .padding(top = if (showsStage) 30.dp else AppSpacing.regular)
                .onboardingRise(t, OnboardingMotion.Copy.TITLE)
                // A polite live region: when the page changes, TalkBack reads the new title.
                .semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                },
        )
        Text(
            body,
            style = AppType.body,
            color = palette.muted,
            modifier = Modifier.padding(top = AppSpacing.compact).onboardingRise(t, OnboardingMotion.Copy.BODY),
        )
    }
}

/** The closing moment of a first run: a check in a ring, "all set", then the overlay fades out. */
@Composable
private fun FinishedState() {
    val p = palette
    Column(
        Modifier.fillMaxSize().background(p.background).padding(horizontal = AppSpacing.section),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.section, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(p.foreground, radius = size.minDimension / 2 - 1.dp.toPx(), style = Stroke(width = 2.dp.toPx()))
            }
            Icon(Icons.Filled.Check, null, Modifier.size(32.dp), tint = p.foreground)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Text(
                stringResource(R.string.onboarding_finish_title),
                style = AppType.title.copy(fontWeight = FontWeight.Bold),
                color = p.foreground,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.onboarding_finish_subtitle),
                style = AppType.body,
                color = p.muted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Reads the device's notification state for the last page; see [NotifyState.from] for the rules. */
private fun readNotifyState(context: Context): NotifyState {
    val graph = context.graph
    val permissionRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val permissionGranted = !permissionRequired ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    return NotifyState.from(
        canPost = graph.notifier.canPost(),
        permissionRequired = permissionRequired,
        permissionGranted = permissionGranted,
        asked = graph.onboardingPrefs.notificationAsked,
    )
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    runCatching { context.startActivity(intent) }
}

/** The content column's width cap on tablets and unfolded foldables. */
private val COLUMN_WIDTH = 480.dp

/** From this font scale up, the stage is hidden (iOS hides it at accessibility text sizes). */
private const val HIDE_STAGE_FONT_SCALE = 1.5f
