package com.calendaralarm.data.selection

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "selected_events")
data class SelectedEventEntity(
    @PrimaryKey val eventId: String,
    val calendarId: String,
    val title: String,
    val startEpochMillis: Long,
    /** Minutes before start; null = use the global default. */
    val leadMinutes: Int?,
)

@Dao
interface SelectedEventDao {
    @Query("SELECT * FROM selected_events ORDER BY startEpochMillis, eventId")
    fun observeAll(): Flow<List<SelectedEventEntity>>

    @Query("SELECT * FROM selected_events ORDER BY startEpochMillis, eventId")
    suspend fun getAll(): List<SelectedEventEntity>

    @Upsert
    suspend fun upsert(entity: SelectedEventEntity)

    @Query("DELETE FROM selected_events WHERE eventId = :eventId")
    suspend fun delete(eventId: String)

    @Query("DELETE FROM selected_events WHERE startEpochMillis < :epochMillis")
    suspend fun deleteStartedBefore(epochMillis: Long): Int
}

@Database(entities = [SelectedEventEntity::class], version = 1, exportSchema = false)
abstract class SelectionDatabase : RoomDatabase() {
    abstract fun selectedEventDao(): SelectedEventDao

    companion object {
        fun create(context: Context): SelectionDatabase =
            Room.databaseBuilder(context.applicationContext, SelectionDatabase::class.java, "selections.db").build()
    }
}
