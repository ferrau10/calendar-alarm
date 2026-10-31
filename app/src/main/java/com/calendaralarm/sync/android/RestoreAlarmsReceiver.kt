package com.calendaralarm.sync.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Android drops all alarms on reboot and app update, and a clock or time-zone change can shift them.
 * Reschedules every selected event's alarm from local storage (no network needed); the periodic sync
 * picks up calendar changes later.
 */
class RestoreAlarmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        AlarmSyncWork.syncNow(context, refreshFromCalendar = false)
        AlarmSyncWork.schedulePeriodic(context)
    }

    internal companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, // "android.intent.action.TIME_SET"
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
