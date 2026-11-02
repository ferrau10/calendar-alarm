package com.calendaralarm.ui.leadtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LeadMinutesTest {
    @Test
    fun acceptsWholeMinutesInRange() {
        assertEquals(LeadMinutesInput.Valid(17), parseLeadMinutes("17"))
        assertEquals(LeadMinutesInput.Valid(0), parseLeadMinutes(" 0 "))
        assertEquals(LeadMinutesInput.Valid(120), parseLeadMinutes("120"))
    }

    @Test
    fun rejectsEmptyNonNumbersAndOutOfRange() {
        assertTrue(parseLeadMinutes("") is LeadMinutesInput.Invalid)
        assertTrue(parseLeadMinutes("abc") is LeadMinutesInput.Invalid)
        assertTrue(parseLeadMinutes("7.5") is LeadMinutesInput.Invalid)
        assertTrue(parseLeadMinutes("-1") is LeadMinutesInput.Invalid)
        assertTrue(parseLeadMinutes("121") is LeadMinutesInput.Invalid)
    }
}
