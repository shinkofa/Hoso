package com.theermite.hoso.onboarding

/**
 * The four first-launch onboarding steps, in display order. Pure model:
 * the mapping to icon / title / body resources lives in the pager adapter
 * so this stays unit-testable on the plain JVM.
 */
enum class OnboardingStep {
    WELCOME,
    PERMISSIONS,
    CONNECT_CHANNEL,
    GO_LIVE;

    companion object {
        val ordered: List<OnboardingStep> = entries.toList()

        /** Show the onboarding only until the user has seen it once. */
        fun shouldShow(onboardingSeen: Boolean): Boolean = !onboardingSeen
    }
}
