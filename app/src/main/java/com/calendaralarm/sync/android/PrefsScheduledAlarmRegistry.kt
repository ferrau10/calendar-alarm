package com.calendaralarm.sync.android

import android.content.Context
import com.calendaralarm.sync.ScheduledAlarmRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [ScheduledAlarmRegistry] persisted in SharedPreferences (a handful of keys, read rarely). */
class PrefsScheduledAlarmRegistry(context: Context) : ScheduledAlarmRegistry {
    private val prefs = context.applicationContext.getSharedPreferences("scheduled_alarms", Context.MODE_PRIVATE)

    override suspend fun keys(): Set<String> = withContext(Dispatchers.IO) {
        prefs.getStringSet(KEY, emptySet()).orEmpty().toSet()
    }

    override suspend fun replace(keys: Set<String>) = withContext(Dispatchers.IO) {
        prefs.edit().putStringSet(KEY, keys.toSet()).commit()
        Unit
    }

    private companion object {
        const val KEY = "keys"
    }
}
