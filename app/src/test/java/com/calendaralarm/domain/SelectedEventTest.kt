package com.calendaralarm.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectedEventTest {
    private val start = Instant.parse("2026-11-02T10:00:00Z")
    private fun event(lead: Int?) = SelectedEvent("e1", "primary", "Standup", start, lead?.let { listOf(it) })

    @Test
    fun usesDefaultLeadTimeWhenNoOverride() {
        assertEquals(Instant.parse("2026-11-02T09:55:00Z"), event(null).alarms(DEFAULT_LEAD_MINUTES).single().at)
    }

    @Test
    fun perEventOverrideWins() {
        assertEquals(Instant.parse("2026-11-02T09:45:00Z"), event(15).alarms(DEFAULT_LEAD_MINUTES).single().at)
    }

    @Test
    fun severalLeadTimesGiveOneAlarmEachEarliestFirst() {
        val e = SelectedEvent("e1", "primary", "Standup", start, listOf(5, 45, 5))
        val alarms = e.alarms(DEFAULT_LEAD_MINUTES)
        assertEquals(listOf("e1@45", "e1@5"), alarms.map { it.key })
        assertEquals(listOf(Instant.parse("2026-11-02T09:15:00Z"), Instant.parse("2026-11-02T09:55:00Z")), alarms.map { it.at })
    }

    @Test
    fun normalizeLeadTimesSortsDedupesAndTreatsEmptyAsDefault() {
        assertEquals(listOf(45, 5), normalizeLeadTimes(listOf(5, 45, 5)))
        assertEquals(null, normalizeLeadTimes(emptyList()))
        assertEquals(null, normalizeLeadTimes(null))
    }
}
