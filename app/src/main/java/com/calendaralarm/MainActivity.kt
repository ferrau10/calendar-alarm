package com.calendaralarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.calendaralarm.data.auth.ConnectResult
import com.calendaralarm.data.auth.GoogleAuthRepository
import com.calendaralarm.data.auth.android.ActivityHost
import com.calendaralarm.data.auth.android.IntentConsentResult
import com.calendaralarm.data.auth.android.PendingIntentConsent
import com.calendaralarm.data.auth.android.PlayServicesGoogleAuthClient
import com.calendaralarm.data.auth.android.PrefsAccountStore
import com.calendaralarm.ui.theme.CalendarAlarmTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // Temporary manual construction; Hilt bindings and real screens arrive with #10-#12 and #16.
    private val auth by lazy {
        GoogleAuthRepository(
            PlayServicesGoogleAuthClient(this, BuildConfig.GOOGLE_WEB_CLIENT_ID),
            PrefsAccountStore(this),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CalendarAlarmTheme {
                ConnectAccountDebugScreen(auth, ActivityHost(this))
            }
        }
    }
}

/** Minimal screen to try the Google connection by hand until onboarding (#12) lands. */
@Composable
private fun ConnectAccountDebugScreen(auth: GoogleAuthRepository, host: ActivityHost) {
    val account by auth.account.collectAsState()
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun show(result: ConnectResult) {
        status = when (result) {
            is ConnectResult.Connected -> null
            is ConnectResult.NeedsConsent -> "Waiting for consent…"
            ConnectResult.Cancelled -> "Cancelled"
            is ConnectResult.Failed -> "Failed: ${result.error.message}"
        }
    }

    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        scope.launch { show(auth.completeConsent(IntentConsentResult(it.data))) }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            if (account == null) {
                Button(onClick = {
                    scope.launch {
                        val result = auth.connect(host)
                        show(result)
                        if (result is ConnectResult.NeedsConsent) {
                            val pendingIntent = (result.request as PendingIntentConsent).pendingIntent
                            consentLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                        }
                    }
                }) { Text("Connect Google Calendar") }
            } else {
                Text("Connected as $account")
                OutlinedButton(onClick = { scope.launch { auth.disconnect() } }) { Text("Disconnect") }
            }
            status?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
