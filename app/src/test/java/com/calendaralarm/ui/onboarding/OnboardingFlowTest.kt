package com.calendaralarm.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingFlowTest {
    private val allDone = OnboardingFacts(
        welcomeSeen = true, connected = true, notifications = true, exactAlarms = true,
        fullScreenAlarms = true, ignoresBatteryOptimizations = true, batteryAsked = false,
    )

    @Test
    fun freshInstallStartsWithWelcome() {
        val fresh = OnboardingFacts(false, false, false, false, false, false, false)
        assertEquals(OnboardingStep.Welcome, OnboardingFlow.step(fresh))
    }

    @Test
    fun stepsComeInOrderAndSatisfiedOnesAreSkipped() {
        assertEquals(OnboardingStep.Connect, OnboardingFlow.step(allDone.copy(connected = false, notifications = false)))
        assertEquals(OnboardingStep.Notifications, OnboardingFlow.step(allDone.copy(notifications = false, exactAlarms = false)))
        assertEquals(OnboardingStep.ExactAlarms, OnboardingFlow.step(allDone.copy(exactAlarms = false, fullScreenAlarms = false)))
        assertEquals(OnboardingStep.FullScreen, OnboardingFlow.step(allDone.copy(fullScreenAlarms = false)))
    }

    @Test
    fun batteryStepIsOptionalAndAskedOnce() {
        assertEquals(OnboardingStep.Battery, OnboardingFlow.step(allDone.copy(ignoresBatteryOptimizations = false)))
        assertEquals(
            OnboardingStep.Done,
            OnboardingFlow.step(allDone.copy(ignoresBatteryOptimizations = false, batteryAsked = true)),
        )
    }

    @Test
    fun everythingGrantedIsDone() {
        assertEquals(OnboardingStep.Done, OnboardingFlow.step(allDone))
    }
}
