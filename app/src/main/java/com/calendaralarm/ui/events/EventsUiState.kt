package com.calendaralarm.ui.events

import com.calendaralarm.domain.CalendarEvent
import com.calendaralarm.domain.DEFAULT_LEAD_MINUTES
import com.calendaralarm.domain.SelectedEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

data class EventRow(
    val eventId: String,
    val calendarId: String,
    val title: String,
    val start: Instant,
    /** e.g. "09:00 – 09:30", or "All day". */
    val timeText: String,
    val colorHex: String?,
    val allDay: Boolean,
    val selected: Boolean,
    /** When the first alarm rings, if selected. */
    val alarmAt: Instant?,
    /** e.g. "Alarm at 08:55 (5 min before)" or "Alarms at 08:15 and 08:55 (45 and 5 min before)". */
    val alarmText: String?,
    /** Per-event lead times, or null when using the default. */
    val customLeadMinutes: List<Int>?,
    /** Google account the event comes from. */
    val account: String = "",
)

data class DaySection(val date: LocalDate, val label: String, val rows: List<EventRow>)

sealed interface EventsUiState {
    data object Loading : EventsUiState
    data object NotConnected : EventsUiState
    data class Error(val message: String) : EventsUiState
    data class Content(
        val sections: List<DaySection>,
        val refreshing: Boolean = false,
        val defaultLeadMinutes: Int = DEFAULT_LEAD_MINUTES,
    ) : EventsUiState {
        val isEmpty: Boolean get() = sections.isEmpty()
    }
}

/** Pure mapping from domain data to what the events list shows. */
class EventsUiMapper(
    private val zone: ZoneId,
    private val locale: Locale = Locale.getDefault(),
) {
    private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone)
    private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM", locale)

    fun sections(
        events: List<CalendarEvent>,
        selections: List<SelectedEvent>,
        calendarColors: Map<String, String?>,
        defaultLeadMinutes: Int,
        today: LocalDate,
    ): List<DaySection> {
        val selectedById = selections.associateBy { it.eventId }
        return events
            .sortedWith(compareBy<CalendarEvent>({ !it.allDay }, { it.start }, { it.title }))
            .groupBy { it.start.atZone(zone).toLocalDate() }
            .toSortedMap()
            .map { (date, dayEvents) ->
                DaySection(date, dayLabel(date, today), dayEvents.map { row(it, selectedById[it.id], calendarColors, defaultLeadMinutes) })
            }
    }

    private fun row(
        event: CalendarEvent,
        selection: SelectedEvent?,
        colors: Map<String, String?>,
        defaultLead: Int,
    ): EventRow {
        val alarms = selection?.alarms(defaultLead).orEmpty()
        return EventRow(
            eventId = event.id,
            calendarId = event.calendarId,
            title = event.title,
            start = event.start,
            timeText = if (event.allDay) "All day" else "${timeFormat.format(event.start)} – ${timeFormat.format(event.end)}",
            colorHex = colors[event.calendarId],
            allDay = event.allDay,
            selected = selection != null,
            alarmAt = alarms.firstOrNull()?.at,
            alarmText = alarms.takeIf { it.isNotEmpty() }?.let { list ->
                val times = joinWithAnd(list.map { timeFormat.format(it.at) })
                (if (list.size == 1) "Alarm at " else "Alarms at ") + "$times (${leadsText(list.map { it.leadMinutes })})"
            },
            customLeadMinutes = selection?.leadMinutes,
            account = event.account,
        )
    }

    fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> dayFormat.format(date)
    }

    companion object {
        fun leadText(minutes: Int): String = if (minutes == 0) "at start time" else "$minutes min before"

        /** "5 min before", "45 and 5 min before", "10 min before and at start time". */
        fun leadsText(minutes: List<Int>): String {
            val nonZero = minutes.filter { it != 0 }
            val parts = buildList {
                if (nonZero.isNotEmpty()) add("${joinWithAnd(nonZero.map(Int::toString))} min before")
                if (0 in minutes) add("at start time")
            }
            return joinWithAnd(parts)
        }

        fun joinWithAnd(items: List<String>): String = when (items.size) {
            0 -> ""
            1 -> items[0]
            else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
        }
    }
}
