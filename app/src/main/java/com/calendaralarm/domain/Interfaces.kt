package com.calendaralarm.domain

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Connection to the user's Google account (issue #4). */
interface AuthRepository {
    /** Email of the connected account, or `null` when not connected. */
    val account: StateFlow<String?>

    /** A valid OAuth access token with the Calendar read-only scope. @throws NotAuthorizedException */
    suspend fun accessToken(): String

    suspend fun disconnect()
}

/** Read access to Google Calendar (issue #5). */
interface CalendarRepository {
    suspend fun calendars(): List<CalendarInfo>

    /** Events overlapping [from, to) across [calendarIds], sorted by start time. */
    suspend fun upcomingEvents(calendarIds: Set<String>, from: Instant, to: Instant): List<CalendarEvent>

    /** The latest version of an event, or `null` if it was deleted or cancelled. */
    suspend fun event(calendarId: String, eventId: String): CalendarEvent?
}

/** Events the user selected for an alarm (issue #6). */
interface SelectionStore {
    fun observe(): Flow<List<SelectedEvent>>
    suspend fun all(): List<SelectedEvent>
    suspend fun upsert(event: SelectedEvent)
    suspend fun remove(eventId: String)
}

/** User preferences (issue #7). */
interface SettingsStore {
    /** Minutes before an event the alarm rings by default. Defaults to [DEFAULT_LEAD_MINUTES]. */
    val defaultLeadMinutes: Flow<Int>
    suspend fun setDefaultLeadMinutes(minutes: Int)

    /** Calendars shown in the events list. Empty means "primary calendar only". */
    val enabledCalendarIds: Flow<Set<String>>
    suspend fun setEnabledCalendarIds(ids: Set<String>)

    /** Automatically add alarms for events with a video-call link. */
    val autoSelectVideoCalls: Flow<Boolean>
    suspend fun setAutoSelectVideoCalls(enabled: Boolean)

    /** Automatically add alarms for events I accepted that have other attendees. */
    val autoSelectAcceptedMeetings: Flow<Boolean>
    suspend fun setAutoSelectAcceptedMeetings(enabled: Boolean)

    /** Events the user turned off; auto-select rules never add them back. */
    val dismissedEventIds: Flow<Set<String>>
    suspend fun addDismissedEventId(eventId: String)
}

/** Schedules the actual alarms on the device (issue #8). */
interface AlarmScheduler {
    /** Schedules (or reschedules) the alarm identified by [alarmKey]. Times in the past are ignored. */
    fun schedule(alarmKey: String, triggerAt: Instant, label: String)
    fun cancel(alarmKey: String)
    fun canScheduleExact(): Boolean
}
