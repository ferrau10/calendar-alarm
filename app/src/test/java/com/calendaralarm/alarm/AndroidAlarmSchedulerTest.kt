package com.calendaralarm.alarm

import android.app.AlarmManager
import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AndroidAlarmSchedulerTest {
    private val now = Instant.parse("2026-11-02T08:00:00Z")
    private lateinit var app: Application
    private lateinit var alarmManager: AlarmManager
    private lateinit var scheduler: AndroidAlarmScheduler

    @Before
    fun setUp() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        app = ApplicationProvider.getApplicationContext()
        alarmManager = app.getSystemService(AlarmManager::class.java)
        scheduler = AndroidAlarmScheduler(app, Clock.fixed(now, ZoneOffset.UTC))
    }

    private fun scheduledCount() = shadowOf(alarmManager).scheduledAlarms.size

    @Test
    fun schedulesAlarmClockAtTriggerTime() {
        val at = now.plusSeconds(3300)
        scheduler.schedule("event-1", at, "Standup")

        assertEquals(1, scheduledCount())
        assertEquals(at.toEpochMilli(), alarmManager.nextAlarmClock.triggerTime)
    }

    @Test
    fun reschedulingSameKeyReplacesAlarm() {
        scheduler.schedule("event-1", now.plusSeconds(3300), "Standup")
        scheduler.schedule("event-1", now.plusSeconds(7200), "Standup (moved)")

        assertEquals(1, scheduledCount())
        assertEquals(now.plusSeconds(7200).toEpochMilli(), alarmManager.nextAlarmClock.triggerTime)
    }

    @Test
    fun differentKeysAreIndependent() {
        scheduler.schedule("event-1", now.plusSeconds(3300), "A")
        scheduler.schedule("event-2", now.plusSeconds(600), "B")
        assertEquals(2, scheduledCount())

        scheduler.cancel("event-2")

        assertEquals(1, scheduledCount())
        assertEquals(now.plusSeconds(3300).toEpochMilli(), alarmManager.nextAlarmClock.triggerTime)
    }

    @Test
    fun cancelRemovesAlarm() {
        scheduler.schedule("event-1", now.plusSeconds(3300), "Standup")

        scheduler.cancel("event-1")

        assertEquals(0, scheduledCount())
        assertNull(alarmManager.nextAlarmClock)
    }

    @Test
    fun cancellingUnknownKeyIsNoOp() {
        scheduler.cancel("nope")
        assertEquals(0, scheduledCount())
    }

    @Test
    fun pastTriggerIsIgnored() {
        scheduler.schedule("event-1", now.minusSeconds(1), "Too late")
        assertEquals(0, scheduledCount())
    }

    @Test
    fun receiverForwardsKeyAndLabelToRinger() {
        val rung = mutableListOf<Pair<String, String>>()
        val receiver = AlarmReceiver()
        val intent = Intent(AlarmReceiver.ACTION_FIRE)
            .putExtra(AlarmReceiver.EXTRA_KEY, "event-1")
            .putExtra(AlarmReceiver.EXTRA_LABEL, "Standup")

        // The test Application doesn't implement AlarmRingerProvider, so this just must not crash.
        receiver.onReceive(app, intent)

        val ringer = AlarmRinger { key, label -> rung += key to label }
        val providerContext = object : android.content.ContextWrapper(app) {
            override fun getApplicationContext() = object : Application(), AlarmRingerProvider {
                override val alarmRinger = ringer
            }
        }
        receiver.onReceive(providerContext, intent)

        assertEquals(listOf("event-1" to "Standup"), rung)
    }
}
