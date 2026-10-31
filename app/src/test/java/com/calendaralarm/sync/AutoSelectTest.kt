package com.calendaralarm.sync

import com.calendaralarm.domain.CalendarEvent
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSelectTest {
    private val now = Instant.parse("2026-11-02T08:00:00Z")
    private val calendar = FakeCalendar()
    private val selections = FakeSelectionStore()
    private val settings = FakeSettings()
    private val scheduler = FakeScheduler()
    private val syncer = AlarmSyncer(calendar, selections, settings, scheduler, FakeRegistry(), Clock.fixed(now, ZoneOffset.UTC))

    private fun event(
        id: String,
        hours: Long,
        meet: String? = null,
        others: Boolean = false,
        accepted: Boolean = true,
        allDay: Boolean = false,
    ) = CalendarEvent(
        id, "primary", "Event $id", now.plus(Duration.ofHours(hours)), now.plus(Duration.ofHours(hours + 1)),
        allDay, null, meet, others, accepted,
    ).also { calendar.events[id] = it }

    @Test
    fun rulesMatchExpectedEvents() {
        val video = event("v", 1, meet = "https://meet.google.com/x")
        val accepted = event("a", 1, others = true, accepted = true)
        val declined = event("d", 1, others = true, accepted = false)
        val solo = event("s", 1)
        val allDayCall = event("ad", 1, meet = "https://meet.google.com/y", allDay = true)

        assertTrue(AutoSelectRules.matches(video, videoCalls = true, acceptedMeetings = false))
        assertFalse(AutoSelectRules.matches(video, videoCalls = false, acceptedMeetings = false))
        assertTrue(AutoSelectRules.matches(accepted, videoCalls = false, acceptedMeetings = true))
        assertFalse(AutoSelectRules.matches(declined, videoCalls = false, acceptedMeetings = true))
        assertFalse(AutoSelectRules.matches(solo, videoCalls = true, acceptedMeetings = true))
        assertFalse(AutoSelectRules.matches(allDayCall, videoCalls = true, acceptedMeetings = true))
    }

    @Test
    fun noRulesEnabledAddsNothingAndSkipsCalendarListing() = runTest {
        event("v", 1, meet = "https://meet.google.com/x")
        syncer.sync()
        assertTrue(selections.state.value.isEmpty())
    }

    @Test
    fun enabledRuleAddsMatchingUpcomingEventsAndSchedulesThem() = runTest {
        event("v", 2, meet = "https://meet.google.com/x")
        event("plain", 3)
        event("later", 24 * 8, meet = "https://meet.google.com/z") // outside 7-day window
        settings.autoVideo.value = true

        syncer.sync()

        assertEquals(setOf("v"), selections.state.value.keys)
        assertEquals(now.plus(Duration.ofHours(2)).minusSeconds(300), scheduler.alarms["v"]?.at)
    }

    @Test
    fun dismissedEventsAreNotAddedBack() = runTest {
        event("v", 2, meet = "https://meet.google.com/x")
        settings.autoVideo.value = true
        settings.dismissed.value = setOf("v")

        syncer.sync()

        assertTrue(selections.state.value.isEmpty())
    }

    @Test
    fun existingSelectionKeepsCustomLeadTime() = runTest {
        val e = event("v", 2, meet = "https://meet.google.com/x")
        selections.upsert(com.calendaralarm.domain.SelectedEvent(e.id, e.calendarId, e.title, e.start, 15))
        settings.autoVideo.value = true

        syncer.sync()

        assertEquals(15, selections.state.value["v"]?.leadMinutes)
    }

    @Test
    fun usesEnabledCalendars() = runTest {
        event("v", 2, meet = "https://meet.google.com/x") // primary
        val team = CalendarEvent("t", "team", "Team call", now.plusSeconds(7200), now.plusSeconds(9000), false, null, "https://meet/t")
        calendar.events["t"] = team
        settings.autoVideo.value = true
        settings.calendars.value = setOf("team")

        syncer.sync()

        assertEquals(setOf("t"), selections.state.value.keys)
    }

    @Test
    fun offlineAutoSelectKeepsExistingAlarms() = runTest {
        val e = event("a", 2)
        selections.upsert(com.calendaralarm.domain.SelectedEvent(e.id, e.calendarId, e.title, e.start, null))
        settings.autoVideo.value = true
        calendar.failWith = IOException("offline")

        val result = syncer.sync()

        assertTrue(result.offline)
        assertEquals(setOf("a"), selections.state.value.keys)
    }
}
