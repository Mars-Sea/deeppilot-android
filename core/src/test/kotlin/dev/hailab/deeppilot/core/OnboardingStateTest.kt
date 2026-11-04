package dev.hailab.deeppilot.core

import dev.hailab.deeppilot.core.onboarding.OnboardingState
import dev.hailab.deeppilot.core.onboarding.OnboardingStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Who sees the tour: existing users never, new users once, finished users not again. */
class OnboardingStateTest {
    private class MemoryStorage(
        override var completedVersion: Int = 0,
        override var armedVersion: Int = 0,
    ) : OnboardingStorage

    @Test
    fun existingPairedUserIsMarkedCompletedWithoutTheTour() {
        val storage = MemoryStorage()
        val state = OnboardingState(storage)
        state.reconcile(paired = true)

        assertFalse(state.needsOnboarding(paired = true))
        assertEquals(1, storage.completedVersion)
        assertEquals(0, storage.armedVersion)
    }

    @Test
    fun newUserSeesTheTourOnceAfterPairing() {
        val state = OnboardingState(MemoryStorage())
        state.reconcile(paired = false)
        assertFalse("not while unpaired", state.needsOnboarding(paired = false))

        // Pairing finished: reconciling again must not mistake a new user for an upgrade.
        state.reconcile(paired = true)
        assertTrue(state.needsOnboarding(paired = true))

        state.markCompleted()
        assertFalse(state.needsOnboarding(paired = true))
    }

    @Test
    fun unreconciledStateNeverShowsTheTour() {
        // Right at launch, before reconcile: an existing user must not see a flash of the tour.
        assertFalse(OnboardingState(MemoryStorage()).needsOnboarding(paired = true))
    }

    @Test
    fun quittingMidTourShowsItAgainOnNextLaunch() {
        val storage = MemoryStorage()
        OnboardingState(storage).apply {
            reconcile(paired = false)
            reconcile(paired = true)
        }

        // A cold start: a new object over the same storage, already paired, never finished.
        val relaunched = OnboardingState(storage)
        relaunched.reconcile(paired = true)
        assertTrue(relaunched.needsOnboarding(paired = true))
    }

    @Test
    fun completionSurvivesRelaunchAndRepairing() {
        val storage = MemoryStorage()
        OnboardingState(storage).apply {
            reconcile(paired = false)
            reconcile(paired = true)
            markCompleted()
        }

        val relaunched = OnboardingState(storage)
        relaunched.reconcile(paired = false) // unpaired
        relaunched.reconcile(paired = true) // paired again
        assertFalse(relaunched.needsOnboarding(paired = true))
    }

    @Test
    fun preseededCompletionSkipsTheTourEvenForAFreshPairing() {
        val storage = MemoryStorage(completedVersion = 1)
        val state = OnboardingState(storage)
        state.reconcile(paired = false)
        state.reconcile(paired = true)

        assertFalse(state.needsOnboarding(paired = true))
        assertEquals("a finished user is not armed again", 0, storage.armedVersion)
    }

    @Test
    fun versionBumpOnlyArmsUnpairedUsers() {
        // Version 1: an existing user.
        val legacy = MemoryStorage()
        OnboardingState(legacy, version = 1).reconcile(paired = true)
        assertEquals(1, legacy.completedVersion)

        // Version 2: that already paired user is silently marked done, not shown the new tour.
        val upgraded = OnboardingState(legacy, version = 2)
        upgraded.reconcile(paired = true)
        assertFalse(upgraded.needsOnboarding(paired = true))
        assertEquals(2, legacy.completedVersion)

        // A brand new device first launched at version 2, still unpaired: armed as usual.
        val fresh = OnboardingState(MemoryStorage(), version = 2)
        fresh.reconcile(paired = false)
        fresh.reconcile(paired = true)
        assertTrue(fresh.needsOnboarding(paired = true))
    }

    @Test
    fun markCompletedNeverLowersAHigherVersion() {
        val storage = MemoryStorage(completedVersion = 3)
        OnboardingState(storage, version = 1).markCompleted()
        assertEquals(3, storage.completedVersion)
    }
}
