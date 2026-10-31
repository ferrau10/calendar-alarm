package com.calendaralarm.sync

import com.calendaralarm.domain.AlarmScheduler
import com.calendaralarm.domain.CalendarEvent
import com.calendaralarm.domain.CalendarInfo
import com.calendaralarm.domain.CalendarRepository
import com.calendaralarm.domain.DEFAULT_LEAD_MINUTES
import com.calendaralarm.domain.SelectedEvent
import com.calendaralarm.domain.SelectionStore
import com.calendaralarm.domain.SettingsStore
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeCalendar : CalendarRepository {
    val events = mutableMapOf<String, CalendarEvent>()
    var offline = false
    var fetches = 0

    override suspend fun calendars(): List<CalendarInfo> = emptyList()
    override suspend fun upcomingEvents(calendarIds: Set<String>, from: Instant, to: Instant) =
        events.values.filter { it.calendarId in calendarIds }.sortedBy { it.start }

    override suspend fun event(calendarId: String, eventId: String): CalendarEvent? {
        fetches++
        if (offline) throw IOException("offline")
        return events[eventId]
    }
}

class FakeSelectionStore : SelectionStore {
    val state = MutableStateFlow<Map<String, SelectedEvent>>(emptyMap())
    override fun observe(): Flow<List<SelectedEvent>> = state.map { it.values.toList() }
    override suspend fun all() = state.value.values.toList()
    override suspend fun upsert(event: SelectedEvent) { state.value = state.value + (event.eventId to event) }
    override suspend fun remove(eventId: String) { state.value = state.value - eventId }
}

class FakeSettings : SettingsStore {
    val lead = MutableStateFlow(DEFAULT_LEAD_MINUTES)
    val calendars = MutableStateFlow<Set<String>>(emptySet())
    override val defaultLeadMinutes: Flow<Int> = lead
    override suspend fun setDefaultLeadMinutes(minutes: Int) { lead.value = minutes }
    override val enabledCalendarIds: Flow<Set<String>> = calendars
    override suspend fun setEnabledCalendarIds(ids: Set<String>) { calendars.value = ids }
}

class FakeScheduler : AlarmScheduler {
    data class Alarm(val at: Instant, val label: String)
    val alarms = mutableMapOf<String, Alarm>()
    override fun schedule(alarmKey: String, triggerAt: Instant, label: String) {
        alarms[alarmKey] = Alarm(triggerAt, label)
    }
    override fun cancel(alarmKey: String) { alarms.remove(alarmKey) }
    override fun canScheduleExact() = true
}

class FakeRegistry : ScheduledAlarmRegistry {
    var stored = emptySet<String>()
    override suspend fun keys() = stored
    override suspend fun replace(keys: Set<String>) { stored = keys }
}
