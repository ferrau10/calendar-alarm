package com.calendaralarm.screenshots

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.calendaralarm.alarm.ringing.RingingScreen
import com.calendaralarm.ui.events.DaySection
import com.calendaralarm.ui.events.EventRow
import com.calendaralarm.ui.events.EventsScreen
import com.calendaralarm.ui.events.EventsUiState
import com.calendaralarm.ui.onboarding.OnboardingScreen
import com.calendaralarm.ui.onboarding.OnboardingStep
import com.calendaralarm.ui.permissions.PermissionState
import com.calendaralarm.ui.settings.CalendarChoice
import com.calendaralarm.ui.settings.PermissionIntents
import com.calendaralarm.ui.settings.SettingsScreen
import com.calendaralarm.ui.settings.SettingsUiState
import com.calendaralarm.ui.theme.CalendarAlarmTheme
import java.io.File
import java.time.Instant
import java.time.LocalDate
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real screens with sample data for the Play Store listing (1080×1920 phone screenshots).
 * Skipped in normal test runs; generate with:
 * `./gradlew testDebugUnitTest --tests '*StoreScreenshots*' -Pscreenshots=true`
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi", application = Application::class)
class StoreScreenshots {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun onlyWhenRequested() = assumeTrue(System.getProperty("screenshots") == "true")

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.setContent { CalendarAlarmTheme(darkTheme = false, dynamicColor = false) { content() } }
        compose.waitForIdle()
        // Draw the window's view hierarchy in software (PixelCopy-based captureToImage hangs under Robolectric).
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bitmap)) }
        val dir = File(System.getProperty("screenshotDir") ?: "build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun row(
        id: String, title: String, time: String, color: String, alarm: String? = null,
        allDay: Boolean = false, custom: List<Int>? = null,
    ) = EventRow(id, "primary", title, Instant.EPOCH, time, color, allDay, alarm != null, null, alarm, custom)

    @Test
    fun events() = shoot("1-events") {
        EventsScreen(
            state = EventsUiState.Content(
                listOf(
                    DaySection(
                        LocalDate.of(2026, 11, 3), "Today",
                        listOf(
                            row("1", "Daily standup", "09:00 – 09:15", "#4285F4", "Alarms at 08:40 and 08:55 (20 and 5 min before)", custom = listOf(20, 5)),
                            row("2", "Design review", "11:00 – 12:00", "#4285F4", "Alarm at 10:55 (5 min before)"),
                            row("3", "Lunch with Sam", "12:30 – 13:30", "#33B679"),
                            row("4", "Quarterly planning", "15:00 – 16:30", "#8E24AA", "Alarms at 14:15 and 14:55 (45 and 5 min before)", custom = listOf(45, 5)),
                        ),
                    ),
                    DaySection(
                        LocalDate.of(2026, 11, 4), "Tomorrow",
                        listOf(
                            row("5", "Team offsite", "All day", "#F6BF26", allDay = true),
                            row("6", "1:1 with Alex", "10:00 – 10:30", "#4285F4", "Alarm at 09:30 (30 min before)", custom = listOf(30)),
                            row("7", "Dentist", "17:00 – 17:45", "#E67C73"),
                        ),
                    ),
                ),
            ),
            onRefresh = {}, onToggle = {}, onSetLead = { _, _ -> }, onConnect = {}, onOpenSettings = {},
        )
    }

    @Test
    fun ringing() = shoot("2-ringing") {
        RingingScreen("Quarterly planning · in 5 min", onDismiss = {}, onSnooze = {})
    }

    @Test
    fun settings() = shoot("3-settings") {
        SettingsScreen(
            state = SettingsUiState(
                defaultLeadMinutes = 5,
                calendars = listOf(
                    CalendarChoice("primary", "Work", "#4285F4", true),
                    CalendarChoice("family", "Family", "#33B679", false),
                ),
                account = "you@example.com",
                permissions = PermissionState.ALL_GRANTED,
                autoSelectVideoCalls = true,
            ),
            onBack = {}, onLeadChange = {}, onToggleCalendar = {}, onRetryCalendars = {}, onDisconnect = {},
            onTestAlarm = {}, onOpenSystemSettings = {}, permissionIntents = PermissionIntents(null, null, null, null),
        )
    }

    @Test
    fun welcome() = shoot("4-welcome") {
        OnboardingScreen(OnboardingStep.Welcome, null, {}, {}, {}, {}, {}, {}, {})
    }
}
