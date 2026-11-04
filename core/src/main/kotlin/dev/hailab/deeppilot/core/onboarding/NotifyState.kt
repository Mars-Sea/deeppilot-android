package dev.hailab.deeppilot.core.onboarding

/**
 * What the last onboarding page sees of the notification permission. The page's button,
 * secondary button and explanation all branch on it.
 */
enum class NotifyState {
    /** Never asked: the primary button shows the system permission dialog. */
    Undecided,

    /** Notifications can be posted: nothing left to ask. */
    Granted,

    /** The system will not (or cannot) ask again: the only way forward is the system settings. */
    Denied;

    companion object {
        /**
         * Folds Android's signals into the three states.
         *
         * @param canPost the notifier can actually post right now (permission granted and the
         *   app's notifications not switched off in the system settings)
         * @param permissionRequired the device has the runtime permission at all (API 33+)
         * @param permissionGranted the runtime permission is currently granted
         * @param asked the app has already shown the runtime permission dialog once; after a
         *   refusal the system may stop showing it, so the app sends the user to the settings
         */
        fun from(
            canPost: Boolean,
            permissionRequired: Boolean,
            permissionGranted: Boolean,
            asked: Boolean,
        ): NotifyState = when {
            canPost -> Granted
            // On API 32 and below there is no dialog: not posting means the user switched it off.
            // A granted permission with notifications still off is the same: the dialog cannot help.
            permissionRequired && !permissionGranted && !asked -> Undecided
            else -> Denied
        }
    }
}
