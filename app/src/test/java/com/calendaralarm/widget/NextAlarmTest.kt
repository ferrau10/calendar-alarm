package com.calendaralarm.widget

import com.calendaralarm.domain.SelectedEvent
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextAlarmTest {
    private val now = Instant.parse("2026-11-02T08:00:00Z")
    private fun sel(id: String, startMin: Long, lead: Int? = null) =
        SelectedEvent(id, "primary", "Event $id", now.plusSeconds(startMin * 60), lead?.let { listOf(it) })

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
    fun considersEveryAlarmOfAnEvent() {
        val e = SelectedEvent("a", "primary", "Event a", now.plusSeconds(60 * 60), listOf(45, 5))
        assertEquals(now.plusSeconds(15 * 60), nextAlarm(listOf(e), 5, now)?.alarmAt)
        // 45-min alarm already rang (event in 30 min): the 5-min one is next
        val soon = e.copy(start = now.plusSeconds(30 * 60))
        assertEquals(now.plusSeconds(25 * 60), nextAlarm(listOf(soon), 5, now)?.alarmAt)
    }

    @Test
    fun nothingSelected() {
        assertNull(nextAlarm(emptyList(), 5, now))
    }
}
