package com.calendaralarm.sync

import com.calendaralarm.domain.CalendarEvent
import com.calendaralarm.domain.SelectedEvent
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmSyncerTest {
    private val now = Instant.parse("2026-11-02T08:00:00Z")
    private val calendar = FakeCalendar()
    private val selections = FakeSelectionStore()
    private val settings = FakeSettings()
    private val scheduler = FakeScheduler()
    private val registry = FakeRegistry()
    private val syncer = AlarmSyncer(
        calendar, selections, settings, scheduler, registry, Clock.fixed(now, ZoneOffset.UTC),
    )

    private fun at(minutesFromNow: Long): Instant = now.plus(Duration.ofMinutes(minutesFromNow))

    private fun event(id: String, startIn: Long, title: String = "Meeting $id") = CalendarEvent(
        id = id, calendarId = "primary", title = title, start = at(startIn), end = at(startIn + 30),
        allDay = false, location = null, meetLink = null,
    )

    private suspend fun select(e: CalendarEvent, lead: Int? = null) {
        calendar.events[e.id] = e
        selections.upsert(SelectedEvent(e.id, e.calendarId, e.title, e.start, lead?.let { listOf(it) }))
    }

    @Test
    fun newSelectionIsScheduledWithDefaultLeadTime() = runTest {
        select(event("a", startIn = 60))

        val result = syncer.sync()

        assertEquals(FakeScheduler.Alarm(at(55), "Meeting a · in 5 min"), scheduler.alarms["a@5"])
        assertEquals(setOf("a@5"), result.scheduled)
        assertEquals(setOf("a@5"), registry.stored)
    }

    @Test
    fun perEventLeadTimeOverridesDefault() = runTest {
        select(event("a", startIn = 60), lead = 15)

        syncer.sync()

        assertEquals(at(45), scheduler.alarms["a@15"]?.at)
    }

    @Test
    fun movedEventMovesAlarmAndUpdatesSelection() = runTest {
        select(event("a", startIn = 60))
        syncer.sync()
        calendar.events["a"] = event("a", startIn = 120)

        val result = syncer.sync()

        assertEquals(at(115), scheduler.alarms["a@5"]?.at)
        assertEquals(at(120), selections.state.value["a"]?.start)
        assertEquals(setOf("a"), result.updatedSelections)
    }

    @Test
    fun renamedEventRelabelsAlarm() = runTest {
        select(event("a", startIn = 60))
        calendar.events["a"] = event("a", startIn = 60, title = "Renamed")

        syncer.sync()

        assertEquals("Renamed · in 5 min", scheduler.alarms["a@5"]?.label)
        assertEquals("Renamed", selections.state.value["a"]?.title)
    }

    @Test
    fun cancelledEventRemovesSelectionAndAlarm() = runTest {
        select(event("a", startIn = 60))
        syncer.sync()
        calendar.events.remove("a")

        val result = syncer.sync()

        assertNull(scheduler.alarms["a@5"])
        assertTrue(selections.state.value.isEmpty())
        assertEquals(setOf("a"), result.removedSelections)
        assertEquals(setOf("a@5"), result.cancelled)
        assertTrue(registry.stored.isEmpty())
    }

    @Test
    fun pastEventsArePruned() = runTest {
        select(event("past", startIn = -10))
        select(event("future", startIn = 60))

        syncer.sync()

        assertEquals(setOf("future"), selections.state.value.keys)
        assertEquals(setOf("future@5"), scheduler.alarms.keys)
    }

    @Test
    fun alarmTimeAlreadyPassedButEventNotStartedIsNotScheduled() = runTest {
        select(event("soon", startIn = 3)) // alarm would be 2 minutes ago

        val result = syncer.sync()

        assertTrue(scheduler.alarms.isEmpty())
        assertTrue(result.scheduled.isEmpty())
        assertEquals(setOf("soon"), selections.state.value.keys) // still selected until it starts
    }

    @Test
    fun defaultLeadTimeChangeReschedules() = runTest {
        select(event("a", startIn = 60))
        syncer.sync()
        settings.lead.value = 10

        syncer.sync(refreshFromCalendar = false)

        assertEquals(at(50), scheduler.alarms["a@10"]?.at)
    }

    @Test
    fun deselectedEventAlarmIsCancelled() = runTest {
        select(event("a", startIn = 60))
        select(event("b", startIn = 90))
        syncer.sync()
        selections.remove("a")

        val result = syncer.sync(refreshFromCalendar = false)

        assertEquals(setOf("b@5"), scheduler.alarms.keys)
        assertEquals(setOf("a@5"), result.cancelled)
    }

    @Test
    fun offlineKeepsExistingAlarms() = runTest {
        select(event("a", startIn = 60))
        syncer.sync()
        calendar.offline = true

        val result = syncer.sync()

        assertTrue(result.offline)
        assertEquals(FakeScheduler.Alarm(at(55), "Meeting a · in 5 min"), scheduler.alarms["a@5"])
        assertEquals(setOf("a"), selections.state.value.keys)
    }

    @Test
    fun localSyncDoesNotHitNetwork() = runTest {
        select(event("a", startIn = 60))
        calendar.offline = true

        val result = syncer.sync(refreshFromCalendar = false)

        assertEquals(0, calendar.fetches)
        assertFalse(result.offline)
        assertEquals(at(55), scheduler.alarms["a@5"]?.at)
    }

    @Test
    fun multipleLeadTimesScheduleOneAlarmEach() = runTest {
        val e = event("a", startIn = 60)
        calendar.events[e.id] = e
        selections.upsert(SelectedEvent(e.id, e.calendarId, e.title, e.start, listOf(5, 45)))

        val result = syncer.sync()

        assertEquals(setOf("a@45", "a@5"), result.scheduled)
        assertEquals(FakeScheduler.Alarm(at(15), "Meeting a · in 45 min"), scheduler.alarms["a@45"])
        assertEquals(FakeScheduler.Alarm(at(55), "Meeting a · in 5 min"), scheduler.alarms["a@5"])
    }

    @Test
    fun removingOneLeadTimeCancelsOnlyThatAlarm() = runTest {
        val e = event("a", startIn = 60)
        calendar.events[e.id] = e
        selections.upsert(SelectedEvent(e.id, e.calendarId, e.title, e.start, listOf(45, 5)))
        syncer.sync()

        selections.upsert(SelectedEvent(e.id, e.calendarId, e.title, e.start, listOf(5)))
        val result = syncer.sync(refreshFromCalendar = false)

        assertEquals(setOf("a@5"), scheduler.alarms.keys)
        assertEquals(setOf("a@45"), result.cancelled)
    }

    @Test
    fun alarmsFromBeforeTheUpdateAreCancelled() = runTest {
        registry.stored = setOf("a") // key format of version 0.1
        select(event("a", startIn = 60))

        val result = syncer.sync()

        assertEquals(setOf("a"), result.cancelled)
        assertEquals(setOf("a@5"), registry.stored)
    }
}
