package com.calendaralarm.alarm.ringing

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RingingServiceTest {
    private lateinit var app: Application

    @Before
    fun setUp() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        app = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun ringerStartsServiceWithKeyAndLabel() {
        AndroidAlarmRinger(app).ring("event-1", "Standup")

        val started = shadowOf(app).nextStartedService
        assertEquals(RingingService.ACTION_RING, started.action)
        assertEquals("event-1", started.getStringExtra(RingingService.EXTRA_KEY))
        assertEquals("Standup", started.getStringExtra(RingingService.EXTRA_LABEL))
    }

    @Test
    fun ringingPostsFullScreenAlarmNotificationWithEventName() {
        val controller = Robolectric.buildService(
            RingingService::class.java, RingingService.ringIntent(app, "event-1", "Standup"),
        ).create().startCommand(0, 1)

        val notification = shadowOf(controller.get()).lastForegroundNotification
        assertNotNull(notification)
        assertEquals("Standup", notification.extras.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString())
        assertNotNull(notification.fullScreenIntent)
        assertEquals(NotificationCompat.CATEGORY_ALARM, notification.category)
        assertEquals(2, notification.actions.size)
        assertEquals(RingingAlarm("event-1", "Standup"), RingingService.current.value)

        val channel = app.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(RingingService.CHANNEL_ID)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        controller.destroy()
    }

    @Test
    fun dismissStopsRinging() {
        val controller = Robolectric.buildService(
            RingingService::class.java, RingingService.ringIntent(app, "event-1", "Standup"),
        ).create().startCommand(0, 1)

        controller.withIntent(RingingService.actionIntent(app, RingingService.ACTION_DISMISS)).startCommand(0, 2)

        assertNull(RingingService.current.value)
        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
        controller.destroy()
    }

    @Test
    fun snoozeSchedulesAlarmUnderSeparateKeyAndStops() {
        val controller = Robolectric.buildService(
            RingingService::class.java, RingingService.ringIntent(app, "event-1", "Standup"),
        ).create().startCommand(0, 1)

        controller.withIntent(RingingService.actionIntent(app, RingingService.ACTION_SNOOZE)).startCommand(0, 2)

        assertNull(RingingService.current.value)
        val alarmManager = app.getSystemService(AlarmManager::class.java)
        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)
        val fire = shadowOf(shadowOf(alarmManager).scheduledAlarms.single().operation).savedIntent
        assertEquals("event-1#snooze", fire.getStringExtra("alarm_key"))
        controller.destroy()
    }

    @Test
    fun snoozeKeyIsStable() {
        assertEquals("a#snooze", RingingService.snoozeKey("a"))
        assertEquals("a#snooze", RingingService.snoozeKey("a#snooze"))
    }
}
