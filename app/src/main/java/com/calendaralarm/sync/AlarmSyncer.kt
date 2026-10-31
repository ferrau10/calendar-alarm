package com.calendaralarm.sync

import com.calendaralarm.domain.AlarmScheduler
import com.calendaralarm.domain.CalendarEvent
import com.calendaralarm.domain.CalendarRepository
import com.calendaralarm.domain.SelectedEvent
import com.calendaralarm.domain.SelectionStore
import com.calendaralarm.domain.SettingsStore
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Remembers which alarm keys are currently scheduled, so alarms for removed selections can be cancelled. */
interface ScheduledAlarmRegistry {
    suspend fun keys(): Set<String>
    suspend fun replace(keys: Set<String>)
}

data class SyncResult(
    val scheduled: Set<String>,
    val cancelled: Set<String>,
    val removedSelections: Set<String>,
    val updatedSelections: Set<String>,
    /** True when the calendar couldn't be reached for at least one event; those alarms were kept as they were. */
    val offline: Boolean,
)

/**
 * Single source of truth turning selected events into scheduled alarms.
 *
 * Network failures never drop an alarm: an event we can't refresh keeps its last known time.
 */
class AlarmSyncer(
    private val calendar: CalendarRepository,
    private val selections: SelectionStore,
    private val settings: SettingsStore,
    private val scheduler: AlarmScheduler,
    private val registry: ScheduledAlarmRegistry,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val mutex = Mutex()

    /**
     * @param refreshFromCalendar fetch the latest version of every selected event first. Pass `false` for
     *   purely local changes (selection or lead time edited) to reschedule instantly without network.
     */
    suspend fun sync(refreshFromCalendar: Boolean = true): SyncResult = mutex.withLock {
        val now = clock.instant()
        val defaultLead = settings.defaultLeadMinutes.first()
        val removed = mutableSetOf<String>()
        val updated = mutableSetOf<String>()
        var offline = false
        val active = mutableListOf<SelectedEvent>()

        val autoAdded = if (refreshFromCalendar) autoSelect(now) else AutoSelectOutcome.Skipped
        if (autoAdded == AutoSelectOutcome.Offline) offline = true

        for (selection in selections.all()) {
            var current = selection
            if (refreshFromCalendar) {
                val latest = try {
                    Fetch.Found(calendar.event(selection.calendarId, selection.eventId))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Fetch.Failed
                }
                when (latest) {
                    Fetch.Failed -> offline = true
                    is Fetch.Found -> {
                        val event = latest.event
                        if (event == null) {
                            selections.remove(selection.eventId)
                            removed += selection.eventId
                            continue
                        }
                        if (event.start != selection.start || event.title != selection.title) {
                            current = selection.copy(start = event.start, title = event.title)
                            selections.upsert(current)
                            updated += selection.eventId
                        }
                    }
                }
            }
            if (current.start <= now) {
                selections.remove(current.eventId)
                removed += current.eventId
                continue
            }
            active += current
        }

        val scheduled = mutableSetOf<String>()
        for (selection in active) {
            val triggerAt = selection.alarmTime(defaultLead)
            if (triggerAt.isAfter(now)) {
                scheduler.schedule(selection.eventId, triggerAt, selection.title)
                scheduled += selection.eventId
            } else {
                // Alarm time already passed (e.g. lead time increased); make sure no stale alarm rings later.
                scheduler.cancel(selection.eventId)
            }
        }

        val cancelled = registry.keys() - scheduled
        cancelled.forEach(scheduler::cancel)
        registry.replace(scheduled)

        SyncResult(scheduled, cancelled, removed, updated, offline)
    }

    private enum class AutoSelectOutcome { Skipped, Done, Offline }

    /** Adds upcoming events matching the enabled auto-select rules, except ones the user turned off. */
    private suspend fun autoSelect(now: java.time.Instant): AutoSelectOutcome {
        val video = settings.autoSelectVideoCalls.first()
        val accepted = settings.autoSelectAcceptedMeetings.first()
        if (!video && !accepted) return AutoSelectOutcome.Skipped
        val dismissed = settings.dismissedEventIds.first()
        val existing = selections.all().map { it.eventId }.toSet()
        val events = try {
            val ids = settings.enabledCalendarIds.first()
                .ifEmpty { calendar.calendars().filter { it.primary }.map { it.id }.toSet() }
            calendar.upcomingEvents(ids, now, now.plus(AUTO_SELECT_WINDOW))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return AutoSelectOutcome.Offline
        }
        events
            .filter { it.start.isAfter(now) && it.id !in existing && it.id !in dismissed }
            .filter { AutoSelectRules.matches(it, video, accepted) }
            .forEach { selections.upsert(SelectedEvent(it.id, it.calendarId, it.title, it.start, leadMinutes = null)) }
        return AutoSelectOutcome.Done
    }

    private companion object {
        val AUTO_SELECT_WINDOW: java.time.Duration = java.time.Duration.ofDays(7)
    }

    private sealed interface Fetch {
        data class Found(val event: CalendarEvent?) : Fetch
        data object Failed : Fetch
    }
}
