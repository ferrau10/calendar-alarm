package com.calendaralarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.calendaralarm.data.auth.ConnectResult
import com.calendaralarm.data.auth.GoogleAuthRepository
import com.calendaralarm.data.auth.android.ActivityHost
import com.calendaralarm.data.auth.android.IntentConsentResult
import com.calendaralarm.data.auth.android.PendingIntentConsent
import com.calendaralarm.sync.android.AlarmSyncWork
import com.calendaralarm.ui.events.EventsRoute
import com.calendaralarm.ui.events.EventsViewModel
import com.calendaralarm.ui.onboarding.OnboardingRoute
import com.calendaralarm.ui.onboarding.OnboardingStore
import com.calendaralarm.ui.onboarding.OnboardingViewModel
import com.calendaralarm.ui.permissions.AppPermissions
import com.calendaralarm.ui.settings.SettingsRoute
import com.calendaralarm.ui.settings.SettingsViewModel
import com.calendaralarm.ui.theme.CalendarAlarmTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

private object Routes {
    const val ONBOARDING = "onboarding"
    const val EVENTS = "events"
    const val SETTINGS = "settings"
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var auth: GoogleAuthRepository
    @Inject lateinit var permissions: AppPermissions
    @Inject lateinit var onboardingStore: OnboardingStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val start = if (onboardingStore.completed) Routes.EVENTS else Routes.ONBOARDING
        setContent {
            CalendarAlarmTheme {
                AppNavHost(start)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // App opened: pick up calendar changes for the selected events.
        if (auth.account.value != null) AlarmSyncWork.syncNow(this, refreshFromCalendar = true)
    }

    @Composable
    private fun AppNavHost(startDestination: String) {
        val nav = rememberNavController()
        NavHost(navController = nav, startDestination = startDestination) {
            composable(Routes.ONBOARDING) {
                val viewModel: OnboardingViewModel = hiltViewModel()
                var error by remember { mutableStateOf<String?>(null) }
                val connect = rememberConnect { result ->
                    error = (result as? ConnectResult.Failed)?.error?.message
                    viewModel.refresh()
                }
                OnboardingRoute(
                    viewModel = viewModel,
                    permissions = permissions,
                    onConnect = connect,
                    connectError = error,
                    onFinished = {
                        nav.navigate(Routes.EVENTS) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                    },
                )
            }
            composable(Routes.EVENTS) {
                val viewModel: EventsViewModel = hiltViewModel()
                val connect = rememberConnect { viewModel.refresh() }
                EventsRoute(viewModel, onConnect = connect, onOpenSettings = { nav.navigate(Routes.SETTINGS) })
            }
            composable(Routes.SETTINGS) {
                val viewModel: SettingsViewModel = hiltViewModel()
                SettingsRoute(viewModel, permissions, onBack = { nav.popBackStack() })
            }
        }
    }

    /** Returns a callback that runs the Google connection flow, including the consent screen if needed. */
    @Composable
    private fun rememberConnect(onDone: (ConnectResult) -> Unit): () -> Unit {
        val scope = rememberCoroutineScope()
        val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            scope.launch { onDone(auth.completeConsent(IntentConsentResult(it.data))) }
        }
        return {
            scope.launch {
                when (val result = auth.connect(ActivityHost(this@MainActivity))) {
                    is ConnectResult.NeedsConsent -> {
                        val pendingIntent = (result.request as PendingIntentConsent).pendingIntent
                        consentLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                    }
                    else -> onDone(result)
                }
            }
        }
    }
}
