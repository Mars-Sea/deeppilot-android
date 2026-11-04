package dev.hailab.deeppilot.core.onboarding

/** How the tour was opened. */
enum class OnboardingMode {
    /** Right after the first pairing: records completion, and the last page asks for notifications. */
    FirstRun,

    /** From Settings: records nothing, asks for no permission, and closes when finished. */
    Replay,
}

/** What the primary button does when tapped. */
enum class OnboardingAction {
    Next,
    RequestNotifications,
    OpenSystemSettings,
    Finish,
}

/**
 * The pager's rules: which page, which controls show, what the main button does (iOS
 * `OnboardingFlow`). No UI code, so it can be unit tested.
 */
data class OnboardingFlow(val requestedIndex: Int) {
    val index: Int = requestedIndex.coerceIn(0, PAGE_COUNT - 1)

    val isFirst: Boolean get() = index == 0
    val isLast: Boolean get() = index == PAGE_COUNT - 1

    /** The first page has no previous page; the back control keeps its place but is not shown. */
    val showsBack: Boolean get() = !isFirst

    /** The last page is already the last step, so skipping it is meaningless. */
    val showsSkip: Boolean get() = !isLast

    fun primaryAction(notify: NotifyState, replay: Boolean = false): OnboardingAction {
        if (!isLast) return OnboardingAction.Next
        if (replay) return OnboardingAction.Finish
        return when (notify) {
            NotifyState.Undecided -> OnboardingAction.RequestNotifications
            NotifyState.Granted -> OnboardingAction.Finish
            NotifyState.Denied -> OnboardingAction.OpenSystemSettings
        }
    }

    /**
     * "Not now" appears only on the last page and only while notifications are still off.
     * A replay never touches permissions, so it has nothing to postpone.
     */
    fun showsLater(notify: NotifyState, replay: Boolean = false): Boolean =
        isLast && notify != NotifyState.Granted && !replay

    companion object {
        const val PAGE_COUNT = 4
    }
}
