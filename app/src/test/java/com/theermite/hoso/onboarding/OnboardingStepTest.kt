package com.theermite.hoso.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * First-launch onboarding — the ordered step model and the show/skip
 * decision. Kept as a pure enum + companion so it is unit-testable on the
 * plain JVM; the mapping of each step to its layout/strings lives in the
 * adapter (Android side).
 */
class OnboardingStepTest {

    @Test
    fun should_expose_exactly_four_steps_in_declared_order() {
        assertEquals(
            listOf(
                OnboardingStep.WELCOME,
                OnboardingStep.PERMISSIONS,
                OnboardingStep.CONNECT_CHANNEL,
                OnboardingStep.GO_LIVE,
            ),
            OnboardingStep.ordered
        )
    }

    @Test
    fun should_have_four_enum_entries() {
        assertEquals(4, OnboardingStep.entries.size)
    }

    @Test
    fun should_show_onboarding_when_not_seen_yet() {
        assertTrue(OnboardingStep.shouldShow(onboardingSeen = false))
    }

    @Test
    fun should_not_show_onboarding_once_seen() {
        assertFalse(OnboardingStep.shouldShow(onboardingSeen = true))
    }

    @Test
    fun ordered_size_matches_entries_count() {
        assertEquals(OnboardingStep.entries.size, OnboardingStep.ordered.size)
    }
}
