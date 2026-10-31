package com.calendaralarm.ui.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import com.calendaralarm.domain.AlarmScheduler
import com.calendaralarm.domain.AuthRepository
import com.calendaralarm.domain.CalendarInfo
import com.calendaralarm.domain.CalendarRepository
import com.calendaralarm.domain.SettingsStore
import com.calendaralarm.ui.permissions.PermissionChecker
import com.calendaralarm.ui.permissions.PermissionState
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CalendarChoice(val id: String, val name: String, val colorHex: String?, val checked: Boolean)

data class SettingsUiState(
    val defaultLeadMinutes: Int = 5,
    val calendars: List<CalendarChoice> = emptyList(),
    val calendarsError: String? = null,
    val account: String? = null,
    val permissions: PermissionState = PermissionState.ALL_GRANTED,
    val testAlarmScheduled: Boolean = false,
    val autoSelectVideoCalls: Boolean = false,
    val autoSelectAcceptedMeetings: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val calendar: CalendarRepository,
    private val settings: SettingsStore,
    private val scheduler: AlarmScheduler,
    private val permissionChecker: PermissionChecker,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    private val calendars = MutableStateFlow<List<CalendarInfo>>(emptyList())
    private val calendarsError = MutableStateFlow<String?>(null)
    private val permissions = MutableStateFlow(permissionChecker.current())
    private val testAlarm = MutableStateFlow(false)

    private val base = combine(
        settings.defaultLeadMinutes, settings.enabledCalendarIds, calendars, calendarsError, auth.account,
    ) { lead, enabled, list, error, account ->
        val effective = effectiveSelection(enabled, list)
        SettingsUiState(
            defaultLeadMinutes = lead,
            calendars = list.map { CalendarChoice(it.id, it.name, it.colorHex, it.id in effective) },
            calendarsError = error,
            account = account,
        )
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        base, permissions, testAlarm, settings.autoSelectVideoCalls, settings.autoSelectAcceptedMeetings,
    ) { state, perms, test, video, accepted ->
        state.copy(
            permissions = perms,
            testAlarmScheduled = test,
            autoSelectVideoCalls = video,
            autoSelectAcceptedMeetings = accepted,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        loadCalendars()
    }

    fun loadCalendars() {
        if (auth.account.value == null) return
        viewModelScope.launch {
            try {
                calendars.value = calendar.calendars()
                calendarsError.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                calendarsError.value = e.message ?: "Couldn't load calendars"
            }
        }
    }

    fun setDefaultLeadMinutes(minutes: Int) {
        viewModelScope.launch { settings.setDefaultLeadMinutes(minutes) }
    }

    fun toggleCalendar(id: String) {
        viewModelScope.launch {
            val current = effectiveSelection(settings.enabledCalendarIds.first(), calendars.value)
            val updated = if (id in current) current - id else current + id
            settings.setEnabledCalendarIds(updated)
        }
    }

    fun setAutoSelectVideoCalls(enabled: Boolean) {
        viewModelScope.launch { settings.setAutoSelectVideoCalls(enabled) }
    }

    fun setAutoSelectAcceptedMeetings(enabled: Boolean) {
        viewModelScope.launch { settings.setAutoSelectAcceptedMeetings(enabled) }
    }

    fun disconnect() {
        viewModelScope.launch {
            auth.disconnect()
            calendars.value = emptyList()
        }
    }

    /** Call when the screen resumes, e.g. after returning from system settings. */
    fun refreshPermissions() {
        permissions.value = permissionChecker.current()
    }

    fun scheduleTestAlarm() {
        scheduler.schedule(TEST_ALARM_KEY, clock.instant().plusSeconds(TEST_ALARM_DELAY_SECONDS), "Test meeting")
        testAlarm.value = true
    }

    companion object {
        const val TEST_ALARM_KEY = "test-alarm"
        const val TEST_ALARM_DELAY_SECONDS = 10L
        val LEAD_CHOICES = listOf(0, 1, 2, 5, 10, 15, 30, 60)

        /** No explicit choice means "primary calendar only". */
        fun effectiveSelection(enabled: Set<String>, calendars: List<CalendarInfo>): Set<String> =
            enabled.ifEmpty { calendars.filter { it.primary }.map { it.id }.toSet() }
    }
}
