package com.calendaralarm.data.calendar

import com.calendaralarm.domain.CalendarEvent
import com.calendaralarm.domain.CalendarInfo
import com.calendaralarm.domain.NotAuthorizedException
import com.calendaralarm.sync.FakeAuth
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class MultiAccountCalendarRepositoryTest {
    private val t0 = Instant.parse("2026-11-02T08:00:00Z")

    private class Api(val calendars: List<CalendarInfo>, val events: List<CalendarEvent>) : AccountCalendarApi {
        var failWith: Exception? = null
        val asked = mutableListOf<Set<String>>()
        override suspend fun calendars(): List<CalendarInfo> { failWith?.let { throw it }; return calendars }
        override suspend fun upcomingEvents(calendarIds: Set<String>, from: Instant, to: Instant): List<CalendarEvent> {
            failWith?.let { throw it }
            asked += calendarIds
            return events.filter { it.calendarId in calendarIds || (it.calendarId == calendars.first().id && "primary" in calendarIds) }
        }
        override suspend fun event(calendarId: String, eventId: String) = events.firstOrNull { it.id == eventId }
    }

    private fun event(id: String, cal: String, minutes: Long, title: String = id) =
        CalendarEvent(id, cal, title, t0.plusSeconds(minutes * 60), t0.plusSeconds(minutes * 60 + 1800), false, null, null)

    private val me = Api(
        listOf(CalendarInfo("me@example.com", "Me", "#111111", primary = true), CalendarInfo("shared", "Family", null, primary = false)),
        listOf(event("a", "me@example.com", 60), event("both", "me@example.com", 90), event("s", "shared", 120)),
    )
    private val work = Api(
        listOf(CalendarInfo("work@example.com", "Work", "#222222", primary = true), CalendarInfo("shared", "Family (work)", null, primary = false)),
        listOf(event("w", "work@example.com", 30), event("both", "work@example.com", 90)),
    )
    private val auth = FakeAuth("me@example.com", "work@example.com")
    private val repo = MultiAccountCalendarRepository(auth) { if (it == "me@example.com") me else work }

    @Test
    fun calendarsOfAllAccountsTaggedWithTheirAccountAndDeduplicated() = runTest {
        val calendars = repo.calendars()
        assertEquals(listOf("me@example.com", "shared", "work@example.com"), calendars.map { it.id })
        assertEquals(listOf("me@example.com", "me@example.com", "work@example.com"), calendars.map { it.account })
    }

    @Test
    fun eventsAreFetchedFromTheOwningAccountMergedSortedAndDeduplicated() = runTest {
        repo.calendars()

        val events = repo.upcomingEvents(setOf("me@example.com", "work@example.com", "shared"), t0, t0.plusSeconds(86400))

        assertEquals(listOf("w", "a", "both", "s"), events.map { it.id })
        assertEquals("work@example.com", events.first { it.id == "w" }.account)
        assertEquals(listOf(setOf("me@example.com", "shared")), me.asked)
        assertEquals(listOf(setOf("work@example.com")), work.asked)
    }

    @Test
    fun unknownCalendarIdsTriggerACalendarListLookup() = runTest {
        val events = repo.upcomingEvents(setOf("work@example.com"), t0, t0.plusSeconds(86400))
        assertEquals(listOf("w", "both"), events.map { it.id })
    }

    @Test
    fun oneFailingAccountDoesNotHideTheOthers() = runTest {
        work.failWith = IOException("revoked")
        assertEquals(listOf("me@example.com", "shared"), repo.calendars().map { it.id })
    }

    @Test
    fun allAccountsFailingThrows() = runTest {
        me.failWith = IOException("offline")
        work.failWith = IOException("offline")
        try {
            repo.calendars()
            fail("expected IOException")
        } catch (_: IOException) {
        }
    }

    @Test
    fun eventIsReadThroughTheGivenAccountOrTheCalendarsOwner() = runTest {
        assertEquals("work@example.com", repo.event("work@example.com", "work@example.com", "w")?.account)
        repo.calendars()
        // Selections from before multiple accounts have no account: use whoever owns the calendar.
        assertEquals("work@example.com", repo.event(null, "work@example.com", "w")?.account)
        assertEquals("me@example.com", repo.event(null, "unknown", "a")?.account)
        assertNull(repo.event("me@example.com", "me@example.com", "missing"))
    }

    @Test
    fun noAccountsMeansNotAuthorized() = runTest {
        auth.accounts.value = emptyList()
        try {
            repo.calendars()
            fail("expected NotAuthorizedException")
        } catch (_: NotAuthorizedException) {
        }
    }
}
