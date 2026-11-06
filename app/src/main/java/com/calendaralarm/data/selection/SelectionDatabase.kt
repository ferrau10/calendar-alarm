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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "selected_events")
data class SelectedEventEntity(
    @PrimaryKey val eventId: String,
    val calendarId: String,
    val title: String,
    val startEpochMillis: Long,
    /** Comma-separated minutes before start, e.g. "45,5"; null = use the global default. */
    val leadMinutesCsv: String?,
    /** Google account the event belongs to; null for selections from before multiple accounts. */
    val account: String? = null,
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

@Database(entities = [SelectedEventEntity::class], version = 3, exportSchema = false)
abstract class SelectionDatabase : RoomDatabase() {
    abstract fun selectedEventDao(): SelectedEventDao

    companion object {
        fun create(context: Context): SelectionDatabase =
            Room.databaseBuilder(context.applicationContext, SelectionDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

        const val DB_NAME = "selections.db"

        /** v3: remember which Google account each selection belongs to. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `selected_events` ADD COLUMN `account` TEXT")
            }
        }

        /** v2: one lead time (INTEGER) → several (comma-separated TEXT). Keeps existing selections. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `selected_events_new` (`eventId` TEXT NOT NULL, " +
                        "`calendarId` TEXT NOT NULL, `title` TEXT NOT NULL, `startEpochMillis` INTEGER NOT NULL, " +
                        "`leadMinutesCsv` TEXT, PRIMARY KEY(`eventId`))",
                )
                db.execSQL(
                    "INSERT INTO `selected_events_new` (eventId, calendarId, title, startEpochMillis, leadMinutesCsv) " +
                        "SELECT eventId, calendarId, title, startEpochMillis, CAST(leadMinutes AS TEXT) FROM `selected_events`",
                )
                db.execSQL("DROP TABLE `selected_events`")
                db.execSQL("ALTER TABLE `selected_events_new` RENAME TO `selected_events`")
            }
        }
    }
}
