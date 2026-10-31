package com.calendaralarm.data.calendar

import com.calendaralarm.domain.CalendarEvent
import com.calendaralarm.domain.CalendarInfo
import com.calendaralarm.domain.CalendarRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json

/** Supplies OAuth access tokens. `forceRefresh = true` is passed after the API rejected a token (HTTP 401). */
fun interface AccessTokenProvider {
    suspend fun token(forceRefresh: Boolean): String
}

class CalendarApiException(val status: Int, message: String) : Exception("Calendar API error $status: $message")

/**
 * [CalendarRepository] backed by the Google Calendar REST API v3.
 *
 * @param zone time zone used to place all-day events, which have a date but no time.
 */
class GoogleCalendarRepository(
    private val client: HttpClient,
    private val tokens: AccessTokenProvider,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : CalendarRepository {

    override suspend fun calendars(): List<CalendarInfo> {
        val result = mutableListOf<CalendarInfo>()
        var pageToken: String? = null
        do {
            val page: CalendarListResponse = getJson(listOf("users", "me", "calendarList")) {
                pageToken?.let { url.parameters.append("pageToken", it) }
            } ?: CalendarListResponse()
            page.items.mapTo(result) {
                CalendarInfo(
                    id = it.id,
                    name = it.summaryOverride ?: it.summary ?: it.id,
                    colorHex = it.backgroundColor,
                    primary = it.primary,
                )
            }
            pageToken = page.nextPageToken
        } while (pageToken != null)
        return result
    }

    override suspend fun upcomingEvents(
        calendarIds: Set<String>,
        from: Instant,
        to: Instant,
    ): List<CalendarEvent> = coroutineScope {
        calendarIds
            .map { id -> async { eventsOf(id, from, to) } }
            .awaitAll()
            .flatten()
            .sortedWith(compareBy({ it.start }, { it.title }))
    }

    override suspend fun event(calendarId: String, eventId: String): CalendarEvent? {
        val dto: EventDto = getJson(listOf("calendars", calendarId, "events", eventId)) ?: return null
        return dto.toDomain(calendarId)
    }

    private suspend fun eventsOf(calendarId: String, from: Instant, to: Instant): List<CalendarEvent> {
        val result = mutableListOf<CalendarEvent>()
        var pageToken: String? = null
        do {
            val page: EventsResponse = getJson(listOf("calendars", calendarId, "events")) {
                url.parameters.apply {
                    append("singleEvents", "true")
                    append("orderBy", "startTime")
                    append("timeMin", from.toString())
                    append("timeMax", to.toString())
                    append("maxResults", "250")
                    pageToken?.let { append("pageToken", it) }
                }
            } ?: EventsResponse()
            page.items.mapNotNullTo(result) { it.toDomain(calendarId) }
            pageToken = page.nextPageToken
        } while (pageToken != null)
        return result
    }

    /** GET returning the parsed body, or `null` for 404/410. */
    private suspend inline fun <reified T> getJson(
        path: List<String>,
        noinline configure: HttpRequestBuilder.() -> Unit = {},
    ): T? {
        val response = get(path, configure)
        return when {
            response.status.isSuccess() -> response.body<T>()
            response.status == HttpStatusCode.NotFound || response.status == HttpStatusCode.Gone -> null
            else -> throw CalendarApiException(response.status.value, response.bodyAsText().take(500))
        }
    }

    /** Authenticated GET; retries once with a fresh token when the API answers 401. */
    private suspend fun get(path: List<String>, configure: HttpRequestBuilder.() -> Unit): HttpResponse {
        val first = send(path, tokens.token(forceRefresh = false), configure)
        if (first.status != HttpStatusCode.Unauthorized) return first
        return send(path, tokens.token(forceRefresh = true), configure)
    }

    private suspend fun send(
        path: List<String>,
        token: String,
        configure: HttpRequestBuilder.() -> Unit,
    ): HttpResponse = client.get {
        url {
            takeFrom(baseUrl)
            appendPathSegments(path)
        }
        bearerAuth(token)
        configure()
    }

    private fun EventDto.toDomain(calendarId: String): CalendarEvent? {
        if (status == "cancelled") return null
        val startDt = start ?: return null
        val endDt = end ?: startDt
        val allDay = startDt.dateTime == null && startDt.date != null
        val startInstant = startDt.toInstant() ?: return null
        return CalendarEvent(
            id = id,
            calendarId = calendarId,
            title = summary?.takeIf { it.isNotBlank() } ?: NO_TITLE,
            start = startInstant,
            end = endDt.toInstant() ?: startInstant,
            allDay = allDay,
            location = location?.takeIf { it.isNotBlank() },
            meetLink = hangoutLink
                ?: conferenceData?.entryPoints?.firstOrNull { it.entryPointType == "video" }?.uri,
            hasOtherAttendees = attendees.any { !it.self && !it.resource },
            acceptedByMe = attendees.firstOrNull { it.self }
                ?.let { it.responseStatus == "accepted" || (it.organizer && it.responseStatus == null) }
                // No attendee list means it's my own event.
                ?: attendees.isEmpty(),
        )
    }

    private fun EventDateTimeDto.toInstant(): Instant? = when {
        dateTime != null -> OffsetDateTime.parse(dateTime).toInstant()
        date != null -> LocalDate.parse(date).atStartOfDay(zone()).toInstant()
        else -> null
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://www.googleapis.com/calendar/v3"
        const val NO_TITLE = "(No title)"

        /** HTTP client configured for the Calendar API. */
        fun httpClient(engine: HttpClientEngine = OkHttp.create()): HttpClient = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; explicitNulls = false })
            }
        }
    }
}
