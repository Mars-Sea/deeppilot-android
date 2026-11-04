package dev.hailab.deeppilot.data

import android.content.Context
import androidx.core.content.edit
import dev.hailab.deeppilot.core.onboarding.OnboardingStorage

/**
 * Persistence for the post-pairing onboarding (iOS `@AppStorage("onboarding.*")`).
 *
 * Kept in its own preferences file, per device rather than per host, so a second host never
 * repeats the tour. The two version keys are read by [dev.hailab.deeppilot.core.onboarding.OnboardingState].
 */
class OnboardingPrefs(context: Context) : OnboardingStorage {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override var completedVersion: Int
        get() = prefs.getInt(KEY_COMPLETED, 0)
        set(value) = prefs.edit { putInt(KEY_COMPLETED, value) }

    override var armedVersion: Int
        get() = prefs.getInt(KEY_ARMED, 0)
        set(value) = prefs.edit { putInt(KEY_ARMED, value) }

    /**
     * Whether the runtime notification permission dialog has been shown once. After a refusal the
     * system may stop showing it, so "asked and not granted" is treated as denied and the user is
     * sent to the system settings instead.
     */
    var notificationAsked: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_ASKED, false)
        set(value) = prefs.edit { putBoolean(KEY_NOTIFICATION_ASKED, value) }

    private companion object {
        const val FILE = "onboarding"
        const val KEY_COMPLETED = "onboarding.completedVersion"
        const val KEY_ARMED = "onboarding.armedVersion"
        const val KEY_NOTIFICATION_ASKED = "onboarding.notificationAsked"
    }
}
