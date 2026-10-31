package com.calendaralarm.data.selection

import com.calendaralarm.domain.SelectedEvent
import com.calendaralarm.domain.SelectionStore
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** [SelectionStore] persisted with Room. */
class RoomSelectionStore(private val dao: SelectedEventDao) : SelectionStore {

    override fun observe(): Flow<List<SelectedEvent>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun all(): List<SelectedEvent> = dao.getAll().map { it.toDomain() }

    override suspend fun upsert(event: SelectedEvent) = dao.upsert(event.toEntity())

    override suspend fun remove(eventId: String) = dao.delete(eventId)

    /** Removes selections whose event started before [instant]; returns how many were removed. */
    suspend fun deleteStartedBefore(instant: Instant): Int = dao.deleteStartedBefore(instant.toEpochMilli())
}

internal fun SelectedEventEntity.toDomain() = SelectedEvent(
    eventId = eventId,
    calendarId = calendarId,
    title = title,
    start = Instant.ofEpochMilli(startEpochMillis),
    leadMinutes = leadMinutes,
)

internal fun SelectedEvent.toEntity() = SelectedEventEntity(
    eventId = eventId,
    calendarId = calendarId,
    title = title,
    startEpochMillis = start.toEpochMilli(),
    leadMinutes = leadMinutes,
)
