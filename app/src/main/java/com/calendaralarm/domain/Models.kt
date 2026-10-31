package com.calendaralarm.domain

import java.time.Duration
import java.time.Instant

/** A calendar in the connected Google account. */
data class CalendarInfo(
    val id: String,
    val name: String,
    val colorHex: String?,
    val primary: Boolean,
)

/**
 * A single event occurrence. Recurring events are expanded, so every occurrence has its own [id].
 */
data class CalendarEvent(
    val id: String,
    val calendarId: String,
    val title: String,
    val start: Instant,
    val end: Instant,
    val allDay: Boolean,
    val location: String?,
    val meetLink: String?,
    /** Other people are invited (not just me). */
    val hasOtherAttendees: Boolean = false,
    /** My response: true if I accepted, or I'm the organizer of an event without a response. */
    val acceptedByMe: Boolean = false,
)

/** An event the user wants an alarm for. */
data class SelectedEvent(
    val eventId: String,
    val calendarId: String,
    val title: String,
    val start: Instant,
    /** Minutes before [start] the alarm should ring; `null` means "use the global default". */
    val leadMinutes: Int?,
) {
    fun alarmTime(defaultLeadMinutes: Int): Instant =
        start.minus(Duration.ofMinutes((leadMinutes ?: defaultLeadMinutes).toLong()))
}

/** Thrown by [AuthRepository.accessToken] when no Google account is connected or access was revoked. */
class NotAuthorizedException(message: String = "No Google account connected") : Exception(message)

const val DEFAULT_LEAD_MINUTES = 5
