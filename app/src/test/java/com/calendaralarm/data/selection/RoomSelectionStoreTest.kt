package com.calendaralarm.data.selection

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.calendaralarm.domain.SelectedEvent
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RoomSelectionStoreTest {
    private lateinit var db: SelectionDatabase
    private lateinit var store: RoomSelectionStore

    private val t0 = Instant.parse("2026-11-02T08:00:00Z")
    private fun event(id: String, startOffsetMin: Long, lead: Int? = null, title: String = "Meeting $id") =
        SelectedEvent(id, "primary", title, t0.plusSeconds(startOffsetMin * 60), lead)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SelectionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = RoomSelectionStore(db.selectedEventDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun upsertInsertsAndRoundTripsAllFields() = runBlocking {
        store.upsert(event("a", 60, lead = 15))
        store.upsert(event("b", 30))

        val all = store.all()
        assertEquals(listOf(event("b", 30), event("a", 60, lead = 15)), all) // ordered by start
    }

    @Test
    fun upsertReplacesExistingEvent() = runBlocking {
        store.upsert(event("a", 60))
        store.upsert(event("a", 90, lead = 2, title = "Moved"))

        assertEquals(listOf(event("a", 90, lead = 2, title = "Moved")), store.all())
    }

    @Test
    fun removeDeletesOnlyThatEvent() = runBlocking {
        store.upsert(event("a", 60))
        store.upsert(event("b", 90))

        store.remove("a")
        store.remove("does-not-exist")

        assertEquals(listOf("b"), store.all().map { it.eventId })
    }

    @Test
    fun observeEmitsCurrentState() = runBlocking {
        assertTrue(store.observe().first().isEmpty())
        store.upsert(event("a", 60))
        assertEquals(listOf("a"), store.observe().first().map { it.eventId })
    }

    @Test
    fun deleteStartedBeforePrunesPastEvents() = runBlocking {
        store.upsert(event("past", -10))
        store.upsert(event("now", 0))
        store.upsert(event("future", 10))

        val removed = store.deleteStartedBefore(t0)

        assertEquals(1, removed)
        assertEquals(listOf("now", "future"), store.all().map { it.eventId })
    }
}
