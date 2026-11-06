package com.calendaralarm.ui.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calendaralarm.domain.LEAD_TIME_CHOICES
import com.calendaralarm.ui.events.EventsUiMapper
import com.calendaralarm.ui.leadtime.CustomMinutesDialog
import com.calendaralarm.ui.permissions.AppPermissions
import com.calendaralarm.ui.permissions.PermissionState
import com.calendaralarm.ui.theme.CalendarAlarmTheme

/** System-settings shortcuts for each permission row; null when not applicable on this Android version. */
data class PermissionIntents(
    val notifications: Intent?,
    val exactAlarms: Intent?,
    val fullScreen: Intent?,
    val battery: Intent?,
)

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    permissions: AppPermissions,
    onBack: () -> Unit,
    /** Runs the Google sign-in flow for one more account. */
    onAddAccount: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermissions() }
    val context = LocalContext.current
    val intents = PermissionIntents(
        notifications = permissions.notificationSettingsIntent(),
        exactAlarms = permissions.exactAlarmSettingsIntent(),
        fullScreen = permissions.fullScreenSettingsIntent(),
        battery = permissions.batterySettingsIntent(),
    )
    SettingsScreen(
        state = state,
        onBack = onBack,
        onLeadChange = viewModel::setDefaultLeadMinutes,
        onToggleCalendar = viewModel::toggleCalendar,
        onRetryCalendars = viewModel::loadCalendars,
        onRemoveAccount = viewModel::removeAccount,
        onAddAccount = onAddAccount,
        onTestAlarm = viewModel::scheduleTestAlarm,
        onAutoVideoChange = viewModel::setAutoSelectVideoCalls,
        onAutoAcceptedChange = viewModel::setAutoSelectAcceptedMeetings,
        onOpenSystemSettings = { intent -> runCatching { context.startActivity(intent) } },
        permissionIntents = intents,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onLeadChange: (Int) -> Unit,
    onToggleCalendar: (String) -> Unit,
    onRetryCalendars: () -> Unit,
    onRemoveAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    onTestAlarm: () -> Unit,
    onOpenSystemSettings: (Intent) -> Unit,
    permissionIntents: PermissionIntents,
    onAutoVideoChange: (Boolean) -> Unit = {},
    onAutoAcceptedChange: (Boolean) -> Unit = {},
) {
    var customDefault by remember { mutableStateOf(false) }
    if (customDefault) {
        CustomMinutesDialog(
            title = "Default alarm time",
            initialMinutes = state.defaultLeadMinutes,
            onSave = { onLeadChange(it); customDefault = false },
            onDismiss = { customDefault = false },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
        ) {
            Section("Alarm time")
            Text(
                "Default for all meetings: ${EventsUiMapper.leadText(state.defaultLeadMinutes)}. " +
                    "To change a single meeting, tap “Change” under it in the list.",
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            FlowRow(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val choices = (LEAD_TIME_CHOICES + state.defaultLeadMinutes).distinct().sorted()
                choices.forEach { minutes ->
                    FilterChip(
                        selected = minutes == state.defaultLeadMinutes,
                        onClick = { onLeadChange(minutes) },
                        label = { Text(if (minutes == 0) "0 min" else "$minutes min") },
                    )
                }
                FilterChip(selected = false, onClick = { customDefault = true }, label = { Text("Custom…") })
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Section("Automatic alarms")
            SwitchRow("Events with a video call link", state.autoSelectVideoCalls, onAutoVideoChange)
            SwitchRow("Meetings I accepted with other people", state.autoSelectAcceptedMeetings, onAutoAcceptedChange)
            Hint("Matching events in the next 7 days get an alarm automatically. Turn one off and it stays off.")
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Section("Calendars to show")
            when {
                state.accounts.isEmpty() -> Hint("Connect a Google account to choose calendars.")
                state.calendarsError != null -> {
                    Hint(state.calendarsError)
                    OutlinedButton(onClick = onRetryCalendars, modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text("Try again")
                    }
                }
                state.calendars.isEmpty() -> Hint("Loading calendars…")
                else -> state.calendars.forEach { choice ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onToggleCalendar(choice.id) }.padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = choice.checked, onCheckedChange = { onToggleCalendar(choice.id) })
                        Box(Modifier.size(10.dp).clip(CircleShape).background(choice.colorHex.toColor() ?: MaterialTheme.colorScheme.outline))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(choice.name)
                            // With several accounts, show which one each calendar belongs to.
                            if (state.accounts.size > 1 && choice.account.isNotEmpty() && choice.account != choice.name) {
                                Text(choice.account, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Section("Permissions")
            PermissionRow("Notifications", state.permissions.notifications, permissionIntents.notifications, onOpenSystemSettings)
            PermissionRow("Exact alarms", state.permissions.exactAlarms, permissionIntents.exactAlarms, onOpenSystemSettings)
            PermissionRow("Full-screen alarms", state.permissions.fullScreenAlarms, permissionIntents.fullScreen, onOpenSystemSettings)
            PermissionRow(
                "Unrestricted battery (optional)", state.permissions.ignoresBatteryOptimizations,
                permissionIntents.battery, onOpenSystemSettings,
            )
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onTestAlarm) { Text("Test alarm") }
                Spacer(Modifier.width(12.dp))
                if (state.testAlarmScheduled) Text("Rings in 10 seconds", style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Section("Google accounts")
            if (state.accounts.isEmpty()) Hint("Not connected")
            state.accounts.forEach { account ->
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(account, Modifier.weight(1f))
                    TextButton(onClick = { onRemoveAccount(account) }) { Text("Remove") }
                }
            }
            OutlinedButton(onClick = onAddAccount, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(if (state.accounts.isEmpty()) "Connect Google account" else "Add another account")
            }
            if (state.accounts.size > 1) {
                Hint("Removing an account also removes the alarms for its meetings.")
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, fix: Intent?, open: (Intent) -> Unit) {
    val clickable = !granted && fix != null
    Row(
        Modifier.fillMaxWidth()
            .then(if (clickable) Modifier.clickable { open(fix!!) } else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label)
            if (clickable) Text("Tap to allow", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Text(
            if (granted) "Allowed" else "Not allowed",
            color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
}

private fun String?.toColor(): Color? = this?.let {
    runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
}

@Preview(showBackground = true)
@Composable
private fun SettingsPreview() = CalendarAlarmTheme {
    SettingsScreen(
        state = SettingsUiState(
            defaultLeadMinutes = 5,
            calendars = listOf(
                CalendarChoice("me@example.com", "me@example.com", "#4285f4", true, "me@example.com"),
                CalendarChoice("work@example.com", "Work", "#0b8043", false, "work@example.com"),
            ),
            accounts = listOf("me@example.com", "work@example.com"),
            permissions = PermissionState(true, true, false, false),
        ),
        onBack = {}, onLeadChange = {}, onToggleCalendar = {}, onRetryCalendars = {}, onRemoveAccount = {}, onAddAccount = {},
        onTestAlarm = {}, onOpenSystemSettings = {},
        permissionIntents = PermissionIntents(null, null, null, null),
    )
}
