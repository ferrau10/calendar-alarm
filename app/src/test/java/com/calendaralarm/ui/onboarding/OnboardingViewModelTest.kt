package com.calendaralarm.ui.onboarding

import com.calendaralarm.domain.AuthRepository
import com.calendaralarm.ui.permissions.PermissionState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingViewModelTest {
    private class FakeAuth : AuthRepository {
        override val account = MutableStateFlow<String?>(null)
        override suspend fun accessToken() = "token"
        override suspend fun disconnect() { account.value = null }
    }

    private class MemoryStore : OnboardingStore {
        override var welcomeSeen = false
        override var batteryAsked = false
        override var completed = false
    }

    private val auth = FakeAuth()
    private val store = MemoryStore()
    private var perms = PermissionState(false, false, false, false)
    private val vm = OnboardingViewModel(auth, { perms }, store)

    @Test
    fun walksThroughEveryStepAndCompletes() {
        assertEquals(OnboardingStep.Welcome, vm.step.value)

        vm.welcomeSeen()
        assertEquals(OnboardingStep.Connect, vm.step.value)

        auth.account.value = "me@example.com"
        vm.refresh()
        assertEquals(OnboardingStep.Notifications, vm.step.value)

        perms = perms.copy(notifications = true)
        vm.refresh()
        assertEquals(OnboardingStep.ExactAlarms, vm.step.value)

        perms = perms.copy(exactAlarms = true)
        vm.refresh()
        assertEquals(OnboardingStep.FullScreen, vm.step.value)

        perms = perms.copy(fullScreenAlarms = true)
        vm.refresh()
        assertEquals(OnboardingStep.Battery, vm.step.value)
        assertFalse(store.completed)

        vm.batteryAnswered()
        assertEquals(OnboardingStep.Done, vm.step.value)
        assertTrue(store.completed)
    }

    @Test
    fun reopeningWithEverythingGrantedGoesStraightToDone() {
        store.welcomeSeen = true
        auth.account.value = "me@example.com"
        perms = PermissionState.ALL_GRANTED

        val reopened = OnboardingViewModel(auth, { perms }, store)

        assertEquals(OnboardingStep.Done, reopened.step.value)
        assertTrue(store.completed)
    }
}
