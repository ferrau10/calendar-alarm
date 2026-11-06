package com.calendaralarm.domain

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Connections to the user's Google accounts (#4, several accounts: #72). */
interface AuthRepository {
    /** Emails of the connected accounts, in the order they were added; empty when none is connected. */
    val accounts: StateFlow<List<String>>

    /** A valid OAuth access token for [account] with the Calendar read-only scope. @throws NotAuthorizedException */
    suspend fun accessToken(account: String): String

    /** Disconnects one account. */
    suspend fun disconnect(account: String)
}

/** Read access to Google Calendar across all connected accounts (#5, #72). */
interface CalendarRepository {
    /** Calendars of every connected account, each tagged with its [CalendarInfo.account]. */
    suspend fun calendars(): List<CalendarInfo>

    /** Events overlapping [from, to) across [calendarIds], sorted by start time; the same meeting seen
     *  through two accounts appears once. */
    suspend fun upcomingEvents(calendarIds: Set<String>, from: Instant, to: Instant): List<CalendarEvent>

    /** The latest version of an event, read through [account] (null = whichever account owns the calendar),
     *  or `null` if it was deleted or cancelled. */
    suspend fun event(account: String?, calendarId: String, eventId: String): CalendarEvent?
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
