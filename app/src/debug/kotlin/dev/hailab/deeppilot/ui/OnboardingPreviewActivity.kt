package dev.hailab.deeppilot.ui

import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.hailab.deeppilot.core.onboarding.NotifyState
import dev.hailab.deeppilot.core.onboarding.OnboardingMode
import dev.hailab.deeppilot.data.AppearanceOption
import dev.hailab.deeppilot.ui.onboarding.OnboardingScreen

/**
 * Debug-only offline entry: shows the onboarding on its own, with no paired host. Extras:
 * `notify` = undecided | granted | denied (default: the device's real state), `replay` (boolean),
 * `dark` (boolean). Finishing closes the activity. It records no completion state, because the
 * completion callback is left empty.
 */
class OnboardingPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The status bar icons follow the preview's own light / dark choice, not the system's,
        // otherwise a light page under a dark system theme gets white icons on white.
        val dark = intent.getBooleanExtra("dark", false)
        val bars = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        super.onCreate(savedInstanceState)
        val notify = when (intent.getStringExtra("notify")) {
            "undecided" -> NotifyState.Undecided
            "granted" -> NotifyState.Granted
            "denied" -> NotifyState.Denied
            else -> null
        }
        val mode = if (intent.getBooleanExtra("replay", false)) OnboardingMode.Replay else OnboardingMode.FirstRun
        val appearance = if (dark) AppearanceOption.Dark else AppearanceOption.Light
        setContent {
            DeepPilotTheme(appearance = appearance) {
                OnboardingScreen(mode = mode, onComplete = {}, onDismiss = { finish() }, notifyOverride = notify)
            }
        }
    }
}
