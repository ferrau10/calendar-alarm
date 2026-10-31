package com.calendaralarm.ui.onboarding

/** First-run steps, in order. Steps already satisfied are skipped. */
enum class OnboardingStep { Welcome, Connect, Notifications, ExactAlarms, FullScreen, Battery, Done }

/** Inputs that decide where onboarding stands. */
data class OnboardingFacts(
    val welcomeSeen: Boolean,
    val connected: Boolean,
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val fullScreenAlarms: Boolean,
    val ignoresBatteryOptimizations: Boolean,
    /** The user saw the optional battery step (allowed or skipped it). */
    val batteryAsked: Boolean,
)

object OnboardingFlow {
    fun step(facts: OnboardingFacts): OnboardingStep = when {
        !facts.welcomeSeen -> OnboardingStep.Welcome
        !facts.connected -> OnboardingStep.Connect
        !facts.notifications -> OnboardingStep.Notifications
        !facts.exactAlarms -> OnboardingStep.ExactAlarms
        !facts.fullScreenAlarms -> OnboardingStep.FullScreen
        !facts.ignoresBatteryOptimizations && !facts.batteryAsked -> OnboardingStep.Battery
        else -> OnboardingStep.Done
    }
}
