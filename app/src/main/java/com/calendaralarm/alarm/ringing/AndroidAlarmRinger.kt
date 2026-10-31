package com.calendaralarm.alarm.ringing

import android.content.Context
import androidx.core.content.ContextCompat
import com.calendaralarm.alarm.AlarmRinger

/** Rings by starting [RingingService], which shows the full-screen alarm and plays the alarm sound. */
class AndroidAlarmRinger(context: Context) : AlarmRinger {
    private val context = context.applicationContext

    override fun ring(alarmKey: String, label: String) {
        ContextCompat.startForegroundService(context, RingingService.ringIntent(context, alarmKey, label))
    }
}
