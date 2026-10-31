package com.calendaralarm.ui.events

import com.calendaralarm.domain.CalendarEvent
import com.calendaralarm.domain.SelectedEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventsUiMapperTest {
    private val zone = ZoneId.of("Europe/Madrid") // UTC+1 in November
    private val mapper = EventsUiMapper(zone, Locale.UK)
    private val today = LocalDate.of(2026, 11, 2)

    private fun event(id: String, start: String, end: String, allDay: Boolean = false, cal: String = "primary") =
        CalendarEvent(id, cal, "Event $id", Instant.parse(start), Instant.parse(end), allDay, null, null)

    @Test
    fun groupsByLocalDayWithTodayTomorrowLabels() {
        val events = listOf(
            event("late", "2026-11-02T22:30:00Z", "2026-11-02T23:00:00Z"), // 23:30 local, still Monday
            event("next", "2026-11-02T23:30:00Z", "2026-11-03T00:00:00Z"), // 00:30 local Tuesday
            event("thu", "2026-11-05T09:00:00Z", "2026-11-05T10:00:00Z"),
            event("early", "2026-11-02T08:00:00Z", "2026-11-02T08:30:00Z"),
        )

        val sections = mapper.sections(events, emptyList(), emptyMap(), 5, today)

        assertEquals(listOf("Today", "Tomorrow", "Thu 5 Nov"), sections.map { it.label })
        assertEquals(listOf("early", "late"), sections[0].rows.map { it.eventId })
        assertEquals("09:00 – 09:30", sections[0].rows[0].timeText)
    }

    @Test
    fun allDayEventsComeFirstInTheirDay() {
        val events = listOf(
            event("meeting", "2026-11-02T08:00:00Z", "2026-11-02T09:00:00Z"),
            event("holiday", "2026-11-01T23:00:00Z", "2026-11-02T23:00:00Z", allDay = true),
        )

        val rows = mapper.sections(events, emptyList(), emptyMap(), 5, today).single().rows

        assertEquals(listOf("holiday", "meeting"), rows.map { it.eventId })
        assertEquals("All day", rows[0].timeText)
    }

    @Test
    fun selectedRowsShowAlarmTimeWithDefaultOrCustomLead() {
        val a = event("a", "2026-11-02T09:00:00Z", "2026-11-02T09:30:00Z", cal = "work")
        val b = event("b", "2026-11-02T10:00:00Z", "2026-11-02T10:30:00Z")
        val c = event("c", "2026-11-02T11:00:00Z", "2026-11-02T11:30:00Z")
        val selections = listOf(
            SelectedEvent("a", "work", a.title, a.start, null),
            SelectedEvent("b", "primary", b.title, b.start, 15),
        )

        val rows = mapper.sections(listOf(a, b, c), selections, mapOf("work" to "#ff0000"), 5, today).single().rows

        assertTrue(rows[0].selected)
        assertEquals("Alarm at 09:55 (5 min before)", rows[0].alarmText)
        assertEquals(Instant.parse("2026-11-02T08:55:00Z"), rows[0].alarmAt)
        assertNull(rows[0].customLeadMinutes)
        assertEquals("#ff0000", rows[0].colorHex)

        assertEquals("Alarm at 10:45 (15 min before)", rows[1].alarmText)
        assertEquals(15, rows[1].customLeadMinutes)

        assertFalse(rows[2].selected)
        assertNull(rows[2].alarmText)
    }

    @Test
    fun zeroLeadReadsAsAtStartTime() {
        assertEquals("at start time", EventsUiMapper.leadText(0))
        assertEquals("2 min before", EventsUiMapper.leadText(2))
    }
}
