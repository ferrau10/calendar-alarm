package com.calendaralarm.sync.android

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RestoreAlarmsReceiverTest {
    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            app, Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
    }

    private fun uniqueWork(name: String) = WorkManager.getInstance(app).getWorkInfosForUniqueWork(name).get()

    @Test
    fun manifestActionsAreHandled() {
        assertEquals(
            setOf(
                "android.intent.action.BOOT_COMPLETED",
                "android.intent.action.MY_PACKAGE_REPLACED",
                "android.intent.action.TIME_SET",
                "android.intent.action.TIMEZONE_CHANGED",
            ),
            RestoreAlarmsReceiver.HANDLED_ACTIONS,
        )
    }

    @Test
    fun bootEnqueuesLocalResyncAndPeriodicSync() {
        RestoreAlarmsReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertEquals(1, uniqueWork(AlarmSyncWork.NOW).size)
        assertEquals(1, uniqueWork(AlarmSyncWork.PERIODIC).size)
    }

    @Test
    fun timeChangeEnqueuesResync() {
        RestoreAlarmsReceiver().onReceive(app, Intent(Intent.ACTION_TIMEZONE_CHANGED))

        assertEquals(1, uniqueWork(AlarmSyncWork.NOW).size)
    }

    @Test
    fun unrelatedBroadcastIsIgnored() {
        RestoreAlarmsReceiver().onReceive(app, Intent("com.example.SOMETHING"))

        assertTrue(uniqueWork(AlarmSyncWork.NOW).isEmpty())
    }
}
