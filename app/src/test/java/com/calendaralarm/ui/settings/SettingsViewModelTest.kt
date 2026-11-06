package com.calendaralarm.ui.settings

import com.calendaralarm.domain.AuthRepository
import com.calendaralarm.domain.CalendarInfo
import com.calendaralarm.sync.FakeAuth
import com.calendaralarm.sync.FakeCalendar
import com.calendaralarm.sync.FakeSelectionStore
import com.calendaralarm.sync.FakeScheduler
import com.calendaralarm.sync.FakeSettings
import com.calendaralarm.ui.permissions.PermissionState
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val now = Instant.parse("2026-11-02T08:00:00Z")

    private val auth = FakeAuth("me@example.com")
    private val selections = FakeSelectionStore()
    private val calendar = FakeCalendar().apply {
        calendarList = listOf(
            CalendarInfo("primary", "me@example.com", "#4285f4", primary = true),
            CalendarInfo("team", "Team", "#0b8043", primary = false),
        )
    }
    private val settings = FakeSettings()
    private val scheduler = FakeScheduler()
    private var permissions = PermissionState(true, false, true, false)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = SettingsViewModel(
        auth, calendar, settings, selections, scheduler, { permissions }, Clock.fixed(now, ZoneOffset.UTC),
    )

    private fun TestScope.state(vm: SettingsViewModel): () -> SettingsUiState {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return { vm.uiState.value }
    }

    @Test
    fun showsDefaultLeadCalendarsAccountAndPermissions() = runTest {
        val current = state(viewModel())

        assertEquals(5, current().defaultLeadMinutes)
        assertEquals(listOf("me@example.com"), current().accounts)
        assertEquals(listOf("primary" to true, "team" to false), current().calendars.map { it.id to it.checked })
        assertEquals(permissions, current().permissions)
    }

    @Test
    fun changingDefaultLeadIsSaved() = runTest {
        val vm = viewModel()
        val current = state(vm)

        vm.setDefaultLeadMinutes(15)

        assertEquals(15, settings.lead.value)
        assertEquals(15, current().defaultLeadMinutes)
    }

    @Test
    fun togglingCalendarsStartsFromPrimaryDefault() = runTest {
        val vm = viewModel()
        val current = state(vm)

        vm.toggleCalendar("team")
        assertEquals(setOf("primary", "team"), settings.calendars.value)

        vm.toggleCalendar("primary")
        assertEquals(setOf("team"), settings.calendars.value)
        assertEquals(listOf("primary" to false, "team" to true), current().calendars.map { it.id to it.checked })
    }

    @Test
    fun calendarErrorIsShownAndRetryRecovers() = runTest {
        calendar.failWith = IOException("offline")
        val vm = viewModel()
        val current = state(vm)
        assertEquals("offline", current().calendarsError)

        calendar.failWith = null
        vm.loadCalendars()

        assertNull(current().calendarsError)
        assertEquals(2, current().calendars.size)
    }

    @Test
    fun disconnectClearsAccountAndCalendars() = runTest {
        val vm = viewModel()
        val current = state(vm)

        vm.removeAccount("me@example.com")

        assertEquals(listOf("me@example.com"), auth.disconnected)
        assertTrue(current().accounts.isEmpty())
        assertTrue(current().calendars.isEmpty())
    }

    @Test
    fun permissionsRefreshOnResume() = runTest {
        val vm = viewModel()
        val current = state(vm)

        permissions = PermissionState.ALL_GRANTED
        vm.refreshPermissions()

        assertTrue(current().permissions.allRequiredGranted)
    }

    @Test
    fun autoSelectTogglesAreSaved() = runTest {
        val vm = viewModel()
        val current = state(vm)

        vm.setAutoSelectVideoCalls(true)
        vm.setAutoSelectAcceptedMeetings(true)

        assertTrue(settings.autoVideo.value && settings.autoAccepted.value)
        assertTrue(current().autoSelectVideoCalls && current().autoSelectAcceptedMeetings)
    }

    @Test
    fun testAlarmRingsInTenSeconds() = runTest {
        val vm = viewModel()
        val current = state(vm)

        vm.scheduleTestAlarm()

        assertEquals(FakeScheduler.Alarm(now.plusSeconds(10), "Test meeting"), scheduler.alarms["test-alarm"])
        assertTrue(current().testAlarmScheduled)
    }

    @Test
    fun removingOneAccountKeepsTheOtherAccountsMeetings() = runTest {
        auth.accounts.value = listOf("me@example.com", "work@example.com")
        calendar.calendarList = listOf(
            CalendarInfo("me@example.com", "Me", null, primary = true, account = "me@example.com"),
            CalendarInfo("team", "Team", null, primary = false, account = "work@example.com"),
        )
        val start = now.plusSeconds(3600)
        selections.upsert(com.calendaralarm.domain.SelectedEvent("legacy", "me@example.com", "Old", start, null, account = null))
        selections.upsert(com.calendaralarm.domain.SelectedEvent("mine", "me@example.com", "Mine", start, null, account = "me@example.com"))
        selections.upsert(com.calendaralarm.domain.SelectedEvent("work", "team", "Work", start, null, account = "work@example.com"))
        settings.calendars.value = setOf("me@example.com", "team")
        val vm = viewModel()
        val current = state(vm)

        vm.removeAccount("work@example.com")

        assertEquals(setOf("legacy", "mine"), selections.state.value.keys)
        assertEquals(setOf("me@example.com"), settings.calendars.value)
        assertEquals(listOf("me@example.com"), current().accounts)
    }

    @Test
    fun removingTheFirstAccountAlsoRemovesSelectionsFromBeforeMultipleAccounts() = runTest {
        auth.accounts.value = listOf("me@example.com", "work@example.com")
        val start = now.plusSeconds(3600)
        selections.upsert(com.calendaralarm.domain.SelectedEvent("legacy", "primary", "Old", start, null, account = null))
        selections.upsert(com.calendaralarm.domain.SelectedEvent("work", "team", "Work", start, null, account = "work@example.com"))
        val vm = viewModel()
        state(vm)

        vm.removeAccount("me@example.com")

        assertEquals(setOf("work"), selections.state.value.keys)
    }
}

