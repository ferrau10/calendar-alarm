package com.calendaralarm.alarm.ringing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.calendaralarm.R
import com.calendaralarm.alarm.AndroidAlarmScheduler
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The alarm currently ringing, observed by [RingingActivity]. */
data class RingingAlarm(val alarmKey: String, val label: String)

/**
 * Foreground service that rings an alarm: posts a full-screen notification (which opens [RingingActivity]
 * over the lock screen), plays the alarm sound on the alarm audio stream and vibrates, until dismissed,
 * snoozed, or [AUTO_STOP_MILLIS] elapse.
 */
class RingingService : Service() {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoStop = Runnable { stopRinging() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RING -> {
                val key = intent.getStringExtra(EXTRA_KEY) ?: return stopAndForget()
                val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
                startRinging(RingingAlarm(key, label))
            }
            ACTION_DISMISS -> stopRinging()
            ACTION_SNOOZE -> {
                _current.value?.let { snooze(it) }
                stopRinging()
            }
            else -> return stopAndForget()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseOutputs()
        handler.removeCallbacks(autoStop)
        _current.value = null
        super.onDestroy()
    }

    private fun startRinging(alarm: RingingAlarm) {
        ensureChannel(this)
        val notification = buildNotification(alarm)
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
            } else {
                0
            },
        )
        // A new alarm while one is ringing replaces it.
        releaseOutputs()
        _current.value = alarm
        startSound()
        startVibration()
        handler.removeCallbacks(autoStop)
        handler.postDelayed(autoStop, AUTO_STOP_MILLIS)
    }

    private fun stopRinging() {
        releaseOutputs()
        handler.removeCallbacks(autoStop)
        _current.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopAndForget(): Int {
        if (_current.value == null) stopSelf()
        return START_NOT_STICKY
    }

    private fun snooze(alarm: RingingAlarm) {
        // Separate key so a calendar sync (which manages the event's own key) never cancels a snooze.
        AndroidAlarmScheduler(this).schedule(
            snoozeKey(alarm.alarmKey),
            Instant.now().plusSeconds(SNOOZE_MINUTES * 60L),
            alarm.label,
        )
    }

    private fun startSound() {
        val uri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: return
        player = try {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM) // alarm volume; plays even on silent
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(this@RingingService, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't play alarm sound; vibrating only", e)
            null
        }
    }

    private fun startVibration() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        } ?: return
        vibrator = v
        val pattern = longArrayOf(0, 800, 600)
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        @Suppress("DEPRECATION")
        v.vibrate(VibrationEffect.createWaveform(pattern, 0), attributes)
    }

    private fun releaseOutputs() {
        player?.runCatching { stop(); release() }
        player = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun buildNotification(alarm: RingingAlarm): Notification {
        val title = alarm.label.ifBlank { getString(R.string.alarm_untitled) }
        val fullScreen = PendingIntent.getActivity(
            this, 0, RingingActivity.intent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm_notification)
            .setContentTitle(title)
            .setContentText(getString(R.string.alarm_subtitle))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .addAction(0, getString(R.string.alarm_snooze, SNOOZE_MINUTES), servicePendingIntent(ACTION_SNOOZE))
            .addAction(0, getString(R.string.alarm_dismiss), servicePendingIntent(ACTION_DISMISS))
            .setDeleteIntent(servicePendingIntent(ACTION_DISMISS))
            .build()
    }

    private fun servicePendingIntent(action: String): PendingIntent = PendingIntent.getService(
        this, action.hashCode(), Intent(this, RingingService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val TAG = "RingingService"
        const val CHANNEL_ID = "meeting_alarms"
        const val NOTIFICATION_ID = 1001
        const val SNOOZE_MINUTES = 2
        const val AUTO_STOP_MILLIS = 60_000L

        const val ACTION_RING = "com.calendaralarm.action.RING"
        const val ACTION_DISMISS = "com.calendaralarm.action.DISMISS"
        const val ACTION_SNOOZE = "com.calendaralarm.action.SNOOZE"
        const val EXTRA_KEY = "alarm_key"
        const val EXTRA_LABEL = "alarm_label"

        private val _current = MutableStateFlow<RingingAlarm?>(null)
        val current: StateFlow<RingingAlarm?> = _current.asStateFlow()

        fun snoozeKey(alarmKey: String) = if (alarmKey.endsWith(SNOOZE_SUFFIX)) alarmKey else alarmKey + SNOOZE_SUFFIX
        private const val SNOOZE_SUFFIX = "#snooze"

        fun ringIntent(context: Context, alarmKey: String, label: String): Intent =
            Intent(context, RingingService::class.java)
                .setAction(ACTION_RING)
                .putExtra(EXTRA_KEY, alarmKey)
                .putExtra(EXTRA_LABEL, label)

        fun actionIntent(context: Context, action: String): Intent =
            Intent(context, RingingService::class.java).setAction(action)

        /** High-importance channel without its own sound (the service plays the alarm sound). */
        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID, context.getString(R.string.alarm_channel_name), NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.alarm_channel_description)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }
    }
}
