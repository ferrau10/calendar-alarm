package com.calendaralarm.di

import android.content.Context
import com.calendaralarm.BuildConfig
import com.calendaralarm.alarm.AlarmRinger
import com.calendaralarm.alarm.AndroidAlarmScheduler
import com.calendaralarm.alarm.ringing.AndroidAlarmRinger
import com.calendaralarm.data.auth.GoogleAuthRepository
import com.calendaralarm.data.auth.android.PlayServicesGoogleAuthClient
import com.calendaralarm.data.auth.android.PrefsAccountStore
import com.calendaralarm.data.calendar.AccessTokenProvider
import com.calendaralarm.data.calendar.GoogleCalendarRepository
import com.calendaralarm.data.selection.RoomSelectionStore
import com.calendaralarm.data.selection.SelectionDatabase
import com.calendaralarm.data.settings.DataStoreSettingsStore
import com.calendaralarm.data.settings.settingsDataStore
import com.calendaralarm.domain.AlarmScheduler
import com.calendaralarm.domain.AuthRepository
import com.calendaralarm.domain.CalendarRepository
import com.calendaralarm.domain.SelectionStore
import com.calendaralarm.domain.SettingsStore
import com.calendaralarm.sync.AlarmSyncer
import com.calendaralarm.sync.ScheduledAlarmRegistry
import com.calendaralarm.sync.SyncTriggers
import com.calendaralarm.sync.android.PrefsScheduledAlarmRegistry
import com.calendaralarm.ui.onboarding.OnboardingStore
import com.calendaralarm.ui.onboarding.PrefsOnboardingStore
import com.calendaralarm.ui.permissions.AppPermissions
import com.calendaralarm.ui.permissions.PermissionChecker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import java.time.Clock
import javax.inject.Singleton

/** Binds every domain interface to its Android implementation. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemDefaultZone()

    // --- Google account & calendar ---

    @Provides
    @Singleton
    fun googleAuthRepository(@ApplicationContext context: Context): GoogleAuthRepository = GoogleAuthRepository(
        PlayServicesGoogleAuthClient(context, BuildConfig.GOOGLE_WEB_CLIENT_ID),
        PrefsAccountStore(context),
    )

    @Provides
    fun authRepository(repository: GoogleAuthRepository): AuthRepository = repository

    @Provides
    @Singleton
    fun httpClient(): HttpClient = GoogleCalendarRepository.httpClient()

    @Provides
    @Singleton
    fun calendarRepository(client: HttpClient, auth: AuthRepository): CalendarRepository =
        GoogleCalendarRepository(client, AccessTokenProvider { auth.accessToken() })

    // --- Local storage ---

    @Provides
    @Singleton
    fun selectionDatabase(@ApplicationContext context: Context): SelectionDatabase = SelectionDatabase.create(context)

    @Provides
    @Singleton
    fun selectionStore(database: SelectionDatabase): SelectionStore = RoomSelectionStore(database.selectedEventDao())

    @Provides
    @Singleton
    fun settingsStore(@ApplicationContext context: Context): SettingsStore =
        DataStoreSettingsStore(context.settingsDataStore)

    @Provides
    @Singleton
    fun onboardingStore(@ApplicationContext context: Context): OnboardingStore = PrefsOnboardingStore(context)

    // --- Alarms ---

    @Provides
    @Singleton
    fun alarmScheduler(@ApplicationContext context: Context, clock: Clock): AlarmScheduler =
        AndroidAlarmScheduler(context, clock)

    @Provides
    @Singleton
    fun alarmRinger(@ApplicationContext context: Context): AlarmRinger = AndroidAlarmRinger(context)

    @Provides
    @Singleton
    fun scheduledAlarmRegistry(@ApplicationContext context: Context): ScheduledAlarmRegistry =
        PrefsScheduledAlarmRegistry(context)

    @Provides
    @Singleton
    fun alarmSyncer(
        calendar: CalendarRepository,
        selections: SelectionStore,
        settings: SettingsStore,
        scheduler: AlarmScheduler,
        registry: ScheduledAlarmRegistry,
        clock: Clock,
    ): AlarmSyncer = AlarmSyncer(calendar, selections, settings, scheduler, registry, clock)

    @Provides
    @Singleton
    fun syncTriggers(selections: SelectionStore, settings: SettingsStore, syncer: AlarmSyncer): SyncTriggers =
        SyncTriggers(selections, settings, syncer)

    // --- Permissions ---

    @Provides
    @Singleton
    fun appPermissions(@ApplicationContext context: Context): AppPermissions = AppPermissions(context)

    @Provides
    fun permissionChecker(permissions: AppPermissions): PermissionChecker = permissions
}
