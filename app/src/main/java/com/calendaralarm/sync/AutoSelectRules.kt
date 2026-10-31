package com.calendaralarm.sync

import com.calendaralarm.domain.CalendarEvent

/** Which events the optional auto-select rules pick. */
object AutoSelectRules {
    fun matches(event: CalendarEvent, videoCalls: Boolean, acceptedMeetings: Boolean): Boolean {
        if (event.allDay) return false
        return (videoCalls && event.meetLink != null) ||
            (acceptedMeetings && event.hasOtherAttendees && event.acceptedByMe)
    }
}
