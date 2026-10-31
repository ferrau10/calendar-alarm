package com.calendaralarm.ui.settings

import com.calendaralarm.domain.AuthRepository
import com.calendaralarm.domain.CalendarInfo
import com.calendaralarm.sync.FakeCalendar
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

    private class FakeAuth(email: String?) : AuthRepository {
        override val account = MutableStateFlow(email)
        var disconnected = false
        override suspend fun accessToken() = "token"
        override suspend fun disconnect() { disconnected = true; account.value = null }
    }

    private val auth = FakeAuth("me@example.com")
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
        auth, calendar, settings, scheduler, { permissions }, Clock.fixed(now, ZoneOffset.UTC),
    )

    private fun TestScope.state(vm: SettingsViewModel): () -> SettingsUiState {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return { vm.uiState.value }
    }

    @Test
    fun showsDefaultLeadCalendarsAccountAndPermissions() = runTest {
        val current = state(viewModel())

        assertEquals(5, current().defaultLeadMinutes)
        assertEquals("me@example.com", current().account)
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

        vm.disconnect()

        assertTrue(auth.disconnected)
        assertNull(current().account)
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
    fun testAlarmRingsInTenSeconds() = runTest {
        val vm = viewModel()
        val current = state(vm)

        vm.scheduleTestAlarm()

        assertEquals(FakeScheduler.Alarm(now.plusSeconds(10), "Test meeting"), scheduler.alarms["test-alarm"])
        assertTrue(current().testAlarmScheduled)
    }
}
