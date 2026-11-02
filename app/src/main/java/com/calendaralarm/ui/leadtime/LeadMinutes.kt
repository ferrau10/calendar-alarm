package com.calendaralarm.ui.leadtime

/** Largest custom alarm time, in minutes (matches the settings store's clamp). */
const val MAX_CUSTOM_LEAD_MINUTES = 120

sealed interface LeadMinutesInput {
    data class Valid(val minutes: Int) : LeadMinutesInput
    data class Invalid(val reason: String) : LeadMinutesInput
}

/** Validates what the user typed in a custom "minutes before" field. */
fun parseLeadMinutes(text: String): LeadMinutesInput {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return LeadMinutesInput.Invalid("Enter a number of minutes")
    val minutes = trimmed.toIntOrNull()
        ?: return LeadMinutesInput.Invalid("Use whole minutes, e.g. 15")
    return if (minutes in 0..MAX_CUSTOM_LEAD_MINUTES) {
        LeadMinutesInput.Valid(minutes)
    } else {
        LeadMinutesInput.Invalid("Choose between 0 and $MAX_CUSTOM_LEAD_MINUTES minutes")
    }
}
