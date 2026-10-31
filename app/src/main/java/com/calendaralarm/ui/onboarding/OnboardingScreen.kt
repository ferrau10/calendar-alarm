package com.calendaralarm.ui.onboarding

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calendaralarm.ui.permissions.AppPermissions
import com.calendaralarm.ui.theme.CalendarAlarmTheme

/**
 * @param onConnect starts the Google connection flow; call [OnboardingViewModel.refresh] when it finishes.
 * @param connectError message to show if the last connection attempt failed.
 */
@Composable
fun OnboardingRoute(
    viewModel: OnboardingViewModel,
    permissions: AppPermissions,
    onConnect: () -> Unit,
    connectError: String?,
    onFinished: () -> Unit,
) {
    val step by viewModel.step.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    LaunchedEffect(step) { if (step == OnboardingStep.Done) onFinished() }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refresh()
    }
    fun open(intent: Intent?) {
        if (intent == null || runCatching { context.startActivity(intent) }.isFailure) viewModel.refresh()
    }

    OnboardingScreen(
        step = step,
        connectError = connectError,
        onContinueWelcome = viewModel::welcomeSeen,
        onConnect = onConnect,
        onAllowNotifications = {
            if (AppPermissions.needsNotificationRuntimePermission) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                open(permissions.notificationSettingsIntent())
            }
        },
        onAllowExactAlarms = { open(permissions.exactAlarmSettingsIntent()) },
        onAllowFullScreen = { open(permissions.fullScreenSettingsIntent()) },
        onAllowBattery = {
            viewModel.batteryAnswered()
            open(permissions.batterySettingsIntent())
        },
        onSkipBattery = viewModel::batteryAnswered,
    )
}

@Composable
fun OnboardingScreen(
    step: OnboardingStep,
    connectError: String?,
    onContinueWelcome: () -> Unit,
    onConnect: () -> Unit,
    onAllowNotifications: () -> Unit,
    onAllowExactAlarms: () -> Unit,
    onAllowFullScreen: () -> Unit,
    onAllowBattery: () -> Unit,
    onSkipBattery: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (step) {
                OnboardingStep.Welcome -> StepContent(
                    "Never miss a meeting",
                    "Pick the events in your Google Calendar you care about and your phone will ring a real " +
                        "alarm, with the event's name, a few minutes before each one.",
                    "Get started", onContinueWelcome,
                )
                OnboardingStep.Connect -> StepContent(
                    "Connect Google Calendar",
                    "Calendar Alarm only reads your events; it never changes your calendar.",
                    "Connect", onConnect, error = connectError,
                )
                OnboardingStep.Notifications -> StepContent(
                    "Allow notifications",
                    "Alarms are shown as notifications, so this is required for them to ring.",
                    "Allow", onAllowNotifications,
                )
                OnboardingStep.ExactAlarms -> StepContent(
                    "Allow alarms",
                    "Lets the app ring at the exact minute, even when your phone is idle.",
                    "Open settings", onAllowExactAlarms,
                )
                OnboardingStep.FullScreen -> StepContent(
                    "Allow full-screen alarms",
                    "Shows the alarm over your lock screen, like a regular alarm clock.",
                    "Open settings", onAllowFullScreen,
                )
                OnboardingStep.Battery -> StepContent(
                    "Keep alarms reliable (optional)",
                    "Some phones stop background apps to save battery. Choose \"Don't optimize\" / " +
                        "\"Unrestricted\" for Calendar Alarm so your meeting list stays up to date.",
                    "Open settings", onAllowBattery,
                    secondary = "Skip", onSecondary = onSkipBattery,
                )
                OnboardingStep.Done -> Unit
            }
        }
    }
}

@Composable
private fun StepContent(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    error: String? = null,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
) {
    Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(16.dp))
    Text(body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    if (error != null) {
        Spacer(Modifier.height(12.dp))
        Text(error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
    }
    Spacer(Modifier.height(32.dp))
    Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(action) }
    if (secondary != null) {
        TextButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth()) { Text(secondary) }
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingConnectPreview() = CalendarAlarmTheme {
    OnboardingScreen(OnboardingStep.Connect, null, {}, {}, {}, {}, {}, {}, {})
}

@Preview(showBackground = true)
@Composable
private fun OnboardingBatteryPreview() = CalendarAlarmTheme {
    OnboardingScreen(OnboardingStep.Battery, null, {}, {}, {}, {}, {}, {}, {})
}
