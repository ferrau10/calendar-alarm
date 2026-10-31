package com.calendaralarm.alarm.ringing

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.calendaralarm.R
import com.calendaralarm.ui.theme.CalendarAlarmTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.delay

/** Full-screen alarm shown over the lock screen while [RingingService] rings. */
class RingingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        setContent {
            CalendarAlarmTheme {
                val alarm by RingingService.current.collectAsState()
                LaunchedEffect(alarm) {
                    if (alarm == null) finish() // dismissed elsewhere, snoozed, or timed out
                }
                alarm?.let {
                    RingingScreen(
                        label = it.label.ifBlank { stringResource(R.string.alarm_untitled) },
                        onDismiss = { send(RingingService.ACTION_DISMISS) },
                        onSnooze = { send(RingingService.ACTION_SNOOZE) },
                    )
                }
            }
        }
    }

    private fun send(action: String) {
        startService(RingingService.actionIntent(this, action))
        finish()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, RingingActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}

@Composable
fun RingingScreen(label: String, onDismiss: () -> Unit, onSnooze: () -> Unit) {
    val formatter = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    val time = remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            time.value = LocalTime.now()
            delay(1_000)
        }
    }
    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(time.value.format(formatter), style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(24.dp))
            Text(
                label,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.alarm_subtitle), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(64.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(onClick = onSnooze, modifier = Modifier.weight(1f).height(64.dp)) {
                    Text(stringResource(R.string.alarm_snooze, RingingService.SNOOZE_MINUTES))
                }
                Button(onClick = onDismiss, modifier = Modifier.weight(1f).height(64.dp)) {
                    Text(stringResource(R.string.alarm_dismiss))
                }
            }
        }
    }
}

@Preview
@Composable
private fun RingingScreenPreview() {
    CalendarAlarmTheme { RingingScreen("Weekly planning", onDismiss = {}, onSnooze = {}) }
}
