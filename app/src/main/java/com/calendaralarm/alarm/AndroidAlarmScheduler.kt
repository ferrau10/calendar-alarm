package com.calendaralarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.calendaralarm.MainActivity
import com.calendaralarm.domain.AlarmScheduler
import java.time.Clock
import java.time.Instant

/**
 * [AlarmScheduler] using [AlarmManager.setAlarmClock]: exact, allowed in Doze, and shown as the
 * upcoming alarm in the status bar / lock screen.
 *
 * Each alarm is identified by its key through the intent's data URI, so scheduling the same key again
 * replaces the previous alarm and keys can never collide.
 */
class AndroidAlarmScheduler(
    context: Context,
    private val clock: Clock = Clock.systemUTC(),
) : AlarmScheduler {
    private val context = context.applicationContext
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(alarmKey: String, triggerAt: Instant, label: String) {
        if (!triggerAt.isAfter(clock.instant())) return
        val operation = PendingIntent.getBroadcast(
            context, 0, fireIntent(alarmKey).putExtra(AlarmReceiver.EXTRA_LABEL, label),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt.toEpochMilli(), showIntent()), operation)
        } catch (e: SecurityException) {
            // Exact alarms not permitted (user revoked it on API 31-32): better late than never.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt.toEpochMilli(), operation)
        }
    }

    override fun cancel(alarmKey: String) {
        val operation = PendingIntent.getBroadcast(
            context, 0, fireIntent(alarmKey),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(operation)
        operation.cancel()
    }

    override fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun fireIntent(alarmKey: String) = Intent(context, AlarmReceiver::class.java)
        .setAction(AlarmReceiver.ACTION_FIRE)
        .setData(Uri.fromParts(URI_SCHEME, alarmKey, null))
        .putExtra(AlarmReceiver.EXTRA_KEY, alarmKey)

    /** What opens when the user taps the upcoming alarm in the system UI. */
    private fun showIntent(): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val URI_SCHEME = "calendaralarm"
    }
}
