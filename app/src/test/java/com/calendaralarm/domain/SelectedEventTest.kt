package com.calendaralarm.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectedEventTest {
    private val start = Instant.parse("2026-11-02T10:00:00Z")
    private fun event(lead: Int?) = SelectedEvent("e1", "primary", "Standup", start, lead)

    @Test
    fun usesDefaultLeadTimeWhenNoOverride() {
        assertEquals(Instant.parse("2026-11-02T09:55:00Z"), event(null).alarmTime(DEFAULT_LEAD_MINUTES))
    }

    @Test
    fun perEventOverrideWins() {
        assertEquals(Instant.parse("2026-11-02T09:45:00Z"), event(15).alarmTime(DEFAULT_LEAD_MINUTES))
    }
}
