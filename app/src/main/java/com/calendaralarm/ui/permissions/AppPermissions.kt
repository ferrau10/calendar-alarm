package com.calendaralarm.ui.permissions

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** What the app needs from the user for alarms to work reliably. */
data class PermissionState(
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val fullScreenAlarms: Boolean,
    val ignoresBatteryOptimizations: Boolean,
) {
    /** Everything required (battery optimization is optional). */
    val allRequiredGranted: Boolean get() = notifications && exactAlarms && fullScreenAlarms

    companion object {
        val ALL_GRANTED = PermissionState(true, true, true, true)
    }
}

fun interface PermissionChecker {
    fun current(): PermissionState
}

/** Reads permission state and builds the system-settings intents to fix each one. */
class AppPermissions(context: Context) : PermissionChecker {
    private val context = context.applicationContext
    private val packageUri: Uri get() = Uri.parse("package:${context.packageName}")

    override fun current() = PermissionState(
        notifications = notificationsGranted(),
        exactAlarms = exactAlarmsGranted(),
        fullScreenAlarms = fullScreenGranted(),
        ignoresBatteryOptimizations = context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) ?: true,
    )

    fun notificationsGranted(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun exactAlarmsGranted(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun fullScreenGranted(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
        context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    /** Requires the runtime permission dialog on API 33+ (see [needsNotificationRuntimePermission]). */
    fun notificationSettingsIntent(): Intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun exactAlarmSettingsIntent(): Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    } else {
        null
    }

    fun fullScreenSettingsIntent(): Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    } else {
        null
    }

    /** The system list of battery-optimization exemptions (no special permission needed). */
    fun batterySettingsIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    companion object {
        val needsNotificationRuntimePermission: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }
}
