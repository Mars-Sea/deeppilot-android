package dev.hailab.deeppilot.core

import dev.hailab.deeppilot.core.onboarding.NotifyState
import dev.hailab.deeppilot.core.onboarding.OnboardingAction
import dev.hailab.deeppilot.core.onboarding.OnboardingFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pager's controls, the last page's button branches, and the notification state mapping. */
class OnboardingFlowTest {
    private val last = OnboardingFlow(OnboardingFlow.PAGE_COUNT - 1)

    // NotifyState

    @Test
    fun postableNotificationsAreAlwaysGranted() {
        for (required in listOf(true, false)) for (asked in listOf(true, false)) {
            assertEquals(
                NotifyState.Granted,
                NotifyState.from(canPost = true, permissionRequired = required, permissionGranted = required, asked = asked),
            )
        }
    }

    @Test
    fun neverAskedOnAnApiWithTheRuntimePermissionIsUndecided() {
        assertEquals(
            NotifyState.Undecided,
            NotifyState.from(canPost = false, permissionRequired = true, permissionGranted = false, asked = false),
        )
    }

    @Test
    fun afterTheDialogWasShownARefusalIsDenied() {
        // From then on the system may stop showing the dialog, so the user goes to the settings.
        assertEquals(
            NotifyState.Denied,
            NotifyState.from(canPost = false, permissionRequired = true, permissionGranted = false, asked = true),
        )
    }

    @Test
    fun belowApi33SwitchedOffNotificationsAreDenied() {
        // No runtime permission exists there: not posting means the user switched them off.
        for (asked in listOf(true, false)) {
            assertEquals(
                NotifyState.Denied,
                NotifyState.from(canPost = false, permissionRequired = false, permissionGranted = false, asked = asked),
            )
        }
    }

    @Test
    fun grantedPermissionWithNotificationsSwitchedOffIsDenied() {
        // The dialog cannot help here, even if the app never asked: only the settings can.
        assertEquals(
            NotifyState.Denied,
            NotifyState.from(canPost = false, permissionRequired = true, permissionGranted = true, asked = false),
        )
    }

    // OnboardingFlow

    @Test
    fun indexIsClampedToThePageRange() {
        assertEquals(0, OnboardingFlow(-3).index)
        assertEquals(OnboardingFlow.PAGE_COUNT - 1, OnboardingFlow(99).index)
    }

    @Test
    fun headerControlsFollowThePosition() {
        val first = OnboardingFlow(0)
        assertFalse(first.showsBack)
        assertTrue(first.showsSkip)

        val middle = OnboardingFlow(1)
        assertTrue(middle.showsBack)
        assertTrue(middle.showsSkip)

        assertTrue(last.showsBack)
        assertFalse("the last page is the last step", last.showsSkip)
    }

    @Test
    fun primaryActionIsNextBeforeTheLastPage() {
        for (index in 0 until OnboardingFlow.PAGE_COUNT - 1) for (state in NotifyState.entries) {
            assertEquals(OnboardingAction.Next, OnboardingFlow(index).primaryAction(state))
            assertFalse(OnboardingFlow(index).showsLater(state))
        }
    }

    @Test
    fun lastPageBranchesOnTheNotificationState() {
        assertEquals(OnboardingAction.RequestNotifications, last.primaryAction(NotifyState.Undecided))
        assertTrue(last.showsLater(NotifyState.Undecided))

        assertEquals(OnboardingAction.Finish, last.primaryAction(NotifyState.Granted))
        assertFalse("nothing left to postpone once granted", last.showsLater(NotifyState.Granted))

        assertEquals(OnboardingAction.OpenSystemSettings, last.primaryAction(NotifyState.Denied))
        assertTrue(last.showsLater(NotifyState.Denied))
    }

    @Test
    fun replayNeverAsksForPermissionsAndAlwaysEndsWithFinish() {
        for (state in NotifyState.entries) {
            assertEquals(OnboardingAction.Finish, last.primaryAction(state, replay = true))
            assertFalse(last.showsLater(state, replay = true))
        }
        // Replay does not change the earlier pages: still one page at a time.
        assertEquals(OnboardingAction.Next, OnboardingFlow(1).primaryAction(NotifyState.Undecided, replay = true))
    }
}
