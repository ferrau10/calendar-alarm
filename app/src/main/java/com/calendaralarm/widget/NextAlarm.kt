package com.calendaralarm.widget

import com.calendaralarm.domain.SelectedEvent
import java.time.Instant

data class NextAlarm(val title: String, val alarmAt: Instant, val start: Instant)

/** The soonest alarm that hasn't rung yet, or null. */
fun nextAlarm(selections: List<SelectedEvent>, defaultLeadMinutes: Int, now: Instant): NextAlarm? =
    selections
        .map { NextAlarm(it.title, it.alarmTime(defaultLeadMinutes), it.start) }
        .filter { it.alarmAt.isAfter(now) }
        .minByOrNull { it.alarmAt }
