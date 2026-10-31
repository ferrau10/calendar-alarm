package com.calendaralarm.ui.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.calendaralarm.domain.AuthRepository
import com.calendaralarm.ui.permissions.PermissionChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Remembers onboarding progress across launches. */
interface OnboardingStore {
    var welcomeSeen: Boolean
    var batteryAsked: Boolean
    var completed: Boolean
}

class PrefsOnboardingStore(context: Context) : OnboardingStore {
    private val prefs = context.applicationContext.getSharedPreferences("onboarding", Context.MODE_PRIVATE)
    override var welcomeSeen: Boolean
        get() = prefs.getBoolean("welcome_seen", false)
        set(value) = prefs.edit().putBoolean("welcome_seen", value).apply()
    override var batteryAsked: Boolean
        get() = prefs.getBoolean("battery_asked", false)
        set(value) = prefs.edit().putBoolean("battery_asked", value).apply()
    override var completed: Boolean
        get() = prefs.getBoolean("completed", false)
        set(value) = prefs.edit().putBoolean("completed", value).apply()
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val permissions: PermissionChecker,
    private val store: OnboardingStore,
) : ViewModel() {
    private val _step = MutableStateFlow(compute())
    val step: StateFlow<OnboardingStep> = _step.asStateFlow()

    /** Re-evaluate, e.g. on resume after the user granted something in system settings. */
    fun refresh() {
        _step.value = compute()
    }

    fun welcomeSeen() {
        store.welcomeSeen = true
        refresh()
    }

    /** The user allowed or skipped the optional battery step. */
    fun batteryAnswered() {
        store.batteryAsked = true
        refresh()
    }

    private fun compute(): OnboardingStep {
        val perms = permissions.current()
        val step = OnboardingFlow.step(
            OnboardingFacts(
                welcomeSeen = store.welcomeSeen,
                connected = auth.account.value != null,
                notifications = perms.notifications,
                exactAlarms = perms.exactAlarms,
                fullScreenAlarms = perms.fullScreenAlarms,
                ignoresBatteryOptimizations = perms.ignoresBatteryOptimizations,
                batteryAsked = store.batteryAsked,
            ),
        )
        if (step == OnboardingStep.Done) store.completed = true
        return step
    }
}
