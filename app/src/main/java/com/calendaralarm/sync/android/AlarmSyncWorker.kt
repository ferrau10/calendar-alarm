package com.calendaralarm.sync.android

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.calendaralarm.sync.AlarmSyncer
import java.util.concurrent.TimeUnit

/** Implemented by the Application so workers and receivers can reach the syncer (wired in #15). */
interface AlarmSyncerProvider {
    val alarmSyncer: AlarmSyncer
}

/** Runs [AlarmSyncer.sync] in the background. */
class AlarmSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val provider = applicationContext as? AlarmSyncerProvider ?: return Result.failure()
        val refresh = inputData.getBoolean(KEY_REFRESH, true)
        val result = provider.alarmSyncer.sync(refreshFromCalendar = refresh)
        // Alarms were kept as they were; try again later to pick up calendar changes.
        return if (result.offline) Result.retry() else Result.success()
    }

    internal companion object {
        const val KEY_REFRESH = "refresh"
    }
}

object AlarmSyncWork {
    internal const val PERIODIC = "alarm-sync-periodic"
    internal const val NOW = "alarm-sync-now"

    /** Refreshes selected events from the calendar every 30 minutes while online. */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<AlarmSyncWorker>(30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /**
     * Syncs as soon as possible. With [refreshFromCalendar] = false no network is needed, which is what the
     * reboot / time-change receivers want.
     */
    fun syncNow(context: Context, refreshFromCalendar: Boolean = true) {
        val builder = OneTimeWorkRequestBuilder<AlarmSyncWorker>()
            .setInputData(workDataOf(AlarmSyncWorker.KEY_REFRESH to refreshFromCalendar))
        if (refreshFromCalendar) {
            builder.setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        }
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, builder.build())
    }
}
