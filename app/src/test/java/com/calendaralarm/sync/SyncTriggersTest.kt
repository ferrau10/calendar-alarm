package com.calendaralarm.sync

import com.calendaralarm.domain.CalendarEvent
import com.calendaralarm.domain.SelectedEvent
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncTriggersTest {
    private val now = Instant.parse("2026-11-02T08:00:00Z")
    private val calendar = FakeCalendar()
    private val selections = FakeSelectionStore()
    private val settings = FakeSettings()
    private val scheduler = FakeScheduler()
    private val syncer = AlarmSyncer(
        calendar, selections, settings, scheduler, FakeRegistry(), Clock.fixed(now, ZoneOffset.UTC),
    )
    /** Past the debounce; advanceUntilIdle() doesn't advance time for backgroundScope work. */
    private fun TestScope.settle() {
        advanceTimeBy(1_000)
        runCurrent()
    }

    private val selected = SelectedEvent("a", "primary", "Standup", now.plusSeconds(3600), null)

    @Test
    fun selectionAndLeadTimeChangesRescheduleWithoutNetwork() = runTest {
        val job = SyncTriggers(selections, settings, syncer).start(backgroundScope)
        settle()
        assertTrue(scheduler.alarms.isEmpty()) // initial state doesn't trigger

        selections.upsert(selected)
        settle()
        assertEquals(now.plusSeconds(3300), scheduler.alarms["a"]?.at)

        settings.lead.value = 10
        settle()
        assertEquals(now.plusSeconds(3000), scheduler.alarms["a"]?.at)

        selections.upsert(selected.copy(leadMinutes = 1))
        settle()
        assertEquals(now.plusSeconds(3540), scheduler.alarms["a"]?.at)

        assertEquals(0, calendar.fetches)
        job.cancel()
    }

    @Test
    fun syncerOwnTitleUpdatesDoNotRetrigger() = runTest {
        selections.upsert(selected)
        SyncTriggers(selections, settings, syncer).start(backgroundScope)
        settle()

        selections.upsert(selected.copy(title = "Renamed by sync"))
        settle()

        assertTrue(scheduler.alarms.isEmpty())
    }
}
