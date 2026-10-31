package com.calendaralarm.data.calendar

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GoogleCalendarRepositoryTest {

    private val requests = mutableListOf<HttpRequestData>()
    private val tokenCalls = mutableListOf<Boolean>()

    private fun repo(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        GoogleCalendarRepository(
            client = GoogleCalendarRepository.httpClient(MockEngine { req -> requests += req; handler(req) }),
            tokens = { force -> tokenCalls += force; if (force) "fresh-token" else "token" },
            baseUrl = "https://example.test/calendar/v3",
            zone = { ZoneId.of("Europe/Madrid") },
        )

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private val from = Instant.parse("2026-11-02T00:00:00Z")
    private val to = Instant.parse("2026-11-09T00:00:00Z")

    @Test
    fun calendarsMapsFieldsAndFollowsPagination() = runTest {
        val repo = repo { req ->
            if (req.url.parameters["pageToken"] == null) json(CALENDAR_LIST_PAGE_1) else json(CALENDAR_LIST_PAGE_2)
        }

        val calendars = repo.calendars()

        assertEquals(listOf("me@example.com", "team@group.calendar.google.com"), calendars.map { it.id })
        assertEquals("Me", calendars[0].name)
        assertTrue(calendars[0].primary)
        assertEquals("#9fe1e7", calendars[0].colorHex)
        assertEquals("My team", calendars[1].name) // summaryOverride wins
        assertFalse(calendars[1].primary)
        assertEquals("/calendar/v3/users/me/calendarList", requests[0].url.encodedPath)
        assertEquals("page2", requests[1].url.parameters["pageToken"])
        assertEquals("Bearer token", requests[0].headers[HttpHeaders.Authorization])
    }

    @Test
    fun upcomingEventsMapsTimedAllDayAndRecurringEvents() = runTest {
        val repo = repo { json(EVENTS_PAGE) }

        val events = repo.upcomingEvents(setOf("me@example.com"), from, to)

        assertEquals(listOf("allday1", "standup_20261102T080000Z", "planning"), events.map { it.id })

        val allDay = events[0]
        assertTrue(allDay.allDay)
        assertEquals(Instant.parse("2026-11-01T23:00:00Z"), allDay.start) // midnight in Madrid (UTC+1)
        assertEquals("(No title)", allDay.title)

        val standup = events[1]
        assertFalse(standup.allDay)
        assertEquals("Standup", standup.title)
        assertEquals(Instant.parse("2026-11-02T08:00:00Z"), standup.start)
        assertEquals(Instant.parse("2026-11-02T08:15:00Z"), standup.end)
        assertEquals("https://meet.google.com/abc-defg-hij", standup.meetLink)
        assertEquals("me@example.com", standup.calendarId)

        val planning = events[2]
        assertEquals("https://zoom.example/j/1", planning.meetLink) // from conferenceData
        assertEquals("Room 4", planning.location)

        val params = requests.single().url.parameters
        assertEquals("true", params["singleEvents"])
        assertEquals("startTime", params["orderBy"])
        assertEquals("2026-11-02T00:00:00Z", params["timeMin"])
        assertEquals("2026-11-09T00:00:00Z", params["timeMax"])
    }

    @Test
    fun upcomingEventsSkipsCancelledFollowsPagesAndMergesCalendarsSortedByStart() = runTest {
        val repo = repo { req ->
            when {
                req.url.encodedPath.contains("team") -> json(TEAM_EVENTS)
                req.url.parameters["pageToken"] == null -> json(PAGED_1)
                else -> json(PAGED_2)
            }
        }

        val events = repo.upcomingEvents(setOf("me@example.com", "team@group.calendar.google.com"), from, to)

        assertEquals(listOf("a", "team1", "b"), events.map { it.id })
        assertEquals("team@group.calendar.google.com", events[1].calendarId)
    }

    @Test
    fun calendarIdWithSpecialCharactersIsEncodedAsOnePathSegment() = runTest {
        val repo = repo { json("""{"items":[]}""") }

        repo.upcomingEvents(setOf("abc#holiday@group.v.calendar.google.com"), from, to)

        assertEquals(
            "/calendar/v3/calendars/abc%23holiday@group.v.calendar.google.com/events",
            requests.single().url.encodedPath,
        )
    }

    @Test
    fun eventReturnsNullWhenDeletedOrCancelled() = runTest {
        val repo = repo { req ->
            when {
                req.url.encodedPath.endsWith("/gone") -> json("{}", HttpStatusCode.Gone)
                req.url.encodedPath.endsWith("/missing") -> json("{}", HttpStatusCode.NotFound)
                else -> json(CANCELLED_EVENT)
            }
        }

        assertNull(repo.event("primary", "gone"))
        assertNull(repo.event("primary", "missing"))
        assertNull(repo.event("primary", "cancelled1"))
    }

    @Test
    fun eventReturnsLatestVersion() = runTest {
        val repo = repo { json(SINGLE_EVENT) }

        val event = repo.event("primary", "planning")!!

        assertEquals("Planning (moved)", event.title)
        assertEquals(Instant.parse("2026-11-03T14:30:00Z"), event.start)
        assertEquals("/calendar/v3/calendars/primary/events/planning", requests.single().url.encodedPath)
    }

    @Test
    fun retriesOnceWithFreshTokenOn401() = runTest {
        val repo = repo { req ->
            if (req.headers[HttpHeaders.Authorization] == "Bearer token") json("{}", HttpStatusCode.Unauthorized)
            else json(SINGLE_EVENT)
        }

        val event = repo.event("primary", "planning")

        assertEquals("Planning (moved)", event?.title)
        assertEquals(listOf(false, true), tokenCalls)
    }

    @Test
    fun throwsOnOtherErrors() = runTest {
        val repo = repo { json("""{"error":{"message":"Rate Limit Exceeded"}}""", HttpStatusCode.Forbidden) }

        try {
            repo.calendars()
            fail("expected CalendarApiException")
        } catch (e: CalendarApiException) {
            assertEquals(403, e.status)
            assertTrue(e.message!!.contains("Rate Limit Exceeded"))
        }
    }

    private companion object {
        const val CALENDAR_LIST_PAGE_1 = """
        {"kind":"calendar#calendarList","nextPageToken":"page2","items":[
          {"id":"me@example.com","summary":"Me","backgroundColor":"#9fe1e7","primary":true,"accessRole":"owner"}
        ]}"""

        const val CALENDAR_LIST_PAGE_2 = """
        {"kind":"calendar#calendarList","items":[
          {"id":"team@group.calendar.google.com","summary":"Team","summaryOverride":"My team","backgroundColor":"#f83a22"}
        ]}"""

        const val EVENTS_PAGE = """
        {"kind":"calendar#events","timeZone":"Europe/Madrid","items":[
          {"id":"allday1","status":"confirmed","start":{"date":"2026-11-02"},"end":{"date":"2026-11-03"}},
          {"id":"standup_20261102T080000Z","status":"confirmed","summary":"Standup","recurringEventId":"standup",
           "start":{"dateTime":"2026-11-02T09:00:00+01:00","timeZone":"Europe/Madrid"},
           "end":{"dateTime":"2026-11-02T09:15:00+01:00","timeZone":"Europe/Madrid"},
           "hangoutLink":"https://meet.google.com/abc-defg-hij"},
          {"id":"planning","status":"confirmed","summary":"Planning","location":"Room 4",
           "start":{"dateTime":"2026-11-02T14:00:00Z"},"end":{"dateTime":"2026-11-02T15:00:00Z"},
           "conferenceData":{"entryPoints":[
             {"entryPointType":"phone","uri":"tel:+1-555"},
             {"entryPointType":"video","uri":"https://zoom.example/j/1"}]}}
        ]}"""

        const val PAGED_1 = """
        {"nextPageToken":"p2","items":[
          {"id":"a","summary":"A","start":{"dateTime":"2026-11-02T08:00:00Z"},"end":{"dateTime":"2026-11-02T09:00:00Z"}},
          {"id":"x","status":"cancelled","summary":"Gone","start":{"dateTime":"2026-11-02T08:30:00Z"},"end":{"dateTime":"2026-11-02T09:00:00Z"}}
        ]}"""

        const val PAGED_2 = """
        {"items":[
          {"id":"b","summary":"B","start":{"dateTime":"2026-11-04T08:00:00Z"},"end":{"dateTime":"2026-11-04T09:00:00Z"}}
        ]}"""

        const val TEAM_EVENTS = """
        {"items":[
          {"id":"team1","summary":"Team sync","start":{"dateTime":"2026-11-03T10:00:00Z"},"end":{"dateTime":"2026-11-03T10:30:00Z"}}
        ]}"""

        const val CANCELLED_EVENT = """
        {"id":"cancelled1","status":"cancelled","summary":"Old","start":{"dateTime":"2026-11-02T08:00:00Z"},"end":{"dateTime":"2026-11-02T09:00:00Z"}}"""

        const val SINGLE_EVENT = """
        {"id":"planning","status":"confirmed","summary":"Planning (moved)",
         "start":{"dateTime":"2026-11-03T15:30:00+01:00"},"end":{"dateTime":"2026-11-03T16:30:00+01:00"}}"""
    }
}
