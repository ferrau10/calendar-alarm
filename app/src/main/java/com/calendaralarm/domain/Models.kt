package com.calendaralarm.domain

import java.time.Duration
import java.time.Instant

/** A calendar in the connected Google account. */
data class CalendarInfo(
    val id: String,
    val name: String,
    val colorHex: String?,
    val primary: Boolean,
    /** Email of the Google account this calendar belongs to. */
    val account: String = "",
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
    /** Email of the Google account the event was read from. */
    val account: String = "",
)

/** An event the user wants an alarm for. */
data class SelectedEvent(
    val eventId: String,
    val calendarId: String,
    val title: String,
    val start: Instant,
    /**
     * How many minutes before [start] to ring; one alarm per entry (e.g. `[45, 5]`).
     * `null` (or empty) means "one alarm at the global default".
     */
    val leadMinutes: List<Int>?,
    /** Google account to refresh this event with; null for selections made before multiple accounts. */
    val account: String? = null,
) {
    /** Lead times in effect, largest first (= earliest alarm first), without duplicates. */
    fun leadTimes(defaultLeadMinutes: Int): List<Int> =
        (leadMinutes?.takeIf { it.isNotEmpty() } ?: listOf(defaultLeadMinutes)).distinct().sortedDescending()

    /** Every alarm for this event, earliest first. */
    fun alarms(defaultLeadMinutes: Int): List<EventAlarm> = leadTimes(defaultLeadMinutes).map { lead ->
        EventAlarm(alarmKey(eventId, lead), lead, start.minus(Duration.ofMinutes(lead.toLong())))
    }
}

/** One alarm of a selected event. */
data class EventAlarm(val key: String, val leadMinutes: Int, val at: Instant)

/** Stable id of the alarm ringing [leadMinutes] before [eventId] starts. */
fun alarmKey(eventId: String, leadMinutes: Int): String = "$eventId@$leadMinutes"

/** Normalizes a picked set of lead times: largest first, no duplicates; empty means "use the default". */
fun normalizeLeadTimes(minutes: Collection<Int>?): List<Int>? =
    minutes?.distinct()?.sortedDescending()?.takeIf { it.isNotEmpty() }

/** Thrown by [AuthRepository.accessToken] when no Google account is connected or access was revoked. */
class NotAuthorizedException(message: String = "No Google account connected") : Exception(message)

const val DEFAULT_LEAD_MINUTES = 5

/** Alarm times offered when picking how long before a meeting to ring (default and per meeting). */
val LEAD_TIME_CHOICES: List<Int> = listOf(1, 5, 20, 30)
