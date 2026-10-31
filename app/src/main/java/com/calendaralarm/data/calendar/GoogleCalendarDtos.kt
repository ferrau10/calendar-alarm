package com.calendaralarm.data.calendar

import kotlinx.serialization.Serializable

// Subset of the Google Calendar API v3 resources we use. Unknown fields are ignored.

@Serializable
internal data class CalendarListResponse(
    val items: List<CalendarListEntryDto> = emptyList(),
    val nextPageToken: String? = null,
)

@Serializable
internal data class CalendarListEntryDto(
    val id: String,
    val summary: String? = null,
    val summaryOverride: String? = null,
    val backgroundColor: String? = null,
    val primary: Boolean = false,
)

@Serializable
internal data class EventsResponse(
    val items: List<EventDto> = emptyList(),
    val nextPageToken: String? = null,
)

@Serializable
internal data class EventDto(
    val id: String,
    val status: String? = null,
    val summary: String? = null,
    val location: String? = null,
    val start: EventDateTimeDto? = null,
    val end: EventDateTimeDto? = null,
    val hangoutLink: String? = null,
    val conferenceData: ConferenceDataDto? = null,
    val attendees: List<AttendeeDto> = emptyList(),
)

@Serializable
internal data class AttendeeDto(
    val self: Boolean = false,
    val resource: Boolean = false,
    val responseStatus: String? = null,
    val organizer: Boolean = false,
)

@Serializable
internal data class EventDateTimeDto(
    /** RFC 3339 timestamp, set for timed events. */
    val dateTime: String? = null,
    /** yyyy-MM-dd, set for all-day events. */
    val date: String? = null,
    val timeZone: String? = null,
)

@Serializable
internal data class ConferenceDataDto(
    val entryPoints: List<EntryPointDto> = emptyList(),
)

@Serializable
internal data class EntryPointDto(
    val entryPointType: String? = null,
    val uri: String? = null,
)
