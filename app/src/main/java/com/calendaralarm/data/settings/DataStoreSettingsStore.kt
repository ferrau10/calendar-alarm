package com.calendaralarm.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.calendaralarm.domain.DEFAULT_LEAD_MINUTES
import com.calendaralarm.domain.SettingsStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The app's settings file; use a single instance per process. */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** [SettingsStore] backed by Preferences DataStore. */
class DataStoreSettingsStore(private val dataStore: DataStore<Preferences>) : SettingsStore {

    private val prefs: Flow<Preferences> = dataStore.data.catch { e ->
        // A corrupt/unreadable file must not break alarms: fall back to defaults.
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    override val defaultLeadMinutes: Flow<Int> = prefs
        .map { clampLead(it[DEFAULT_LEAD] ?: DEFAULT_LEAD_MINUTES) }
        .distinctUntilChanged()

    override suspend fun setDefaultLeadMinutes(minutes: Int) {
        dataStore.edit { it[DEFAULT_LEAD] = clampLead(minutes) }
    }

    override val enabledCalendarIds: Flow<Set<String>> = prefs
        .map { it[ENABLED_CALENDARS].orEmpty() }
        .distinctUntilChanged()

    override suspend fun setEnabledCalendarIds(ids: Set<String>) {
        dataStore.edit { it[ENABLED_CALENDARS] = ids }
    }

    override val autoSelectVideoCalls: Flow<Boolean> = prefs.map { it[AUTO_VIDEO] ?: false }.distinctUntilChanged()

    override suspend fun setAutoSelectVideoCalls(enabled: Boolean) {
        dataStore.edit { it[AUTO_VIDEO] = enabled }
    }

    override val autoSelectAcceptedMeetings: Flow<Boolean> =
        prefs.map { it[AUTO_ACCEPTED] ?: false }.distinctUntilChanged()

    override suspend fun setAutoSelectAcceptedMeetings(enabled: Boolean) {
        dataStore.edit { it[AUTO_ACCEPTED] = enabled }
    }

    override val dismissedEventIds: Flow<Set<String>> = prefs.map { it[DISMISSED].orEmpty() }.distinctUntilChanged()

    override suspend fun addDismissedEventId(eventId: String) {
        dataStore.edit {
            // Bounded: oldest entries (by insertion order of the stored set) are dropped first.
            val updated = (it[DISMISSED].orEmpty().toList() + eventId).distinct().takeLast(MAX_DISMISSED)
            it[DISMISSED] = updated.toSet()
        }
    }

    companion object {
        const val MIN_LEAD_MINUTES = 0
        const val MAX_DISMISSED = 500
        const val MAX_LEAD_MINUTES = 120

        fun clampLead(minutes: Int): Int = minutes.coerceIn(MIN_LEAD_MINUTES, MAX_LEAD_MINUTES)

        private val DEFAULT_LEAD = intPreferencesKey("default_lead_minutes")
        private val ENABLED_CALENDARS = stringSetPreferencesKey("enabled_calendar_ids")
        private val AUTO_VIDEO = booleanPreferencesKey("auto_select_video_calls")
        private val AUTO_ACCEPTED = booleanPreferencesKey("auto_select_accepted_meetings")
        private val DISMISSED = stringSetPreferencesKey("dismissed_event_ids")
    }
}
