package com.calendaralarm.widget

import com.calendaralarm.domain.SelectedEvent
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextAlarmTest {
    private val now = Instant.parse("2026-11-02T08:00:00Z")
    private fun sel(id: String, startMin: Long, lead: Int? = null) =
        SelectedEvent(id, "primary", "Event $id", now.plusSeconds(startMin * 60), lead)

    @Test
    fun picksSoonestAlarmConsideringLeadTimes() {
        // b starts later but rings earlier because of its 30-minute lead time
        val next = nextAlarm(listOf(sel("a", 60), sel("b", 70, lead = 30)), 5, now)
        assertEquals("Event b", next?.title)
        assertEquals(now.plusSeconds(40 * 60), next?.alarmAt)
    }

    @Test
    fun ignoresAlarmsThatAlreadyRang() {
        val next = nextAlarm(listOf(sel("soon", 3), sel("later", 120)), 5, now)
        assertEquals("Event later", next?.title)
    }

    @Test
    fun nothingSelected() {
        assertNull(nextAlarm(emptyList(), 5, now))
    }
}
