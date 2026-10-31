package com.calendaralarm

import android.app.Application
import com.calendaralarm.alarm.AlarmRinger
import com.calendaralarm.alarm.AlarmRingerProvider
import com.calendaralarm.domain.SelectionStore
import com.calendaralarm.domain.SettingsStore
import com.calendaralarm.sync.AlarmSyncer
import com.calendaralarm.sync.SyncTriggers
import com.calendaralarm.sync.android.AlarmSyncWork
import com.calendaralarm.sync.android.AlarmSyncerProvider
import com.calendaralarm.widget.NextAlarmWidget
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@HiltAndroidApp
class CalendarAlarmApp : Application(), AlarmSyncerProvider, AlarmRingerProvider {
    @Inject lateinit var syncer: AlarmSyncer
    @Inject lateinit var ringer: AlarmRinger
    @Inject lateinit var syncTriggers: SyncTriggers
    @Inject lateinit var selections: SelectionStore
    @Inject lateinit var settings: SettingsStore

    override val alarmSyncer: AlarmSyncer get() = syncer
    override val alarmRinger: AlarmRinger get() = ringer

    /** Lives as long as the process; used to reschedule alarms when selections or settings change. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        syncTriggers.start(appScope)
        NextAlarmWidget.keepUpdated(this, appScope, selections, settings)
        AlarmSyncWork.schedulePeriodic(this)
    }
}
