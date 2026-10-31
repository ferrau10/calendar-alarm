package com.calendaralarm.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calendaralarm.domain.AuthRepository
import com.calendaralarm.domain.CalendarEvent
import com.calendaralarm.domain.CalendarRepository
import com.calendaralarm.domain.NotAuthorizedException
import com.calendaralarm.domain.SelectedEvent
import com.calendaralarm.domain.SelectionStore
import com.calendaralarm.domain.SettingsStore
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EventsViewModel(
    private val auth: AuthRepository,
    private val calendar: CalendarRepository,
    private val selections: SelectionStore,
    private val settings: SettingsStore,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private sealed interface Load {
        data object Loading : Load
        data object NotConnected : Load
        data class Failed(val message: String) : Load
        data class Loaded(val events: List<CalendarEvent>, val colors: Map<String, String?>, val refreshing: Boolean) : Load
    }

    private val load = MutableStateFlow<Load>(Load.Loading)
    private val mapper = EventsUiMapper(clock.zone)

    val uiState: StateFlow<EventsUiState> =
        combine(load, selections.observe(), settings.defaultLeadMinutes) { load, selected, defaultLead ->
            when (load) {
                Load.Loading -> EventsUiState.Loading
                Load.NotConnected -> EventsUiState.NotConnected
                is Load.Failed -> EventsUiState.Error(load.message)
                is Load.Loaded -> EventsUiState.Content(
                    sections = mapper.sections(
                        load.events, selected, load.colors, defaultLead, clock.instant().atZone(clock.zone).toLocalDate(),
                    ),
                    refreshing = load.refreshing,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EventsUiState.Loading)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            if (auth.account.value == null) {
                load.value = Load.NotConnected
                return@launch
            }
            load.value = when (val current = load.value) {
                is Load.Loaded -> current.copy(refreshing = true)
                else -> Load.Loading
            }
            load.value = try {
                val calendars = calendar.calendars()
                val enabled = settings.enabledCalendarIds.first()
                val ids = enabled.ifEmpty { calendars.filter { it.primary }.map { it.id }.toSet().ifEmpty { setOf("primary") } }
                val now = clock.instant()
                val events = calendar.upcomingEvents(ids, now, now.plus(WINDOW))
                Load.Loaded(events, calendars.associate { it.id to it.colorHex }, refreshing = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: NotAuthorizedException) {
                Load.NotConnected
            } catch (e: Exception) {
                Load.Failed(e.message ?: "Couldn't load your calendar")
            }
        }
    }

    /** Turns the alarm for [row] on or off. All-day events can't have alarms. */
    fun toggle(row: EventRow) {
        viewModelScope.launch {
            if (row.selected) {
                selections.remove(row.eventId)
            } else if (!row.allDay) {
                selections.upsert(SelectedEvent(row.eventId, row.calendarId, row.title, row.start, leadMinutes = null))
            }
        }
    }

    /** Sets a per-event lead time ([minutes] = null to use the default); also turns the alarm on. */
    fun setLeadMinutes(row: EventRow, minutes: Int?) {
        if (row.allDay) return
        viewModelScope.launch {
            selections.upsert(SelectedEvent(row.eventId, row.calendarId, row.title, row.start, minutes))
        }
    }

    companion object {
        val WINDOW: Duration = Duration.ofDays(7)
        val LEAD_CHOICES = listOf(0, 1, 2, 5, 10, 15, 30)
    }
}
