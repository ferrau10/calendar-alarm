package com.calendaralarm.sync

import com.calendaralarm.domain.SelectionStore
import com.calendaralarm.domain.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Reschedules alarms (without network) whenever the user changes which events are selected, a per-event
 * lead time, or the default lead time.
 */
class SyncTriggers(
    private val selections: SelectionStore,
    private val settings: SettingsStore,
    private val syncer: AlarmSyncer,
    private val debounceMillis: Long = 300,
) {
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope): Job = scope.launch {
        combine(selections.observe(), settings.defaultLeadMinutes) { list, default ->
            // Only what the user controls; title/start updates written by the syncer itself don't retrigger.
            list.map { it.eventId to it.leadMinutes }.toSet() to default
        }
            .distinctUntilChanged()
            .drop(1) // initial state: the app-open sync covers it
            .debounce(debounceMillis)
            .collect { syncer.sync(refreshFromCalendar = false) }
    }
}
