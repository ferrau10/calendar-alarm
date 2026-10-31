package com.calendaralarm.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreSettingsStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() = scopes.forEach { it.cancel() }

    /** A DataStore over [file]; creating a second one on the same file simulates an app restart. */
    private fun store(file: File = File(tmp.root, "settings.preferences_pb")): DataStoreSettingsStore {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scopes += it }
        return DataStoreSettingsStore(PreferenceDataStoreFactory.create(scope = scope) { file })
    }

    @Test
    fun defaultsToFiveMinutesAndNoCalendars() = runBlocking {
        val store = store()
        assertEquals(5, store.defaultLeadMinutes.first())
        assertEquals(emptySet<String>(), store.enabledCalendarIds.first())
    }

    @Test
    fun clampsLeadMinutes() = runBlocking {
        val store = store()
        store.setDefaultLeadMinutes(500)
        assertEquals(120, store.defaultLeadMinutes.first())
        store.setDefaultLeadMinutes(-3)
        assertEquals(0, store.defaultLeadMinutes.first())
        store.setDefaultLeadMinutes(15)
        assertEquals(15, store.defaultLeadMinutes.first())
    }

    @Test
    fun autoSelectFlagsDefaultOffAndPersist() = runBlocking {
        val store = store()
        assertEquals(false, store.autoSelectVideoCalls.first())
        assertEquals(false, store.autoSelectAcceptedMeetings.first())
        store.setAutoSelectVideoCalls(true)
        store.setAutoSelectAcceptedMeetings(true)
        assertEquals(true, store.autoSelectVideoCalls.first())
        assertEquals(true, store.autoSelectAcceptedMeetings.first())
    }

    @Test
    fun dismissedIdsAccumulate() = runBlocking {
        val store = store()
        store.addDismissedEventId("a")
        store.addDismissedEventId("b")
        store.addDismissedEventId("a")
        assertEquals(setOf("a", "b"), store.dismissedEventIds.first())
    }

    @Test
    fun persistsAcrossInstances() = runBlocking {
        val file = File(tmp.root, "persist.preferences_pb")
        val first = store(file)
        first.setDefaultLeadMinutes(10)
        first.setEnabledCalendarIds(setOf("primary", "team@group.calendar.google.com"))
        // Fully close the first DataStore before reopening the file (only one may be active per file).
        scopes.forEach { it.coroutineContext[Job]!!.cancelAndJoin() }
        scopes.clear()

        val second = store(file)
        assertEquals(10, second.defaultLeadMinutes.first())
        assertEquals(setOf("primary", "team@group.calendar.google.com"), second.enabledCalendarIds.first())
    }
}
