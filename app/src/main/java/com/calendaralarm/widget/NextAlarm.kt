package com.calendaralarm.widget

import com.calendaralarm.domain.SelectedEvent
import java.time.Instant

data class NextAlarm(val title: String, val alarmAt: Instant, val start: Instant)

/** The soonest alarm that hasn't rung yet, or null. */
fun nextAlarm(selections: List<SelectedEvent>, defaultLeadMinutes: Int, now: Instant): NextAlarm? =
    selections
        .flatMap { event -> event.alarms(defaultLeadMinutes).map { NextAlarm(event.title, it.at, event.start) } }
        .filter { it.alarmAt.isAfter(now) }
        .minByOrNull { it.alarmAt }
