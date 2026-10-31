package com.calendaralarm.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.calendaralarm.MainActivity
import com.calendaralarm.domain.SelectionStore
import com.calendaralarm.domain.SettingsStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun selectionStore(): SelectionStore
    fun settingsStore(): SettingsStore
}

/** Home-screen widget: "Standup · alarm 09:55". Tapping it opens the events list. */
class NextAlarmWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val next = nextAlarm(
            entryPoint.selectionStore().all(),
            entryPoint.settingsStore().defaultLeadMinutes.first(),
            Instant.now(),
        )
        val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())
        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.widgetBackground)
                        .cornerRadius(16.dp)
                        .padding(12.dp)
                        .clickable(actionStartActivity<MainActivity>()),
                ) {
                    if (next == null) {
                        Text("No upcoming alarms", style = TextStyle(color = GlanceTheme.colors.onSurface))
                    } else {
                        Text(
                            next.title,
                            maxLines = 2,
                            style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp),
                        )
                        Text(
                            "Alarm ${time.format(next.alarmAt)} · starts ${time.format(next.start)}",
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                        )
                    }
                }
            }
        }
    }

    companion object {
        /** Keeps every placed widget current as selections or the default lead time change. */
        fun keepUpdated(context: Context, scope: CoroutineScope, selections: SelectionStore, settings: SettingsStore): Job =
            scope.launch {
                combine(selections.observe(), settings.defaultLeadMinutes) { list, lead -> list to lead }
                    .distinctUntilChanged()
                    .collect { runCatching { NextAlarmWidget().updateAll(context.applicationContext) } }
            }
    }
}

class NextAlarmWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextAlarmWidget()
}
