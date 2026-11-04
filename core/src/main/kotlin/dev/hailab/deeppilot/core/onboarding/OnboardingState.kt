package dev.hailab.deeppilot.core.onboarding

/** Where the onboarding decision is persisted. The app backs it with SharedPreferences. */
interface OnboardingStorage {
    /** The onboarding version the user has finished (or was judged not to need); 0 means never. */
    var completedVersion: Int

    /** The version at which a not-yet-paired user was first seen; only armed users see the tour. */
    var armedVersion: Int
}

/**
 * Whether the post-pairing onboarding should be shown (iOS `OnboardingStore`).
 *
 * The state is per device, not per host, so adding a second host never repeats the tour:
 * - a user who is already paired the first time [reconcile] runs (an upgrade) is marked done
 *   and never sees the tour;
 * - a user who is not paired yet is armed, and sees the tour once after pairing;
 * - a finished user never sees it again, even after unpairing and pairing anew.
 *
 * Raising [version] only arms users who are still unpaired; it never makes an already
 * paired user watch a new tour. Pushing a new tour to existing users needs its own decision.
 */
class OnboardingState(
    private val storage: OnboardingStorage,
    private val version: Int = CURRENT_VERSION,
) {
    val completedVersion: Int get() = storage.completedVersion
    val armedVersion: Int get() = storage.armedVersion

    /**
     * Call at launch and whenever the pairing state changes, before anything depends on
     * [needsOnboarding]. It does nothing once a conclusion exists, so repeating it is safe.
     */
    fun reconcile(paired: Boolean) {
        if (storage.completedVersion >= version || storage.armedVersion >= version) return
        if (paired) {
            // An upgrade: this user was paired before the tour existed, so they have not "missed" it.
            storage.completedVersion = version
        } else {
            storage.armedVersion = version
        }
    }

    /**
     * Before [reconcile] has run this is false on purpose: showing the tour one frame late is
     * better than flashing it at an existing user during launch.
     */
    fun needsOnboarding(paired: Boolean): Boolean =
        paired && storage.armedVersion >= version && storage.completedVersion < version

    fun markCompleted() {
        if (storage.completedVersion < version) storage.completedVersion = version
    }

    companion object {
        const val CURRENT_VERSION = 1
    }
}
