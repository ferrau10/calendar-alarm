package com.calendaralarm.data.selection

import android.app.Application
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.calendaralarm.domain.SelectedEvent
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SelectionMigrationTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    /** Creates the database exactly as version 0.1 (schema v1) did, with two selections. */
    private fun createV1() {
        app.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(app).name(name).callback(
                object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `selected_events` (`eventId` TEXT NOT NULL, `calendarId` TEXT NOT NULL, " +
                                "`title` TEXT NOT NULL, `startEpochMillis` INTEGER NOT NULL, `leadMinutes` INTEGER, PRIMARY KEY(`eventId`))",
                        )
                        db.execSQL("INSERT INTO selected_events VALUES ('a', 'primary', 'Standup', 1000, NULL)")
                        db.execSQL("INSERT INTO selected_events VALUES ('b', 'primary', 'Planning', 2000, 15)")
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                },
            ).build(),
        )
        helper.writableDatabase.close()
        helper.close()
    }

    @Test
    fun existingSelectionsSurviveTheUpgrade() = runBlocking {
        createV1()

        val db = Room.databaseBuilder(app, SelectionDatabase::class.java, name)
            .addMigrations(SelectionDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        val all = RoomSelectionStore(db.selectedEventDao()).all()
        db.close()

        assertEquals(
            listOf(
                SelectedEvent("a", "primary", "Standup", Instant.ofEpochMilli(1000), null),
                SelectedEvent("b", "primary", "Planning", Instant.ofEpochMilli(2000), listOf(15)),
            ),
            all,
        )
    }
}
