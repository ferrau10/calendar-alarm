package com.calendaralarm.ui.events

import com.calendaralarm.domain.AuthRepository
import com.calendaralarm.domain.CalendarEvent
import com.calendaralarm.domain.CalendarInfo
import com.calendaralarm.domain.NotAuthorizedException
import com.calendaralarm.sync.FakeCalendar
import com.calendaralarm.sync.FakeSelectionStore
import com.calendaralarm.sync.FakeSettings
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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
class EventsViewModelTest {
    private val now = Instant.parse("2026-11-02T08:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private class FakeAuth(email: String?) : AuthRepository {
        override val account: StateFlow<String?> = MutableStateFlow(email)
        override suspend fun accessToken() = "token"
        override suspend fun disconnect() {}
    }

    private val calendar = FakeCalendar()
    private val selections = FakeSelectionStore()
    private val settings = FakeSettings()

    private fun event(id: String, hoursFromNow: Long, cal: String = "primary", allDay: Boolean = false) = CalendarEvent(
        id, cal, "Event $id", now.plusSeconds(hoursFromNow * 3600), now.plusSeconds(hoursFromNow * 3600 + 1800),
        allDay, null, null,
    ).also { calendar.events[id] = it }

    private val defaultLocale = Locale.getDefault()

    @Before
    fun setUp() {
        Locale.setDefault(Locale.UK) // 24-hour times in the expected strings
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        Locale.setDefault(defaultLocale)
    }

    private fun viewModel(email: String? = "me@example.com") =
        EventsViewModel(FakeAuth(email), calendar, selections, settings, clock)

    /** Collects uiState so the WhileSubscribed flow is active, returning the latest value. */
    private fun kotlinx.coroutines.test.TestScope.state(vm: EventsViewModel): () -> EventsUiState {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return { vm.uiState.value }
    }

    private fun EventsUiState.rows() = (this as EventsUiState.Content).sections.flatMap { it.rows }

    @Test
    fun notConnectedWithoutAccount() = runTest {
        val current = state(viewModel(email = null))
        assertEquals(EventsUiState.NotConnected, current())
    }

    @Test
    fun loadsPrimaryCalendarEventsWhenNoCalendarsChosen() = runTest {
        event("a", 2)
        event("other", 3, cal = "team")
        calendar.calendarList = listOf(
            CalendarInfo("primary", "Me", "#4285f4", primary = true),
            CalendarInfo("team", "Team", "#0b8043", primary = false),
        )

        val current = state(viewModel())

        assertEquals(listOf("a"), current().rows().map { it.eventId })
        assertEquals("#4285f4", current().rows().single().colorHex)
    }

    @Test
    fun usesChosenCalendarsAndSevenDayWindow() = runTest {
        event("team1", 2, cal = "team")
        event("farAway", 24 * 8, cal = "team")
        event("mine", 1)
        settings.calendars.value = setOf("team")

        val current = state(viewModel())

        assertEquals(listOf("team1"), current().rows().map { it.eventId })
    }

    @Test
    fun emptyCalendarShowsEmptyContent() = runTest {
        val current = state(viewModel())
        assertTrue((current() as EventsUiState.Content).isEmpty)
    }

    @Test
    fun errorsAreShownAndRefreshRecovers() = runTest {
        event("a", 2)
        calendar.failWith = IOException("offline")
        val vm = viewModel()
        val current = state(vm)
        assertEquals(EventsUiState.Error("offline"), current())

        calendar.failWith = null
        vm.refresh()

        assertEquals(listOf("a"), current().rows().map { it.eventId })
    }

    @Test
    fun revokedAccessShowsNotConnected() = runTest {
        calendar.failWith = NotAuthorizedException()
        val current = state(viewModel())
        assertEquals(EventsUiState.NotConnected, current())
    }

    @Test
    fun toggleSelectsAndDeselects() = runTest {
        event("a", 2)
        val vm = viewModel()
        val current = state(vm)

        vm.toggle(current().rows().single())
        val selected = current().rows().single()
        assertTrue(selected.selected)
        assertEquals("Alarm at 09:55 (5 min before)", selected.alarmText)
        assertEquals(setOf("a"), selections.state.value.keys)

        vm.toggle(selected)
        assertTrue(selections.state.value.isEmpty())
        assertNull(current().rows().single().alarmText)
    }

    @Test
    fun allDayEventsCannotBeSelected() = runTest {
        event("holiday", 2, allDay = true)
        val vm = viewModel()
        val current = state(vm)

        vm.toggle(current().rows().single())
        vm.setLeadMinutes(current().rows().single(), 10)

        assertTrue(selections.state.value.isEmpty())
    }

    @Test
    fun customLeadTimeIsSavedAndShown() = runTest {
        event("a", 2)
        val vm = viewModel()
        val current = state(vm)

        vm.setLeadMinutes(current().rows().single(), 15)

        assertEquals(15, selections.state.value["a"]?.leadMinutes)
        assertEquals("Alarm at 09:45 (15 min before)", current().rows().single().alarmText)
    }

    @Test
    fun defaultLeadChangeUpdatesAlarmText() = runTest {
        event("a", 2)
        val vm = viewModel()
        val current = state(vm)
        vm.toggle(current().rows().single())

        settings.lead.value = 10

        assertEquals("Alarm at 09:50 (10 min before)", current().rows().single().alarmText)
    }
}
