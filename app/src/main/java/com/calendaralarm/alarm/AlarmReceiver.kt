package com.calendaralarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Makes the phone ring for an alarm (implemented by the ringing screen, #9). */
fun interface AlarmRinger {
    fun ring(alarmKey: String, label: String)
}

/** Implemented by the Application to provide the real [AlarmRinger] (wired in #15). */
interface AlarmRingerProvider {
    val alarmRinger: AlarmRinger
}

/** Receives alarms scheduled by [AndroidAlarmScheduler] and hands them to the [AlarmRinger]. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
        val ringer = (context.applicationContext as? AlarmRingerProvider)?.alarmRinger ?: LogRinger
        ringer.ring(key, label)
    }

    private object LogRinger : AlarmRinger {
        override fun ring(alarmKey: String, label: String) {
            Log.w("AlarmReceiver", "No AlarmRinger wired yet; alarm '$label' ($alarmKey) fired")
        }
    }

    companion object {
        const val ACTION_FIRE = "com.calendaralarm.action.FIRE_ALARM"
        const val EXTRA_KEY = "alarm_key"
        const val EXTRA_LABEL = "alarm_label"
    }
}
