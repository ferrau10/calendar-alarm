package com.calendaralarm.ui.events

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calendaralarm.ui.theme.CalendarAlarmTheme
import java.time.Instant
import java.time.LocalDate

@Composable
fun EventsRoute(viewModel: EventsViewModel, onConnect: () -> Unit, onOpenSettings: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshIfStale() }
    EventsScreen(
        state = state,
        onRefresh = viewModel::refresh,
        onToggle = viewModel::toggle,
        onSetLead = viewModel::setLeadMinutes,
        onConnect = onConnect,
        onOpenSettings = onOpenSettings,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(
    state: EventsUiState,
    onRefresh: () -> Unit,
    onToggle: (EventRow) -> Unit,
    onSetLead: (EventRow, Int?) -> Unit,
    onConnect: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var editing by remember { mutableStateOf<EventRow?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upcoming events") },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                EventsUiState.Loading -> Message("Loading your calendar…")
                EventsUiState.NotConnected -> Message("Connect your Google Calendar to pick meetings.", "Connect", onConnect)
                is EventsUiState.Error -> Message(state.message, "Try again", onRefresh)
                is EventsUiState.Content -> PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (state.isEmpty) {
                        Message("No events in the next 7 days.")
                    } else {
                        EventList(state.sections, onToggle, onLongPress = { editing = it })
                    }
                }
            }
        }
    }
    editing?.let { row ->
        LeadTimeDialog(
            row = row,
            onPick = { minutes -> onSetLead(row, minutes); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun Message(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun EventList(sections: List<DaySection>, onToggle: (EventRow) -> Unit, onLongPress: (EventRow) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        sections.forEach { section ->
            item(key = "header-${section.date}") {
                Text(
                    section.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
                )
            }
            items(section.rows, key = { it.eventId }) { row ->
                EventRowItem(row, onToggle, onLongPress)
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EventRowItem(row: EventRow, onToggle: (EventRow) -> Unit, onLongPress: (EventRow) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = !row.allDay,
                onClick = { onToggle(row) },
                onLongClick = { onLongPress(row) },
                onLongClickLabel = "Change alarm time",
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(10.dp).clip(CircleShape).background(row.colorHex.toColor() ?: MaterialTheme.colorScheme.outline),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(row.title, style = MaterialTheme.typography.bodyLarge)
            Text(row.timeText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            row.alarmText?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        Switch(checked = row.selected, onCheckedChange = { onToggle(row) }, enabled = !row.allDay)
    }
}

@Composable
private fun LeadTimeDialog(row: EventRow, onPick: (Int?) -> Unit, onDismiss: () -> Unit) {
    var choice by remember { mutableStateOf(row.customLeadMinutes) }
    val options: List<Pair<Int?, String>> =
        listOf<Pair<Int?, String>>(null to "Use default") + EventsViewModel.LEAD_CHOICES.map { it to EventsUiMapper.leadText(it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ring before “${row.title}”") },
        text = {
            Column {
                options.forEach { (minutes, label) ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected = choice == minutes, onClick = { choice = minutes }, role = Role.RadioButton)
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = choice == minutes, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(choice) }) { Text("Set alarm") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun String?.toColor(): Color? = this?.let {
    runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
}

private val previewSections = listOf(
    DaySection(
        LocalDate.of(2026, 11, 2), "Today",
        listOf(
            EventRow("1", "primary", "Standup", Instant.EPOCH, "09:00 – 09:15", "#4285f4", false, true, Instant.EPOCH, "Alarm at 08:55 (5 min before)", null),
            EventRow("2", "primary", "Lunch with Sam", Instant.EPOCH, "13:00 – 14:00", "#0b8043", false, false, null, null, null),
        ),
    ),
    DaySection(
        LocalDate.of(2026, 11, 3), "Tomorrow",
        listOf(EventRow("3", "work", "Company holiday", Instant.EPOCH, "All day", "#f6bf26", true, false, null, null, null)),
    ),
)

@Preview(showBackground = true)
@Composable
private fun EventsListPreview() = CalendarAlarmTheme {
    EventsScreen(EventsUiState.Content(previewSections), {}, {}, { _, _ -> }, {}, {})
}

@Preview(showBackground = true)
@Composable
private fun EventsEmptyPreview() = CalendarAlarmTheme {
    EventsScreen(EventsUiState.Content(emptyList()), {}, {}, { _, _ -> }, {}, {})
}

@Preview(showBackground = true)
@Composable
private fun EventsErrorPreview() = CalendarAlarmTheme {
    EventsScreen(EventsUiState.Error("Couldn't reach Google Calendar"), {}, {}, { _, _ -> }, {}, {})
}

@Preview(showBackground = true)
@Composable
private fun EventsNotConnectedPreview() = CalendarAlarmTheme {
    EventsScreen(EventsUiState.NotConnected, {}, {}, { _, _ -> }, {}, {})
}
